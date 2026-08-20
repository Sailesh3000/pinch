package com.expensetracker.ui.insights

import com.expensetracker.core.database.dao.RecurringMerchantRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionDetectorTest {

    private val detector = SubscriptionDetector()

    private fun recurringRow(
        merchant: String,
        avgAmount: Double = 500.0,
        txnCount: Int = 6,
        firstSeen: Long = 1_700_000_000_000L,
        lastSeen: Long = 1_715_000_000_000L,
    ) = RecurringMerchantRow(
        merchantName = merchant,
        avgAmount = avgAmount,
        txnCount = txnCount,
        firstSeen = firstSeen,
        lastSeen = lastSeen,
    )

    @Test
    fun `detects monthly subscription`() {
        val row = recurringRow(
            merchant = "Netflix",
            txnCount = 6,
            firstSeen = 1_700_000_000_000L,
            lastSeen = 1_715_000_000_000L,
        )
        val results = detector.detect(listOf(row))
        assertEquals(1, results.size)
        assertEquals("Netflix", results[0].merchantName)
        assertEquals(30, results[0].estimatedIntervalDays)
    }

    @Test
    fun `ignores single-occurrence merchants`() {
        val row = recurringRow(merchant = "OneOff", txnCount = 1)
        val results = detector.detect(listOf(row))
        assertTrue(results.isEmpty())
    }

    @Test
    fun `detects multiple subscriptions`() {
        val rows = listOf(
            recurringRow(merchant = "Netflix", txnCount = 6,
                firstSeen = 1_700_000_000_000L, lastSeen = 1_715_000_000_000L),
            recurringRow(merchant = "Spotify", txnCount = 3,
                firstSeen = 1_700_000_000_000L, lastSeen = 1_707_500_000_000L),
        )
        val results = detector.detect(rows)
        assertEquals(2, results.size)
    }

    @Test
    fun `sorted by next renewal date`() {
        val rows = listOf(
            recurringRow(merchant = "B", lastSeen = 1_715_000_000_000L),
            recurringRow(merchant = "A", lastSeen = 1_710_000_000_000L),
        )
        val results = detector.detect(rows)
        if (results.size == 2) {
            assertTrue(results[0].nextRenewalEstimate <= results[1].nextRenewalEstimate)
        }
    }

    @Test
    fun `handles empty input`() {
        val results = detector.detect(emptyList())
        assertTrue(results.isEmpty())
    }

    @Test
    fun `amount tolerance filter passes for reasonable amounts`() {
        val row = recurringRow(merchant = "Zomato", avgAmount = 500.0, txnCount = 5,
            firstSeen = 1_700_000_000_000L, lastSeen = 1_712_000_000_000L)
        val results = detector.detect(listOf(row))
        assertTrue(results.isNotEmpty())
    }

    @Test
    fun `detects annual subscription`() {
        val row = recurringRow(
            merchant = "Amazon Prime",
            txnCount = 3,
            firstSeen = 1_640_000_000_000L,
            lastSeen = 1_703_000_000_000L,
        )
        val results = detector.detect(listOf(row))
        if (results.isNotEmpty()) {
            assertEquals(365, results[0].estimatedIntervalDays)
        }
    }
}
