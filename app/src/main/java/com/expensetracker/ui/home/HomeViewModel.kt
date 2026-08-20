package com.expensetracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.core.database.entity.TransactionEntity
import com.expensetracker.core.math.DeterministicMathEngine
import com.expensetracker.core.model.Category
import com.expensetracker.core.model.ConfidenceTier
import com.expensetracker.core.model.SourceType
import com.expensetracker.core.model.Transaction
import com.expensetracker.core.model.TransactionType
import com.expensetracker.data.repository.CategoryRepository
import com.expensetracker.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val transactions: List<Transaction> = emptyList(),
    val monthSummary: DeterministicMathEngine.MonthSpendSummary? = null,
    val categories: List<Category> = emptyList(),
    val searchQuery: String = "",
    val selectedCategoryId: Long? = null,
    val isLoading: Boolean = true,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val selectedCategoryId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        combine(searchQuery, selectedCategoryId) { query, categoryId -> query to categoryId }
            .flatMapLatest { (query, categoryId) ->
                transactionRepository.observeFiltered(
                    search = query.takeIf { it.isNotBlank() },
                    categoryId = categoryId,
                )
            },
        transactionRepository.observeMonthSummary(),
        categoryRepository.observeAll(),
        searchQuery,
        selectedCategoryId,
    ) { transactions, summary, categories, query, categoryId ->
        HomeUiState(
            transactions = transactions,
            monthSummary = summary,
            categories = categories,
            searchQuery = query,
            selectedCategoryId = categoryId,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setCategoryFilter(categoryId: Long?) {
        selectedCategoryId.value = categoryId
    }

    fun updateCategory(transactionId: Long, categoryId: Long) {
        viewModelScope.launch {
            transactionRepository.updateCategory(transactionId, categoryId)
            categoryRepository.incrementUsage(categoryId)
        }
    }

    fun deleteTransaction(transactionId: Long) {
        viewModelScope.launch {
            transactionRepository.deleteById(transactionId)
        }
    }

    fun addManualTransaction(
        amount: Double,
        merchant: String,
        categoryId: Long,
        txnType: TransactionType,
        timestamp: Long,
    ) {
        viewModelScope.launch {
            transactionRepository.insertFromExtraction(
                TransactionEntity(
                    amount = amount,
                    currency = "INR",
                    txnType = txnType.dbValue,
                    merchantName = merchant,
                    cleanPayee = merchant,
                    categoryId = categoryId,
                    timestamp = timestamp,
                    sourcePackage = "MANUAL",
                    sourceType = SourceType.MANUAL_ENTRY.dbValue,
                    rawNotificationText = "",
                    confidenceScore = 1.0f,
                    confidenceTier = ConfidenceTier.MANUAL.dbValue,
                    needsClarification = false,
                    isClarified = true,
                    dedupHash = null,
                    mergedFromDualSource = false,
                    accountReference = null,
                    createdAt = timestamp,
                )
            )
            categoryRepository.incrementUsage(categoryId)
        }
    }
}
