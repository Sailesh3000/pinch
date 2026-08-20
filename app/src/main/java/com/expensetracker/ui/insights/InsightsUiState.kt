package com.expensetracker.ui.insights

import com.expensetracker.core.database.dao.CategorySpend
import com.expensetracker.core.math.DeterministicMathEngine
import com.expensetracker.insights.AnomalyItem
import com.expensetracker.insights.NarrativeResult
import com.expensetracker.insights.QueryResult

data class InsightsUiState(
    val isLoading: Boolean = true,
    val monthSummary: DeterministicMathEngine.MonthSpendSummary? = null,
    val categoryBreakdown: List<CategorySpend> = emptyList(),
    val monthlyTrend: List<MonthlyBarData> = emptyList(),
    val topMerchants: List<TopMerchantData> = emptyList(),
    val subscriptions: List<SubscriptionDetector.Subscription> = emptyList(),
    val confidenceStats: ConfidenceData? = null,
    // Phase 5 additions
    val spendingNarrative: NarrativeResult? = null,
    val anomalies: List<AnomalyItem> = emptyList(),
    val queryResult: QueryResult? = null,
    val isQueryLoading: Boolean = false,
    val queryError: String? = null,
)

data class MonthlyBarData(
    val label: String,
    val amount: Double,
    val isCurrentMonth: Boolean,
)

data class TopMerchantData(
    val merchantName: String,
    val totalAmount: Double,
    val txnCount: Int,
)

data class ConfidenceData(
    val totalCount: Int,
    val autoCategorizedCount: Int,
    val percentage: Float,
)
