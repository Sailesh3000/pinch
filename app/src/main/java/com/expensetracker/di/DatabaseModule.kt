package com.expensetracker.di

import android.content.Context
import androidx.room.Room
import com.expensetracker.core.database.AppDatabase
import com.expensetracker.core.database.MIGRATION_1_2
import com.expensetracker.core.database.SeedDatabaseCallback
import com.expensetracker.core.database.dao.CategoryDao
import com.expensetracker.core.database.dao.ClarificationHistoryDao
import com.expensetracker.core.database.dao.MonitoredPackageDao
import com.expensetracker.core.database.dao.TemplateCacheDao
import com.expensetracker.core.database.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "expense_tracker.db")
            .addCallback(SeedDatabaseCallback())
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideTransactionDao(db: AppDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideMonitoredPackageDao(db: AppDatabase): MonitoredPackageDao = db.monitoredPackageDao()

    @Provides
    fun provideClarificationHistoryDao(db: AppDatabase): ClarificationHistoryDao = db.clarificationHistoryDao()

    @Provides
    fun provideTemplateCacheDao(db: AppDatabase): TemplateCacheDao = db.templateCacheDao()
}
