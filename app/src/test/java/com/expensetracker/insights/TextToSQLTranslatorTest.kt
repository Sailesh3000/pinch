package com.expensetracker.insights

import com.expensetracker.core.database.dao.CategoryDao
import com.expensetracker.core.database.dao.TransactionDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class TextToSQLTranslatorTest {

    private lateinit var translator: TextToSQLTranslator

    private val fakeDao = object : TransactionDao {
        private val debits = mutableListOf<TransactionDao.DebitTransaction>()
        fun addDebit(id: Long, amount: Double, merchant: String, timestamp: Long) {
            debits.add(TransactionDao.DebitTransaction(id, amount, merchant, merchant, 1L, timestamp))
        }

        override suspend fun debitsBetween(start: Long, end: Long) = debits.filter { it.timestamp in start..end }
        override suspend fun merchantFrequency(start: Long, end: Long) = emptyList<TransactionDao.MerchantFrequencyRow>()
        override suspend fun spendByMerchant(merchant: String, start: Long, end: Long) =
            debits.filter { it.merchantName.contains(merchant, true) && it.timestamp in start..end }.sumOf { it.amount }
        override suspend fun totalDebitsBetween(start: Long, end: Long) =
            debits.filter { it.timestamp in start..end }.sumOf { it.amount }
        override suspend fun debitCountBetween(start: Long, end: Long) =
            debits.count { it.timestamp in start..end }
        override suspend fun maxDebitBetween(start: Long, end: Long) =
            debits.filter { it.timestamp in start..end }.maxOfOrNull { it.amount } ?: 0.0

        // Stubs for unused methods
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
        override suspend fun getAllOnce() = emptyList<com.expensetracker.core.database.dao.TransactionWithCategory>()
        override suspend fun deleteAll() {}
    }

    private val fakeCategoryDao = object : CategoryDao {
        override fun observeAll() = kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.expensetracker.core.database.entity.CategoryEntity>())
        override suspend fun getAllOnce() = emptyList<com.expensetracker.core.database.entity.CategoryEntity>()
        override suspend fun getById(id: Long) = null
        override suspend fun getByName(name: String) = null
        override suspend fun insertAll(entities: List<com.expensetracker.core.database.entity.CategoryEntity>) {}
        override suspend fun incrementUsage(categoryId: Long) {}
        override suspend fun deleteAll() {}
    }

    @Before
    fun setup() {
        translator = TextToSQLTranslator(fakeDao, fakeCategoryDao)
    }

    @Test
    fun `translate - merchant spend this month`() = runTest {
        val plan = translator.translateToSQL("How much did I spend on Swiggy this month?")
        assertNotNull(plan)
        assertEquals("Total spent on swiggy this month", plan!!.description)
    }

    @Test
    fun `translate - merchant spend last month`() = runTest {
        val plan = translator.translateToSQL("How much did I spend at Zomato last month?")
        assertNotNull(plan)
        assertEquals("Total spent on zomato last month", plan!!.description)
    }

    @Test
    fun `translate - total spend this month`() = runTest {
        val plan = translator.translateToSQL("How much did I spend this month?")
        assertNotNull(plan)
        assertEquals("Total spend this month", plan!!.description)
    }

    @Test
    fun `translate - transaction count this month`() = runTest {
        val plan = translator.translateToSQL("How many transactions did I make this month?")
        assertNotNull(plan)
        assertEquals("Transaction count this month", plan!!.description)
    }

    @Test
    fun `translate - largest transaction`() = runTest {
        val plan = translator.translateToSQL("What's my biggest expense this month?")
        assertNotNull(plan)
        assertEquals("Largest single transaction", plan!!.description)
    }

    @Test
    fun `translate - unrecognized question returns null`() = runTest {
        val plan = translator.translateToSQL("What's the weather like?")
        assertNull(plan)
    }

    @Test
    fun `translate - case insensitive`() = runTest {
        val plan = translator.translateToSQL("HOW MUCH DID I SPEND ON SWIGGY THIS MONTH?")
        assertNotNull(plan)
        assertEquals("Total spent on swiggy this month", plan!!.description)
    }
}
