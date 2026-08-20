package com.expensetracker.data.repository

import com.expensetracker.core.database.dao.ClarificationHistoryDao
import com.expensetracker.core.database.dao.TransactionDao
import com.expensetracker.core.database.entity.ClarificationHistoryEntity
import com.expensetracker.extraction.TemplateCacheEngine
import com.expensetracker.core.model.ConfidenceTier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FR-CLARIFY-01/02/04/05: Manages the clarification queue, user answers, and
 * Tier 0 auto-promotion. The three steps on user answer:
 *   1. Update transaction (categoryId, needs_clarification=false, is_clarified=true)
 *   2. Insert clarification_history row
 *   3. Promote structural signature into template_cache (Tier 0)
 */
@Singleton
class ClarificationRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val clarificationHistoryDao: ClarificationHistoryDao,
    private val templateCacheEngine: TemplateCacheEngine,
) {

    fun observeQueue(): Flow<List<com.expensetracker.core.database.dao.TransactionWithCategory>> =
        transactionDao.observeClarificationQueue()

    fun observePendingCount(): Flow<Int> =
        transactionDao.observeClarificationCount()

    /**
     * FR-CLARIFY-02: Top-3 personalized suggestions for the given merchant.
     */
    suspend fun getTopSuggestions(merchant: String, limit: Int = 3): List<com.expensetracker.core.database.entity.CategoryEntity> =
        transactionDao.topCategoriesForMerchant(merchant, limit)

    /**
     * FR-CLARIFY-04: Resolve a clarification — update the transaction,
     * record history, and promote to Tier 0.
     */
    suspend fun resolve(
        transactionId: Long,
        newCategoryId: Long,
        suggestedCategoryId: Long?,
        clarificationSource: String,
        responseTimeMs: Long,
    ) {
        val existing = transactionDao.getById(transactionId) ?: return

        // Step 1: update transaction
        transactionDao.update(
            existing.copy(
                categoryId = newCategoryId,
                needsClarification = false,
                isClarified = true,
                confidenceTier = ConfidenceTier.MANUAL.dbValue,
            )
        )

        // Step 2: insert history
        clarificationHistoryDao.insert(
            ClarificationHistoryEntity(
                transactionId = transactionId,
                rawText = existing.rawNotificationText,
                suggestedCategoryId = suggestedCategoryId,
                chosenCategoryId = newCategoryId,
                responseTimeMs = responseTimeMs,
                clarificationSource = clarificationSource,
            )
        )

        // Step 3: promote to Tier 0 cache
        templateCacheEngine.promote(
            packageName = existing.sourcePackage,
            sourceType = existing.sourceType,
            rawText = existing.rawNotificationText,
            merchantName = existing.merchantName,
            defaultCategoryId = newCategoryId,
        )
    }
}
