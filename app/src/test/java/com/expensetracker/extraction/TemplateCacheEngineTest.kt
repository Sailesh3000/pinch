package com.expensetracker.extraction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import com.expensetracker.ui.FakeCategoryDao
import org.junit.Test

/**
 * Spec §12.1 — TemplateCacheEngineTest: structural hashing, regex generation,
 * and Tier 0 cache lookup/rejection logic.
 */
class TemplateCacheEngineTest {

    private val engine = TemplateCacheEngine(
        templateCacheDao = FakeTemplateCacheDao(),
        categoryDao = FakeCategoryDao(),
    )

    @Test
    fun `structural hash is deterministic for same input`() {
        val hash1 = engine.structuralHash("com.phonepe", "Paid Rs 450 to Zomato via PhonePe UPI")
        val hash2 = engine.structuralHash("com.phonepe", "Paid Rs 450 to Zomato via PhonePe UPI")
        assertEquals(hash1, hash2)
    }

    @Test
    fun `structural hash is same for different amounts but same skeleton`() {
        val hash1 = engine.structuralHash("com.phonepe", "Paid Rs 100 to Zomato via PhonePe UPI")
        val hash2 = engine.structuralHash("com.phonepe", "Paid Rs 999 to Zomato via PhonePe UPI")
        assertEquals(hash1, hash2)
    }

    @Test
    fun `structural hash differs for different merchants`() {
        val hash1 = engine.structuralHash("com.phonepe", "Paid Rs 100 to Zomato via PhonePe UPI")
        val hash2 = engine.structuralHash("com.phonepe", "Paid Rs 100 to Swiggy via PhonePe UPI")
        assertTrue(hash1 != hash2)
    }

    @Test
    fun `structural hash differs for different packages`() {
        val hash1 = engine.structuralHash("com.phonepe", "Paid Rs 100 to Zomato via PhonePe UPI")
        val hash2 = engine.structuralHash("com.gpay", "Paid Rs 100 to Zomato via PhonePe UPI")
        assertTrue(hash1 != hash2)
    }

    @Test
    fun `buildExtractionRegex captures amount group`() {
        val regex = engine.buildExtractionRegex("Zomato")
        val pattern = Regex(regex, RegexOption.IGNORE_CASE)
        val match = pattern.find("Paid Rs 450 to Zomato via PhonePe UPI")
        assertNotNull(match)
        assertEquals("450", match!!.groups["amount"]?.value)
    }

    @Test
    fun `buildExtractionRegex is case insensitive`() {
        val regex = engine.buildExtractionRegex("ZOMATO")
        val pattern = Regex(regex, RegexOption.IGNORE_CASE)
        val match = pattern.find("Paid Rs 450 to Zomato via PhonePe UPI")
        assertNotNull(match)
    }

    @Test
    fun `buildExtractionRegex fails for unrelated merchant`() {
        val regex = engine.buildExtractionRegex("Swiggy")
        val pattern = Regex(regex, RegexOption.IGNORE_CASE)
        val match = pattern.find("Paid Rs 450 to Zomato via PhonePe UPI")
        assertNull(match)
    }

    @Test
    fun `buildExtractionRegex handles amount with decimals`() {
        val regex = engine.buildExtractionRegex("Zomato")
        val pattern = Regex(regex, RegexOption.IGNORE_CASE)
        val match = pattern.find("Paid Rs 450.50 to Zomato via PhonePe UPI")
        assertNotNull(match)
        assertEquals("450.50", match!!.groups["amount"]?.value)
    }

    @Test
    fun `buildExtractionRegex handles amount with commas`() {
        val regex = engine.buildExtractionRegex("Zomato")
        val pattern = Regex(regex, RegexOption.IGNORE_CASE)
        val match = pattern.find("Paid Rs 1,450 to Zomato via PhonePe UPI")
        assertNotNull(match)
        assertEquals("1,450", match!!.groups["amount"]?.value)
    }

    @Test
    fun `buildExtractionRegex handles INR prefix`() {
        val regex = engine.buildExtractionRegex("Zomato")
        val pattern = Regex(regex, RegexOption.IGNORE_CASE)
        val match = pattern.find("INR 450 to Zomato via PhonePe UPI")
        assertNotNull(match)
        assertEquals("450", match!!.groups["amount"]?.value)
    }
}
