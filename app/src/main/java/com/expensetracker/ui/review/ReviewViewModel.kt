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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewUiState(
    val pendingTransactions: List<TransactionWithCategory> = emptyList(),
    val pendingCount: Int = 0,
    /**
     * Transaction id whose card is expanded, meaning it shows every category
     * rather than just the top suggestions. Expanding and "Select another..."
     * are the same action, so they share this one piece of state.
     */
    val expandedId: Long? = null,
    /** Top category suggestions per transaction id. */
    val suggestionChips: Map<Long, List<Category>> = emptyMap(),
    /** Full category list, shown for the expanded card. */
    val allCategories: List<Category> = emptyList(),
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val clarificationRepository: ClarificationRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val expandedId = MutableStateFlow<Long?>(null)
    private val suggestionChips = MutableStateFlow<Map<Long, List<Category>>>(emptyMap())

    init {
        // Suggestions have to be fetched per merchant, which is a suspend call
        // and so cannot happen inside the combine transform. Driving it from
        // the queue keeps the map in step with what is actually on screen.
        viewModelScope.launch {
            clarificationRepository.observeQueue().collect { queue -> syncSuggestions(queue) }
        }
    }

    val uiState: StateFlow<ReviewUiState> = combine(
        clarificationRepository.observeQueue(),
        clarificationRepository.observePendingCount(),
        expandedId,
        suggestionChips,
        categoryRepository.observeAll(),
    ) { queue, count, expanded, chips, categories ->
        ReviewUiState(
            pendingTransactions = queue,
            pendingCount = count,
            expandedId = expanded,
            suggestionChips = chips,
            allCategories = categories,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewUiState())

    /**
     * Fetches suggestions for transactions that do not have them yet and drops
     * entries for transactions that have left the queue, so a long session does
     * not accumulate chips for already-resolved items.
     */
    private suspend fun syncSuggestions(queue: List<TransactionWithCategory>) {
        val existing = suggestionChips.value
        val liveIds = queue.mapTo(mutableSetOf()) { it.transaction.id }
        val updated = existing.filterKeys { it in liveIds }.toMutableMap()

        queue.forEach { tx ->
            val id = tx.transaction.id
            if (updated.containsKey(id)) return@forEach
            updated[id] = clarificationRepository
                .getTopSuggestions(tx.transaction.merchantName)
                .map { it.toDomain() }
        }

        if (updated != existing) suggestionChips.value = updated
    }

    fun toggleExpand(transactionId: Long) {
        expandedId.value = if (expandedId.value == transactionId) null else transactionId
    }

    /**
     * "Select another..." and expanding the card are the same action - both
     * swap the top three suggestions for the full category list.
     */
    fun showAllCategories(transactionId: Long) {
        expandedId.value = transactionId
    }

    fun resolveCategory(transactionId: Long, categoryId: Long, suggestedCategoryId: Long?) {
        viewModelScope.launch {
            clarificationRepository.resolve(
                transactionId = transactionId,
                newCategoryId = categoryId,
                suggestedCategoryId = suggestedCategoryId,
                clarificationSource = "IN_APP_REVIEW",
                responseTimeMs = 0L, // simplified: actual response time tracked by UI if needed
            )
            categoryRepository.incrementUsage(categoryId)
            // The resolved card leaves the queue; make sure it does not stay
            // expanded and reopen against whatever takes its place.
            expandedId.value = null
        }
    }

    fun reject(transactionId: Long) {
        viewModelScope.launch {
            clarificationRepository.reject(transactionId)
            expandedId.value = null
        }
    }

    private fun CategoryEntity.toDomain(): Category = Category(
        id = id,
        name = name,
        iconKey = iconKey,
        colorHex = colorHex,
        isSystemDefault = isSystemDefault,
        usageCount = usageCount,
    )
}
