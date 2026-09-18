package com.expensetracker.extraction

import android.content.Context
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runtime hardware capability detector (spec §11, Phase 4).
 * Routes extraction to the best available engine:
 *   1. Gemini Nano (AICore AVAILABLE)
 *   2. MediaPipe Qwen2.5-0.5B (model present — PAD asset pack first, then internal storage)
 *   3. Deterministic Regex (always available)
 */
@Singleton
class CapabilityDetector @Inject constructor(
    private val context: Context,
    private val nanoExtractor: GeminiNanoExtractor,
    private val modelAssetProvider: ModelAssetProvider,
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
     * Checks if the model file exists — in the PAD asset pack (Play Store
     * installs) or in internal storage (sideload/dev installs).
     */
    fun isMediaPipeModelPresent(): Boolean {
        return modelAssetProvider.modelPath() != null ||
            internalStorageModelFile().let { it.exists() && it.length() > MIN_MODEL_SIZE_BYTES }
    }

    /**
     * Returns the absolute path to the MediaPipe model file, or null if absent.
     * Resolution order: PAD asset pack -> internal storage download.
     */
    fun mediaPipeModelPath(): String? {
        modelAssetProvider.modelPath()?.let { return it }
        return internalStorageModelFile()
            .takeIf { it.exists() && it.length() > MIN_MODEL_SIZE_BYTES }
            ?.absolutePath
    }

    private fun internalStorageModelFile(): File = File(context.filesDir, MODEL_FILENAME)

    companion object {
        const val MODEL_FILENAME = "qwen2.5-0.5b-pinch-finetuned.litertlm"
        private const val MIN_MODEL_SIZE_BYTES = 50_000_000L // 50 MB sanity check
    }
}