package com.expensetracker.extraction

import android.content.Context
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runtime hardware capability detector (spec §11, Phase 4).
 * Routes extraction to the best available engine:
 *   1. Gemini Nano (AICore AVAILABLE)
 *   2. MediaPipe Gemma-3-1B (model file present on disk)
 *   3. Deterministic Regex (always available)
 */
@Singleton
class CapabilityDetector @Inject constructor(
    private val context: Context,
    private val nanoExtractor: GeminiNanoExtractor,
) {

    /**
     * Returns the best available engine type for this device.
     */
    suspend fun detect(): EngineType {
        if (nanoExtractor.isAvailable()) return EngineType.GEMINI_NANO
        if (isMediaPipeModelPresent()) return EngineType.MEDIAPIPE
        return EngineType.REGEX
    }

    /**
     * Checks if the Gemma 3 1B .task model file exists in internal storage.
     */
    fun isMediaPipeModelPresent(): Boolean {
        val modelFile = File(context.filesDir, MODEL_FILENAME)
        return modelFile.exists() && modelFile.length() > MIN_MODEL_SIZE_BYTES
    }

    /**
     * Returns the absolute path to the MediaPipe model file, or null if absent.
     */
    fun mediaPipeModelPath(): String? {
        val modelFile = File(context.filesDir, MODEL_FILENAME)
        return if (modelFile.exists() && modelFile.length() > MIN_MODEL_SIZE_BYTES) {
            modelFile.absolutePath
        } else {
            null
        }
    }

    companion object {
        const val MODEL_FILENAME = "qwen2.5-0.5b-instruct.task"
        private const val MIN_MODEL_SIZE_BYTES = 50_000_000L // 50 MB sanity check
    }
}
