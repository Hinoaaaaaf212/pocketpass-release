package com.pocketpass.app.service

import android.util.Log
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Handles ephemeral ECDH key exchange and AES-256-GCM encryption for BLE payload exchange.
 *
 * Protocol:
 * 1. Both devices generate ephemeral EC keypairs (P-256)
 * 2. Exchange public keys via KEY_EXCHANGE_CHAR
 * 3. Derive shared secret via ECDH
 * 4. Derive AES-256-GCM key via HKDF-SHA256
 * 5. Encrypt/decrypt payloads with AES-GCM (provides confidentiality + integrity + authentication)
 *
 * Each encounter uses a unique ephemeral keypair — forward secrecy by default.
 */
class BleCryptoHandshake {

    companion object {
        private const val TAG = "BleCrypto"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_BITS = 128
        private val HKDF_INFO = "pocketpass-ble-v1".toByteArray(Charsets.UTF_8)

        // Maximum clock skew allowed for timestamp validation (5 minutes)
        const val MAX_TIMESTAMP_DRIFT_MS = 5 * 60 * 1000L
    }

    private var myPrivateKeyBytes: ByteArray? = null
    private var myPublicKeyBytes: ByteArray? = null
    private var derivedKey: ByteArray? = null

    /**
     * Generate a fresh ephemeral EC keypair for this handshake.
     * Returns the public key bytes (X.509 encoded) to send to the peer.
     */
    fun generateEphemeralKeypair(): ByteArray {
        val keyPairGen = KeyPairGenerator.getInstance("EC")
        keyPairGen.initialize(256, SecureRandom())
        val keyPair = keyPairGen.generateKeyPair()
        myPrivateKeyBytes = keyPair.private.encoded
        myPublicKeyBytes = keyPair.public.encoded
        derivedKey = null // Reset any previously derived key
        return myPublicKeyBytes!!
    }

    /**
     * Receive the peer's public key and derive the shared AES-256 key.
     * Must be called after [generateEphemeralKeypair].
     * Returns true if key derivation succeeded.
     */
    fun deriveSharedKey(peerPublicKeyBytes: ByteArray): Boolean {
        val privBytes = myPrivateKeyBytes ?: run {
            Log.e(TAG, "No ephemeral keypair generated")
            return false
        }

        return try {
            val kf = KeyFactory.getInstance("EC")
            val privateKey = kf.generatePrivate(PKCS8EncodedKeySpec(privBytes))
            val publicKey = kf.generatePublic(X509EncodedKeySpec(peerPublicKeyBytes))

            val ka = KeyAgreement.getInstance("ECDH")
            ka.init(privateKey)
            ka.doPhase(publicKey, true)
            val sharedSecret = ka.generateSecret()

            // HKDF-SHA256: extract then expand to get 32-byte AES key
            derivedKey = hkdfSha256(sharedSecret, HKDF_INFO)
            true
        } catch (e: Exception) {
            Log.e(TAG, "ECDH key derivation failed", e)
            derivedKey = null
            false
        }
    }

    /**
     * Encrypt a plaintext payload with AES-256-GCM using the derived key.
     * Returns: IV (12 bytes) || ciphertext || GCM tag (16 bytes)
     */
    fun encrypt(plaintext: ByteArray): ByteArray? {
        val key = derivedKey ?: run {
            Log.e(TAG, "No shared key derived")
            return null
        }
        return try {
            val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
            val secretKey = SecretKeySpec(key, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            val ciphertext = cipher.doFinal(plaintext)
            // iv || ciphertext (GCM tag is appended by doFinal)
            iv + ciphertext
        } catch (e: Exception) {
            Log.e(TAG, "AES-GCM encryption failed", e)
            null
        }
    }

    /**
     * Decrypt an AES-256-GCM ciphertext using the derived key.
     * Input format: IV (12 bytes) || ciphertext || GCM tag (16 bytes)
     * Returns null if decryption fails (wrong key, tampered data, etc.)
     */
    fun decrypt(data: ByteArray): ByteArray? {
        val key = derivedKey ?: run {
            Log.e(TAG, "No shared key derived")
            return null
        }
        if (data.size < GCM_IV_LENGTH + 16) { // IV + minimum GCM tag
            Log.w(TAG, "Encrypted data too short: ${data.size} bytes")
            return null
        }
        return try {
            val iv = data.copyOfRange(0, GCM_IV_LENGTH)
            val ciphertext = data.copyOfRange(GCM_IV_LENGTH, data.size)
            val secretKey = SecretKeySpec(key, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            Log.e(TAG, "AES-GCM decryption failed (tampered or wrong key)", e)
            null
        }
    }

    /** Clear ephemeral key material */
    fun clear() {
        myPrivateKeyBytes?.fill(0)
        myPublicKeyBytes?.fill(0)
        derivedKey?.fill(0)
        myPrivateKeyBytes = null
        myPublicKeyBytes = null
        derivedKey = null
    }

    val hasKey: Boolean get() = derivedKey != null

    // ── HKDF-SHA256 (extract + expand, single 32-byte output) ──

    private fun hkdfSha256(inputKeyMaterial: ByteArray, info: ByteArray): ByteArray {
        // Extract (zero salt)
        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(SecretKeySpec(ByteArray(32), "HmacSHA256"))
        val prk = hmac.doFinal(inputKeyMaterial)

        // Expand (single block = 32 bytes)
        hmac.init(SecretKeySpec(prk, "HmacSHA256"))
        hmac.update(info)
        hmac.update(byteArrayOf(0x01))
        return hmac.doFinal()
    }
}
