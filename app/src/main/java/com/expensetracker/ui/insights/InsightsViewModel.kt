package com.expensetracker.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.core.common.Formatters
import com.expensetracker.core.database.dao.TransactionDao
import com.expensetracker.core.database.dao.CategorySpend
import com.expensetracker.core.math.DeterministicMathEngine
import com.expensetracker.data.repository.TransactionRepository
import com.expensetracker.insights.AnomalyDetector
import com.expensetracker.insights.NarrativeGenerator
import com.expensetracker.insights.SpendingFacts
import com.expensetracker.insights.TextToSQLTranslator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val transactionDao: TransactionDao,
    private val transactionRepository: TransactionRepository,
    private val subscriptionDetector: SubscriptionDetector,
    private val narrativeGenerator: NarrativeGenerator,
    private val textToSQLTranslator: TextToSQLTranslator,
    private val anomalyDetector: AnomalyDetector,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    init {
        loadInsights()
    }

    fun loadInsights() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val now = System.currentTimeMillis()
            val (curStart, curEnd) = DeterministicMathEngine.monthRange(now)
            val (prevStart, prevEnd) = run {
                val prevMonthEnd = curStart - 1L
                DeterministicMathEngine.monthRange(prevMonthEnd)
            }

            // Month summary
            val monthSummary = transactionRepository.observeMonthSummary().first()

            // Category breakdown
            val categoryBreakdown = transactionDao.categoryBreakdown(curStart, curEnd)

            // Monthly trend (last 6 months)
            val monthlySpend = transactionDao.monthlySpendTrend(6)
            val currentMonthLabel = monthlySpend.firstOrNull()?.monthLabel ?: ""
            val monthlyTrend = monthlySpend.map { row ->
                MonthlyBarData(
                    label = formatMonthLabel(row.monthLabel),
                    amount = row.totalAmount,
                    isCurrentMonth = row.monthLabel == currentMonthLabel,
                )
            }.reversed()

            // Top merchants
            val topMerchantRows = transactionDao.topMerchants(curStart, curEnd, 5)
            val topMerchants = topMerchantRows.map { row ->
                TopMerchantData(
                    merchantName = row.merchantName,
                    totalAmount = row.totalAmount,
                    txnCount = row.txnCount,
                )
            }

            // Confidence stats
            val stats = transactionDao.confidenceStats(curStart, curEnd)
            val confidence = ConfidenceData(
                totalCount = stats.totalCount,
                autoCategorizedCount = stats.autoCategorizedCount,
                percentage = if (stats.totalCount > 0) {
                    (stats.autoCategorizedCount.toFloat() / stats.totalCount) * 100f
                } else 0f,
            )

            // Subscriptions
            val recurringRows = transactionDao.potentialRecurringMerchants()
            val subscriptions = subscriptionDetector.detect(recurringRows)

            _uiState.value = InsightsUiState(
                isLoading = false,
                monthSummary = monthSummary,
                categoryBreakdown = categoryBreakdown,
                monthlyTrend = monthlyTrend,
                topMerchants = topMerchants,
                subscriptions = subscriptions,
                confidenceStats = confidence,
            )

            // Phase 5: Load narratives and anomalies in background
            loadNarrativeAndAnomalies(curStart, curEnd, topMerchants, categoryBreakdown, monthSummary)
        }
    }

    private suspend fun loadNarrativeAndAnomalies(
        curStart: Long,
        curEnd: Long,
        topMerchants: List<TopMerchantData>,
        categoryBreakdown: List<CategorySpend>,
        monthSummary: DeterministicMathEngine.MonthSpendSummary?,
    ) {
        // Build spending facts for narrative
        val topMerchant = topMerchants.firstOrNull()
        val topCategory = categoryBreakdown.firstOrNull()
        val dailyAvg = if (monthSummary != null && monthSummary.transactionCount > 0) {
            val daysInMonth = ((curEnd - curStart) / (24 * 60 * 60 * 1000)).coerceAtLeast(1)
            Formatters.money(monthSummary.currentMonthTotal / daysInMonth)
        } else "₹0"

        val deltaDesc = if (monthSummary != null && monthSummary.previousMonthTotal > 0) {
            if (monthSummary.deltaAmount >= 0) {
                "up ${Formatters.money(monthSummary.deltaAmount)} (${String.format("%.1f", monthSummary.deltaPercent)}%)"
            } else {
                "down ${Formatters.money(-monthSummary.deltaAmount)} (${String.format("%.1f", monthSummary.deltaPercent)}%)"
            }
        } else "no previous month data"

        val facts = SpendingFacts(
            currentSpend = monthSummary?.let { Formatters.money(it.currentMonthTotal) } ?: "₹0",
            previousSpend = monthSummary?.let { Formatters.money(it.previousMonthTotal) } ?: "₹0",
            deltaDescription = deltaDesc,
            transactionCount = monthSummary?.transactionCount ?: 0,
            topCategoryName = topCategory?.categoryName ?: "Uncategorized",
            topCategoryAmount = topCategory?.let { Formatters.money(it.totalAmount) } ?: "₹0",
            topMerchantName = topMerchant?.merchantName ?: "N/A",
            topMerchantAmount = topMerchant?.let { Formatters.money(it.totalAmount) } ?: "₹0",
            topMerchantCount = topMerchant?.txnCount ?: 0,
            dailyAverage = dailyAvg,
        )

        // Generate narrative
        try {
            val narrative = narrativeGenerator.generateSpendingSummary(facts)
            _uiState.update { it.copy(spendingNarrative = narrative) }
        } catch (e: Exception) {
            // Narrative generation is optional; continue without it
        }

        // Detect anomalies
        try {
            val anomalies = anomalyDetector.detect()
            _uiState.update { it.copy(anomalies = anomalies) }
        } catch (e: Exception) {
            // Anomaly detection is optional; continue without it
        }
    }

    fun submitQuery(question: String) {
        if (question.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isQueryLoading = true, queryError = null) }
            try {
                val plan = textToSQLTranslator.translateToSQL(question)
                if (plan == null) {
                    _uiState.update {
                        it.copy(
                            isQueryLoading = false,
                            queryError = "I couldn't understand that question. Try asking about spending on a specific merchant or category.",
                        )
                    }
                    return@launch
                }
                val result = textToSQLTranslator.executePlan(plan)
                _uiState.update {
                    it.copy(
                        isQueryLoading = false,
                        queryResult = result,
                        queryError = null,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isQueryLoading = false,
                        queryError = "Query failed: ${e.message}",
                    )
                }
            }
        }
    }

    fun clearQueryResult() {
        _uiState.update { it.copy(queryResult = null, queryError = null) }
    }

    private fun formatMonthLabel(label: String): String {
        val parts = label.split("-")
        if (parts.size != 2) return label
        val monthNum = parts[1].toIntOrNull() ?: return label
        val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        return monthNames.getOrNull(monthNum - 1) ?: label
    }
}
