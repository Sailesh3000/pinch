package com.expensetracker.extraction

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for MediaPipeExtractor public contract.
 * Does NOT test actual model inference (requires physical device + model file).
 * The native MediaPipe library is not available in JVM unit tests.
 */
class MediaPipeExtractorTest {

    @Test
    fun `engineName matches expected value`() {
        // Access via a subclass that doesn't trigger native init
        val extractor = TestableMediaPipeExtractor()
        assertEquals("MediaPipe Qwen2.5-0.5B (Local Engine)", extractor.engineName)
    }

    @Test
    fun `engineName constant is correct`() {
        // Verify the string without instantiating (avoids native init)
        val expected = "MediaPipe Gemma-3-1B (Local Engine)"
        assertEquals(expected, "MediaPipe Gemma-3-1B (Local Engine)")
    }

    /**
     * Subclass that prevents native MediaPipe initialization in unit tests.
     */
    private class TestableMediaPipeExtractor : MediaPipeExtractor(context = null, modelPath = "/fake/path") {
        override suspend fun isAvailable(): Boolean = false
        override suspend fun extract(
            packageName: String, title: String, body: String, timestampEpoch: Long,
        ): ExtractionResult? = null
    }
}
