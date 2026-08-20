package com.expensetracker.ingestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec §12.1 — DeduplicationEngineTest: verify dual-source merging within the
 * 180s window and rejection of events arriving later (FR-DEDUP-02).
 */
class DeduplicationEngineTest {

    private var currentTime = 1_000_000L
    private val engine = DeduplicationEngine(now = { currentTime })

    private fun decision(amount: Double, text: String, atMillis: Long) =
        engine.evaluate(amount, "INR", text, atMillis)

    @Test
    fun `same transaction via sms then app push within 30s is a duplicate`() {
        val first = decision(850.0, "Rs 850 debited from A/c *1234 at Swiggy", 10_000L)
        currentTime = 10_000L
        val second = decision(850.0, "Paid Rs 850.00 to Swiggy via PhonePe UPI", 40_000L)
        assertTrue(first is DeduplicationEngine.Decision.Fresh)
        assertTrue(second is DeduplicationEngine.Decision.Duplicate)
    }

    @Test
    fun `same transaction within 120s is a duplicate`() {
        decision(1200.0, "Rs 1200 debited at Big Bazaar", 10_000L)
        currentTime = 10_000L
        val second = decision(1200.0, "Rs 1,200.00 spent at Big Bazaar", 130_000L)
        assertTrue(second is DeduplicationEngine.Decision.Duplicate)
    }

    @Test
    fun `event arriving after 180s window is fresh`() {
        decision(500.0, "Rs 500 debited at Cafe", 10_000L)
        currentTime = 10_000L
        val second = decision(500.0, "Rs 500 debited at Cafe", 10_000L + 181_000L)
        assertTrue(second is DeduplicationEngine.Decision.Fresh)
    }

    @Test
    fun `different amounts are never merged`() {
        decision(500.0, "Rs 500 debited at Cafe", 10_000L)
        currentTime = 10_000L
        val second = decision(501.0, "Rs 501 debited at Cafe", 20_000L)
        assertTrue(second is DeduplicationEngine.Decision.Fresh)
    }

    @Test
    fun `different payees with same amount within window merge per spec`() {
        // FR-DEDUP-02: amount equal + currency equal is sufficient.
        decision(100.0, "Rs 100 to Swiggy", 10_000L)
        currentTime = 10_000L
        val second = decision(100.0, "Rs 100 to Zomato", 20_000L)
        assertTrue(second is DeduplicationEngine.Decision.Duplicate)
    }

    @Test
    fun `same payee and amount merge across app and sms sources`() {
        decision(850.0, "UPI/SWIGGY/PAY/1234 - Rs 850", 5_000L)
        currentTime = 5_000L
        val second = decision(850.0, "Debited Rs 850 at Swiggy", 20_000L)
        assertTrue(second is DeduplicationEngine.Decision.Duplicate)
    }

    @Test
    fun `dedup hash is deterministic for same inputs`() {
        val h1 = DedupKey.compute(850.0, "INR", "swiggy", 100_000L)
        val h2 = DedupKey.compute(850.0, "INR", "swiggy", 100_000L)
        assertEquals(h1, h2)
        val h3 = DedupKey.compute(850.0, "INR", "swiggy", 100_000L + 1L)
        // Same 180s window bucket.
        assertEquals(h1, h3)
        val h4 = DedupKey.compute(850.0, "INR", "swiggy", 100_000L + 180_001L)
        assertNotEquals(h1, h4)
    }

    @Test
    fun `normalizePayee extracts to target`() {
        assertEquals("swiggy", engine.normalizePayee("Rs 850 debited to Swiggy"))
    }

    @Test
    fun `normalizePayee returns null for noise`() {
        assertNull(engine.normalizePayee("Your monthly statement is ready"))
    }

    @Test
    fun `duplicate carries existing hash`() {
        val first = decision(42.0, "Rs 42 to Cafe", 10_000L) as DeduplicationEngine.Decision.Fresh
        currentTime = 10_000L
        val second = decision(42.0, "Rs 42 to Cafe", 30_000L) as DeduplicationEngine.Decision.Duplicate
        assertEquals(first.dedupHash, second.existingDedupHash)
    }
}
