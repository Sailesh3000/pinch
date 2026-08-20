package com.expensetracker.extraction

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests for [ExtractorChain] — verifies tier routing on a real
 * Android device. On devices without Gemini Nano/MediaPipe, the chain falls
 * through to the deterministic regex tier.
 */
@RunWith(AndroidJUnit4::class)
class ExtractorChainInstrumentedTest {

    @Test
    fun extract_regexFallback_returnsFinancialResult() = runBlocking {
        val chain = ExtractorChain(
            nanoExtractor = UnavailableNanoExtractor(),
            regexExtractor = DeterministicRegexExtractor(),
        )
        val result = chain.extract(
            packageName = "com.phonepe.app",
            title = "Payment Successful",
            body = "Rs. 450.00 debited. UPI/SWIGGY/PHONEPE/1234, A/c *1234",
            timestampEpoch = 1_700_000_000_000L,
        )
        assertTrue(result.isFinancialTransaction)
        assertEquals(450.0, result.amount, 0.001)
        assertEquals("SWIGGY", result.merchantOrPayee)
        assertEquals("Food & Dining", result.category)
        assertEquals(EngineType.REGEX, chain.activeEngineType())
    }

    @Test
    fun extract_nonFinancialNotification_returnsNonFinancialResult() = runBlocking {
        val chain = ExtractorChain(
            nanoExtractor = UnavailableNanoExtractor(),
            regexExtractor = DeterministicRegexExtractor(),
        )
        val result = chain.extract(
            packageName = "com.phonepe.app",
            title = "Security Alert",
            body = "Your OTP is 482913. Valid for 30 seconds. Do not share.",
            timestampEpoch = 1_700_000_000_000L,
        )
        assertTrue(
            "OTP notification must be classified as non-financial",
            !result.isFinancialTransaction,
        )
    }

    @Test
    fun generateText_noLlmAvailable_returnsNull() = runBlocking {
        val chain = ExtractorChain(
            nanoExtractor = UnavailableNanoExtractor(),
            regexExtractor = DeterministicRegexExtractor(),
        )
        val result = chain.generateText("Summarize spending")
        assertNotNull("generateText should return null when no LLM is available", result == null)
    }

    @Test
    fun isNanoAvailable_withoutAicore_returnsFalse() = runBlocking {
        val chain = ExtractorChain(
            nanoExtractor = UnavailableNanoExtractor(),
            regexExtractor = DeterministicRegexExtractor(),
        )
        assertTrue(
            "Nano should be unavailable on devices without AICore",
            !chain.isNanoAvailable(),
        )
    }
}
