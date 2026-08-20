package com.expensetracker.worker

import com.expensetracker.core.database.dao.CategorySpend
import com.expensetracker.core.database.dao.TransactionDao
import com.expensetracker.core.database.dao.TransactionWithCategory
import com.expensetracker.core.database.entity.CategoryEntity
import com.expensetracker.core.database.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeTransactionDaoForDecay : TransactionDao {

    private val _pending = mutableListOf<Pair<Long, Long>>() // id -> timestamp

    fun addPending(id: Long, timestamp: Long) {
        _pending.add(id to timestamp)
    }

    val pendingIds: Set<Long> get() = _pending.map { it.first }.toSet()

    override suspend fun autoResolveExpired(cutoffMillis: Long): Int {
        val before = _pending.size
        _pending.removeAll { it.second < cutoffMillis }
        return before - _pending.size
    }

    // --- stubs for remaining DAO methods ---

    override suspend fun insert(entity: TransactionEntity): Long = 0L
    override fun observeAll(): Flow<List<TransactionWithCategory>> = MutableStateFlow(emptyList())
    override fun observeFiltered(search: String?, categoryId: Long?): Flow<List<TransactionWithCategory>> = MutableStateFlow(emptyList())
    override suspend fun getById(id: Long): TransactionEntity? = null
    override suspend fun update(entity: TransactionEntity) {}
    override suspend fun deleteById(id: Long) {}
    override suspend fun deleteAll() {}
    override suspend fun sumDebitsBetween(start: Long, end: Long): Double = 0.0
    override suspend fun sumCreditsBetween(start: Long, end: Long): Double = 0.0
    override suspend fun countBetween(start: Long, end: Long): Int = 0
    override suspend fun categoryBreakdown(start: Long, end: Long): List<CategorySpend> = emptyList()
    override fun observeClarificationCount(): Flow<Int> = MutableStateFlow(0)
    override fun observeClarificationQueue(): Flow<List<TransactionWithCategory>> = MutableStateFlow(emptyList())
    override suspend fun updateClarificationState(id: Long, needs: Boolean) {}
    override suspend fun markMerged(dedupHash: String) {}
    override suspend fun topCategoriesForMerchant(merchant: String, limit: Int): List<CategoryEntity> = emptyList()
    override suspend fun topMerchants(start: Long, end: Long, limit: Int): List<TransactionDao.TopMerchantRow> = emptyList()
    override suspend fun confidenceStats(start: Long, end: Long): TransactionDao.ConfidenceStats = TransactionDao.ConfidenceStats(0, 0)
    override suspend fun monthlySpendTrend(limit: Int): List<TransactionDao.MonthlySpend> = emptyList()
    override suspend fun potentialRecurringMerchants(): List<com.expensetracker.core.database.dao.RecurringMerchantRow> = emptyList()

    // --- Phase 5 stubs ---
    override suspend fun debitsBetween(start: Long, end: Long): List<TransactionDao.DebitTransaction> = emptyList()
    override suspend fun merchantFrequency(start: Long, end: Long): List<TransactionDao.MerchantFrequencyRow> = emptyList()
    override suspend fun spendByMerchant(merchant: String, start: Long, end: Long): Double = 0.0
    override suspend fun totalDebitsBetween(start: Long, end: Long): Double = 0.0
    override suspend fun debitCountBetween(start: Long, end: Long): Int = 0
    override suspend fun maxDebitBetween(start: Long, end: Long): Double = 0.0
}
