package com.expensetracker.ui.home

import android.content.Context
import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.expensetracker.core.database.AppDatabase
import com.expensetracker.core.database.SeedData
import com.expensetracker.core.database.entity.TransactionEntity
import com.expensetracker.data.repository.CategoryRepository
import com.expensetracker.data.repository.TransactionRepository
import com.expensetracker.insights.DeepLinkCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises Home search against a real Room database. The UI tests use
 * FakeTransactionDao, whose Kotlin `contains` filter never touched the SQL,
 * so a broken LIKE query or flow wiring could ship unnoticed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class HomeSearchTest {

    private lateinit var db: AppDatabase
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var categoryRepository: CategoryRepository

    @Before
    fun setUp() {
        // Real dispatcher: the search debounce must elapse in wall time while
        // Room emits from its own executor.
        Dispatchers.setMain(Dispatchers.Default)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        transactionRepository = TransactionRepository(db.transactionDao())
        categoryRepository = CategoryRepository(db.categoryDao())
        runBlocking {
            db.categoryDao().insertAll(SeedData.defaultCategories())
            val food = db.categoryDao().getByName("Food & Dining")!!.id
            val shopping = db.categoryDao().getByName("Shopping")!!.id
            val income = db.categoryDao().getByName("Salary/Income")!!.id
            insert(amount = 250.0, merchant = "Swiggy", categoryId = food, raw = "Paid Rs 250 to Swiggy via UPI")
            insert(amount = 1200.0, merchant = "Amazon", categoryId = shopping, raw = "Rs 1200 debited for Amazon order")
            insert(amount = 5000.0, merchant = "Rahul Sharma", categoryId = income, raw = "Rs 5000 credited", type = "CREDIT")
        }
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun repository_filters_by_merchant_case_insensitive() = runBlocking {
        val rows = transactionRepository.observeFiltered("swig", null).first()
        assertEquals(listOf("Swiggy"), rows.map { it.merchantName })
    }

    @Test
    fun repository_filters_by_category_name() = runBlocking {
        val rows = transactionRepository.observeFiltered("food", null).first()
        assertEquals(listOf("Swiggy"), rows.map { it.merchantName })
    }

    @Test
    fun repository_filters_by_amount() = runBlocking {
        val rows = transactionRepository.observeFiltered("1200", null).first()
        assertEquals(listOf("Amazon"), rows.map { it.merchantName })
    }

    @Test
    fun viewModel_search_typed_char_by_char_filters_list() = runBlocking {
        val vm = HomeViewModel(transactionRepository, categoryRepository, DeepLinkCoordinator())
        "  ama".forEach { ch -> vm.setSearchQuery(vm.searchText.value + ch) }
        assertEquals("  ama", vm.searchText.value)

        val state = withTimeout(5_000) {
            vm.uiState.first { !it.isLoading && it.transactions.map { t -> t.merchantName } == listOf("Amazon") }
        }
        assertEquals(1, state.transactions.size)

        vm.setSearchQuery("")
        val cleared = withTimeout(5_000) { vm.uiState.first { it.transactions.size == 3 } }
        assertEquals(3, cleared.transactions.size)
    }

    @Test
    fun monthSummary_reports_earned_separately_from_spent() = runBlocking {
        val summary = transactionRepository.observeMonthSummary().first()
        assertEquals(1450.0, summary.currentMonthTotal, 0.001)
        assertEquals(5000.0, summary.currentMonthEarned, 0.001)
    }

    @Test
    fun updateAmount_persists_and_marks_clarified() = runBlocking {
        val swiggy = transactionRepository.observeFiltered("swiggy", null).first().single()
        transactionRepository.updateAmount(swiggy.id, 275.5)
        val updated = transactionRepository.getById(swiggy.id)!!
        assertEquals(275.5, updated.amount, 0.001)
        assertEquals(true, updated.isClarified)
    }

    private suspend fun insert(amount: Double, merchant: String, categoryId: Long, raw: String, type: String = "DEBIT") {
        val now = System.currentTimeMillis()
        db.transactionDao().insert(
            TransactionEntity(
                amount = amount,
                txnType = type,
                merchantName = merchant,
                cleanPayee = merchant,
                categoryId = categoryId,
                timestamp = now,
                sourcePackage = "test",
                sourceType = "APP_NOTIFICATION",
                rawNotificationText = raw,
                confidenceScore = 0.9f,
                confidenceTier = "TIER_1_LOCAL_SLM",
                dedupHash = null,
                accountReference = null,
            )
        )
    }
}
