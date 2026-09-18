package com.expensetracker.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.expensetracker.data.repository.CategoryRepository
import com.expensetracker.data.repository.ClarificationRepository
import com.expensetracker.data.repository.MonitoredPackageRepository
import com.expensetracker.data.repository.TransactionRepository
import com.expensetracker.extraction.DeterministicRegexExtractor
import com.expensetracker.extraction.ExtractorChain
import com.expensetracker.extraction.GeminiNanoExtractor
import com.expensetracker.extraction.ModelAssetProvider
import com.expensetracker.extraction.ModelDownloader
import com.expensetracker.extraction.TemplateCacheEngine
import com.expensetracker.ingestion.PackageWhitelist
import com.expensetracker.insights.AnomalyDetector
import com.expensetracker.insights.NarrativeGenerator
import com.expensetracker.insights.TextToSQLTranslator
import com.expensetracker.ui.home.HomeScreen
import com.expensetracker.ui.home.HomeViewModel
import com.expensetracker.ui.insights.InsightsScreen
import com.expensetracker.ui.insights.InsightsViewModel
import com.expensetracker.ui.insights.SubscriptionDetector
import com.expensetracker.ui.onboarding.OnboardingScreen
import com.expensetracker.ui.review.ReviewScreen
import com.expensetracker.ui.review.ReviewViewModel
import com.expensetracker.ui.settings.SettingsScreen
import com.expensetracker.ui.settings.SettingsViewModel
import com.expensetracker.ui.theme.ExpenseTrackerTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * JVM screenshot tests (Robolectric + Roborazzi, no emulator).
 *
 * Each test renders one app screen with realistic sample data and saves a
 * reference PNG to `app/build/roborazzi/`. On later runs Roborazzi compares
 * against the reference and fails on visual regressions.
 *
 * Run: `.\gradlew.bat :app:recordRoborazziDebug --tests "com.expensetracker.ui.ScreenshotTest"`
 * (record = regenerate references; `:app:verifyRoborazziDebug` = compare against them)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {

    @Test
    fun onboarding() {
        captureRoboImage("build/roborazzi/onboarding.png") {
            ExpenseTrackerTheme {
                OnboardingScreen(onComplete = {})
            }
        }
    }

    @Test
    fun home() {
        val vm = HomeViewModel(
            TransactionRepository(FakeTransactionDao()),
            CategoryRepository(FakeCategoryDao()),
        )
        val scope = warmUp(vm.uiState)
        try {
            captureRoboImage("build/roborazzi/home.png") {
                ExpenseTrackerTheme {
                    HomeScreen(viewModel = vm)
                }
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun review() {
        val vm = ReviewViewModel(
            ClarificationRepository(
                FakeTransactionDao(),
                FakeClarificationHistoryDao(),
                TemplateCacheEngine(FakeTemplateCacheDao()),
            ),
            CategoryRepository(FakeCategoryDao()),
        )
        val scope = warmUp(vm.uiState)
        try {
            captureRoboImage("build/roborazzi/review.png") {
                ExpenseTrackerTheme {
                    ReviewScreen(viewModel = vm)
                }
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w360dp-h1200dp-xxhdpi")
    fun insights() {
        val vm = insightsViewModel()
        captureRoboImage("build/roborazzi/insights.png") {
            ExpenseTrackerTheme {
                InsightsScreen(viewModel = vm)
            }
        }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w360dp-h1200dp-xxhdpi")
    fun insightsWithQuery() {
        val vm = insightsViewModel()
        vm.submitQuery("How much did I spend on Amazon this month?")
        captureRoboImage("build/roborazzi/insights_query.png") {
            ExpenseTrackerTheme {
                InsightsScreen(viewModel = vm)
            }
        }
    }

    @Test
    fun settings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Simulate the user having granted notification-listener access.
        android.provider.Settings.Secure.putString(
            context.contentResolver,
            "enabled_notification_listeners",
            "com.expensetracker/com.expensetracker.ingestion.TransactionNotificationListenerService",
        )
        val vm = SettingsViewModel(
            MonitoredPackageRepository(FakeMonitoredPackageDao(), PackageWhitelist()),
            TransactionRepository(FakeTransactionDao()),
            ExtractorChain(UnavailableNanoExtractor(), DeterministicRegexExtractor()),
            ModelDownloader(context),
            ModelAssetProvider(context),
            context,
        )
        vm.refreshPermissionStatus(true)
        vm.refreshAiStatus()
        val scope = warmUp(vm.uiState)
        try {
            captureRoboImage("build/roborazzi/settings.png") {
                ExpenseTrackerTheme {
                    SettingsScreen(viewModel = vm)
                }
            }
        } finally {
            scope.cancel()
        }
    }

    private fun insightsViewModel(): InsightsViewModel {
        val txnDao = FakeTransactionDao()
        return InsightsViewModel(
            transactionDao = txnDao,
            transactionRepository = TransactionRepository(txnDao),
            subscriptionDetector = SubscriptionDetector(),
            narrativeGenerator = NarrativeGenerator(
                ExtractorChain(UnavailableNanoExtractor(), DeterministicRegexExtractor()),
            ),
            textToSQLTranslator = TextToSQLTranslator(txnDao, FakeCategoryDao()),
            anomalyDetector = AnomalyDetector(txnDao),
        )
    }

    /**
     * Starts collecting a WhileSubscribed shared flow on the (Robolectric) main thread
     * so its StateFlow holds real data before the screen composes. Fakes emit
     * synchronously, so the first collection completes inline.
     */
    private fun warmUp(shared: StateFlow<*>): CoroutineScope {
        val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
        scope.launch { shared.collect { } }
        return scope
    }
}

/** ML Kit AICore does not exist on the JVM — report unavailable immediately. */
private class UnavailableNanoExtractor : GeminiNanoExtractor() {
    override suspend fun isAvailable(): Boolean = false
}
