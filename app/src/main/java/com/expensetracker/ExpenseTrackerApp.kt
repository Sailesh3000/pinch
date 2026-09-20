package com.expensetracker

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.expensetracker.extraction.ExtractorChain
import com.expensetracker.extraction.MediaPipeExtractor
import com.expensetracker.extraction.ModelAssetProvider
import com.expensetracker.worker.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class ExpenseTrackerApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var extractorChain: ExtractorChain

    @Inject
    lateinit var modelAssetProvider: ModelAssetProvider

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        initSentry()
        WorkScheduler.enqueueClarificationDecay(this)
        wireBundledModelIfAvailable()
    }

    /**
     * ExtractorChain is a Hilt singleton built at first injection, which can
     * happen before a fast-follow Play Asset Delivery pack has finished
     * downloading - if so it's permanently constructed with no MediaPipe
     * engine, and extraction silently stays on regex even after the pack
     * lands, no matter what Settings reports. Re-checking here on every
     * process start (not just when the user happens to open Settings) is
     * what makes the bundled model actually take effect without requiring
     * that visit. Never touches ModelDownloader - a real network download
     * stays opt-in via the manual "Download AI Model" button only.
     */
    private fun wireBundledModelIfAvailable() {
        if (extractorChain.hasMediaPipeExtractor()) return
        appScope.launch {
            modelAssetProvider.modelPath()?.let { path ->
                extractorChain.updateMediaPipeExtractor(MediaPipeExtractor(this@ExpenseTrackerApp, path))
            }
        }
    }

    /**
     * Initializes crash reporting with strict privacy guarantees:
     *  - DSN comes from a buildConfig field (never hardcoded in source).
     *  - `sendDefaultPii = false` — no device identifiers.
     *  - `isEnableAutoSessionTracking = false` — no usage/session analytics.
     *  - A `beforeSend` scrubber strips any extra/breadcrumb that could carry
     *    financial data (amounts, merchants, account refs) before an event is
     *    sent, so only stack traces + OS/device info leave the device.
     */
    private fun initSentry() {
        if (BuildConfig.SENTRY_DSN.isBlank()) {
            Log.d(TAG, "SENTRY_DSN not configured — crash reporting disabled")
            return
        }
        SentryAndroid.init(this) { options ->
            options.dsn = BuildConfig.SENTRY_DSN
            options.isSendDefaultPii = false
            options.isEnableAutoSessionTracking = false
            options.beforeSend = SentryOptions.BeforeSendCallback { event, _ ->
                stripFinancialData(event)
            }
        }
    }

    private fun stripFinancialData(event: SentryEvent): SentryEvent {
        val keywords = listOf(
            "amount", "merchant", "upi", "paytm", "phonepe", "account", "transaction",
            "balance", "debit", "credit", "rupee", "rs.", "refund", "swiggy", "uni",
            "bank", "card", "wallet", "razorpay",
        )
        fun financial(s: String): Boolean =
            keywords.any { s.contains(it, ignoreCase = true) }

        event.apply {
            extras?.let { existing ->
                extras = existing.filterKeys { key -> !financial(key) }
            }
            breadcrumbs?.let { crumbs ->
                breadcrumbs = crumbs.filter { crumb ->
                    val message = crumb.message ?: ""
                    val data = crumb.data?.values?.joinToString(" ") ?: ""
                    !financial(message) && !financial(data)
                }
            }
            request?.let { req ->
                req.data?.let { data ->
                    if (data is Map<*, *>) {
                        req.data = data.filterKeys { key -> !financial(key?.toString() ?: "") }
                    }
                }
            }
        }
        return event
    }

    companion object {
        private const val TAG = "ExpenseTrackerApp"
    }
}