package com.expensetracker.extraction

import com.expensetracker.core.database.dao.CategoryDao
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
    private val categoryDao: CategoryDao,
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
        // The merchant must be a named group: lookup() reads
        // match.groups["merchant"], and a pattern without it throws.
        //
        // No verb alternation between amount and merchant. The previous
        // pattern demanded (?:to|at|via|on|paid|spent), which does not appear
        // in the dominant Indian bank phrasing ("INR 1,250.00 debited from
        // A/c XX4567 at Swiggy"), so the regex never matched and Tier 0
        // effectively never fired. Matching is already done by the structural
        // hash - only entries whose whole skeleton is identical get this far -
        // so this regex only has to *extract*, and can stay permissive.
        return """(?i)(?:Rs|₹|INR|\$)\s*$amountCapture.*?(?<merchant>$escapedMerchant)"""
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
        // A template stored before the merchant group existed cannot yield a
        // merchant. Bail to the SLM rather than fall back to the pattern text:
        // that old fallback wrote the regex source into the merchant field, so
        // cache hits produced rows literally named "(?i)(?:Rs|INR...".
        val merchant = runCatching { match.groups[entry.merchantCaptureGroup]?.value }
            .getOrNull()
            ?.trim()
            ?.takeUnless { it.isBlank() }
            ?: return null

        templateCacheDao.recordHit(entry.id, System.currentTimeMillis())

        // The whole point of promotion is to reuse the category the user
        // picked. Hardcoding "Uncategorized" here meant every Tier 0 hit was
        // filed as uncategorized and defaultCategoryId was never read at all,
        // so the app could never appear to learn from a clarification.
        val category = categoryDao.getById(entry.defaultCategoryId)?.name ?: "Uncategorized"

        return ExtractionResult(
            isFinancialTransaction = true,
            amount = amount,
            currency = "INR",
            txnType = "DEBIT",
            merchantOrPayee = merchant,
            accountReference = null,
            category = category,
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

        // @Upsert resolves a conflict by UPDATE on the primary key, but the
        // conflict here comes from the unique index (package_name,
        // pattern_hash). Building the entity with the default id = 0 therefore
        // matched no row, and re-answering a clarification for an already
        // promoted pattern silently kept the stale regex and category. Reusing
        // the existing row's id makes the update land.
        val existing = templateCacheDao.findByPattern(packageName, hash)

        templateCacheDao.upsert(
            TemplateCacheEntity(
                id = existing?.id ?: 0,
                packageName = packageName,
                patternHash = hash,
                regexPattern = regex,
                defaultCategoryId = defaultCategoryId,
                merchantCaptureGroup = "merchant",
                amountCaptureGroup = "amount",
                hitCount = existing?.hitCount ?: 0,
                lastHitTimestamp = System.currentTimeMillis(),
            )
        )
    }
}
