package com.expensetracker.extraction

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Locates the AI model when it is delivered via Play Asset Delivery (PAD) —
 * the primary source for Play Store installs (Step 13). Sideloaded APKs and
 * dev builds have no asset pack, so [modelPath] falls back to null and
 * [ModelDownloader] remains the fallback delivery path.
 *
 * Install-time packs are unpacked under the app's files dir, so
 * `AssetPackLocation.assetsPath()` returns a plain directory path we can read
 * directly with [File].
 */
@Singleton
class ModelAssetProvider @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val assetPackManager by lazy {
        try {
            AssetPackManagerFactory.getInstance(context)
        } catch (t: Throwable) {
            Log.e(TAG, "AssetPackManager unavailable", t)
            null
        }
    }

    /** Absolute path to the bundled model file, or null when no pack is installed. */
    fun modelPath(): String? {
        if (!isSupported()) return null
        return try {
            val location = assetPackManager?.getPackLocation(PACK_NAME) ?: return null
            val assetsPath = location.assetsPath() ?: return null
            val modelFile = File(assetsPath, MODEL_FILE_NAME)
            if (modelFile.exists() && modelFile.length() > MIN_MODEL_SIZE_BYTES) {
                modelFile.absolutePath
            } else {
                null
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to resolve asset pack model path", t)
            null
        }
    }

    /** Whether the bundle ships the AI model as an asset pack. */
    fun isAvailable(): Boolean = modelPath() != null

    private fun isSupported(): Boolean = assetPackManager != null

    companion object {
        private const val TAG = "ModelAssetProvider"
        const val PACK_NAME = "aimodel"
        const val MODEL_FILE_NAME = "qwen-pinch.litertlm"
        private const val MIN_MODEL_SIZE_BYTES = 50_000_000L // 50 MB sanity check
    }
}