package com.expensetracker.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.expensetracker.extraction.ExtractorChain
import com.expensetracker.extraction.MediaPipeExtractor
import com.expensetracker.extraction.ModelAssetProvider
import com.expensetracker.extraction.ModelDownloader
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * One-time WorkManager worker that downloads the on-device model (when not
 * shipped via Play Asset Delivery) and wires the MediaPipeExtractor into the
 * ExtractorChain on success. For Play Store installs the model arrives bundled,
 * so the download is skipped and the PAD asset path is wired directly.
 */
@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val modelDownloader: ModelDownloader,
    private val extractorChain: ExtractorChain,
    private val modelAssetProvider: ModelAssetProvider,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting model download")
        val bundledPath = modelAssetProvider.modelPath()
        if (bundledPath != null) {
            extractorChain.updateMediaPipeExtractor(MediaPipeExtractor(applicationContext, bundledPath))
            Log.d(TAG, "Model available via asset pack; no download needed")
            return Result.success()
        }
        return when (val state = modelDownloader.downloadIfNeeded()) {
            is ModelDownloader.DownloadState.Complete -> {
                val path = modelDownloader.modelPath
                if (path != null) {
                    extractorChain.updateMediaPipeExtractor(MediaPipeExtractor(applicationContext, path))
                    Log.d(TAG, "Model downloaded and MediaPipeExtractor wired")
                }
                Result.success()
            }
            is ModelDownloader.DownloadState.Failed -> {
                Log.e(TAG, "Model download failed: ${state.message}")
                Result.retry()
            }
            else -> Result.retry()
        }
    }

    companion object {
        private const val TAG = "ModelDownloadWorker"
        const val WORK_NAME = "model_download"
    }
}
