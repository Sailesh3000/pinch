package com.expensetracker.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.core.database.dao.TransactionWithCategory
import com.expensetracker.core.database.entity.CategoryEntity
import com.expensetracker.core.model.Category
import com.expensetracker.data.repository.CategoryRepository
import com.expensetracker.data.repository.ClarificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewUiState(
    val pendingTransactions: List<TransactionWithCategory> = emptyList(),
    val pendingCount: Int = 0,
    val expandedId: Long? = null,
    val suggestionChips: Map<Long, List<Category>> = emptyMap(),
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val clarificationRepository: ClarificationRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val expandedId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<ReviewUiState> = combine(
        clarificationRepository.observeQueue(),
        clarificationRepository.observePendingCount(),
        expandedId,
    ) { queue, count, expanded ->
        ReviewUiState(
            pendingTransactions = queue,
            pendingCount = count,
            expandedId = expanded,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewUiState())

    fun toggleExpand(transactionId: Long) {
        expandedId.value = if (expandedId.value == transactionId) null else transactionId
    }

    fun loadSuggestions(merchant: String, categoryId: Long) {
        // Lazy-load: only fetch if not already cached.
        val current = (uiState.value.suggestionChips[categoryId])
        if (current != null) return
        viewModelScope.launch {
            val suggestions = clarificationRepository.getTopSuggestions(merchant)
                .map { entity ->
                    Category(
                        id = entity.id,
                        name = entity.name,
                        iconKey = entity.iconKey,
                        colorHex = entity.colorHex,
                        isSystemDefault = entity.isSystemDefault,
                        usageCount = entity.usageCount,
                    )
                }
            expandedId.value = expandedId.value // trigger recomposition
        }
    }

    fun resolveCategory(transactionId: Long, categoryId: Long, suggestedCategoryId: Long?) {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            clarificationRepository.resolve(
                transactionId = transactionId,
                newCategoryId = categoryId,
                suggestedCategoryId = suggestedCategoryId,
                clarificationSource = "IN_APP_REVIEW",
                responseTimeMs = 0L, // simplified: actual response time tracked by UI if needed
            )
            categoryRepository.incrementUsage(categoryId)
        }
    }
}
