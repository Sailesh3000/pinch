package com.expensetracker.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.expensetracker.core.database.dao.TransactionDao
import com.expensetracker.core.database.dao.TemplateCacheDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * FR-CLARIFY-05: Runs every 6 hours to auto-resolve pending clarifications
 * older than 48 hours. The transaction is marked with tier AUTO_RESOLVED_AFTER_TIMEOUT
 * and retains its best-guess category. Template cache hit_count is also decayed:
 * entries unused for 30 days are deleted.
 */
@HiltWorker
class ClarificationAutoDecayWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val transactionDao: TransactionDao,
    private val templateCacheDao: TemplateCacheDao,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val cutoff48h = System.currentTimeMillis() - FORTY_EIGHT_HOURS_MS
        val resolvedCount = transactionDao.autoResolveExpired(cutoff48h)

        val cutoff30d = System.currentTimeMillis() - THIRTY_DAYS_MS
        val staleTemplates = templateCacheDao.getAll()
            .filter { it.lastHitTimestamp < cutoff30d && it.hitCount < 2 }
        staleTemplates.forEach { templateCacheDao.deleteById(it.id) }

        return Result.success()
    }

    companion object {
        const val WORK_NAME = "clarification_auto_decay"
        val FORTY_EIGHT_HOURS_MS: Long = 48 * 60 * 60 * 1000
        val THIRTY_DAYS_MS: Long = 30L * 24 * 60 * 60 * 1000
    }
}
