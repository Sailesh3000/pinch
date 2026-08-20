package com.expensetracker.extraction

/**
 * Contract for on-device literal extractors (FR-EXTRACT). Implementations must
 * copy literal values from the notification text and MUST NOT perform arithmetic,
 * splits, or currency conversions (zero-arithmetic SLM rule, spec §3).
 */
interface TransactionExtractor {

    val engineName: String

    /** True when the underlying engine is usable on this device right now. */
    suspend fun isAvailable(): Boolean

    /**
     * Extract a structured transaction from a raw notification.
     * Returns null when the engine cannot produce a result.
     */
    suspend fun extract(
        packageName: String,
        title: String,
        body: String,
        timestampEpoch: Long,
    ): ExtractionResult?

    /**
     * Free-form completion for narrative generation (FR-INSIGHT-02).
     * The caller supplies a guardrailed prompt containing only pre-computed fact
     * tokens — the model composes natural language, never arithmetic.
     * Returns null when the engine is unavailable (deterministic regex has no LLM).
     */
    suspend fun generateText(prompt: String): String? = null
}
