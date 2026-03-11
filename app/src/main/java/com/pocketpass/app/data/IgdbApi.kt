package com.pocketpass.app.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/** Minimal game data stored locally and shared via BLE. */
data class IgdbGame(
    val id: Int,
    val name: String,
    val coverId: String? = null
) {
    fun coverUrl(size: String = "t_cover_big"): String? {
        return coverId?.let { "https://images.igdb.com/igdb/image/upload/$size/$it.jpg" }
    }
}

/** IGDB API response types for Gson parsing. */
private data class IgdbGameResponse(
    val id: Int = 0,
    val name: String = "",
    val cover: IgdbCoverResponse? = null
)

private data class IgdbCoverResponse(
    @SerializedName("image_id")
    val imageId: String? = null
)

class IgdbApi(private val context: Context) {

    companion object {
        private const val TAG = "IgdbApi"
        private val PROXY_URL: String by lazy {
            val supabaseUrl = NativeKeys.getSupabaseUrl().trimEnd('/')
            "$supabaseUrl/functions/v1/igdb-proxy"
        }
    }

    private val gson = Gson()

    /** Search games by name, returns up to 10 results with cover art IDs. */
    suspend fun searchGames(query: String): List<IgdbGame> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        try {
            val url = URL(PROXY_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "text/plain")
            conn.setRequestProperty("Authorization", "Bearer ${NativeKeys.getSupabaseAnonKey()}")
            conn.doOutput = true

            val requestBody = "search \"${query.replace("\"", "\\\"")}\"; fields id,name,cover.image_id; limit 10;"
            conn.outputStream.use { it.write(requestBody.toByteArray()) }

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                val errorBody = try {
                    BufferedReader(InputStreamReader(conn.errorStream)).use { it.readText() }
                } catch (_: Exception) { "unknown" }
                Log.e(TAG, "Search failed: $responseCode - $errorBody")
                return@withContext emptyList()
            }

            val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            val type = object : TypeToken<List<IgdbGameResponse>>() {}.type
            val results: List<IgdbGameResponse> = gson.fromJson(body, type) ?: emptyList()

            results.map { game ->
                IgdbGame(
                    id = game.id,
                    name = game.name,
                    coverId = game.cover?.imageId
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to search games", e)
            emptyList()
        }
    }
}
