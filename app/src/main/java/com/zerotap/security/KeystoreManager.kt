package com.zerotap.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeystoreManager {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun getOrCreateKey(alias: String): SecretKey {
        if (keyStore.containsAlias(alias)) {
            val entry = keyStore.getEntry(alias, null) as KeyStore.SecretKeyEntry
            return entry.secretKey
        }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun getCipher(mode: Int, keyAlias: String, iv: ByteArray? = null): Cipher {
        val key = getOrCreateKey(keyAlias)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        if (mode == Cipher.DECRYPT_MODE && iv != null) {
            cipher.init(mode, key, GCMParameterSpec(128, iv))
        } else {
            cipher.init(mode, key)
        }
        return cipher
    }

    fun deleteKey(alias: String) {
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    }
}
