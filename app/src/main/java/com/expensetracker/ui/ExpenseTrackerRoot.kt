package com.expensetracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expensetracker.ui.navigation.MainScaffold
import com.expensetracker.ui.onboarding.OnboardingScreen

@Composable
fun ExpenseTrackerRoot(viewModel: MainViewModel = hiltViewModel()) {
    val onboardingComplete by viewModel.onboardingComplete.collectAsStateWithLifecycle()
    if (onboardingComplete) {
        MainScaffold()
    } else {
        OnboardingScreen(onComplete = viewModel::completeOnboarding)
    }
}
