package com.expensetracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.expensetracker.core.model.MonitoredPackage
import com.expensetracker.data.repository.MonitoredPackageRepository
import com.expensetracker.extraction.ExtractorChain
import com.expensetracker.extraction.MediaPipeExtractor
import com.expensetracker.extraction.ModelDownloader
import com.expensetracker.worker.ModelDownloadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val monitoredPackages: List<MonitoredPackage> = emptyList(),
    val listenerEnabled: Boolean = false,
    val nanoAvailable: Boolean? = null,
    val aiEngineName: String = "checking...",
    val modelPresent: Boolean = false,
    val modelDownloadProgress: Int = -1,
    val modelDownloading: Boolean = false,
    val downloadError: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val monitoredPackageRepository: MonitoredPackageRepository,
    private val extractorChain: ExtractorChain,
    private val modelDownloader: ModelDownloader,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val listenerEnabled = MutableStateFlow(false)
    private val nanoAvailable = MutableStateFlow<Boolean?>(null)
    private val aiEngineName = MutableStateFlow("checking...")
    private val modelDownloadProgress = MutableStateFlow(-1)
    private val modelDownloading = MutableStateFlow(false)
    private val downloadError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        monitoredPackageRepository.observeAll(),
        listenerEnabled,
        nanoAvailable,
        aiEngineName,
        modelDownloading,
        modelDownloadProgress,
        downloadError,
    ) { values ->
        SettingsUiState(
            monitoredPackages = values[0] as List<MonitoredPackage>,
            listenerEnabled = values[1] as Boolean,
            nanoAvailable = values[2] as Boolean?,
            aiEngineName = values[3] as String,
            modelPresent = modelDownloader.isModelPresent,
            modelDownloadProgress = values[5] as Int,
            modelDownloading = values[4] as Boolean,
            downloadError = values[6] as String?,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        // Observe the (singleton) ModelDownloader's real state from the moment this
        // ViewModel is created, not just from inside downloadModel() — otherwise a
        // ViewModel recreated while a download is already in flight (e.g. navigating
        // away from and back to Settings) has no idea a download is running, shows
        // "Download"/"Retry" again, and a tap re-enqueues on top of the real one.
        viewModelScope.launch {
            modelDownloader.state.collect { state ->
                when (state) {
                    is ModelDownloader.DownloadState.Downloading -> {
                        modelDownloading.value = true
                        modelDownloadProgress.value = state.progressPercent
                    }
                    is ModelDownloader.DownloadState.Complete -> {
                        modelDownloading.value = false
                        modelDownloadProgress.value = 100
                        downloadError.value = null
                        refreshAiStatus()
                    }
                    is ModelDownloader.DownloadState.Failed -> {
                        modelDownloading.value = false
                        modelDownloadProgress.value = -1
                        downloadError.value = state.message
                    }
                    else -> {}
                }
            }
        }
    }

    fun refreshPermissionStatus(enabled: Boolean) {
        listenerEnabled.value = enabled
    }

    fun refreshAiStatus() {
        viewModelScope.launch {
            val nano = extractorChain.isNanoAvailable()
            nanoAvailable.value = nano
            aiEngineName.value = extractorChain.availableEngineName()
        }
    }

    fun downloadModel() {
        if (modelDownloader.isModelPresent || modelDownloading.value) return
        modelDownloading.value = true
        modelDownloadProgress.value = 0
        downloadError.value = null

        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>().build()
        // KEEP, not REPLACE: if a download is already enqueued/running (including one
        // started by a since-recreated ViewModel instance), leave it alone rather than
        // cancelling and restarting it — that cancel-and-restart loop is exactly what
        // was happening before, visible as the download failing within ~1s every time.
        WorkManager.getInstance(context).enqueueUniqueWork(
            ModelDownloadWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun setPackageEnabled(packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            monitoredPackageRepository.setEnabled(packageName, enabled)
        }
    }

    fun addCustomPackage(packageName: String): Boolean {
        val normalized = packageName.trim()
        if (!normalized.contains('.') || normalized.isBlank()) return false
        viewModelScope.launch {
            monitoredPackageRepository.addCustomPackage(normalized)
        }
        return true
    }
}
