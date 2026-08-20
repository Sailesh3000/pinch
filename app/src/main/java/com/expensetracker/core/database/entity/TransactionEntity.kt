package com.expensetracker.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["category_id"]),
        Index(value = ["merchant_name"]),
        Index(value = ["needs_clarification"]),
        Index(value = ["dedup_hash"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "amount")
    val amount: Double,

    @ColumnInfo(name = "currency")
    val currency: String = "INR",

    @ColumnInfo(name = "txn_type")
    val txnType: String,

    @ColumnInfo(name = "merchant_name")
    val merchantName: String,

    @ColumnInfo(name = "clean_payee")
    val cleanPayee: String?,

    @ColumnInfo(name = "category_id")
    val categoryId: Long,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "source_package")
    val sourcePackage: String,

    @ColumnInfo(name = "source_type")
    val sourceType: String,

    @ColumnInfo(name = "raw_notification_text")
    val rawNotificationText: String,

    @ColumnInfo(name = "confidence_score")
    val confidenceScore: Float,

    @ColumnInfo(name = "confidence_tier")
    val confidenceTier: String,

    @ColumnInfo(name = "needs_clarification")
    val needsClarification: Boolean = false,

    @ColumnInfo(name = "is_clarified")
    val isClarified: Boolean = false,

    @ColumnInfo(name = "dedup_hash")
    val dedupHash: String?,

    @ColumnInfo(name = "merged_from_dual_source")
    val mergedFromDualSource: Boolean = false,

    @ColumnInfo(name = "account_reference")
    val accountReference: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
