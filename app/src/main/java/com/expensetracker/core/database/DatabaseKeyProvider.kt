package com.expensetracker.core.database

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates and protects the SQLCipher database passphrase.
 *
 * On first use a fresh 256-bit random passphrase is created and stored in
 * SharedPreferences encrypted with an AES/GCM key held inside the Android
 * Keystore (`AndroidKeyStore` provider), so the passphrase never sits in
 * plaintext on disk and is non-exportable by design. Every subsequent DB open
 * decrypts the stored ciphertext with the Keystore key to recover the raw
 * passphrase for SQLCipher's `SupportOpenHelperFactory`.
 */
@Singleton
class DatabaseKeyProvider @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Returns the raw 256-bit passphrase to hand to SQLCipher. */
    fun getOrCreatePassphrase(): ByteArray {
        prefs.getString(KEY_PASSPHRASE_CIPHER, null)?.let { encrypted ->
            return decryptPassphrase(encrypted)
        }
        val passphrase = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        val encrypted = encryptPassphrase(passphrase)
        prefs.edit().putString(KEY_PASSPHRASE_CIPHER, encrypted).apply()
        return passphrase
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encryptPassphrase(passphrase: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(passphrase)
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) +
            SEPARATOR +
            Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    }

    private fun decryptPassphrase(encoded: String): ByteArray {
        return try {
            val parts = encoded.split(SEPARATOR)
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt DB passphrase", e)
            throw IllegalStateException("Database passphrase could not be recovered", e)
        }
    }

    companion object {
        private const val TAG = "DatabaseKeyProvider"
        private const val PREFS_NAME = "db_key_prefs"
        private const val KEY_ALIAS = "expense_tracker_db_key"
        private const val KEY_PASSPHRASE_CIPHER = "db_passphrase_cipher"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val PASSPHRASE_BYTES = 32 // 256-bit SQLCipher passphrase
        private const val SEPARATOR = ":"
    }
}