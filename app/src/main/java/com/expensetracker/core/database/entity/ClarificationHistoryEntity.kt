package com.expensetracker.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * FR-CLARIFY-04: Stores every user clarification answer. Records the original
 * suggested category, the category the user chose, response time, and the
 * answer source (chip, detail-view correction, etc.).
 */
@Entity(tableName = "clarification_history")
data class ClarificationHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "transaction_id")
    val transactionId: Long,

    @ColumnInfo(name = "raw_text")
    val rawText: String,

    @ColumnInfo(name = "suggested_category_id")
    val suggestedCategoryId: Long?,

    @ColumnInfo(name = "chosen_category_id")
    val chosenCategoryId: Long,

    @ColumnInfo(name = "response_time_ms")
    val responseTimeMs: Long,

    @ColumnInfo(name = "clarification_source")
    val clarificationSource: String, // "NOTIFICATION_CHIP", "IN_APP_REVIEW", "DETAIL_VIEW_CORRECTION"

    @ColumnInfo(name = "answered_at")
    val answeredAt: Long = System.currentTimeMillis()
)
