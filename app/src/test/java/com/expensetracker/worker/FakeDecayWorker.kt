package com.expensetracker.worker

import com.expensetracker.core.database.dao.TemplateCacheDao
import com.expensetracker.core.database.entity.TemplateCacheEntity

class FakeDecayWorker(
    private val transactionDao: FakeTransactionDaoForDecay,
    private val templateCacheDao: TemplateCacheDao,
) {
    suspend fun resolveExpired(nowEpochMillis: Long): Int {
        val cutoff = nowEpochMillis - ClarificationAutoDecayWorker.FORTY_EIGHT_HOURS_MS
        return transactionDao.autoResolveExpired(cutoff)
    }
}
