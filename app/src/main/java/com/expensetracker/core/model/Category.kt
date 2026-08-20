package com.expensetracker.core.model

data class Category(
    val id: Long,
    val name: String,
    val iconKey: String,
    val colorHex: String,
    val isSystemDefault: Boolean,
    val usageCount: Int,
)
