package com.expensetracker.core.model

enum class TransactionType(val dbValue: String) {
    DEBIT("DEBIT"),
    CREDIT("CREDIT"),
    TRANSFER("TRANSFER");

    companion object {
        fun from(value: String?): TransactionType =
            entries.firstOrNull { it.dbValue == value } ?: TRANSFER
    }
}
