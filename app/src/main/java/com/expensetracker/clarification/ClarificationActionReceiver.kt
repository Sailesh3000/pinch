package com.expensetracker.clarification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.expensetracker.core.database.AppDatabase
import com.expensetracker.core.model.ConfidenceTier
import com.expensetracker.extraction.TemplateCacheEngine
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * FR-CLARIFY-03: Handles interactive notification action button taps.
 * When the user taps a category button on a clarification notification,
 * this receiver resolves the transaction and dismisses the notification.
 */
class ClarificationActionReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DatabaseEntryPoint {
        fun appDatabase(): AppDatabase
    }

    override fun onReceive(context: Context, intent: Intent) {
        val transactionId = intent.getLongExtra(EXTRA_TRANSACTION_ID, -1L)
        val categoryId = intent.getLongExtra(EXTRA_CATEGORY_ID, -1L)
        val categoryName = intent.getStringExtra(EXTRA_CATEGORY_NAME) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        if (transactionId == -1L || categoryId == -1L) {
            Log.w(TAG, "Invalid intent extras: transactionId=$transactionId, categoryId=$categoryId")
            return
        }

        Log.d(TAG, "Notification action: txn=$transactionId, category=$categoryName ($categoryId)")

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            DatabaseEntryPoint::class.java,
        )
        val db = entryPoint.appDatabase()

        val pendingResult = goAsync()
        scope.launch {
            try {
                val transactionDao = db.transactionDao()
                val historyDao = db.clarificationHistoryDao()

                val existing = transactionDao.getById(transactionId) ?: return@launch

                // Step 1: update transaction
                transactionDao.update(
                    existing.copy(
                        categoryId = categoryId,
                        needsClarification = false,
                        isClarified = true,
                        confidenceTier = ConfidenceTier.MANUAL.dbValue,
                    )
                )

                // Step 2: insert clarification history
                historyDao.insert(
                    com.expensetracker.core.database.entity.ClarificationHistoryEntity(
                        transactionId = transactionId,
                        rawText = existing.rawNotificationText,
                        suggestedCategoryId = null,
                        chosenCategoryId = categoryId,
                        responseTimeMs = 0L,
                        clarificationSource = "NOTIFICATION_ACTION",
                    )
                )

                // Step 3: promote to Tier 0 cache
                val cacheEngine = TemplateCacheEngine(db.templateCacheDao())
                cacheEngine.promote(
                    packageName = existing.sourcePackage,
                    sourceType = existing.sourceType,
                    rawText = existing.rawNotificationText,
                    merchantName = existing.merchantName,
                    defaultCategoryId = categoryId,
                )

                // Dismiss the notification
                if (notificationId != -1) {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.cancel(notificationId)
                }

                Log.d(TAG, "Resolved txn=$transactionId with category=$categoryName via notification action")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve from notification action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ClarificationAction"
        const val EXTRA_TRANSACTION_ID = "transaction_id"
        const val EXTRA_CATEGORY_ID = "category_id"
        const val EXTRA_CATEGORY_NAME = "category_name"
        const val EXTRA_NOTIFICATION_ID = "notification_id"

        fun createIntent(
            context: Context,
            transactionId: Long,
            categoryId: Long,
            categoryName: String,
            notificationId: Int,
        ): Intent {
            return Intent(context, ClarificationActionReceiver::class.java).apply {
                putExtra(EXTRA_TRANSACTION_ID, transactionId)
                putExtra(EXTRA_CATEGORY_ID, categoryId)
                putExtra(EXTRA_CATEGORY_NAME, categoryName)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
        }
    }
}
