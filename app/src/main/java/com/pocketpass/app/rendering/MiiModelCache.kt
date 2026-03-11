package com.pocketpass.app.rendering

import android.content.Context
import android.util.Log
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.model.ModelInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Singleton cache for GLB model instances and downloaded hat files.
 * Body/animation/costume GLBs are loaded from assets; heads from network; hats on demand.
 */
object MiiModelCache {
    private const val TAG = "MiiModelCache"

    // All bundled hat file names (in assets/models/hats/)
    val BUNDLED_HATS = listOf(
        "hat_3ds_model.glb",
        "hat_cake_model.glb",
        "hat_cat_model.glb",
        "hat_crown_model.glb",
        "hat_link_model.glb",
        "hat_luigi_model.glb",
        "hat_mario_model.glb",
        "hat_pirate_model.glb",
        "hat_silkhat_model_01.glb",
        "hat_star_model.glb"
    )

    // All bundled costume file names
    val BUNDLED_COSTUMES by lazy {
        listOf(
            "cloth_chief_model.glb",
            "cloth_donkey_model.glb",
            "cloth_foxluigi_model.glb",
            "cloth_horror_model.glb",
            "cloth_kabuto_model.glb",
            "cloth_link_model.glb",
            "cloth_luigi_model.glb",
            "cloth_mario_model.glb",
            "cloth_mario_model_sample.glb",
            "cloth_ninja_model.glb",
            "cloth_pirate_model.glb",
            "cloth_soccer_model.glb",
            "cloth_tanukimario_model.glb",
            "cloth_tengallon_model.glb",
            "cloth_yoshi_model.glb"
        )
    }

    // Curated animations for the test screen selector
    val CURATED_ANIMATIONS = listOf(
        "mii_hand_wait.glb" to "Idle",
        "mii_hand_walk.glb" to "Walk",
        "mii_hand_greeting.glb" to "Greeting",
        "mii_hand_appeal.glb" to "Appeal",
        "mii_hand_happy.glb" to "Happy",
        "mii_hand_angry.glb" to "Angry",
        "mii_hand_sleep.glb" to "Sleep",
        "mii_hand_talk.glb" to "Talk",
        "mii_hand_surprised.glb" to "Surprised",
        "mii_hand_handwaving.glb" to "Wave",
        "mii_hand_sad_02.glb" to "Sad",
        "mii_hand_run.glb" to "Run"
    )

    // All bundled animation file names
    private var animationNames: List<String>? = null
    private val animMutex = Mutex()

    suspend fun getAnimationNames(context: Context): List<String> {
        animMutex.withLock {
            if (animationNames == null) {
                animationNames = withContext(Dispatchers.IO) {
                    try {
                        context.assets.list("models/animations")?.toList() ?: emptyList()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to list animations", e)
                        emptyList()
                    }
                }
            }
            return animationNames!!
        }
    }

    // Disk cache for downloaded hats
    fun getHatCacheDir(context: Context): File {
        val dir = File(context.cacheDir, "mii_hats")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun isHatCached(context: Context, hatFileName: String): Boolean {
        return File(getHatCacheDir(context), hatFileName).exists()
    }

    fun getHatCachePath(context: Context, hatFileName: String): String {
        return File(getHatCacheDir(context), hatFileName).absolutePath
    }

    /**
     * Returns the asset path for a combined body+hands model based on gender.
     * The combined GLBs include the node hierarchy (rootPs, bodyPs, handLPs, handRPs, headPs)
     * needed for animation playback.
     */
    fun getBodyAssetPath(isMale: Boolean): String {
        return if (isMale) "models/bodies/male_combined.glb" else "models/bodies/female_combined.glb"
    }

    fun getHandAssetPath(isMale: Boolean): String {
        return if (isMale) "models/bodies/male_hand_model.glb" else "models/bodies/female_hand_model.glb"
    }

    fun getHatAssetPath(hatFileName: String): String {
        return "models/hats/$hatFileName"
    }

    fun getCostumeAssetPath(costumeFileName: String): String {
        return "models/costumes/$costumeFileName"
    }

    fun getAnimationAssetPath(animName: String): String {
        return "models/animations/$animName"
    }

    /** Display name from filename: hat_mario_model.glb -> Mario */
    fun hatDisplayName(fileName: String): String {
        return fileName
            .removePrefix("hat_")
            .removeSuffix("_model.glb")
            .removeSuffix("_model_01.glb")
            .replaceFirstChar { it.uppercase() }
    }

    fun costumeDisplayName(fileName: String): String {
        return fileName
            .removePrefix("cloth_")
            .removeSuffix("_model.glb")
            .removeSuffix("_model_sample.glb")
            .replaceFirstChar { it.uppercase() }
    }
}
