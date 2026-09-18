package com.expensetracker.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.expensetracker.core.database.dao.CategoryDao
import com.expensetracker.core.database.dao.ClarificationHistoryDao
import com.expensetracker.core.database.dao.MonitoredPackageDao
import com.expensetracker.core.database.dao.TemplateCacheDao
import com.expensetracker.core.database.dao.TransactionDao
import com.expensetracker.core.database.entity.CategoryEntity
import com.expensetracker.core.database.entity.ClarificationHistoryEntity
import com.expensetracker.core.database.entity.MonitoredPackageEntity
import com.expensetracker.core.database.entity.TemplateCacheEntity
import com.expensetracker.core.database.entity.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        MonitoredPackageEntity::class,
        ClarificationHistoryEntity::class,
        TemplateCacheEntity::class,
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun monitoredPackageDao(): MonitoredPackageDao
    abstract fun clarificationHistoryDao(): ClarificationHistoryDao
    abstract fun templateCacheDao(): TemplateCacheDao
}
