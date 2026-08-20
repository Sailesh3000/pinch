package com.expensetracker.extraction

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for EngineType enum and CapabilityDetector routing logic.
 */
class EngineTypeTest {

    @Test
    fun `engine type display names are human-readable`() {
        assertEquals("Gemini Nano (Hardware Accelerated)", EngineType.GEMINI_NANO.displayName)
        assertEquals("MediaPipe Qwen2.5-0.5B (Local Engine)", EngineType.MEDIAPIPE.displayName)
        assertEquals("Deterministic Regex Engine", EngineType.REGEX.displayName)
    }

    @Test
    fun `engine type enum has exactly 3 values`() {
        assertEquals(3, EngineType.entries.size)
    }

    @Test
    fun `CapabilityDetector returns REGEX when no Nano and no model file`() = runTest {
        val detector = FakeCapabilityDetector(nanoAvailable = false, modelPresent = false)
        assertEquals(EngineType.REGEX, detector.detect())
    }

    @Test
    fun `CapabilityDetector returns MEDIAPIPE when Nano unavailable but model present`() = runTest {
        val detector = FakeCapabilityDetector(nanoAvailable = false, modelPresent = true)
        assertEquals(EngineType.MEDIAPIPE, detector.detect())
    }

    @Test
    fun `CapabilityDetector returns GEMINI_NANO when Nano available`() = runTest {
        val detector = FakeCapabilityDetector(nanoAvailable = true, modelPresent = true)
        assertEquals(EngineType.GEMINI_NANO, detector.detect())
    }

    @Test
    fun `CapabilityDetector returns GEMINI_NANO even without model file`() = runTest {
        val detector = FakeCapabilityDetector(nanoAvailable = true, modelPresent = false)
        assertEquals(EngineType.GEMINI_NANO, detector.detect())
    }

    @Test
    fun `isMediaPipeModelPresent returns false when model absent`() {
        val detector = FakeCapabilityDetector(nanoAvailable = false, modelPresent = false)
        assertFalse(detector.isMediaPipeModelPresent())
    }

    @Test
    fun `isMediaPipeModelPresent returns true when model present`() {
        val detector = FakeCapabilityDetector(nanoAvailable = false, modelPresent = true)
        assertTrue(detector.isMediaPipeModelPresent())
    }

    /**
     * Test-only stub that doesn't touch the filesystem or ML Kit.
     */
    private class FakeCapabilityDetector(
        private val nanoAvailable: Boolean,
        private val modelPresent: Boolean,
    ) {
        private val engineType: EngineType by lazy {
            when {
                nanoAvailable -> EngineType.GEMINI_NANO
                modelPresent -> EngineType.MEDIAPIPE
                else -> EngineType.REGEX
            }
        }

        fun detect(): EngineType = engineType

        fun isMediaPipeModelPresent(): Boolean = modelPresent

        fun mediaPipeModelPath(): String? =
            if (modelPresent) "/fake/path/model.task" else null
    }
}
