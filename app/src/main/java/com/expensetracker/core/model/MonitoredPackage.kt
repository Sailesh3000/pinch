package com.expensetracker.core.model

data class MonitoredPackage(
    val id: Long,
    val packageName: String,
    val appLabel: String,
    val isEnabled: Boolean,
    val addedAt: Long,
)
