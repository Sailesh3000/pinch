package com.expensetracker.ingestion

import android.util.Log
import com.expensetracker.clarification.ClarificationNotifier
import com.expensetracker.core.database.entity.TransactionEntity
import com.expensetracker.core.model.ConfidenceTier
import com.expensetracker.core.model.SourceType
import com.expensetracker.core.model.TransactionType
import com.expensetracker.data.repository.CategoryRepository
import com.expensetracker.data.repository.TransactionRepository
import com.expensetracker.extraction.ExtractorChain
import com.expensetracker.extraction.LiteralExtraction
import com.expensetracker.extraction.TemplateCacheEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the post-filter pipeline for a single notification:
 * dedup -> Tier 0 cache check -> extraction (Nano/MediaPipe/regex) -> confidence gating -> DB write.
 * Dispatches interactive clarification notification for low-confidence transactions.
 * Runs entirely on a background dispatcher; never blocks the listener thread.
 */
@Singleton
class NotificationProcessor @Inject constructor(
    private val dedupEngine: DeduplicationEngine,
    private val extractorChain: ExtractorChain,
    private val templateCacheEngine: TemplateCacheEngine,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val clarificationNotifier: ClarificationNotifier,
) {

    suspend fun process(event: NotificationEvent) {
        val rawText = "${event.title} ${event.body}".trim()
        if (rawText.isBlank()) return

        val amount = LiteralExtraction.firstAmount(rawText) ?: 0.0
        val currency = detectCurrency(rawText)

        when (val decision = dedupEngine.evaluate(amount, currency, rawText, event.timestamp)) {
            is DeduplicationEngine.Decision.Duplicate -> {
                // Same economic transaction from a second source (bank SMS + app push).
                transactionRepository.markMerged(decision.existingDedupHash)
                Log.d(TAG, "Deduped notification ${event.notificationKey}")
                return
            }
            is DeduplicationEngine.Decision.Fresh -> {
                // FR-EXTRACT-01: Try Tier 0 template cache first.
                val cachedResult = templateCacheEngine.lookup(event.packageName, rawText)

                val result = cachedResult ?: extractorChain.extract(
                    packageName = event.packageName,
                    title = event.title,
                    body = event.body,
                    timestampEpoch = event.timestamp,
                )
                if (!result.isFinancialTransaction) return

                val gated = com.expensetracker.extraction.ConfidenceGating.apply(result.confidenceScore)
                val categoryId = if (cachedResult != null) {
                    // Tier 0 hit: use the default category from cache.
                    categoryRepository.resolveCategoryId(cachedResult.category)
                } else {
                    categoryRepository.resolveCategoryId(result.category)
                }

                val entity = com.expensetracker.core.database.entity.TransactionEntity(
                    amount = result.amount,
                    currency = result.currency,
                    txnType = TransactionType.from(result.txnType).dbValue,
                    merchantName = result.merchantOrPayee.ifBlank { "(Unknown merchant)" },
                    cleanPayee = result.merchantOrPayee.ifBlank { null },
                    categoryId = categoryId,
                    timestamp = event.timestamp,
                    sourcePackage = event.packageName,
                    sourceType = sourceTypeFor(event.packageName).dbValue,
                    rawNotificationText = rawText,
                    confidenceScore = result.confidenceScore,
                    confidenceTier = if (cachedResult != null) {
                        ConfidenceTier.TIER_0_CACHE.dbValue
                    } else {
                        ConfidenceTier.TIER_1_LOCAL_SLM.dbValue
                    },
                    needsClarification = gated.needsClarification,
                    isClarified = false,
                    dedupHash = decision.dedupHash,
                    mergedFromDualSource = false,
                    accountReference = result.accountReference,
                    createdAt = event.timestamp,
                )
                val id = transactionRepository.insertFromExtraction(entity)
                if (gated.needsClarification) {
                    Log.d(TAG, "Saved low-confidence transaction id=$id (category=${result.category})")
                    // FR-CLARIFY-03: Dispatch interactive clarification notification
                    clarificationNotifier.dispatchClarification(
                        transactionId = id,
                        merchantName = result.merchantOrPayee.ifBlank { "Unknown" },
                        amount = result.amount,
                        currency = result.currency,
                        packageName = event.packageName,
                    )
                } else {
                    Log.d(TAG, "Saved transaction id=$id (tier=${if (cachedResult != null) "TIER_0" else "TIER_1"})")
                }
            }
        }
    }

    private fun detectCurrency(text: String): String {
        val normalized = text.uppercase()
        return when {
            Regex("""\bUSD\b|\$""").containsMatchIn(normalized) -> "USD"
            Regex("""\bEUR\b|€""").containsMatchIn(normalized) -> "EUR"
            Regex("""\bGBP\b|£""").containsMatchIn(normalized) -> "GBP"
            else -> "INR"
        }
    }

    private fun sourceTypeFor(packageName: String): SourceType {
        val smsApps = setOf(
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.mms",
        )
        return if (packageName in smsApps) SourceType.SMS_NOTIFICATION else SourceType.APP_NOTIFICATION
    }

    companion object {
        private const val TAG = "NotificationProcessor"
    }
}
