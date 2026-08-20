package com.expensetracker.core.model

enum class ConfidenceTier(val dbValue: String) {
    TIER_0_CACHE("TIER_0_CACHE"),
    TIER_1_LOCAL_SLM("TIER_1_LOCAL_SLM"),
    MANUAL("MANUAL"),
    AUTO_RESOLVED_AFTER_TIMEOUT("AUTO_RESOLVED_AFTER_TIMEOUT");

    companion object {
        fun from(value: String?): ConfidenceTier =
            entries.firstOrNull { it.dbValue == value } ?: TIER_1_LOCAL_SLM
    }
}
