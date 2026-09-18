package com.expensetracker.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.expensetracker.core.model.MonitoredPackage
import com.expensetracker.data.repository.MonitoredPackageRepository
import com.expensetracker.data.repository.TransactionRepository
import com.expensetracker.extraction.ExtractorChain
import com.expensetracker.extraction.ModelAssetProvider
import com.expensetracker.extraction.ModelDownloader
import com.expensetracker.extraction.ModelSource
import com.expensetracker.worker.ModelDownloadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.OutputStream
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExportResult(val success: Boolean, val message: String)

data class SettingsUiState(
    val monitoredPackages: List<MonitoredPackage> = emptyList(),
    val listenerEnabled: Boolean = false,
    val nanoAvailable: Boolean? = null,
    val aiEngineName: String = "checking...",
    val modelPresent: Boolean = false,
    val modelSource: ModelSource = ModelSource.UNAVAILABLE,
    val aiTierExplanation: String = "",
    val modelDownloadProgress: Int = -1,
    val modelDownloading: Boolean = false,
    val downloadError: String? = null,
    val isExporting: Boolean = false,
    val exportResult: ExportResult? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val monitoredPackageRepository: MonitoredPackageRepository,
    private val transactionRepository: TransactionRepository,
    private val extractorChain: ExtractorChain,
    private val modelDownloader: ModelDownloader,
    private val modelAssetProvider: ModelAssetProvider,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val listenerEnabled = MutableStateFlow(false)
    private val nanoAvailable = MutableStateFlow<Boolean?>(null)
    private val aiEngineName = MutableStateFlow("checking...")
    private val modelDownloadProgress = MutableStateFlow(-1)
    private val modelDownloading = MutableStateFlow(false)
    private val downloadError = MutableStateFlow<String?>(null)
    private val isExporting = MutableStateFlow(false)
    private val exportResult = MutableStateFlow<ExportResult?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        monitoredPackageRepository.observeAll(),
        listenerEnabled,
        nanoAvailable,
        aiEngineName,
        modelDownloading,
        modelDownloadProgress,
        downloadError,
        isExporting,
        exportResult,
    ) { values ->
        val currentNano = values[2] as? Boolean
        val currentModelSource = resolveModelSource()
        SettingsUiState(
            monitoredPackages = values[0] as List<MonitoredPackage>,
            listenerEnabled = values[1] as Boolean,
            nanoAvailable = currentNano,
            aiEngineName = values[3] as String,
            modelPresent = modelDownloader.isModelPresent,
            modelSource = currentModelSource,
            aiTierExplanation = tierExplanation(currentNano, currentModelSource),
            modelDownloadProgress = values[5] as Int,
            modelDownloading = values[4] as Boolean,
            downloadError = values[6] as String?,
            isExporting = values[7] as Boolean,
            exportResult = values[8] as ExportResult?,
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

    /** Writes the full transaction history to [outputStream] in CSV format. */
    fun exportTransactions(outputStream: OutputStream) {
        if (isExporting.value) return
        viewModelScope.launch {
            isExporting.value = true
            exportResult.value = null
            try {
                transactionRepository.exportToCsv(outputStream)
                exportResult.value = ExportResult(true, "Exported successfully")
            } catch (t: Throwable) {
                exportResult.value = ExportResult(false, t.message ?: "Export failed")
            } finally {
                isExporting.value = false
            }
        }
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

    private fun resolveModelSource(): ModelSource = when {
        modelAssetProvider.isAvailable() -> ModelSource.BUNDLED
        modelDownloader.isModelPresent -> ModelSource.DOWNLOADED
        else -> ModelSource.UNAVAILABLE
    }

    /** User-friendly description of the currently active extraction tier. */
    private fun tierExplanation(nanoAvailable: Boolean?, modelSource: ModelSource): String = when {
        nanoAvailable == true ->
            "Using Gemini Nano — Google's on-device AI. Accurate categorization with zero data leaving your phone."
        modelSource == ModelSource.BUNDLED ->
            "Using the on-device AI model bundled with this app install."
        modelSource == ModelSource.DOWNLOADED ->
            "Using the on-device AI model you downloaded."
        else ->
            "Using deterministic regex parsing. Transactions are still tracked and categorized with on-device rule-based matching."
    }
}