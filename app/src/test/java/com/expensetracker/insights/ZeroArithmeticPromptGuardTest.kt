package com.expensetracker.insights

import com.expensetracker.extraction.DeterministicRegexExtractor
import com.expensetracker.extraction.ExtractorChain
import com.expensetracker.extraction.GeminiNanoExtractor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §12.1 `ZeroArithmeticPromptGuardTest` — asserts that fact-injection tokens
 * format accurately and that the SLM prompts contain zero mathematical requests.
 *
 * The app's zero-arithmetic SLM rule (§3, FR-INSIGHT-02) means a prompt may
 * carry pre-computed number tokens but must never ask the model to add,
 * subtract, average, compare, or derive any figure itself.
 */
class ZeroArithmeticPromptGuardTest {

    private val facts = SpendingFacts(
        currentSpend = "₹14,200.00",
        previousSpend = "₹10,750.00",
        deltaDescription = "up ₹3,450.00 (+32.1%)",
        transactionCount = 8,
        topCategoryName = "Food & Dining",
        topCategoryAmount = "₹5,300.00",
        topMerchantName = "Swiggy",
        topMerchantAmount = "₹3,900.00",
        topMerchantCount = 6,
        dailyAverage = "₹458.06",
    )

    // Words that would instruct the model to do math. Bare operator symbols are
    // intentionally absent — hyphens in prose ("2-sentence") are not arithmetic.
    private val arithmeticRequestWords = listOf(
        "sum", "add", "subtract", "multiply", "divide",
        "average", "mean", "percent", "percentage", "delta",
        "calculate", "compute", "derive", "total up",
    )

    private val generator = NarrativeGenerator(
        ExtractorChain(
            nanoExtractor = GeminiNanoExtractor(),
            regexExtractor = DeterministicRegexExtractor(),
        )
    )

    @Test
    fun `fact tokens inject the exact pre-computed strings`() {
        val prompt = generator.buildSummaryPrompt(facts)
        assertTrue(prompt.contains("₹14,200.00"))
        assertTrue(prompt.contains("₹3,900.00"))
        assertTrue(prompt.contains("Swiggy"))
        assertTrue(prompt.contains("Food & Dining"))
        assertTrue(prompt.contains("8"))
    }

    @Test
    fun `summary prompt instructions request no arithmetic`() {
        val prompt = generator.buildSummaryPrompt(facts)
        val instructionLines = prompt
            .lineSequence()
            .filterNot { it.startsWith("- ") } // exclude the FACTS data block
            .joinToString("\n")

        for (word in arithmeticRequestWords) {
            val pattern = Regex("""\b${Regex.escape(word)}\b""", RegexOption.IGNORE_CASE)
            assertFalse(
                "Summary prompt must not ask the SLM to '$word' — got: ${instructionLines.take(200)}",
                pattern.containsMatchIn(instructionLines),
            )
        }
    }

    @Test
    fun `summary prompt tells the model to use exact numbers and not calculate`() {
        val prompt = generator.buildSummaryPrompt(facts)
        assertTrue(prompt.contains("Do NOT perform any calculations", ignoreCase = true))
        assertTrue(prompt.contains("Use the exact numbers provided", ignoreCase = true))
    }

    @Test
    fun `anomaly prompt contains no arithmetic instructions`() {
        val anomalies = listOf(
            AnomalyItem(
                merchantName = "Louis Vuitton",
                description = "₹50,000 spent at Louis Vuitton (2x your daily average of ₹25,000)",
                shortDescription = "Unusually large",
                severity = AnomalySeverity.HIGH,
                timestamp = 0L,
                amount = 50000.0,
            ),
        )
        val prompt = generator.buildAnomalyPrompt(anomalies)
        val instructionLines = prompt
            .lineSequence()
            .filterNot { it.startsWith("- ") }
            .joinToString("\n")

        for (word in arithmeticRequestWords) {
            val pattern = Regex("""\b${Regex.escape(word)}\b""", RegexOption.IGNORE_CASE)
            assertFalse(
                "Anomaly prompt must not ask the SLM to '$word' — got: ${instructionLines.take(200)}",
                pattern.containsMatchIn(instructionLines),
            )
        }
    }

    @Test
    fun `empty anomaly prompt is the benign no-anomaly message`() {
        var result: NarrativeResult? = null
        runTest { result = generator.generateAnomalyNarrative(emptyList()) }
        assertEquals("No unusual spending patterns detected this month.", result?.narrative)
    }
}