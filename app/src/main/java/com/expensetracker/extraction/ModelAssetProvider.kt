package com.expensetracker.extraction

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import java.io.File
import java.io.FileOutputStream
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
 * directly with [File]. However, Play *fuses* install-time packs straight
 * into the base APK on devices/Play Store combos that don't support dynamic
 * asset packs (contributes to APK size but AssetPackManager sees no separate
 * pack) — [fusedAssetPath] is the fallback for that case: it reads the model
 * out of the base APK's regular assets and extracts it once to internal
 * storage, since native inference needs a real file path, not an asset
 * stream.
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

    /** Absolute path to the bundled model file, or null when unavailable via any path. */
    fun modelPath(): String? = dynamicPackPath() ?: fusedAssetPath()

    /** Play Feature Delivery path: a genuinely separate installed asset pack. */
    private fun dynamicPackPath(): String? {
        if (assetPackManager == null) return null
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
            Log.e(TAG, "Failed to resolve asset pack location", t)
            null
        }
    }

    /**
     * Fallback for when Play fused the install-time pack into the base APK
     * (no separate pack exists, so [dynamicPackPath] returns null even though
     * the model bytes are physically in the APK). Extracts once to internal
     * storage on first use.
     */
    private fun fusedAssetPath(): String? {
        val extracted = File(context.filesDir, MODEL_FILE_NAME)
        if (extracted.exists() && extracted.length() > MIN_MODEL_SIZE_BYTES) {
            return extracted.absolutePath
        }
        return try {
            context.assets.open(MODEL_FILE_NAME).use { input ->
                FileOutputStream(extracted).use { output -> input.copyTo(output) }
            }
            if (extracted.length() > MIN_MODEL_SIZE_BYTES) {
                extracted.absolutePath
            } else {
                extracted.delete()
                null
            }
        } catch (t: Throwable) {
            // Not present as a fused asset either - genuinely not bundled
            // (sideload/dev build); ModelDownloader remains the fallback.
            null
        }
    }

    /** Whether the bundle ships the AI model as an asset pack. */
    fun isAvailable(): Boolean = modelPath() != null

    companion object {
        private const val TAG = "ModelAssetProvider"
        const val PACK_NAME = "aimodel"
        const val MODEL_FILE_NAME = "qwen-pinch.litertlm"
        private const val MIN_MODEL_SIZE_BYTES = 50_000_000L // 50 MB sanity check
    }
}