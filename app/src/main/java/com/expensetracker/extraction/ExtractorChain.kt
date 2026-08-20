package com.expensetracker.extraction

import android.util.Log

/**
 * Tiered routing (spec §1.2 / §6 / §11):
 *   Tier 0 — TemplateCacheEngine (checked before this chain in NotificationProcessor)
 *   Tier 1a — Gemini Nano (AICore)
 *   Tier 1b — MediaPipe Gemma-3-1B (model on disk)
 *   Tier 2 — Deterministic regex (always available)
 */
class ExtractorChain(
    private val nanoExtractor: GeminiNanoExtractor,
    private val regexExtractor: DeterministicRegexExtractor,
    private var mediaPipeExtractor: MediaPipeExtractor? = null,
) {

    private var nanoAvailableCache: Boolean? = null
    private var activeEngine: EngineType = EngineType.REGEX

    fun updateMediaPipeExtractor(extractor: MediaPipeExtractor) {
        mediaPipeExtractor = extractor
    }

    suspend fun activeEngineName(): String = activeEngine.displayName

    suspend fun activeEngineType(): EngineType = activeEngine

    /**
     * Resolves the best currently-available engine (Nano → MediaPipe → Regex)
     * without mutating the last-used [activeEngine]. Used by Settings to report
     * which engine is wired, not which one produced the last result.
     */
    suspend fun availableEngineName(): String = when {
        nanoExtractor.isAvailable() -> EngineType.GEMINI_NANO.displayName
        mediaPipeExtractor?.isAvailable() == true -> EngineType.MEDIAPIPE.displayName
        else -> EngineType.REGEX.displayName
    }

    suspend fun extract(
        packageName: String,
        title: String,
        body: String,
        timestampEpoch: Long,
    ): ExtractionResult {
        // Tier 1a: Gemini Nano
        nanoAvailableCache = nanoExtractor.isAvailable()
        if (nanoAvailableCache == true) {
            val result = nanoExtractor.extract(packageName, title, body, timestampEpoch)
            if (result != null) {
                activeEngine = EngineType.GEMINI_NANO
                return result
            }
        }

        // Tier 1b: MediaPipe Gemma-3-1B
        val mp = mediaPipeExtractor
        if (mp != null && mp.isAvailable()) {
            val result = mp.extract(packageName, title, body, timestampEpoch)
            if (result != null) {
                activeEngine = EngineType.MEDIAPIPE
                Log.d(TAG, "Engine=MEDIAPIPE amount=${result.amount} merchant=${result.merchantOrPayee} confidence=${result.confidenceScore}")
                return result
            }
        }

        // Tier 2: Deterministic regex (always succeeds or returns fallback)
        activeEngine = EngineType.REGEX
        val regexResult = regexExtractor.extract(packageName, title, body, timestampEpoch)
        if (regexResult != null) {
            Log.d(TAG, "Engine=REGEX amount=${regexResult.amount} merchant=${regexResult.merchantOrPayee} confidence=${regexResult.confidenceScore}")
        }
        return regexResult ?: fallback()
    }

    suspend fun isNanoAvailable(): Boolean {
        if (nanoAvailableCache == null) {
            nanoAvailableCache = nanoExtractor.isAvailable()
        }
        return nanoAvailableCache == true
    }

    private fun fallback(): ExtractionResult = ExtractionResult(
        isFinancialTransaction = false,
        amount = 0.0,
        txnType = "UNKNOWN",
        merchantOrPayee = "",
        category = "Uncategorized",
        confidenceScore = 0.0f,
    )

    /**
     * Routes a guardrailed narrative prompt to the best available LLM engine.
     * Returns null when no LLM engine is usable (regex has no model), so the
     * caller falls back to deterministic text.
     */
    suspend fun generateText(prompt: String): String? {
        if (nanoExtractor.isAvailable()) {
            nanoExtractor.generateText(prompt)?.let { return it }
        }
        val mp = mediaPipeExtractor
        if (mp != null && mp.isAvailable()) {
            mp.generateText(prompt)?.let { return it }
        }
        return null
    }

    companion object {
        private const val TAG = "ExtractorChain"
    }
}
