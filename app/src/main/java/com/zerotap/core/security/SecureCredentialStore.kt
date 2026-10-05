package com.zerotap.core.security

import android.content.Context
import android.util.Base64
import com.zerotap.security.KeystoreManager
import java.io.File
import javax.crypto.Cipher

/**
 * Secure on-device credential storage for BYOK (Bring Your Own Key) AI provider credentials.
 *
 * Guarantees:
 * - Hardware-backed Android KeyStore AES-256-GCM encryption
 * - Stored in app-private storage, never in plaintext or ordinary SharedPreferences
 * - Zero logging of sensitive key data
 * - Clean replace and wipe capabilities
 */
class SecureCredentialStore(private val context: Context) {

    private val keystoreManager = KeystoreManager()
    private val keyAlias = "zerotap_byok_credential_key"
    private val credentialFile = File(context.filesDir, "byok_vault.enc")

    /**
     * Securely encrypt and store the user's BYOK API key.
     */
    @Synchronized
    fun saveApiKey(provider: String, apiKey: String): Boolean {
        if (apiKey.isBlank()) return false
        return try {
            val cipher = keystoreManager.getCipher(Cipher.ENCRYPT_MODE, keyAlias)
            val iv = cipher.iv
            val rawBytes = "$provider::$apiKey".toByteArray(Charsets.UTF_8)
            val ciphertext = cipher.doFinal(rawBytes)

            // Store IV + Ciphertext
            val combined = ByteArray(iv.size + ciphertext.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)

            credentialFile.writeBytes(combined)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Retrieve the stored API key securely.
     */
    @Synchronized
    fun getApiKey(provider: String): String? {
        if (!credentialFile.exists()) return null
        return try {
            val combined = credentialFile.readBytes()
            if (combined.size < 13) return null // AES GCM standard IV is 12 bytes

            val iv = combined.copyOfRange(0, 12)
            val ciphertext = combined.copyOfRange(12, combined.size)

            val cipher = keystoreManager.getCipher(Cipher.DECRYPT_MODE, keyAlias, iv)
            val decryptedBytes = cipher.doFinal(ciphertext)
            val decryptedString = String(decryptedBytes, Charsets.UTF_8)

            val parts = decryptedString.split("::", limit = 2)
            if (parts.size == 2 && parts[0] == provider) {
                parts[1]
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if a valid API key exists for the provider.
     */
    fun hasApiKey(provider: String): Boolean {
        return getApiKey(provider) != null
    }

    /**
     * Completely wipe the stored credentials.
     */
    @Synchronized
    fun clearApiKey() {
        try {
            if (credentialFile.exists()) {
                credentialFile.delete()
            }
            keystoreManager.deleteKey(keyAlias)
        } catch (_: Exception) {}
    }
}
