package com.expensetracker.ingestion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.expensetracker.data.repository.TransactionRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Debug-only broadcast receiver to test the FULL pipeline (extraction → DB write) from adb:
 *   adb shell am broadcast -n com.expensetracker/.ingestion.DebugPipelineReceiver \
 *     --es title "HDFC Bank" \
 *     --es body "INR 1,250.00 debited from A/c XX4567 at Swiggy on 17-Aug-26. Avl bal: INR 12,345.67"
 *
 * To wipe all test transactions (keeps the downloaded model) send the clear action:
 *   adb shell am broadcast -n com.expensetracker/.ingestion.DebugPipelineReceiver --es action clear
 */
@AndroidEntryPoint
class DebugPipelineReceiver : BroadcastReceiver() {

    @Inject lateinit var processor: NotificationProcessor
    @Inject lateinit var transactionRepository: TransactionRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getStringExtra("action") == "clear") {
            val pendingResult = goAsync()
            scope.launch {
                try {
                    transactionRepository.deleteAll()
                    Log.i(TAG, "=== DEBUG CLEAR — all test transactions deleted ===")
                } catch (e: Exception) {
                    Log.e(TAG, "Debug clear failed", e)
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        val packageName = intent.getStringExtra("package") ?: "com.android.shell"
        val title = intent.getStringExtra("title") ?: "HDFC Bank"
        val body = intent.getStringExtra("body")
            ?: "INR 1,250.00 debited from A/c XX4567 at Swiggy on 17-Aug-26. Avl bal: INR 12,345.67"

        Log.i(TAG, "=== DEBUG PIPELINE TEST ===")
        Log.i(TAG, "Package: $packageName | Title: $title | Body: $body")

        val pendingResult = goAsync()
        scope.launch {
            try {
                val event = NotificationEvent(
                    packageName = packageName,
                    title = title,
                    body = body,
                    timestamp = System.currentTimeMillis(),
                    notificationKey = "debug_${System.currentTimeMillis()}",
                )
                processor.process(event)
                Log.i(TAG, "=== PIPELINE COMPLETE — transaction saved to DB ===")
            } catch (e: Exception) {
                Log.e(TAG, "Pipeline test failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "DebugPipeline"
    }
}
