package com.expensetracker.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * FR-EXTRACT-01 / FR-CLARIFY-04: Learned regex signatures compiled from
 * user clarification answers. Each entry maps a structural text pattern to
 * a deterministic extraction regex, enabling Tier 0 instant extraction
 * without invoking the SLM.
 *
 * Uniqueness constraint: (package_name, pattern_hash).
 */
@Entity(
    tableName = "template_cache",
    indices = [
        Index(value = ["package_name", "pattern_hash"], unique = true)
    ]
)
data class TemplateCacheEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "pattern_hash")
    val patternHash: String, // SHA-256 of normalized structural tokens

    @ColumnInfo(name = "regex_pattern")
    val regexPattern: String, // Compiled extraction regex with named groups: (?<amount>...), (?<merchant>...)

    @ColumnInfo(name = "default_category_id")
    val defaultCategoryId: Long,

    @ColumnInfo(name = "merchant_capture_group")
    val merchantCaptureGroup: String = "merchant",

    @ColumnInfo(name = "amount_capture_group")
    val amountCaptureGroup: String = "amount",

    @ColumnInfo(name = "hit_count")
    val hitCount: Long = 0,

    @ColumnInfo(name = "last_hit_timestamp")
    val lastHitTimestamp: Long = 0
)
