package com.expensetracker.ui.ads

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.expensetracker.ads.AdsConsentManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class AdsViewModel @Inject constructor(
    private val adsConsentManager: AdsConsentManager,
) : ViewModel() {
    val canRequestAds: StateFlow<Boolean> = adsConsentManager.canRequestAds
    val privacyOptionsRequired: StateFlow<Boolean> = adsConsentManager.privacyOptionsRequired

    fun showPrivacyOptions(activity: Activity) = adsConsentManager.showPrivacyOptionsForm(activity)
}
