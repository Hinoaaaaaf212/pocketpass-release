package com.pocketpass.app.data.crypto

import android.util.Base64
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Handles password-based backup and restore of encryption keys.
 * Uses PBKDF2-HMAC-SHA256 to derive a wrapping key from the user's password,
 * then AES-256-GCM to encrypt the key material.
 *
 * Backup blob format:
 *   [4 bytes: salt length][salt][4 bytes: iv length][iv][ciphertext+tag]
 */
object KeyBackupManager {

    private const val PBKDF2_ITERATIONS = 600_000
    private const val SALT_LENGTH = 32
    private const val AES_KEY_LENGTH = 256
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    /**
     * Create an encrypted backup of key material.
     * @param password User's plaintext password
     * @param keyMaterial Raw bytes of keys to back up (concatenated userDataKey + x25519PrivateKey)
     * @return Base64-encoded backup blob
     */
    fun createBackup(password: String, keyMaterial: ByteArray): String {
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val wrappingKey = deriveKey(password, salt)

        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val encrypted = cipher.doFinal(keyMaterial)

        // Pack: saltLen(4) + salt + ivLen(4) + iv + ciphertext
        val buf = ByteBuffer.allocate(4 + salt.size + 4 + iv.size + encrypted.size)
        buf.putInt(salt.size)
        buf.put(salt)
        buf.putInt(iv.size)
        buf.put(iv)
        buf.put(encrypted)

        return Base64.encodeToString(buf.array(), Base64.NO_WRAP)
    }

    /**
     * Restore key material from an encrypted backup.
     * @param password User's plaintext password
     * @param backupBase64 Base64-encoded backup blob
     * @return Raw key material bytes
     * @throws Exception if password is wrong or blob is corrupted
     */
    fun restoreFromBackup(password: String, backupBase64: String): ByteArray {
        val blob = Base64.decode(backupBase64, Base64.NO_WRAP)
        val buf = ByteBuffer.wrap(blob)

        val saltLen = buf.getInt()
        val salt = ByteArray(saltLen).also { buf.get(it) }
        val ivLen = buf.getInt()
        val iv = ByteArray(ivLen).also { buf.get(it) }
        val encrypted = ByteArray(buf.remaining()).also { buf.get(it) }

        val wrappingKey = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))

        return cipher.doFinal(encrypted)
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, AES_KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}
