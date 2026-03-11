package com.pocketpass.app.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AuthRepository {
    private val auth = SupabaseClient.client.auth

    val isLoggedIn: Flow<Boolean> = auth.sessionStatus.map { status ->
        status is SessionStatus.Authenticated
    }

    val currentUserId: String?
        get() = auth.currentUserOrNull()?.id

    suspend fun signUp(username: String, password: String): Result<Unit> {
        return try {
            val email = "${username.lowercase().trim()}@pocketpass.app"
            val result = auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            // When email confirmation is disabled, Supabase returns a user with
            // empty identities if the email already exists (instead of an error).
            val identities = result?.identities
            if (identities != null && identities.isEmpty()) {
                return Result.failure(Exception("Username already taken"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signIn(username: String, password: String): Result<Unit> {
        return try {
            val email = "${username.lowercase().trim()}@pocketpass.app"
            auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        try {
            auth.signOut()
        } catch (_: Exception) {
            // Best-effort sign out
        }
    }

    suspend fun deleteAccount(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Call server-side function that deletes all user data + auth user
            SupabaseClient.client.postgrest.rpc("delete_user_account")
            // Sign out locally
            try { auth.signOut() } catch (_: Exception) {}
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Delete account failed")
            Result.failure(e)
        }
    }
}
