package com.expensetracker.insights

import com.expensetracker.core.database.dao.TransactionDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AnomalyDetectorTest {

    private lateinit var detector: AnomalyDetector

    private val fakeDao = object : TransactionDao {
        private var debitsToReturn = emptyList<TransactionDao.DebitTransaction>()
        private var merchantFreqToReturn = emptyList<TransactionDao.MerchantFrequencyRow>()

        fun setDebits(debits: List<TransactionDao.DebitTransaction>) { debitsToReturn = debits }
        fun setMerchantFrequency(freq: List<TransactionDao.MerchantFrequencyRow>) { merchantFreqToReturn = freq }

        override suspend fun debitsBetween(start: Long, end: Long) = debitsToReturn
        override suspend fun merchantFrequency(start: Long, end: Long) = merchantFreqToReturn
        override suspend fun spendByMerchant(merchant: String, start: Long, end: Long) = 0.0
        override suspend fun totalDebitsBetween(start: Long, end: Long) = 0.0
        override suspend fun debitCountBetween(start: Long, end: Long) = 0
        override suspend fun maxDebitBetween(start: Long, end: Long) = 0.0

        // Stubs
        override suspend fun insert(entity: com.expensetracker.core.database.entity.TransactionEntity) = 0L
        override fun observeAll() = kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.expensetracker.core.database.dao.TransactionWithCategory>())
        override fun observeFiltered(search: String?, categoryId: Long?) = kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.expensetracker.core.database.dao.TransactionWithCategory>())
        override suspend fun getById(id: Long) = null
        override suspend fun update(entity: com.expensetracker.core.database.entity.TransactionEntity) {}
        override suspend fun deleteById(id: Long) {}
        override suspend fun sumDebitsBetween(start: Long, end: Long) = 0.0
        override suspend fun sumCreditsBetween(start: Long, end: Long) = 0.0
        override suspend fun countBetween(start: Long, end: Long) = 0
        override suspend fun categoryBreakdown(start: Long, end: Long) = emptyList<com.expensetracker.core.database.dao.CategorySpend>()
        override fun observeClarificationCount() = kotlinx.coroutines.flow.MutableStateFlow(0)
        override fun observeClarificationQueue() = kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.expensetracker.core.database.dao.TransactionWithCategory>())
        override suspend fun updateClarificationState(id: Long, needs: Boolean) {}
        override suspend fun markMerged(dedupHash: String) {}
        override suspend fun topCategoriesForMerchant(merchant: String, limit: Int) = emptyList<com.expensetracker.core.database.entity.CategoryEntity>()
        override suspend fun topMerchants(start: Long, end: Long, limit: Int) = emptyList<TransactionDao.TopMerchantRow>()
        override suspend fun confidenceStats(start: Long, end: Long) = TransactionDao.ConfidenceStats(0, 0)
        override suspend fun monthlySpendTrend(limit: Int) = emptyList<TransactionDao.MonthlySpend>()
        override suspend fun potentialRecurringMerchants() = emptyList<com.expensetracker.core.database.dao.RecurringMerchantRow>()
        override suspend fun autoResolveExpired(cutoffMillis: Long) = 0
    }

    @Before
    fun setup() {
        detector = AnomalyDetector(fakeDao)
    }

    @Test
    fun `detect - empty transactions returns no anomalies`() = runTest {
        fakeDao.setDebits(emptyList())
        fakeDao.setMerchantFrequency(emptyList())

        val anomalies = detector.detect()
        assertTrue(anomalies.isEmpty())
    }

    @Test
    fun `detect - large transaction flagged`() = runTest {
        val now = System.currentTimeMillis()
        fakeDao.setDebits(listOf(
            TransactionDao.DebitTransaction(1, 10000.0, "Luxury Store", "luxury", 1L, now),
            TransactionDao.DebitTransaction(2, 100.0, "Chai Shop", "chai", 1L, now),
            TransactionDao.DebitTransaction(3, 100.0, "Chai Shop", "chai", 1L, now),
        ))
        fakeDao.setMerchantFrequency(listOf(
            TransactionDao.MerchantFrequencyRow("Chai Shop", 5),
        ))

        val anomalies = detector.detect()
        assertTrue(anomalies.isNotEmpty())
        assertEquals("Luxury Store", anomalies.first().merchantName)
    }

    @Test
    fun `detect - unfamiliar merchant flagged`() = runTest {
        val now = System.currentTimeMillis()
        fakeDao.setDebits(listOf(
            TransactionDao.DebitTransaction(1, 500.0, "New Restaurant", "new", 1L, now),
            TransactionDao.DebitTransaction(2, 100.0, "Regular Shop", "regular", 1L, now),
        ))
        fakeDao.setMerchantFrequency(listOf(
            TransactionDao.MerchantFrequencyRow("Regular Shop", 5),
            TransactionDao.MerchantFrequencyRow("New Restaurant", 1),
        ))

        val anomalies = detector.detect()
        assertTrue(anomalies.any { it.merchantName == "New Restaurant" })
    }

    @Test
    fun `detect - familiar merchant not flagged as unfamiliar`() = runTest {
        val now = System.currentTimeMillis()
        fakeDao.setDebits(listOf(
            TransactionDao.DebitTransaction(1, 200.0, "Regular Shop", "regular", 1L, now),
        ))
        fakeDao.setMerchantFrequency(listOf(
            TransactionDao.MerchantFrequencyRow("Regular Shop", 5),
        ))

        val anomalies = detector.detect()
        assertTrue(anomalies.none { it.severity == AnomalySeverity.LOW })
    }
}
