package com.expensetracker.insights

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Carries a "show me these transactions" request from Insights to Home.
 *
 * The two screens are separate NavHost destinations, so their ViewModels are
 * scoped to different back stack entries and cannot see each other's state.
 * Insights records the request here and navigates; Home picks it up and
 * applies it as a filter.
 *
 * A singleton rather than a nav argument because the bottom bar navigates with
 * saveState/restoreState - Home keeps its existing back stack entry, so route
 * arguments on a re-selected destination would not be re-read.
 */
@Singleton
class DeepLinkCoordinator @Inject constructor() {

    /** A filter Home should apply, or null when there is nothing pending. */
    data class Request(val type: DeepLinkType, val value: String)

    private val _pending = MutableStateFlow<Request?>(null)
    val pending: StateFlow<Request?> = _pending.asStateFlow()

    fun request(type: DeepLinkType, value: String) {
        _pending.value = Request(type, value)
    }

    /**
     * Clears the request once applied, so returning to Home later does not
     * silently re-apply a filter the user has since cleared.
     */
    fun consume() {
        _pending.value = null
    }
}
