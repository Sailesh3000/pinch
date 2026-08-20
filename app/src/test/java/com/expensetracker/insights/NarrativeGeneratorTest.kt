package com.expensetracker.insights

import com.expensetracker.extraction.DeterministicRegexExtractor
import com.expensetracker.extraction.ExtractorChain
import com.expensetracker.extraction.GeminiNanoExtractor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NarrativeGeneratorTest {

    private lateinit var generator: NarrativeGenerator

    @Before
    fun setup() {
        generator = NarrativeGenerator(
            ExtractorChain(
                nanoExtractor = GeminiNanoExtractor(),
                regexExtractor = DeterministicRegexExtractor(),
            )
        )
    }

    @Test
    fun `generateSpendingSummary - fallback returns valid narrative`() = runTest {
        val facts = SpendingFacts(
            currentSpend = "₹15,000",
            previousSpend = "₹12,000",
            deltaDescription = "up ₹3,000 (25.0%)",
            transactionCount = 42,
            topCategoryName = "Food & Dining",
            topCategoryAmount = "₹5,000",
            topMerchantName = "Swiggy",
            topMerchantAmount = "₹3,000",
            topMerchantCount = 15,
            dailyAverage = "₹500",
        )

        val result = generator.generateSpendingSummary(facts)
        assertNotNull(result.narrative)
        assertTrue(result.narrative.contains("15,000"))
    }

    @Test
    fun `generateAnomalyNarrative - empty list returns clean message`() = runTest {
        val result = generator.generateAnomalyNarrative(emptyList())
        assertEquals("No unusual spending patterns detected this month.", result.narrative)
    }

    @Test
    fun `generateAnomalyNarrative - single anomaly returns alert`() = runTest {
        val anomalies = listOf(
            AnomalyItem(
                merchantName = "Louis Vuitton",
                description = "₹50,000 spent at Louis Vuitton (2x your daily average)",
                shortDescription = "Unusually large: ₹50,000 at Louis Vuitton",
                severity = AnomalySeverity.HIGH,
                timestamp = System.currentTimeMillis(),
                amount = 50000.0,
            )
        )

        val result = generator.generateAnomalyNarrative(anomalies)
        assertNotNull(result.narrative)
        assertTrue(result.claims.isNotEmpty())
        assertEquals("Louis Vuitton", result.claims.first().filterValue)
    }
}
