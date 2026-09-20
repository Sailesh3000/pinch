package com.expensetracker.ui

import android.content.Context
import android.os.Build
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.expensetracker.R
import com.expensetracker.core.database.SeedData
import com.expensetracker.core.database.dao.CategoryDao
import com.expensetracker.core.database.dao.ClarificationHistoryDao
import com.expensetracker.core.database.dao.TransactionDao
import com.expensetracker.core.database.entity.TransactionEntity
import com.expensetracker.core.model.Category
import com.expensetracker.core.model.ConfidenceTier
import com.expensetracker.core.model.SourceType
import com.expensetracker.core.model.Transaction
import com.expensetracker.core.model.TransactionType
import com.expensetracker.data.repository.CategoryRepository
import com.expensetracker.data.repository.ClarificationRepository
import com.expensetracker.data.repository.TransactionRepository
import com.expensetracker.extraction.DeterministicRegexExtractor
import com.expensetracker.extraction.ExtractorChain
import com.expensetracker.extraction.GeminiNanoExtractor
import com.expensetracker.extraction.TemplateCacheEngine
import com.expensetracker.ingestion.PackageWhitelist
import com.expensetracker.ingestion.TransactionNotificationListenerService
import com.expensetracker.insights.AnomalyDetector
import com.expensetracker.insights.NarrativeGenerator
import com.expensetracker.insights.TextToSQLTranslator
import com.expensetracker.ui.detail.TransactionDetailSheet
import com.expensetracker.ui.home.HomeScreen
import com.expensetracker.ui.home.HomeViewModel
import com.expensetracker.ui.insights.InsightsScreen
import com.expensetracker.ui.insights.InsightsViewModel
import com.expensetracker.ui.insights.SubscriptionDetector
import com.expensetracker.ui.review.ReviewScreen
import com.expensetracker.ui.review.ReviewViewModel
import com.expensetracker.ui.theme.ExpenseTrackerTheme
import com.github.takahirom.roborazzi.captureRoboImage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import com.expensetracker.insights.DeepLinkCoordinator
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = HiltTestApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class E2EUiTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @javax.inject.Inject
    lateinit var transactionDao: TransactionDao

    @javax.inject.Inject
    lateinit var categoryDao: CategoryDao

    @javax.inject.Inject
    lateinit var clarificationHistoryDao: ClarificationHistoryDao

    @javax.inject.Inject
    lateinit var packageWhitelist: PackageWhitelist

    @javax.inject.Inject
    lateinit var templateCacheEngine: TemplateCacheEngine

    private lateinit var service: TransactionNotificationListenerService
    private lateinit var context: Context

    @Before
    fun setUp() {
        hiltRule.inject()
        context = ApplicationProvider.getApplicationContext()
        
        // Clear existing data
        runBlocking {
            transactionDao.deleteAll()
            categoryDao.deleteAll()
            // Re-seed categories
            categoryDao.insertAll(SeedData.defaultCategories())
        }
        
        // Build service with Hilt injection
        service = Robolectric.buildService(TransactionNotificationListenerService::class.java)
            .create().get()
        
        waitFor("whitelist refresh") {
            packageWhitelist.snapshot().takeIf { PKG_PHONEPE in it }
        }
    }

    @Test
    fun homeScreen_withRealTransactions() {
        // Seed 10 transactions
        runBlocking {
            repeat(10) { i ->
                transactionDao.insert(
                    TransactionEntity(
                        amount = 100.0 + i * 50,
                        currency = "INR",
                        txnType = "DEBIT",
                        merchantName = "Merchant $i",
                        cleanPayee = null,
                        categoryId = 1L,
                        timestamp = System.currentTimeMillis() - i * 86400000L,
                        sourcePackage = PKG_PHONEPE,
                        sourceType = "APP_NOTIFICATION",
                        rawNotificationText = "Test transaction $i",
                        confidenceScore = 0.9f,
                        confidenceTier = "TIER_1_LOCAL_SLM",
                        needsClarification = false,
                        isClarified = false,
                        dedupHash = "hash$i",
                        mergedFromDualSource = false,
                        accountReference = null,
                        createdAt = System.currentTimeMillis(),
                    )
                )
            }
        }

        // Create ViewModel with real repositories
        val vm = HomeViewModel(
            TransactionRepository(transactionDao),
            CategoryRepository(categoryDao),
            DeepLinkCoordinator(),
        )

        // Capture screenshot
        captureRoboImage("build/roborazzi/e2e/home_real_data.png") {
            ExpenseTrackerTheme {
                HomeScreen(viewModel = vm)
            }
        }
    }

    @Test
    fun insightsScreen_withRealData() {
        // Seed 20 transactions across multiple categories
        runBlocking {
            val categories = listOf("Food & Dining", "Groceries", "Transportation", "Shopping")
            repeat(20) { i ->
                val categoryName = categories[i % categories.size]
                val category = categoryDao.getByName(categoryName)
                transactionDao.insert(
                    TransactionEntity(
                        amount = 200.0 + i * 100,
                        currency = "INR",
                        txnType = "DEBIT",
                        merchantName = "Store $i",
                        cleanPayee = null,
                        categoryId = category?.id ?: 1L,
                        timestamp = System.currentTimeMillis() - i * 86400000L,
                        sourcePackage = PKG_PHONEPE,
                        sourceType = "APP_NOTIFICATION",
                        rawNotificationText = "Test transaction $i",
                        confidenceScore = 0.85f,
                        confidenceTier = "TIER_1_LOCAL_SLM",
                        needsClarification = false,
                        isClarified = false,
                        dedupHash = "hash$i",
                        mergedFromDualSource = false,
                        accountReference = null,
                        createdAt = System.currentTimeMillis(),
                    )
                )
            }
        }

        val vm = InsightsViewModel(
            transactionDao = transactionDao,
            transactionRepository = TransactionRepository(transactionDao),
            subscriptionDetector = SubscriptionDetector(),
            narrativeGenerator = NarrativeGenerator(
                ExtractorChain(
                    object : GeminiNanoExtractor() {
                        override suspend fun isAvailable() = false
                    },
                    DeterministicRegexExtractor()
                )
            ),
            textToSQLTranslator = TextToSQLTranslator(transactionDao, categoryDao),
            anomalyDetector = AnomalyDetector(transactionDao),
            deepLinkCoordinator = DeepLinkCoordinator(),
        )

        captureRoboImage("build/roborazzi/e2e/insights_real_data.png") {
            ExpenseTrackerTheme {
                InsightsScreen(viewModel = vm, onNavigateToHome = {})
            }
        }
    }

    @Test
    fun reviewScreen_withPendingClarifications() {
        // Seed 5 low-confidence transactions
        runBlocking {
            repeat(5) { i ->
                transactionDao.insert(
                    TransactionEntity(
                        amount = 150.0 + i * 25,
                        currency = "INR",
                        txnType = "DEBIT",
                        merchantName = "Unknown $i",
                        cleanPayee = null,
                        categoryId = 1L,
                        timestamp = System.currentTimeMillis() - i * 3600000L,
                        sourcePackage = PKG_PHONEPE,
                        sourceType = "APP_NOTIFICATION",
                        rawNotificationText = "Test transaction $i",
                        confidenceScore = 0.45f, // LOW confidence
                        confidenceTier = "TIER_1_LOCAL_SLM",
                        needsClarification = true,
                        isClarified = false,
                        dedupHash = "hash$i",
                        mergedFromDualSource = false,
                        accountReference = null,
                        createdAt = System.currentTimeMillis(),
                    )
                )
            }
        }

        val vm = ReviewViewModel(
            clarificationRepository = ClarificationRepository(
                transactionDao, clarificationHistoryDao, templateCacheEngine
            ),
            categoryRepository = CategoryRepository(categoryDao),
        )

        captureRoboImage("build/roborazzi/e2e/review_pending.png") {
            ExpenseTrackerTheme {
                ReviewScreen(viewModel = vm)
            }
        }
    }

    @Test
    fun notificationInjection_appearsInHomeScreen() {
        // Inject a real notification
        postNotification(
            PKG_PHONEPE, 100, "Payment Successful",
            "Rs. 450.00 debited. UPI/SWIGGY/PHONEPE/1234, A/c *1234",
        )

        // Wait for transaction to be saved
        val row = waitFor("transaction in Room") {
            runBlocking { transactionDao.observeAll().first() }
                .firstOrNull { it.transaction.merchantName == "SWIGGY" }
        }

        // Verify it's in the database
        assertEquals(450.0, row.transaction.amount, 0.001)
        assertEquals("SWIGGY", row.transaction.merchantName)

        // Capture HomeScreen with the new transaction
        val vm = HomeViewModel(
            TransactionRepository(transactionDao),
            CategoryRepository(categoryDao),
            DeepLinkCoordinator(),
        )

        captureRoboImage("build/roborazzi/e2e/home_after_notification.png") {
            ExpenseTrackerTheme {
                HomeScreen(viewModel = vm)
            }
        }
    }

    @Test
    fun transactionDetailSheet_onClick() {
        // Seed a transaction
        val txnId = runBlocking {
            transactionDao.insert(
                TransactionEntity(
                    amount = 999.0,
                    currency = "INR",
                    txnType = "DEBIT",
                    merchantName = "Amazon",
                    cleanPayee = null,
                    categoryId = 4L, // Shopping
                    timestamp = System.currentTimeMillis(),
                    sourcePackage = PKG_PHONEPE,
                    sourceType = "APP_NOTIFICATION",
                    rawNotificationText = "Rs. 999 debited at Amazon",
                    confidenceScore = 0.92f,
                    confidenceTier = "TIER_1_LOCAL_SLM",
                    needsClarification = false,
                    isClarified = false,
                    dedupHash = "hash_amazon",
                    mergedFromDualSource = false,
                    accountReference = "A/c *1234",
                    createdAt = System.currentTimeMillis(),
                )
            )
        }

        val txnEntity = runBlocking { transactionDao.getById(txnId) }!!
        val category = runBlocking { categoryDao.getById(txnEntity.categoryId) }
        val categories = runBlocking { categoryDao.getAllOnce() }
        
        // Convert TransactionEntity to Transaction model
        val transaction = Transaction(
            id = txnEntity.id,
            amount = txnEntity.amount,
            currency = txnEntity.currency,
            txnType = TransactionType.valueOf(txnEntity.txnType),
            merchantName = txnEntity.merchantName,
            cleanPayee = txnEntity.cleanPayee,
            categoryId = txnEntity.categoryId,
            categoryName = category?.name ?: "Unknown",
            categoryIconKey = category?.iconKey,
            categoryColorHex = category?.colorHex,
            timestamp = txnEntity.timestamp,
            sourcePackage = txnEntity.sourcePackage,
            sourceType = SourceType.valueOf(txnEntity.sourceType),
            rawNotificationText = txnEntity.rawNotificationText,
            confidenceScore = txnEntity.confidenceScore,
            confidenceTier = ConfidenceTier.valueOf(txnEntity.confidenceTier),
            needsClarification = txnEntity.needsClarification,
            isClarified = txnEntity.isClarified,
            dedupHash = txnEntity.dedupHash,
            mergedFromDualSource = txnEntity.mergedFromDualSource,
            accountReference = txnEntity.accountReference,
            createdAt = txnEntity.createdAt,
        )

        // Show transaction detail sheet
        captureRoboImage("build/roborazzi/e2e/transaction_detail.png") {
            ExpenseTrackerTheme {
                TransactionDetailSheet(
                    transaction = transaction,
                    categories = categories.map { 
                        Category(
                            id = it.id,
                            name = it.name,
                            iconKey = it.iconKey,
                            colorHex = it.colorHex,
                            isSystemDefault = it.isSystemDefault,
                            usageCount = it.usageCount,
                        )
                    },
                    onDismiss = {},
                    onUpdateCategory = { _, _ -> },
                    onDelete = {},
                )
            }
        }
    }

    // Helper methods

    private fun postNotification(packageName: String, id: Int, title: String, body: String) {
        val notification = NotificationCompat.Builder(context, "e2e_ui_test")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .build()
        val sbn = StatusBarNotification(
            packageName, null, id, ":$packageName:$id:u0",
            1000, 0, 1000, notification, UserHandle.getUserHandleForUid(1000), System.currentTimeMillis(),
        )
        service.onNotificationPosted(sbn)
    }

    private fun <T : Any> waitFor(description: String, timeoutMs: Long = 30_000, condition: () -> T?): T {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            condition()?.let { return it }
            Thread.sleep(50)
        }
        error("Timed out after ${timeoutMs}ms waiting for $description")
    }

    private companion object {
        const val PKG_PHONEPE = "com.phonepe.app"
    }
}
