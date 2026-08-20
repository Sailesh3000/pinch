package com.expensetracker.di

import com.expensetracker.core.database.dao.CategoryDao
import com.expensetracker.core.database.dao.TransactionDao
import com.expensetracker.extraction.ExtractorChain
import com.expensetracker.insights.AnomalyDetector
import com.expensetracker.insights.NarrativeGenerator
import com.expensetracker.insights.TextToSQLTranslator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object InsightsModule {

    @Provides
    @Singleton
    fun provideNarrativeGenerator(
        extractorChain: ExtractorChain,
    ): NarrativeGenerator = NarrativeGenerator(extractorChain)

    @Provides
    @Singleton
    fun provideAnomalyDetector(
        transactionDao: TransactionDao,
    ): AnomalyDetector = AnomalyDetector(transactionDao)

    @Provides
    @Singleton
    fun provideTextToSQLTranslator(
        transactionDao: TransactionDao,
        categoryDao: CategoryDao,
    ): TextToSQLTranslator = TextToSQLTranslator(transactionDao, categoryDao)
}
