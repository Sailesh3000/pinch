package com.expensetracker.ingestion

import android.app.Notification
import android.content.pm.ApplicationInfo
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.expensetracker.core.database.entity.MonitoredPackageEntity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Unified ingestion point (FR-INGEST-01). Captures bank/UPI/wallet app
 * notifications AND SMS-originated bank alerts (via the default Messages app's
 * notification), as declared in `notification-expense-tracker-plan.md` §4.
 *
 * FR-INGEST-04: `onNotificationPosted` runs only a sub-millisecond in-memory
 * pre-filter on the main thread, then hands off to an async Channel. All disk,
 * model, and DB work happens on the background dispatcher.
 */
@AndroidEntryPoint
class TransactionNotificationListenerService : NotificationListenerService() {

    @Inject
    lateinit var preFilterEngine: PreFilterEngine

    @Inject
    lateinit var processor: NotificationProcessor

    @Inject
    lateinit var whitelist: PackageWhitelist

    @Inject
    lateinit var monitoredPackageDao: com.expensetracker.core.database.dao.MonitoredPackageDao

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val channel = Channel<NotificationEvent>(Channel.UNLIMITED)

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            runCatching { whitelist.refresh(monitoredPackageDao.getAllOnce()) }
                .onFailure { Log.w(TAG, "Failed to seed whitelist cache", it) }
        }
        scope.launch {
            for (event in channel) {
                runCatching { processor.process(event) }
                    .onFailure { Log.e(TAG, "Failed to process notification ${event.notificationKey}", it) }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification listener connected")
        // Phase 1 deliberately does not replay active notifications on connect,
        // to avoid importing a burst of pre-existing transactions.
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Fast path — FR-INGEST-04: <5ms on the main thread.
        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE).orEmpty()
        val body = extras.getString(Notification.EXTRA_TEXT).orEmpty()
        val isCandidate = preFilterEngine.isFinancialCandidate(packageName, title, body)
        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebuggable && whitelist.isWhitelisted(packageName)) {
            Log.d(TAG, "pkg=$packageName candidate=$isCandidate title=\"$title\" body=\"$body\"")
        }
        if (!isCandidate) return

        channel.trySend(
            NotificationEvent(
                packageName = packageName,
                title = title,
                body = body,
                timestamp = sbn.postTime,
                notificationKey = sbn.key,
            )
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // No-op in Phase 1.
    }

    companion object {
        private const val TAG = "TxnListenerService"
    }
}
