package com.expensetracker.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the Google UMP consent flow and AdMob SDK initialization.
 *
 * Ads are only requested once [canRequestAds] is true, i.e. after UMP says the
 * user has given (or doesn't need to give) consent. Nothing in here touches
 * transaction / SMS data - the ad SDK only sees what Google's SDK collects itself.
 */
@Singleton
class AdsConsentManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(context)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val consentRequested = AtomicBoolean(false)
    private val mobileAdsInitialized = AtomicBoolean(false)

    private val _canRequestAds = MutableStateFlow(consentInformation.canRequestAds())
    val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(isPrivacyOptionsRequiredNow())
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    init {
        // Returning users who already consented can start loading ads right away,
        // without waiting for this launch's consent refresh.
        if (consentInformation.canRequestAds()) initializeMobileAds()
    }

    /** Refreshes consent status and shows the consent form if required. Call from Activity.onCreate. */
    fun gatherConsent(activity: Activity) {
        if (!consentRequested.compareAndSet(false, true)) return
        val params = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "Consent form error ${formError.errorCode}: ${formError.message}")
                    }
                    refreshState()
                }
            },
            { requestError ->
                Log.w(TAG, "Consent info update failed ${requestError.errorCode}: ${requestError.message}")
                // Allow a retry on next activity creation.
                consentRequested.set(false)
                refreshState()
            },
        )
    }

    /** Re-opens the consent form so the user can change their ad choices (Settings entry). */
    fun showPrivacyOptionsForm(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            if (formError != null) {
                Log.w(TAG, "Privacy options form error ${formError.errorCode}: ${formError.message}")
            }
            refreshState()
        }
    }

    private fun refreshState() {
        _canRequestAds.value = consentInformation.canRequestAds()
        _privacyOptionsRequired.value = isPrivacyOptionsRequiredNow()
        if (_canRequestAds.value) initializeMobileAds()
    }

    private fun isPrivacyOptionsRequiredNow(): Boolean =
        consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    private fun initializeMobileAds() {
        if (!mobileAdsInitialized.compareAndSet(false, true)) return
        scope.launch { MobileAds.initialize(context) {} }
    }

    private companion object {
        const val TAG = "AdsConsentManager"
    }
}
