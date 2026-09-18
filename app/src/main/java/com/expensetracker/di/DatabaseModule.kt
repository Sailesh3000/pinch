package com.expensetracker.di

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.expensetracker.core.database.AppDatabase
import com.expensetracker.core.database.DatabaseKeyProvider
import com.expensetracker.core.database.LegacyDatabaseMigrator
import com.expensetracker.core.database.MIGRATION_1_2
import com.expensetracker.core.database.MIGRATION_2_3
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
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideSupportFactory(
        @ApplicationContext context: Context,
        databaseKeyProvider: DatabaseKeyProvider,
    ): SupportSQLiteOpenHelper.Factory {
        // Loads the SQLCipher native library once per process. When the native
        // lib is unavailable (Robolectric unit tests, exotic/unrooted JVM
        // tooling) we degrade to the framework (plaintext) SQLite factory so
        // the app still runs; on real devices SQLCipher is always used.
        if (loadSqlCipherNative()) {
            return SupportOpenHelperFactory(databaseKeyProvider.getOrCreatePassphrase())
        }
        Log.w(TAG, "SQLCipher native library unavailable - using plaintext framework SQLite")
        return FrameworkSQLiteOpenHelperFactory()
    }

    private fun loadSqlCipherNative(): Boolean = try {
        System.loadLibrary("sqlcipher")
        true
    } catch (e: UnsatisfiedLinkError) {
        false
    }

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        supportFactory: SupportSQLiteOpenHelper.Factory,
        legacyDatabaseMigrator: LegacyDatabaseMigrator,
    ): AppDatabase {
        // One-time migration for installs that predate SQLCipher: detects a
        // plaintext DB, backs it up, and stages rows for re-import into the
        // freshly created encrypted DB (see importPending below).
        val hasLegacyData = legacyDatabaseMigrator.runIfNeeded()

        return Room.databaseBuilder(context, AppDatabase::class.java, "expense_tracker.db")
            .openHelperFactory(supportFactory)
            .addCallback(SeedDatabaseCallback())
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    if (hasLegacyData) {
                        legacyDatabaseMigrator.importPending(db)
                    }
                }
            })
            .build()
    }

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

    private const val TAG = "DatabaseModule"
}