package com.expensetracker.extraction

/**
 * A [GeminiNanoExtractor] that always reports as unavailable — used in tests
 * to force the chain to fall through to the regex tier.
 */
class UnavailableNanoExtractor : GeminiNanoExtractor() {
    override suspend fun isAvailable(): Boolean = false
    override suspend fun extract(
        packageName: String,
        title: String,
        body: String,
        timestampEpoch: Long,
    ): ExtractionResult? = null
}
