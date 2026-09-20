package com.expensetracker.extraction

import android.content.Context
import android.util.Log
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Locates the AI model delivered via Play Asset Delivery (PAD).
 *
 * The `aimodel` pack ships as **fast-follow**: Play downloads it right after
 * install and unpacks it to a real directory, so `getPackLocation()` hands back
 * a filesystem path MediaPipe can load directly (it only accepts a path, never
 * a stream). That path is the primary and expected source on Play installs.
 *
 * Two fallbacks exist behind it:
 *  - [fusedAssetPath] for devices old enough that Play fuses the pack into the
 *    base APK (`dist:fusing include="true"`), where the bytes are reachable
 *    only through [android.content.res.AssetManager] and must be extracted once.
 *  - failing both, [ModelDownloader] fetches the model over the network.
 *
 * Every failure path logs — a silent `null` here previously made a
 * misconfigured delivery type impossible to diagnose from the field.
 */
@Singleton
class ModelAssetProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val assetPackManager by lazy {
        try {
            AssetPackManagerFactory.getInstance(context)
        } catch (t: Throwable) {
            Log.e(TAG, "AssetPackManager unavailable", t)
            null
        }
    }

    /** Absolute path to the bundled model, or null when no delivery path has it yet. */
    fun modelPath(): String? = dynamicPackPath() ?: fusedAssetPath()

    /** Whether a bundled copy of the model is ready to use. */
    fun isAvailable(): Boolean = modelPath() != null

    /**
     * Asks Play to fetch the fast-follow pack if it is not on disk yet. Safe to
     * call repeatedly — Play no-ops when the pack is already installed. Callers
     * keep running on the regex tier until the pack lands.
     */
    fun ensureFetched() {
        if (dynamicPackPath() != null) return
        val manager = assetPackManager ?: return
        try {
            manager.fetch(listOf(PACK_NAME))
            Log.i(TAG, "Requested fetch of asset pack '$PACK_NAME'")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to request asset pack fetch", t)
        }
    }

    /** Play-managed pack directory — the expected path for fast-follow delivery. */
    private fun dynamicPackPath(): String? {
        val manager = assetPackManager ?: return null
        return try {
            val location = manager.getPackLocation(PACK_NAME)
            if (location == null) {
                Log.d(TAG, "Asset pack '$PACK_NAME' not installed yet")
                return null
            }
            val assetsPath = location.assetsPath()
            if (assetsPath == null) {
                Log.w(TAG, "Asset pack '$PACK_NAME' has no assetsPath")
                return null
            }
            val modelFile = File(assetsPath, MODEL_FILE_NAME)
            when {
                !modelFile.exists() -> {
                    Log.w(TAG, "Pack installed but $MODEL_FILE_NAME missing at $assetsPath")
                    null
                }
                modelFile.length() <= MIN_MODEL_SIZE_BYTES -> {
                    Log.w(TAG, "Model at $assetsPath is only ${modelFile.length()} bytes")
                    null
                }
                else -> modelFile.absolutePath
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to resolve asset pack location", t)
            null
        }
    }

    /**
     * Fallback for fused delivery, where the pack was merged into the base APK
     * and is reachable only as a compressed asset. Extracts once, since
     * inference needs a real file. Skipped when storage cannot hold the copy.
     */
    private fun fusedAssetPath(): String? {
        val extracted = File(context.filesDir, MODEL_FILE_NAME)
        if (extracted.exists() && extracted.length() > MIN_MODEL_SIZE_BYTES) {
            return extracted.absolutePath
        }
        return try {
            val free = context.filesDir.usableSpace
            if (free < REQUIRED_FREE_BYTES) {
                Log.w(TAG, "Skipping fused extraction: only $free bytes free")
                return null
            }
            context.assets.open(MODEL_FILE_NAME).use { input ->
                FileOutputStream(extracted).use { output -> input.copyTo(output) }
            }
            if (extracted.length() > MIN_MODEL_SIZE_BYTES) {
                Log.i(TAG, "Extracted fused model (${extracted.length()} bytes)")
                extracted.absolutePath
            } else {
                Log.w(TAG, "Fused extraction truncated at ${extracted.length()} bytes")
                extracted.delete()
                null
            }
        } catch (t: Throwable) {
            Log.d(TAG, "Model not available as a fused asset: ${t.message}")
            extracted.delete()
            null
        }
    }

    companion object {
        private const val TAG = "ModelAssetProvider"
        const val PACK_NAME = "aimodel"
        const val MODEL_FILE_NAME = "qwen-pinch.litertlm"
        private const val MIN_MODEL_SIZE_BYTES = 50_000_000L // 50 MB sanity check
        private const val REQUIRED_FREE_BYTES = 600_000_000L // model + headroom
    }
}
