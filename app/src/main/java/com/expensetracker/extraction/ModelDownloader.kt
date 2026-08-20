package com.expensetracker.extraction

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads a quantized .task model from HuggingFace to internal storage.
 * One-time download; subsequent calls are no-ops if the file already exists.
 * Uses Qwen2.5-0.5B-Instruct (freely downloadable, no auth required).
 * Requires INTERNET permission (spec §9.1 — user consent via first-run dialog).
 */
@Singleton
class ModelDownloader @Inject constructor(
    private val context: Context,
) {

    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    private val downloadMutex = Mutex()

    val isModelPresent: Boolean
        get() {
            val file = File(context.filesDir, MODEL_FILENAME)
            return file.exists() && file.length() > MIN_MODEL_SIZE_BYTES
        }

    val modelPath: String?
        get() {
            val file = File(context.filesDir, MODEL_FILENAME)
            return if (file.exists() && file.length() > MIN_MODEL_SIZE_BYTES) {
                file.absolutePath
            } else null
        }

    /**
     * Triggers download if model is not already present.
     * Returns immediately if model exists.
     */
    suspend fun downloadIfNeeded(): DownloadState {
        if (isModelPresent) {
            _state.value = DownloadState.Complete
            return DownloadState.Complete
        }
        return download()
    }

    /**
     * Downloads the .task file from HuggingFace.
     * Follows redirects (HuggingFace uses 302 → CDN) and validates response code.
     * Single-flight: concurrent callers serialize on a mutex so two downloads
     * never write the same temp file simultaneously.
     */
    suspend fun download(): DownloadState = downloadMutex.withLock {
        if (isModelPresent) {
            _state.value = DownloadState.Complete
            return@withLock DownloadState.Complete
        }

        withContext(Dispatchers.IO) {
            _state.value = DownloadState.Downloading(0)
            var connection: HttpURLConnection? = null
            try {
                val url = URL(MODEL_URL)
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 30_000
                connection.readTimeout = 120_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "ExpenseTracker/0.1.0")
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    val msg = "HTTP $responseCode: ${connection.responseMessage ?: "error"}"
                    Log.e(TAG, "Download failed: $msg")
                    _state.value = DownloadState.Failed(msg)
                    return@withContext DownloadState.Failed(msg)
                }

                val totalBytes = connection.contentLength.toLong()
                Log.d(TAG, "Starting download: ${totalBytes / 1_048_576} MB expected")
                val outputFile = File(context.filesDir, MODEL_FILENAME)
                val tmpFile = File(context.filesDir, "$MODEL_FILENAME.tmp")
                if (tmpFile.exists()) tmpFile.delete() // clear any stale partial file

                connection.inputStream.use { input ->
                    FileOutputStream(tmpFile).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytesRead: Int
                        var accumulated = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            // Abort promptly if the owning worker is cancelled.
                            currentCoroutineContext().ensureActive()
                            output.write(buffer, 0, bytesRead)
                            accumulated += bytesRead
                            val progress = if (totalBytes > 0) {
                                (accumulated * 100 / totalBytes).toInt()
                            } else {
                                -1 // indeterminate
                            }
                            // Throttle state updates to avoid excessive recomposition
                            if (progress == -1 || accumulated % (BUFFER_SIZE * 10) == 0L || accumulated == totalBytes) {
                                _state.value = DownloadState.Downloading(progress)
                            }
                        }
                    }
                }

                // Validate minimum file size
                if (tmpFile.length() < MIN_MODEL_SIZE_BYTES) {
                    Log.e(TAG, "Downloaded file too small: ${tmpFile.length()} bytes")
                    tmpFile.delete()
                    _state.value = DownloadState.Failed("Downloaded file incomplete")
                    return@withContext DownloadState.Failed("Downloaded file incomplete")
                }

                // Atomic rename on success
                if (outputFile.exists()) outputFile.delete()
                val renamed = tmpFile.renameTo(outputFile)
                if (!renamed) {
                    tmpFile.copyTo(outputFile, overwrite = true)
                    tmpFile.delete()
                }

                Log.d(TAG, "Model downloaded: ${outputFile.length()} bytes")
                _state.value = DownloadState.Complete
                DownloadState.Complete
            } catch (e: Exception) {
                Log.e(TAG, "Model download failed", e)
                val tmpFile = File(context.filesDir, "$MODEL_FILENAME.tmp")
                if (tmpFile.exists()) tmpFile.delete()
                val msg = when {
                    e.message?.contains("401") == true -> "Access denied — model requires license acceptance"
                    e.message?.contains("404") == true -> "Model not found at URL"
                    e.message?.contains("timeout", true) == true -> "Download timed out — check internet connection"
                    e.message?.contains("connect", true) == true -> "Cannot connect — check internet connection"
                    else -> e.message ?: "Unknown error"
                }
                _state.value = DownloadState.Failed(msg)
                DownloadState.Failed(msg)
            } finally {
                connection?.disconnect()
            }
        }
    }

    fun resetState() {
        _state.value = DownloadState.Idle
    }

    sealed class DownloadState {
        data object Idle : DownloadState()
        data class Downloading(val progressPercent: Int) : DownloadState()
        data object Complete : DownloadState()
        data class Failed(val message: String) : DownloadState()
    }

    companion object {
        private const val TAG = "ModelDownloader"
        const val MODEL_FILENAME = "qwen2.5-0.5b-instruct.task"
        private const val BUFFER_SIZE = 64 * 1024 // 64 KB
        private const val MIN_MODEL_SIZE_BYTES = 50_000_000L // 50 MB sanity check

        // Qwen2.5-0.5B-Instruct — freely downloadable, no HuggingFace auth required
        const val MODEL_URL =
            "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"
    }
}
