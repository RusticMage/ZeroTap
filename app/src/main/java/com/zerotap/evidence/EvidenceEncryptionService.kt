package com.zerotap.evidence

import android.content.Context
import com.zerotap.domain.model.EvidencePackage
import com.zerotap.security.KeystoreManager
import java.io.File
import java.util.UUID

data class EncryptedData(val ciphertext: ByteArray, val iv: ByteArray, val keyAlias: String)

class EvidenceEncryptionService(private val context: Context) {
    private val keystoreManager = KeystoreManager()

    fun encrypt(data: ByteArray, keyAlias: String): EncryptedData {
        val cipher = keystoreManager.getCipher(javax.crypto.Cipher.ENCRYPT_MODE, keyAlias)
        val ciphertext = cipher.doFinal(data)
        return EncryptedData(ciphertext, cipher.iv, keyAlias)
    }

    fun decrypt(encryptedData: EncryptedData, keyAlias: String): ByteArray {
        val cipher = keystoreManager.getCipher(javax.crypto.Cipher.DECRYPT_MODE, keyAlias, encryptedData.iv)
        return cipher.doFinal(encryptedData.ciphertext)
    }

    fun encryptEvidence(snapshot: EvidenceSnapshot, incidentId: String): EvidencePackage {
        val rawData = snapshot.toString().toByteArray(Charsets.UTF_8)
        val keyAlias = "evidence_key_$incidentId"
        val encrypted = encrypt(rawData, keyAlias)
        
        val file = File(context.filesDir, "evidence_$incidentId.enc")
        file.writeBytes(encrypted.iv + encrypted.ciphertext)
        
        return EvidencePackage(
            id = UUID.randomUUID().toString(),
            incidentId = incidentId,
            createdAt = System.currentTimeMillis(),
            motionSampleCount = snapshot.motionSamples.size,
            locationSampleCount = snapshot.locationSamples.size,
            audioChunkCount = snapshot.audioMetadata.size,
            encryptionKeyAlias = keyAlias,
            storagePath = file.absolutePath,
            sizeBytes = file.length()
        )
    }
}
