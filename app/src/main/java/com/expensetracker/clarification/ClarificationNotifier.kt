package com.expensetracker.clarification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.expensetracker.MainActivity
import com.expensetracker.core.database.dao.TransactionDao
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FR-CLARIFY-03: Dispatches interactive Android notification actions for
 * low-confidence transactions. Shows 3 category suggestion buttons that
 * resolve the transaction directly from the notification shade.
 */
@Singleton
class ClarificationNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionDao: TransactionDao,
) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannel()
    }

    /**
     * Dispatches a clarification notification for a newly saved low-confidence transaction.
     * Queries top categories for the merchant and attaches them as action buttons.
     */
    suspend fun dispatchClarification(
        transactionId: Long,
        merchantName: String,
        amount: Double,
        currency: String,
        packageName: String,
    ) {
        val topCategories = transactionDao.topCategoriesForMerchant(merchantName, 3)
        if (topCategories.isEmpty()) return

        val notificationId = (transactionId % Int.MAX_VALUE).toInt()

        // Tap action: open the app to Review tab
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "review")
        }
        val pendingContent = PendingIntent.getActivity(
            context, notificationId, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.expensetracker.R.mipmap.ic_launcher)
            .setContentTitle("Categorize ₹${amount.toLong()} ${currency}")
            .setContentText("$merchantName — tap a category below")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingContent)

        // Add up to 3 category action buttons
        for (category in topCategories) {
            val actionIntent = ClarificationActionReceiver.createIntent(
                context = context,
                transactionId = transactionId,
                categoryId = category.id,
                categoryName = category.name,
                notificationId = notificationId,
            )
            val pendingAction = PendingIntent.getBroadcast(
                context,
                notificationId * 10 + topCategories.indexOf(category),
                actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, category.name, pendingAction)
        }

        notificationManager.notify(notificationId, builder.build())
    }

    fun dismiss(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Clarification",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Transaction category suggestions"
        }
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "clarification_actions"
    }
}
