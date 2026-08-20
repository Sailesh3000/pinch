package com.expensetracker.di

import com.expensetracker.core.database.dao.ClarificationHistoryDao
import com.expensetracker.core.database.dao.TransactionDao
import com.expensetracker.data.repository.ClarificationRepository
import com.expensetracker.extraction.TemplateCacheEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ClarificationModule {

    @Provides
    @Singleton
    fun provideClarificationRepository(
        transactionDao: TransactionDao,
        clarificationHistoryDao: ClarificationHistoryDao,
        templateCacheEngine: TemplateCacheEngine,
    ): ClarificationRepository =
        ClarificationRepository(transactionDao, clarificationHistoryDao, templateCacheEngine)
}
