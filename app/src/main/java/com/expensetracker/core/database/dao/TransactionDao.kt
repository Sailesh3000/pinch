package com.expensetracker.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Update
import com.expensetracker.core.database.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

data class TransactionWithCategory(
    @Embedded val transaction: TransactionEntity,
    @ColumnInfo(name = "category_name") val categoryName: String?,
    @ColumnInfo(name = "category_icon_key") val categoryIconKey: String?,
    @ColumnInfo(name = "category_color_hex") val categoryColorHex: String?,
)

data class CategorySpend(
    @ColumnInfo(name = "category_name") val categoryName: String,
    @ColumnInfo(name = "icon_key") val iconKey: String?,
    @ColumnInfo(name = "color_hex") val colorHex: String?,
    @ColumnInfo(name = "total_amount") val totalAmount: Double,
    @ColumnInfo(name = "transaction_count") val transactionCount: Int,
)

@Dao
interface TransactionDao {

    @Query(
        """
        SELECT t.*, c.name AS category_name, c.icon_key AS category_icon_key, c.color_hex AS category_color_hex
        FROM transactions t
        LEFT JOIN categories c ON t.category_id = c.id
        ORDER BY t.timestamp DESC, t.id DESC
        """
    )
    fun observeAll(): Flow<List<TransactionWithCategory>>

    /** One-shot, non-reactive dump of every transaction (used for CSV export). */
    @Query(
        """
        SELECT t.*, c.name AS category_name, c.icon_key AS category_icon_key, c.color_hex AS category_color_hex
        FROM transactions t
        LEFT JOIN categories c ON t.category_id = c.id
        ORDER BY t.timestamp ASC, t.id ASC
        """
    )
    suspend fun getAllOnce(): List<TransactionWithCategory>

    @Query(
        """
        SELECT t.*, c.name AS category_name, c.icon_key AS category_icon_key, c.color_hex AS category_color_hex
        FROM transactions t
        LEFT JOIN categories c ON t.category_id = c.id
        WHERE (:search IS NULL OR :search = '' OR t.merchant_name LIKE '%' || :search || '%'
               OR COALESCE(t.clean_payee, '') LIKE '%' || :search || '%')
          AND (:categoryId IS NULL OR t.category_id = :categoryId)
        ORDER BY t.timestamp DESC, t.id DESC
        """
    )
    fun observeFiltered(search: String?, categoryId: Long?): Flow<List<TransactionWithCategory>>

    @Query("SELECT COUNT(*) FROM transactions WHERE needs_clarification = 1")
    fun observeClarificationCount(): Flow<Int>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Insert
    suspend fun insert(entity: TransactionEntity): Long

    @Update
    suspend fun update(entity: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query(
        "SELECT COALESCE(SUM(amount), 0.0) FROM transactions " +
            "WHERE txn_type = 'DEBIT' AND timestamp >= :start AND timestamp <= :end"
    )
    suspend fun sumDebitsBetween(start: Long, end: Long): Double

    @Query(
        "SELECT COALESCE(SUM(amount), 0.0) FROM transactions " +
            "WHERE txn_type = 'CREDIT' AND timestamp >= :start AND timestamp <= :end"
    )
    suspend fun sumCreditsBetween(start: Long, end: Long): Double

    @Query("SELECT COUNT(*) FROM transactions WHERE timestamp >= :start AND timestamp <= :end")
    suspend fun countBetween(start: Long, end: Long): Int

    @Query(
        """
        SELECT c.name AS category_name, c.icon_key AS icon_key, c.color_hex AS color_hex,
               COALESCE(SUM(t.amount), 0.0) AS total_amount, COUNT(t.id) AS transaction_count
        FROM transactions t
        INNER JOIN categories c ON t.category_id = c.id
        WHERE t.txn_type = 'DEBIT' AND t.timestamp >= :start AND t.timestamp <= :end
        GROUP BY c.name, c.icon_key, c.color_hex
        ORDER BY total_amount DESC
        """
    )
    suspend fun categoryBreakdown(start: Long, end: Long): List<CategorySpend>

    @Query("UPDATE transactions SET needs_clarification = :needs WHERE id = :id")
    suspend fun updateClarificationState(id: Long, needs: Boolean)

    @Query("UPDATE transactions SET merged_from_dual_source = 1 WHERE dedup_hash = :dedupHash")
    suspend fun markMerged(dedupHash: String)

    // ---- Phase 2: Review queue ----

    @Query(
        """
        SELECT t.*, c.name AS category_name, c.icon_key AS category_icon_key, c.color_hex AS category_color_hex
        FROM transactions t
        LEFT JOIN categories c ON t.category_id = c.id
        WHERE t.needs_clarification = 1
        ORDER BY t.timestamp ASC
        """
    )
    fun observeClarificationQueue(): Flow<List<TransactionWithCategory>>

    /**
     * Bulk auto-resolve: mark pending transactions older than [cutoffMillis] as
     * auto-resolved with their current best-guess category (FR-CLARIFY-05).
     */
    @Query(
        """
        UPDATE transactions
        SET needs_clarification = 0,
            is_clarified = 1,
            confidence_tier = 'AUTO_RESOLVED_AFTER_TIMEOUT'
        WHERE needs_clarification = 1
          AND timestamp < :cutoffMillis
        """
    )
    suspend fun autoResolveExpired(cutoffMillis: Long): Int

    /**
     * FR-CLARIFY-02: Top-3 personalized category suggestions.
     * Orders by per-merchant category frequency, then global usage_count as tiebreaker.
     */
    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT c.id, c.name, c.icon_key, c.color_hex, c.is_system_default, c.usage_count
        FROM categories c
        LEFT JOIN transactions t ON t.category_id = c.id
        GROUP BY c.id
        ORDER BY COUNT(CASE WHEN COALESCE(t.clean_payee, '') LIKE '%' || :merchant || '%' THEN 1 END) DESC,
                 c.usage_count DESC
        LIMIT :limit
        """
    )
    suspend fun topCategoriesForMerchant(merchant: String, limit: Int = 3): List<com.expensetracker.core.database.entity.CategoryEntity>

    // ---- Phase 3: Insights ----

    data class TopMerchantRow(
        @ColumnInfo(name = "merchant_name") val merchantName: String,
        @ColumnInfo(name = "total_amount") val totalAmount: Double,
        @ColumnInfo(name = "txn_count") val txnCount: Int,
    )

    @Query(
        """
        SELECT merchant_name, COALESCE(SUM(amount), 0.0) AS total_amount, COUNT(*) AS txn_count
        FROM transactions
        WHERE txn_type = 'DEBIT' AND timestamp >= :start AND timestamp <= :end
        GROUP BY merchant_name
        ORDER BY total_amount DESC
        LIMIT :limit
        """
    )
    suspend fun topMerchants(start: Long, end: Long, limit: Int = 5): List<TopMerchantRow>

    data class ConfidenceStats(
        @ColumnInfo(name = "total_count") val totalCount: Int,
        @ColumnInfo(name = "auto_categorized_count") val autoCategorizedCount: Int,
    )

    @Query(
        """
        SELECT COUNT(*) AS total_count,
               COUNT(CASE WHEN is_clarified = 1 OR confidence_tier != 'MANUAL' THEN 1 END) AS auto_categorized_count
        FROM transactions
        WHERE timestamp >= :start AND timestamp <= :end
        """
    )
    suspend fun confidenceStats(start: Long, end: Long): ConfidenceStats

    data class MonthlySpend(
        @ColumnInfo(name = "month_label") val monthLabel: String,
        @ColumnInfo(name = "total_amount") val totalAmount: Double,
        @ColumnInfo(name = "month_start") val monthStart: Long,
    )

    @Query(
        """
        SELECT strftime('%Y-%m', timestamp / 1000, 'unixepoch', 'localtime') AS month_label,
               MIN(timestamp) AS month_start,
               COALESCE(SUM(amount), 0.0) AS total_amount
        FROM transactions
        WHERE txn_type = 'DEBIT'
        GROUP BY month_label
        ORDER BY month_start DESC
        LIMIT :limit
        """
    )
    suspend fun monthlySpendTrend(limit: Int = 6): List<MonthlySpend>

    /**
     * Returns all DEBIT transactions for subscription detection.
     */
    @Query(
        """
        SELECT merchant_name, COALESCE(AVG(amount), 0.0) AS avg_amount, COUNT(*) AS txn_count,
               MIN(timestamp) AS first_seen, MAX(timestamp) AS last_seen
        FROM transactions
        WHERE txn_type = 'DEBIT'
        GROUP BY merchant_name
        HAVING txn_count >= 2
        ORDER BY txn_count DESC
        """
    )
    suspend fun potentialRecurringMerchants(): List<RecurringMerchantRow>

    // ---- Phase 5: Insights & Anomaly Detection ----

    data class DebitTransaction(
        @ColumnInfo(name = "id") val id: Long,
        @ColumnInfo(name = "amount") val amount: Double,
        @ColumnInfo(name = "merchant_name") val merchantName: String,
        @ColumnInfo(name = "clean_payee") val cleanPayee: String?,
        @ColumnInfo(name = "category_id") val categoryId: Long,
        @ColumnInfo(name = "timestamp") val timestamp: Long,
    )

    @Query(
        """
        SELECT id, amount, merchant_name, clean_payee, category_id, timestamp
        FROM transactions
        WHERE txn_type = 'DEBIT' AND timestamp >= :start AND timestamp <= :end
        ORDER BY amount DESC
        """
    )
    suspend fun debitsBetween(start: Long, end: Long): List<DebitTransaction>

    data class MerchantFrequencyRow(
        @ColumnInfo(name = "merchant_name") val merchantName: String,
        @ColumnInfo(name = "txn_count") val txnCount: Int,
    )

    @Query(
        """
        SELECT merchant_name, COUNT(*) AS txn_count
        FROM transactions
        WHERE txn_type = 'DEBIT' AND timestamp >= :start AND timestamp <= :end
        GROUP BY merchant_name
        """
    )
    suspend fun merchantFrequency(start: Long, end: Long): List<MerchantFrequencyRow>

    /**
     * FR-INSIGHT-04: Aggregate spend for a merchant within a date range.
     */
    @Query(
        """
        SELECT COALESCE(SUM(amount), 0.0) FROM transactions
        WHERE txn_type = 'DEBIT' AND (merchant_name LIKE '%' || :merchant || '%' OR clean_payee LIKE '%' || :merchant || '%')
        AND timestamp >= :start AND timestamp <= :end
        """
    )
    suspend fun spendByMerchant(merchant: String, start: Long, end: Long): Double

    /**
     * FR-INSIGHT-04: Total debit spend in a date range.
     */
    @Query(
        """
        SELECT COALESCE(SUM(amount), 0.0) FROM transactions
        WHERE txn_type = 'DEBIT' AND timestamp >= :start AND timestamp <= :end
        """
    )
    suspend fun totalDebitsBetween(start: Long, end: Long): Double

    /**
     * FR-INSIGHT-04: Count of debits in a date range.
     */
    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE txn_type = 'DEBIT' AND timestamp >= :start AND timestamp <= :end
        """
    )
    suspend fun debitCountBetween(start: Long, end: Long): Int

    /**
     * FR-INSIGHT-04: Largest single debit in a date range.
     */
    @Query(
        """
        SELECT COALESCE(MAX(amount), 0.0) FROM transactions
        WHERE txn_type = 'DEBIT' AND timestamp >= :start AND timestamp <= :end
        """
    )
    suspend fun maxDebitBetween(start: Long, end: Long): Double
}

data class RecurringMerchantRow(
    @ColumnInfo(name = "merchant_name") val merchantName: String,
    @ColumnInfo(name = "avg_amount") val avgAmount: Double,
    @ColumnInfo(name = "txn_count") val txnCount: Int,
    @ColumnInfo(name = "first_seen") val firstSeen: Long,
    @ColumnInfo(name = "last_seen") val lastSeen: Long,
)
