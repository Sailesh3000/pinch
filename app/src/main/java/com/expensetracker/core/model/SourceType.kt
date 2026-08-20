package com.expensetracker.core.model

enum class SourceType(val dbValue: String) {
    APP_NOTIFICATION("APP_NOTIFICATION"),
    SMS_NOTIFICATION("SMS_NOTIFICATION"),
    MANUAL_ENTRY("MANUAL_ENTRY");

    companion object {
        fun from(value: String?): SourceType =
            entries.firstOrNull { it.dbValue == value } ?: APP_NOTIFICATION
    }
}
