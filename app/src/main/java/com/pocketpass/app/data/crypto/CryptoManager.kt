package com.pocketpass.app.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import android.util.LruCache
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Manages client-side encryption for protecting user data before it reaches Supabase.
 *
 * Key hierarchy:
 *   Android Keystore MasterKey (AES-256, never leaves device)
 *     ├── UserDataKey (random AES-256, encrypts own encounters)
 *     └── X25519-equivalent EC PrivateKey (for DH key exchange)
 *
 * Since Android Keystore doesn't natively support X25519, we use ECDH with
 * the secp256r1 (P-256) curve which is widely supported.
 */
object CryptoManager {

    private const val TAG = "CryptoManager"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "pocketpass_master_key"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    private const val ENCRYPTED_PREFIX = "enc:1:"

    // Raw key material (stored encrypted under Keystore master key)
    private var userDataKey: SecretKey? = null
    private var ecPrivateKeyBytes: ByteArray? = null
    private var ecPublicKeyBytes: ByteArray? = null

    // Cache derived friendship keys: friendId -> AES key
    private val friendshipKeyCache = LruCache<String, SecretKey>(32)

    // Cache friend public keys: friendId -> public key bytes
    private val publicKeyCache = LruCache<String, ByteArray>(64)

    val isInitialized: Boolean
        get() {
            if (userDataKey != null && ecPrivateKeyBytes != null) return true
            // Try to lazy-load from local Keystore if noBackupDir is set
            if (noBackupDir != null) {
                try {
                    ensureMasterKey()
                    if (loadKeysLocally()) return true
                } catch (_: Exception) {}
            }
            return false
        }

    /**
     * Initialize crypto on sign-up (generates new keys) or sign-in (restores from backup).
     * @param password User's plaintext password (for key backup encryption)
     * @param existingBackup Base64 backup blob from profiles.encrypted_key_backup (null on sign-up)
     * @return Pair(publicKeyBase64, encryptedBackupBase64) to store in profile
     */
    fun initialize(password: String, existingBackup: String?): Pair<String, String> {
        ensureMasterKey()

        if (!existingBackup.isNullOrBlank()) {
            // Restore from backup
            return try {
                val keyMaterial = KeyBackupManager.restoreFromBackup(password, existingBackup)
                val buf = ByteBuffer.wrap(keyMaterial)

                // Read userDataKey (32 bytes)
                val udkLen = buf.getInt()
                val udkBytes = ByteArray(udkLen).also { buf.get(it) }
                userDataKey = SecretKeySpec(udkBytes, "AES")

                // Read EC private key
                val privLen = buf.getInt()
                ecPrivateKeyBytes = ByteArray(privLen).also { buf.get(it) }

                // Read EC public key
                val pubLen = buf.getInt()
                ecPublicKeyBytes = ByteArray(pubLen).also { buf.get(it) }

                // Store under Keystore master key for local persistence
                storeKeysLocally()

                Log.d(TAG, "Keys restored from backup")
                getPublicKeyBase64() to createBackup(password)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore keys from backup, generating new keys", e)
                generateNewKeys(password)
            }
        } else {
            // Try to load from local Keystore first (app wasn't uninstalled)
            if (loadKeysLocally()) {
                Log.d(TAG, "Keys loaded from local Keystore")
                return getPublicKeyBase64() to createBackup(password)
            }
            // Generate fresh keys
            return generateNewKeys(password)
        }
    }

    private fun generateNewKeys(password: String): Pair<String, String> {
        // Generate UserDataKey (AES-256)
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256, SecureRandom())
        userDataKey = keyGen.generateKey()

        // Generate EC keypair (P-256 / secp256r1)
        val ecKeyPairGen = KeyPairGenerator.getInstance("EC")
        ecKeyPairGen.initialize(256, SecureRandom())
        val ecKeyPair = ecKeyPairGen.generateKeyPair()
        ecPrivateKeyBytes = ecKeyPair.private.encoded
        ecPublicKeyBytes = ecKeyPair.public.encoded

        storeKeysLocally()

        Log.d(TAG, "New encryption keys generated")
        return getPublicKeyBase64() to createBackup(password)
    }

    // ── Encrypt/Decrypt for Self (encounters) ──

    fun encryptForSelf(plaintext: String): String {
        val key = userDataKey ?: throw IllegalStateException("CryptoManager not initialized")
        return ENCRYPTED_PREFIX + aesGcmEncrypt(key, plaintext.toByteArray(Charsets.UTF_8))
    }

    fun decryptForSelf(ciphertext: String): String {
        if (!ciphertext.startsWith(ENCRYPTED_PREFIX)) return ciphertext
        val key = userDataKey ?: throw IllegalStateException("CryptoManager not initialized")
        val data = ciphertext.removePrefix(ENCRYPTED_PREFIX)
        return String(aesGcmDecrypt(key, data), Charsets.UTF_8)
    }

    // ── Encrypt/Decrypt for Friend (messages) ──

    fun encryptForFriend(friendId: String, friendPublicKeyBase64: String, myUserId: String, plaintext: String): String {
        val key = getFriendshipKey(friendId, friendPublicKeyBase64, myUserId)
        return ENCRYPTED_PREFIX + aesGcmEncrypt(key, plaintext.toByteArray(Charsets.UTF_8))
    }

    fun decryptFromFriend(friendId: String, friendPublicKeyBase64: String, myUserId: String, ciphertext: String): String {
        if (!ciphertext.startsWith(ENCRYPTED_PREFIX)) return ciphertext
        val key = getFriendshipKey(friendId, friendPublicKeyBase64, myUserId)
        val data = ciphertext.removePrefix(ENCRYPTED_PREFIX)
        return String(aesGcmDecrypt(key, data), Charsets.UTF_8)
    }

    // ── Sealed-box encryption for notifications (sender writes, receiver reads) ──

    fun sealForReceiver(receiverPublicKeyBase64: String, plaintext: String): String {
        if (receiverPublicKeyBase64.isBlank()) return plaintext // Unkeyed user

        val receiverPubBytes = Base64.decode(receiverPublicKeyBase64, Base64.NO_WRAP)

        // Generate ephemeral EC keypair
        val ephKeyPairGen = KeyPairGenerator.getInstance("EC")
        ephKeyPairGen.initialize(256, SecureRandom())
        val ephKeyPair = ephKeyPairGen.generateKeyPair()
        val ephPubBytes = ephKeyPair.public.encoded

        // DH: ephemeral private × receiver public
        val sharedSecret = performECDH(ephKeyPair.private.encoded, receiverPubBytes)
        val aesKey = hkdfDeriveKey(sharedSecret, "pocketpass-seal-v1".toByteArray())

        // Encrypt
        val encrypted = aesGcmEncryptRaw(aesKey, plaintext.toByteArray(Charsets.UTF_8))

        // Prepend ephemeral public key: [ephPubLen(4)][ephPub][encrypted]
        val buf = ByteBuffer.allocate(4 + ephPubBytes.size + encrypted.size)
        buf.putInt(ephPubBytes.size)
        buf.put(ephPubBytes)
        buf.put(encrypted)

        return ENCRYPTED_PREFIX + Base64.encodeToString(buf.array(), Base64.NO_WRAP)
    }

    fun unseal(ciphertext: String): String {
        if (!ciphertext.startsWith(ENCRYPTED_PREFIX)) return ciphertext
        val privBytes = ecPrivateKeyBytes ?: throw IllegalStateException("CryptoManager not initialized")

        val data = Base64.decode(ciphertext.removePrefix(ENCRYPTED_PREFIX), Base64.NO_WRAP)
        val buf = ByteBuffer.wrap(data)

        val ephPubLen = buf.getInt()
        val ephPubBytes = ByteArray(ephPubLen).also { buf.get(it) }
        val encrypted = ByteArray(buf.remaining()).also { buf.get(it) }

        // DH: my private × ephemeral public
        val sharedSecret = performECDH(privBytes, ephPubBytes)
        val aesKey = hkdfDeriveKey(sharedSecret, "pocketpass-seal-v1".toByteArray())

        return String(aesGcmDecryptRaw(aesKey, encrypted), Charsets.UTF_8)
    }

    // ── Public Key ──

    fun getPublicKeyBase64(): String {
        val pubBytes = ecPublicKeyBytes ?: throw IllegalStateException("CryptoManager not initialized")
        return Base64.encodeToString(pubBytes, Base64.NO_WRAP)
    }

    fun cacheFriendPublicKey(friendId: String, publicKeyBase64: String) {
        if (publicKeyBase64.isNotBlank()) {
            publicKeyCache.put(friendId, Base64.decode(publicKeyBase64, Base64.NO_WRAP))
        }
    }

    fun getCachedFriendPublicKey(friendId: String): String? {
        val bytes = publicKeyCache.get(friendId) ?: return null
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    // ── Cleanup ──

    fun clearKeys() {
        userDataKey = null
        ecPrivateKeyBytes = null
        ecPublicKeyBytes = null
        friendshipKeyCache.evictAll()
        publicKeyCache.evictAll()

        // Remove local Keystore entries
        try {
            val ks = KeyStore.getInstance(KEYSTORE_PROVIDER)
            ks.load(null)
            if (ks.containsAlias(MASTER_KEY_ALIAS)) ks.deleteEntry(MASTER_KEY_ALIAS)
            if (ks.containsAlias("pocketpass_wrapped_keys")) ks.deleteEntry("pocketpass_wrapped_keys")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear Keystore entries", e)
        }
    }

    // ── Internal: AES-GCM ──

    private fun aesGcmEncrypt(key: SecretKey, plaintext: ByteArray): String {
        val encrypted = aesGcmEncryptRaw(key, plaintext)
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun aesGcmDecrypt(key: SecretKey, base64Ciphertext: String): ByteArray {
        val encrypted = Base64.decode(base64Ciphertext, Base64.NO_WRAP)
        return aesGcmDecryptRaw(key, encrypted)
    }

    private fun aesGcmEncryptRaw(key: SecretKey, plaintext: ByteArray): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val ciphertext = cipher.doFinal(plaintext)
        // iv || ciphertext (includes GCM tag)
        return iv + ciphertext
    }

    private fun aesGcmDecryptRaw(key: SecretKey, data: ByteArray): ByteArray {
        val iv = data.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = data.copyOfRange(GCM_IV_LENGTH, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(ciphertext)
    }

    // ── Internal: ECDH ──

    private fun performECDH(privateKeyBytes: ByteArray, publicKeyBytes: ByteArray): ByteArray {
        val kf = KeyFactory.getInstance("EC")
        val privateKey = kf.generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))
        val publicKey = kf.generatePublic(X509EncodedKeySpec(publicKeyBytes))

        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(privateKey)
        ka.doPhase(publicKey, true)
        return ka.generateSecret()
    }

    // ── Internal: HKDF-SHA256 (simplified: extract + expand for single 32-byte key) ──

    private fun hkdfDeriveKey(inputKeyMaterial: ByteArray, info: ByteArray): SecretKey {
        // Extract
        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(SecretKeySpec(ByteArray(32), "HmacSHA256")) // zero salt
        val prk = hmac.doFinal(inputKeyMaterial)

        // Expand (single block — 32 bytes)
        hmac.init(SecretKeySpec(prk, "HmacSHA256"))
        hmac.update(info)
        hmac.update(byteArrayOf(0x01))
        val okm = hmac.doFinal()

        return SecretKeySpec(okm, "AES")
    }

    // ── Internal: Friendship Key Derivation ──

    private fun getFriendshipKey(friendId: String, friendPublicKeyBase64: String, myUserId: String): SecretKey {
        friendshipKeyCache.get(friendId)?.let { return it }

        val friendPubBytes = Base64.decode(friendPublicKeyBase64, Base64.NO_WRAP)
        val privBytes = ecPrivateKeyBytes ?: throw IllegalStateException("CryptoManager not initialized")

        val sharedSecret = performECDH(privBytes, friendPubBytes)

        // Salt = sorted(userId1 + userId2) for deterministic derivation
        val sortedIds = listOf(myUserId, friendId).sorted()
        val salt = (sortedIds[0] + sortedIds[1]).toByteArray()

        // HKDF with salt
        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = hmac.doFinal(sharedSecret)

        val info = "pocketpass-msg-v1".toByteArray()
        hmac.init(SecretKeySpec(prk, "HmacSHA256"))
        hmac.update(info)
        hmac.update(byteArrayOf(0x01))
        val okm = hmac.doFinal()

        val key = SecretKeySpec(okm, "AES")
        friendshipKeyCache.put(friendId, key)
        return key
    }

    // ── Internal: Backup ──

    private fun createBackup(password: String): String {
        val udkBytes = userDataKey!!.encoded
        val privBytes = ecPrivateKeyBytes!!
        val pubBytes = ecPublicKeyBytes!!

        // Pack: udkLen(4) + udk + privLen(4) + priv + pubLen(4) + pub
        val buf = ByteBuffer.allocate(4 + udkBytes.size + 4 + privBytes.size + 4 + pubBytes.size)
        buf.putInt(udkBytes.size)
        buf.put(udkBytes)
        buf.putInt(privBytes.size)
        buf.put(privBytes)
        buf.putInt(pubBytes.size)
        buf.put(pubBytes)

        return KeyBackupManager.createBackup(password, buf.array())
    }

    // ── Internal: Keystore local persistence ──

    private fun ensureMasterKey() {
        val ks = KeyStore.getInstance(KEYSTORE_PROVIDER)
        ks.load(null)
        if (ks.containsAlias(MASTER_KEY_ALIAS)) return

        val spec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        keyGen.init(spec)
        keyGen.generateKey()
    }

    private fun storeKeysLocally() {
        try {
            val udkBytes = userDataKey!!.encoded
            val privBytes = ecPrivateKeyBytes!!
            val pubBytes = ecPublicKeyBytes!!

            val buf = ByteBuffer.allocate(4 + udkBytes.size + 4 + privBytes.size + 4 + pubBytes.size)
            buf.putInt(udkBytes.size)
            buf.put(udkBytes)
            buf.putInt(privBytes.size)
            buf.put(privBytes)
            buf.putInt(pubBytes.size)
            buf.put(pubBytes)

            val plainKeyMaterial = buf.array()

            // Encrypt under master key
            val ks = KeyStore.getInstance(KEYSTORE_PROVIDER)
            ks.load(null)
            val masterKey = (ks.getEntry(MASTER_KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, masterKey)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plainKeyMaterial)

            // Store as a Keystore entry using a SecretKey wrapper
            // We'll store iv + encrypted as the "key" bytes in SharedPreferences instead
            // since Keystore doesn't support arbitrary blob storage
            val combined = ByteBuffer.allocate(4 + iv.size + encrypted.size)
            combined.putInt(iv.size)
            combined.put(iv)
            combined.put(encrypted)

            // Store in a file in the app's private directory
            val encoded = Base64.encodeToString(combined.array(), Base64.NO_WRAP)
            val file = java.io.File(getKeysFilePath())
            file.parentFile?.mkdirs()
            file.writeText(encoded)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store keys locally", e)
        }
    }

    private fun loadKeysLocally(): Boolean {
        return try {
            val file = java.io.File(getKeysFilePath())
            if (!file.exists()) return false

            val encoded = file.readText()
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            val buf = ByteBuffer.wrap(combined)

            val ivLen = buf.getInt()
            val iv = ByteArray(ivLen).also { buf.get(it) }
            val encrypted = ByteArray(buf.remaining()).also { buf.get(it) }

            val ks = KeyStore.getInstance(KEYSTORE_PROVIDER)
            ks.load(null)
            if (!ks.containsAlias(MASTER_KEY_ALIAS)) return false

            val masterKey = (ks.getEntry(MASTER_KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, masterKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val plainKeyMaterial = cipher.doFinal(encrypted)

            val keyBuf = ByteBuffer.wrap(plainKeyMaterial)

            val udkLen = keyBuf.getInt()
            val udkBytes = ByteArray(udkLen).also { keyBuf.get(it) }
            userDataKey = SecretKeySpec(udkBytes, "AES")

            val privLen = keyBuf.getInt()
            ecPrivateKeyBytes = ByteArray(privLen).also { keyBuf.get(it) }

            val pubLen = keyBuf.getInt()
            ecPublicKeyBytes = ByteArray(pubLen).also { keyBuf.get(it) }

            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load keys locally", e)
            false
        }
    }

    private var noBackupDir: String? = null

    /**
     * Set the app's noBackupFilesDir path. Must be called once with a Context
     * before initialize() — typically from AuthRepository or Application.
     */
    fun setNoBackupDir(dir: String) {
        noBackupDir = dir
    }

    private fun getKeysFilePath(): String {
        val dir = noBackupDir ?: throw IllegalStateException(
            "CryptoManager.setNoBackupDir() must be called before using crypto"
        )
        return "$dir/crypto_keys.dat"
    }
}
