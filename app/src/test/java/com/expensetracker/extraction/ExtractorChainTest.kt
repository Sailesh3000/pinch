package com.expensetracker.extraction

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for ExtractorChain 3-tier routing: Nano → MediaPipe → Regex.
 */
class ExtractorChainTest {

    private fun chain(
        nanoAvailable: Boolean = false,
        nanoResult: ExtractionResult? = null,
        mediaPipeAvailable: Boolean = false,
        mediaPipeResult: ExtractionResult? = null,
        regexResult: ExtractionResult? = null,
    ): ExtractorChain {
        val nano = FakeNanoExtractor(available = nanoAvailable, result = nanoResult)
        val regex = FakeRegexExtractor(result = regexResult)
        val mp = if (mediaPipeAvailable || mediaPipeResult != null) {
            FakeMediaPipeExtractor(available = mediaPipeAvailable, result = mediaPipeResult)
        } else null
        return ExtractorChain(nano, regex, mp)
    }

    @Test
    fun `routes to Nano when available`() = runTest {
        val nanoResult = ExtractionResult(
            isFinancialTransaction = true, amount = 500.0, currency = "INR",
            txnType = "DEBIT", merchantOrPayee = "Swiggy", category = "Food & Dining",
            confidenceScore = 0.95f,
        )
        val chain = chain(nanoAvailable = true, nanoResult = nanoResult)
        val result = chain.extract("com.swiggy", "Debit", "500 paid", 0L)
        assertEquals("Swiggy", result.merchantOrPayee)
        assertEquals(EngineType.GEMINI_NANO, chain.activeEngineType())
    }

    @Test
    fun `routes to MediaPipe when Nano unavailable`() = runTest {
        val mpResult = ExtractionResult(
            isFinancialTransaction = true, amount = 300.0, currency = "INR",
            txnType = "DEBIT", merchantOrPayee = "Uber", category = "Transportation",
            confidenceScore = 0.85f,
        )
        val chain = chain(nanoAvailable = false, mediaPipeAvailable = true, mediaPipeResult = mpResult)
        val result = chain.extract("com.uber", "Debit", "300 paid", 0L)
        assertEquals("Uber", result.merchantOrPayee)
        assertEquals(EngineType.MEDIAPIPE, chain.activeEngineType())
    }

    @Test
    fun `routes to Regex when both Nano and MediaPipe unavailable`() = runTest {
        val regexResult = ExtractionResult(
            isFinancialTransaction = true, amount = 100.0, currency = "INR",
            txnType = "DEBIT", merchantOrPayee = "Amazon", category = "Shopping",
            confidenceScore = 0.90f,
        )
        val chain = chain(nanoAvailable = false, mediaPipeAvailable = false, regexResult = regexResult)
        val result = chain.extract("com.amazon", "Debit", "100 paid", 0L)
        assertEquals("Amazon", result.merchantOrPayee)
        assertEquals(EngineType.REGEX, chain.activeEngineType())
    }

    @Test
    fun `falls through MediaPipe to Regex when MediaPipe returns null`() = runTest {
        val regexResult = ExtractionResult(
            isFinancialTransaction = true, amount = 200.0, currency = "INR",
            txnType = "DEBIT", merchantOrPayee = "Flipkart", category = "Shopping",
            confidenceScore = 0.75f,
        )
        val chain = chain(nanoAvailable = false, mediaPipeAvailable = true, mediaPipeResult = null, regexResult = regexResult)
        val result = chain.extract("com.flipkart", "Debit", "200 paid", 0L)
        assertEquals("Flipkart", result.merchantOrPayee)
        assertEquals(EngineType.REGEX, chain.activeEngineType())
    }

    @Test
    fun `returns fallback when all engines fail`() = runTest {
        val chain = chain(nanoAvailable = false, mediaPipeAvailable = false)
        val result = chain.extract("com.unknown", "Test", "No amount here", 0L)
        assertEquals(false, result.isFinancialTransaction)
        assertEquals(0.0, result.amount, 0.001)
    }

    @Test
    fun `updateMediaPipeExtractor hot-swaps the engine`() = runTest {
        val chain = chain(nanoAvailable = false, mediaPipeAvailable = false)
        assertEquals(EngineType.REGEX, chain.activeEngineType())

        val mp = FakeMediaPipeExtractor(available = true, result = ExtractionResult(
            isFinancialTransaction = true, amount = 50.0, currency = "INR",
            txnType = "DEBIT", merchantOrPayee = "Test", category = "Shopping",
            confidenceScore = 0.80f,
        ))
        chain.updateMediaPipeExtractor(mp)

        val result = chain.extract("com.test", "Debit", "50 paid", 0L)
        assertEquals(EngineType.MEDIAPIPE, chain.activeEngineType())
    }

    @Test
    fun `activeEngineName returns display name`() = runTest {
        val chain = chain(nanoAvailable = false, mediaPipeAvailable = false)
        val name = chain.activeEngineName()
        assertEquals(EngineType.REGEX.displayName, name)
    }

    // --- Fakes ---

    private class FakeNanoExtractor(
        private val available: Boolean,
        private val result: ExtractionResult?,
    ) : GeminiNanoExtractor() {
        override suspend fun isAvailable(): Boolean = available
        override suspend fun extract(
            packageName: String, title: String, body: String, timestampEpoch: Long,
        ): ExtractionResult? = result
    }

    private class FakeRegexExtractor(
        private val result: ExtractionResult?,
    ) : DeterministicRegexExtractor() {
        override suspend fun extract(
            packageName: String, title: String, body: String, timestampEpoch: Long,
        ): ExtractionResult? = result
    }

    private class FakeMediaPipeExtractor(
        private val available: Boolean,
        private val result: ExtractionResult?,
    ) : MediaPipeExtractor(context = null, modelPath = "/dev/null") {
        override suspend fun isAvailable(): Boolean = available
        override suspend fun extract(
            packageName: String, title: String, body: String, timestampEpoch: Long,
        ): ExtractionResult? = result
    }
}
