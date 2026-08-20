package com.expensetracker.core.database

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Seeds the database on first creation so category ids and the package
 * whitelist are present before any transaction is written (avoids a race
 * between seeding and first insert).
 */
class SeedDatabaseCallback : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        val categories = SeedData.defaultCategories()
        val packages = SeedData.defaultPackages()
        db.beginTransaction()
        try {
            for (c in categories) {
                db.execSQL(
                    "INSERT OR IGNORE INTO categories(name, icon_key, color_hex, is_system_default, usage_count) " +
                        "VALUES(?, ?, ?, 1, 0)",
                    arrayOf(c.name, c.iconKey, c.colorHex)
                )
            }
            for (p in packages) {
                db.execSQL(
                    "INSERT OR IGNORE INTO monitored_packages(package_name, app_label, is_enabled, added_at) " +
                        "VALUES(?, ?, 1, ?)",
                    arrayOf(p.packageName, p.appLabel, System.currentTimeMillis())
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
