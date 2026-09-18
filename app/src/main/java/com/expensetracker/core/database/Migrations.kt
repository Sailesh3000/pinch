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

/**
 * v2 -> v3: expands the monitored package whitelist with popular Indian
 * bank/fintech packages from beta feedback (FR-INGEST-02). INSERT OR IGNORE
 * keeps existing user toggles intact and avoids duplicate package_names
 * (guaranteed by the unique index on monitored_packages.package_name).
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        for ((pkg, label) in NEW_PACKAGES) {
            db.execSQL(
                "INSERT OR IGNORE INTO monitored_packages(package_name, app_label, is_enabled, added_at) " +
                    "VALUES(?, ?, 1, ?)",
                arrayOf(pkg, label, now)
            )
        }
    }

    private val NEW_PACKAGES = listOf(
        "com.indusind.digital" to "IndusInd Bank",
        "com.idbi.mobilebanking" to "IDBI Bank",
        "com.bob.mobilebanking" to "Bank of Baroda",
        "com.unionbankofindia.ecommerce.mobile" to "Union Bank",
        "com.csam.icici.bank.imobile" to "iMobile Pay",
        "com.freecharge.android" to "Freecharge",
        "com.mobikwik_new" to "MobiKwik",
        "in.amazon.mShop.android.shopping" to "Amazon Pay",
        "com.whatsapp" to "WhatsApp Pay",
        "com.slice" to "Slice",
        "com.jupiter.money" to "Jupiter",
        "com.fi.money" to "Fi Money",
    )
}
