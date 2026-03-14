package com.pocketpass.app.data

import android.content.Context
import android.util.Log
import com.pocketpass.app.data.crypto.CryptoManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AuthRepository {
    private val TAG = "AuthRepository"
    private val auth = SupabaseClient.client.auth
    private val client = SupabaseClient.client

    init {
        // Restore persisted session from storage on first access
        // Without this, the session is lost when the app process is killed
        if (!sessionLoaded) {
            sessionLoaded = true
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                try {
                    auth.loadFromStorage(autoRefresh = true)
                } catch (_: Exception) { }
            }
        }
    }

    val isLoggedIn: Flow<Boolean> = auth.sessionStatus.map { status ->
        status is SessionStatus.Authenticated
    }

    val currentUserId: String?
        get() = auth.currentUserOrNull()?.id

    companion object {
        @Volatile
        private var sessionLoaded = false
    }

    suspend fun signUp(username: String, password: String, context: Context? = null): Result<Unit> {
        return try {
            val email = "${username.lowercase().trim()}@pocketpass.app"

            // Create account via gated edge function (bypasses DISABLE_SIGNUP)
            val body = buildJsonObject {
                put("email", email)
                put("password", password)
                put("signup_secret", NativeKeys.getSignupSecret())
            }
            val response = client.functions(
                function = "signup",
                body = body,
                headers = Headers.build {
                    append(HttpHeaders.ContentType, "application/json")
                }
            )
            val status = response.status.value
            if (status == 409) {
                return Result.failure(Exception("Username already taken"))
            }
            if (status !in 200..299) {
                val errorBody = response.bodyAsText()
                return Result.failure(Exception("Signup failed ($status): $errorBody"))
            }

            // Sign in to get a session
            auth.signInWith(Email) {
                this.email = email
                this.password = password
            }

            // Initialize client-side encryption keys
            initializeCrypto(password, context, existingBackup = null)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signIn(username: String, password: String, context: Context? = null): Result<Unit> {
        return try {
            val email = "${username.lowercase().trim()}@pocketpass.app"
            auth.signInWith(Email) {
                this.email = email
                this.password = password
            }

            // Restore encryption keys from cloud backup
            val userId = currentUserId
            if (userId != null) {
                try {
                    val profiles = client.postgrest["profiles"].select {
                        filter { eq("id", userId) }
                    }.decodeList<SupabaseProfile>()
                    val backup = profiles.firstOrNull()?.encryptedKeyBackup
                    initializeCrypto(password, context, existingBackup = backup)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to restore crypto keys on sign-in (non-fatal)", e)
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        try {
            CryptoManager.clearKeys()
            auth.signOut()
        } catch (_: Exception) {
            // Best-effort sign out
        }
    }

    suspend fun deleteAccount(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Call server-side function that deletes all user data + auth user
            SupabaseClient.client.postgrest.rpc("delete_user_account")
            // Clear crypto keys
            CryptoManager.clearKeys()
            // Sign out locally
            try { auth.signOut() } catch (_: Exception) {}
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Delete account failed")
            Result.failure(e)
        }
    }

    /**
     * Initialize or restore client-side encryption keys and upload public key + backup to profile.
     */
    private suspend fun initializeCrypto(
        password: String,
        context: Context?,
        existingBackup: String?
    ) {
        try {
            if (context != null) {
                CryptoManager.setNoBackupDir(context.noBackupFilesDir.absolutePath)
            }
            val (publicKey, encryptedBackup) = CryptoManager.initialize(password, existingBackup)

            // Upload public key and encrypted backup to profile
            val userId = currentUserId ?: return
            client.postgrest["profiles"].update(
                mapOf(
                    "public_key" to publicKey,
                    "encrypted_key_backup" to encryptedBackup
                )
            ) {
                filter { eq("id", userId) }
            }
            Log.d(TAG, "Crypto keys initialized and uploaded to profile")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize crypto (non-fatal): ${e.message}", e)
        }
    }
}
