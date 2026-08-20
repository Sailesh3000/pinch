package com.expensetracker.ingestion

data class NotificationEvent(
    val packageName: String,
    val title: String,
    val body: String,
    val timestamp: Long,
    val notificationKey: String,
)
