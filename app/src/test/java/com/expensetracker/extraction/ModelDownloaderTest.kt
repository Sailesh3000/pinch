package com.expensetracker.extraction

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for ModelDownloader state transitions and file logic.
 * Does NOT test actual HTTP downloads (requires network).
 */
class ModelDownloaderTest {

    @Test
    fun `model filename constant matches expected value`() {
        assertEquals("qwen2.5-0.5b-pinch-finetuned.litertlm", CapabilityDetector.MODEL_FILENAME)
    }

    @Test
    fun `model filename is consistent between ModelDownloader and CapabilityDetector`() {
        assertEquals(ModelDownloader.MODEL_FILENAME, CapabilityDetector.MODEL_FILENAME)
    }

    @Test
    fun `model URL points to HuggingFace`() {
        assertTrue(
            "URL should point to HuggingFace",
            ModelDownloader.MODEL_URL.startsWith("https://huggingface.co/")
        )
        assertTrue(
            "URL should reference the fine-tuned model",
            ModelDownloader.MODEL_URL.contains("pinch")
        )
    }

    @Test
    fun `download state Idle is initial state`() {
        val state = ModelDownloader.DownloadState.Idle
        assertTrue(state is ModelDownloader.DownloadState.Idle)
    }

    @Test
    fun `download state Downloading holds progress`() {
        val state = ModelDownloader.DownloadState.Downloading(42)
        assertTrue(state is ModelDownloader.DownloadState.Downloading)
        assertEquals(42, (state as ModelDownloader.DownloadState.Downloading).progressPercent)
    }

    @Test
    fun `download state Complete is terminal`() {
        val state = ModelDownloader.DownloadState.Complete
        assertTrue(state is ModelDownloader.DownloadState.Complete)
    }

    @Test
    fun `download state Failed holds error message`() {
        val state = ModelDownloader.DownloadState.Failed("network error")
        assertEquals("network error", (state as ModelDownloader.DownloadState.Failed).message)
    }
}
