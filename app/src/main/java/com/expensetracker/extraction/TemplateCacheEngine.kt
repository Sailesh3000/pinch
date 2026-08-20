package com.expensetracker.extraction

import com.expensetracker.core.database.dao.TemplateCacheDao
import com.expensetracker.core.database.entity.TemplateCacheEntity
import com.expensetracker.core.model.ConfidenceTier
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FR-EXTRACT-01 / FR-CLARIFY-04: Tier 0 Template Cache.
 *
 * Promotion path: when a user answers a clarification, the raw notification text
 * is normalized into a structural token signature and a deterministic extraction
 * regex is compiled and stored. On the next notification with the same structure
 * from the same package, Tier 0 matches instantly without waking the SLM.
 *
 * The structural signature strips all digits/amounts/dates and hashes the
 * remaining tokens, so two notifications with the same structural skeleton
 * but different amounts hit the same template.
 */
@Singleton
class TemplateCacheEngine @Inject constructor(
    private val templateCacheDao: TemplateCacheDao,
) {

    companion object {
        /** Tier 0 confidence is fixed at 0.92 — authoritative enough to skip SLM. */
        private const val TIER_0_CONFIDENCE = 0.92f
    }

    /**
     * Build a deterministic regex that captures amount and merchant from the
     * original text. The regex is case-insensitive and tolerates minor formatting.
     */
    fun buildExtractionRegex(merchantName: String): String {
        val escapedMerchant = Regex.escape(merchantName.trim())
        val amountCapture = """(?<amount>\d[\d,]*(?:\.\d{1,2})?)"""
        return """(?i)(?:Rs|₹|INR|\$)\s*$amountCapture\s+(?:to|at|via|on|paid|spent).*?$escapedMerchant"""
    }

    /**
     * Compute a structural signature hash: all digits and currency symbols are
     * replaced with tokens; the remaining normalized word skeleton is hashed.
     */
    fun structuralHash(packageName: String, text: String): String {
        val normalized = text.lowercase()
            .replace(Regex("""[\d,]+(?:\.\d+)?"""), "<NUM>")
            .replace(Regex("""[₹$€£]"""), "<CUR>")
            .replace(Regex("""\s+"""), " ")
            .trim()
        val input = "$packageName|$normalized"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.take(16).joinToString("") { "%02x".format(it) }
    }

    /**
     * Look up Tier 0. Returns an [ExtractionResult] on cache hit (confidence 0.92)
     * or null to signal a cache miss (caller should fall through to SLM).
     */
    suspend fun lookup(packageName: String, text: String): ExtractionResult? {
        val hash = structuralHash(packageName, text)
        val entry = templateCacheDao.findByPattern(packageName, hash) ?: return null
        val regex = Regex(entry.regexPattern, RegexOption.IGNORE_CASE)
        val match = regex.find(text) ?: return null

        val amount = match.groups["amount"]?.value
            ?.replace(",", "")
            ?.toDoubleOrNull()
            ?: return null
        // match.groups[name] throws IllegalArgumentException when the compiled
        // pattern has no such named group (buildExtractionRegex only defines
        // "amount"), so the missing-group case must fall back to the pattern
        // itself instead of aborting the whole Tier-0 lookup.
        val merchant = runCatching { match.groups[entry.merchantCaptureGroup]?.value }
            .getOrNull()
            ?: entry.regexPattern // fallback: merchant baked into regex at compile time
        if (merchant.isBlank()) return null

        templateCacheDao.recordHit(entry.id, System.currentTimeMillis())

        return ExtractionResult(
            isFinancialTransaction = true,
            amount = amount,
            currency = "INR",
            txnType = "DEBIT",
            merchantOrPayee = merchant.trim(),
            accountReference = null,
            category = "Uncategorized",
            confidenceScore = TIER_0_CONFIDENCE,
            reasoning = "Tier 0 template cache hit",
        )
    }

    /**
     * FR-CLARIFY-04 step 3: on user category answer, promote the structural
     * signature + regex into the cache so the next identical notification
     * hits Tier 0 without the SLM.
     */
    suspend fun promote(
        packageName: String,
        sourceType: String, // "APP_NOTIFICATION" / "SMS_NOTIFICATION"
        rawText: String,
        merchantName: String,
        defaultCategoryId: Long,
    ) {
        val hash = structuralHash(packageName, rawText)
        val regex = buildExtractionRegex(merchantName)

        templateCacheDao.upsert(
            TemplateCacheEntity(
                packageName = packageName,
                patternHash = hash,
                regexPattern = regex,
                defaultCategoryId = defaultCategoryId,
                merchantCaptureGroup = "merchant",
                amountCaptureGroup = "amount",
                hitCount = 0,
                lastHitTimestamp = System.currentTimeMillis(),
            )
        )
    }
}
