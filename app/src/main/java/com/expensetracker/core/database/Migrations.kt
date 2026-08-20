package com.expensetracker.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // FR-CLARIFY-04: clarification_history table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `clarification_history` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `transaction_id` INTEGER NOT NULL,
                `raw_text` TEXT NOT NULL,
                `suggested_category_id` INTEGER,
                `chosen_category_id` INTEGER NOT NULL,
                `response_time_ms` INTEGER NOT NULL,
                `clarification_source` TEXT NOT NULL,
                `answered_at` INTEGER NOT NULL
            )
            """
        )

        // FR-EXTRACT-01 / FR-CLARIFY-04: template_cache table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `template_cache` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `package_name` TEXT NOT NULL,
                `pattern_hash` TEXT NOT NULL,
                `regex_pattern` TEXT NOT NULL,
                `default_category_id` INTEGER NOT NULL,
                `merchant_capture_group` TEXT NOT NULL DEFAULT 'merchant',
                `amount_capture_group` TEXT NOT NULL DEFAULT 'amount',
                `hit_count` INTEGER NOT NULL DEFAULT 0,
                `last_hit_timestamp` INTEGER NOT NULL DEFAULT 0
            )
            """
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_template_cache_package_name_pattern_hash` " +
            "ON `template_cache` (`package_name`, `pattern_hash`)"
        )
    }
}
