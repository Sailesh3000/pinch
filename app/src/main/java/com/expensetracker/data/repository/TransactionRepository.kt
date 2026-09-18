package com.expensetracker.data.repository

import com.expensetracker.core.database.dao.TransactionDao
import com.expensetracker.core.database.dao.TransactionWithCategory
import com.expensetracker.core.database.entity.TransactionEntity
import com.expensetracker.core.math.DeterministicMathEngine
import com.expensetracker.core.model.ConfidenceTier
import com.expensetracker.core.model.SourceType
import com.expensetracker.core.model.Transaction
import com.expensetracker.core.model.TransactionType
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
) {

    fun observeAll(): Flow<List<Transaction>> =
        transactionDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    fun observeFiltered(search: String?, categoryId: Long?): Flow<List<Transaction>> =
        transactionDao.observeFiltered(search, categoryId).map { rows -> rows.map { it.toDomain() } }

    fun observeNeedsReviewCount(): Flow<Int> = transactionDao.observeClarificationCount()

    /** Reactive month-to-date summary computed deterministically in Kotlin. */
    fun observeMonthSummary(nowEpochMillis: () -> Long = { System.currentTimeMillis() }): Flow<DeterministicMathEngine.MonthSpendSummary> =
        transactionDao.observeAll().map { rows ->
            DeterministicMathEngine.monthSpendSummary(
                transactions = rows.mapNotNull { row ->
                    if (row.transaction.txnType == TransactionType.DEBIT.dbValue) {
                        row.transaction.amount to row.transaction.timestamp
                    } else {
                        null
                    }
                },
                nowEpochMillis = nowEpochMillis(),
            )
        }

    suspend fun insertFromExtraction(entity: TransactionEntity): Long = transactionDao.insert(entity)

    suspend fun getById(id: Long): TransactionEntity? = transactionDao.getById(id)

    suspend fun update(entity: TransactionEntity) = transactionDao.update(entity)

    suspend fun deleteById(id: Long) = transactionDao.deleteById(id)

    suspend fun deleteAll() = transactionDao.deleteAll()

    suspend fun updateCategory(transactionId: Long, categoryId: Long) {
        val existing = transactionDao.getById(transactionId) ?: return
        transactionDao.update(existing.copy(categoryId = categoryId, needsClarification = false, isClarified = true))
    }

    suspend fun markMerged(dedupHash: String) = transactionDao.markMerged(dedupHash)

    suspend fun monthSpendSql(start: Long, end: Long): Double =
        transactionDao.sumDebitsBetween(start, end)

    suspend fun categoryBreakdownSql(start: Long, end: Long) =
        transactionDao.categoryBreakdown(start, end)

    /**
     * Writes every transaction as CSV with a header line:
     * `Date, Amount, Type, Merchant, Category, Source, Confidence`.
     * Dates use ISO-8601 (yyyy-MM-dd HH:mm) local time; amounts are plain
     * decimal strings so spreadsheets parse them.
     */
    suspend fun exportToCsv(outputStream: OutputStream) {
        val rows = transactionDao.getAllOnce()
        OutputStreamWriter(outputStream, StandardCharsets.UTF_8).use { out ->
            out.write(HEADER.joinToString(","))
            out.write("\n")
            for (row in rows) {
                val t = row.toDomain()
                out.write(
                    listOf(
                        formatTimestamp(t.timestamp),
                        t.amount.toString(),
                        t.txnType.dbValue,
                        escapeCsv(t.merchantName),
                        escapeCsv(t.categoryName ?: "Uncategorized"),
                        t.sourcePackage,
                        t.confidenceScore.toString(),
                    ).joinToString(",")
                )
                out.write("\n")
            }
        }
    }

    private fun formatTimestamp(epochMillis: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
            .format(java.util.Date(epochMillis))

    private fun escapeCsv(field: String): String {
        if (!field.contains(',') && !field.contains('"') && !field.contains('\n')) return field
        return "\"" + field.replace("\"", "\"\"") + "\""
    }

    private fun TransactionWithCategory.toDomain(): Transaction = Transaction(
        id = transaction.id,
        amount = transaction.amount,
        currency = transaction.currency,
        txnType = TransactionType.from(transaction.txnType),
        merchantName = transaction.merchantName,
        cleanPayee = transaction.cleanPayee,
        categoryId = transaction.categoryId,
        categoryName = categoryName,
        categoryIconKey = categoryIconKey,
        categoryColorHex = categoryColorHex,
        timestamp = transaction.timestamp,
        sourcePackage = transaction.sourcePackage,
        sourceType = SourceType.from(transaction.sourceType),
        rawNotificationText = transaction.rawNotificationText,
        confidenceScore = transaction.confidenceScore,
        confidenceTier = ConfidenceTier.from(transaction.confidenceTier),
        needsClarification = transaction.needsClarification,
        isClarified = transaction.isClarified,
        dedupHash = transaction.dedupHash,
        mergedFromDualSource = transaction.mergedFromDualSource,
        accountReference = transaction.accountReference,
        createdAt = transaction.createdAt,
    )

    private companion object {
        val HEADER = listOf("Date", "Amount", "Type", "Merchant", "Category", "Source", "Confidence")
    }
}
