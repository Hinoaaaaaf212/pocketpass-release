package com.pocketpass.app.rendering

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import com.google.android.filament.Engine
import com.pocketpass.app.data.Encounter
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.random.Random

private const val TAG = "Plaza3DMiiManager"

/**
 * Animation state for a plaza Mii.
 */
enum class PlazaAnimState { WALKING, IDLE, GREETING, WAVING }

/**
 * State for a single 3D Mii in the plaza.
 */
class Plaza3DMiiState(
    val encounter: Encounter,
    var modelNode: ModelNode? = null,
    var animState: PlazaAnimState = PlazaAnimState.IDLE,
    var positionX: Float,
    var positionZ: Float,
    var speed: Float,
    var direction: Int,        // 1 = right, -1 = left
    var stateTimer: Float,
    var elapsedTime: Float = Random.nextFloat() * 10f,
    var animIndexMap: Map<String, Int> = emptyMap(),
    var isLoading: Boolean = false,
    var isUser: Boolean = false,
    var animStarted: Boolean = false,  // tracks if current animation has been kicked
    var animRefreshTimer: Float = 0f,  // periodically re-kick animation as heartbeat
    var previousDirection: Int = 1     // saved direction before waving (to restore after)
)

/**
 * Manages the lifecycle of up to 10 3D Mii models in the plaza scene.
 * Handles async loading, animation switching, movement, and state machine.
 */
class Plaza3DMiiManager(
    private val context: Context,
    private val engine: Engine,
    private val modelLoader: ModelLoader,
    parentScope: CoroutineScope
) {
    // Own scope with SupervisorJob so we can cancel in-flight loads on clear()
    // without crashing the parent composition scope
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob())
    companion object {
        const val MAX_MIIS = 10
        const val WORLD_X_MIN = -4.5f
        const val WORLD_X_MAX = 4.5f
        const val WORLD_Z_MIN = -0.5f
        const val WORLD_Z_MAX = 0.5f
        const val SPEED_MIN = 0.3f
        const val SPEED_MAX = 0.8f
        const val IDLE_DURATION_MIN = 2f
        const val IDLE_DURATION_MAX = 5f
        const val GREETING_DURATION = 2.5f
        const val WAVING_DURATION = 2.0f
        const val MII_SCALE = 1.0f
        const val BODY_X_SCALE = 0.67f
    }

    val nodes = mutableStateListOf<Node>()
    private val miiStates = mutableListOf<Plaza3DMiiState>()
    private val loadingIds = mutableSetOf<String>()

    /**
     * Get the current list of Mii states for overlay positioning.
     */
    fun getMiiStates(): List<Plaza3DMiiState> = miiStates.toList()

    /**
     * Sync the plaza with a new list of encounters.
     * Loads new Miis, removes ones no longer present.
     */
    fun syncEncounters(encounters: List<Encounter>, userAvatarHex: String? = null) {
        val subset = if (encounters.size > MAX_MIIS) encounters.shuffled().take(MAX_MIIS) else encounters
        val currentIds = miiStates.map { it.encounter.encounterId }.toSet()
        val newIds = subset.map { it.encounterId }.toSet()

        // Remove Miis no longer in encounters
        val toRemove = miiStates.filter { !it.isUser && it.encounter.encounterId !in newIds }
        for (mii in toRemove) {
            mii.modelNode?.let { nodes.remove(it) }
            miiStates.remove(mii)
        }

        // Add new Miis
        for (encounter in subset) {
            if (encounter.encounterId !in currentIds && encounter.encounterId !in loadingIds) {
                val state = Plaza3DMiiState(
                    encounter = encounter,
                    positionX = WORLD_X_MIN + Random.nextFloat() * (WORLD_X_MAX - WORLD_X_MIN),
                    positionZ = WORLD_Z_MIN + Random.nextFloat() * (WORLD_Z_MAX - WORLD_Z_MIN),
                    speed = SPEED_MIN + Random.nextFloat() * (SPEED_MAX - SPEED_MIN),
                    direction = if (Random.nextBoolean()) 1 else -1,
                    stateTimer = IDLE_DURATION_MIN + Random.nextFloat() * (IDLE_DURATION_MAX - IDLE_DURATION_MIN),
                    animState = if (Random.nextFloat() < 0.4f) PlazaAnimState.IDLE else PlazaAnimState.WALKING
                )
                miiStates.add(state)
                loadMii(state)
            }
        }
    }

    /**
     * Add the user's own Mii at the gate position (center, slightly forward).
     */
    fun addUserMii(avatarHex: String, costumeFileName: String? = null) {
        // Don't add if already present
        if (miiStates.any { it.isUser }) return

        val userEncounter = Encounter(
            encounterId = "__user__",
            timestamp = System.currentTimeMillis(),
            otherUserAvatarHex = avatarHex,
            otherUserName = "You",
            greeting = "",
            origin = "",
            age = "",
            hobbies = "",
            costumeId = costumeFileName ?: ""
        )

        val state = Plaza3DMiiState(
            encounter = userEncounter,
            positionX = 0f,
            positionZ = 0.3f,
            speed = 0f,
            direction = 1,
            stateTimer = Float.MAX_VALUE,
            animState = PlazaAnimState.IDLE,
            isUser = true
        )
        miiStates.add(state)
        loadMii(state, costumeFileName)
    }

    /**
     * Async load a Mii model: prepare buffer on Default, create ModelNode on Main.
     */
    private fun loadMii(state: Plaza3DMiiState, costumeOverride: String? = null) {
        val encounterId = state.encounter.encounterId
        if (state.isLoading) return
        state.isLoading = true
        loadingIds.add(encounterId)

        scope.launch(Dispatchers.Default) {
            try {
                val avatarHex = state.encounter.otherUserAvatarHex
                val costume = costumeOverride
                    ?: state.encounter.costumeId.takeIf { it.isNotBlank() }

                val result = MiiSceneAssembler.preparePlazaMiiBuffer(
                    context,
                    isMale = state.encounter.isMale,
                    avatarHex = avatarHex.takeIf { it.isNotBlank() },
                    costumeFileName = costume
                )

                // Check if we were cancelled while preparing the buffer
                ensureActive()

                if (result != null) {
                    withContext(Dispatchers.Main) {
                        // Bail out if scope was cancelled (user left the screen)
                        ensureActive()

                        try {
                            val bodyColor = MiiStudioDecoder.getColorFromAvatarData(avatarHex)
                            val pantsColor = MiiStudioDecoder.getPantsColorFromAvatarData(avatarHex)

                            val node = MiiSceneAssembler.createAnimatedBodyNodeFromBuffer(
                                modelLoader, result.buffer,
                                bodyColor = bodyColor, pantsColor = pantsColor
                            )

                            if (node != null) {
                                // Apply head textures
                                MiiSceneAssembler.applyHeadTextures(engine, node, result.headTextureDir)
                                MiiSceneAssembler.boostMergedHeadSize(node)

                                // Set initial position and rotation
                                node.position = Position(state.positionX, 0f, state.positionZ)
                                val yRot = if (state.direction == 1) 90f else -90f
                                node.rotation = Rotation(0f, yRot, 0f)

                                // Scale down for plaza view
                                val s = node.scale
                                node.scale = Scale(s.x * MII_SCALE, s.y * MII_SCALE, s.z * MII_SCALE)

                                state.modelNode = node
                                state.animIndexMap = result.animIndexMap

                                // Force-start the appropriate animation after load
                                switchAnimation(state, state.animState, force = true)

                                nodes.add(node)
                                Log.d(TAG, "Loaded 3D Mii: ${state.encounter.otherUserName} (anims: ${node.animationCount})")
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to create ModelNode for ${state.encounter.otherUserName}", e)
                        }
                    }
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Load cancelled for ${state.encounter.otherUserName}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load Mii: ${state.encounter.otherUserName}", e)
            } finally {
                state.isLoading = false
                loadingIds.remove(encounterId)
            }
        }
    }

    /**
     * Update all Mii positions and state machines per frame.
     * Called from the game loop (withFrameNanos).
     */
    fun updateFrame(dt: Float) {
        val clampedDt = dt.coerceAtMost(0.1f)

        for (mii in miiStates) {
            if (mii.modelNode == null || mii.isUser) continue
            mii.elapsedTime += clampedDt

            when (mii.animState) {
                PlazaAnimState.WALKING -> {
                    mii.positionX += mii.speed * mii.direction * clampedDt
                    // Bounce off edges
                    if (mii.positionX < WORLD_X_MIN) {
                        mii.positionX = WORLD_X_MIN
                        mii.direction = 1
                        updateNodeRotation(mii)
                    } else if (mii.positionX > WORLD_X_MAX) {
                        mii.positionX = WORLD_X_MAX
                        mii.direction = -1
                        updateNodeRotation(mii)
                    }
                    // Update node position
                    mii.modelNode?.position = Position(mii.positionX, 0f, mii.positionZ)

                    // Periodically re-kick the walk animation (heartbeat every 1.5s)
                    // Stop then replay to ensure SceneView doesn't ignore duplicate play calls
                    mii.animRefreshTimer -= clampedDt
                    if (!mii.animStarted || mii.animRefreshTimer <= 0f) {
                        val node = mii.modelNode
                        val walkIdx = mii.animIndexMap["WALKING"]
                        if (node != null && walkIdx != null && walkIdx < node.animationCount) {
                            node.stopAnimation(walkIdx)
                            node.playAnimation(walkIdx, loop = true)
                            mii.animStarted = true
                        }
                        mii.animRefreshTimer = 1.5f
                    }

                    // Random chance to stop
                    if (Random.nextFloat() < 0.003f) {
                        switchAnimation(mii, PlazaAnimState.IDLE)
                        mii.stateTimer = IDLE_DURATION_MIN + Random.nextFloat() * (IDLE_DURATION_MAX - IDLE_DURATION_MIN)
                    }
                }
                PlazaAnimState.IDLE -> {
                    mii.stateTimer -= clampedDt
                    if (mii.stateTimer <= 0f) {
                        val roll = Random.nextFloat()
                        if (roll < 0.2f && mii.encounter.greeting.isNotBlank()) {
                            switchAnimation(mii, PlazaAnimState.GREETING)
                            mii.stateTimer = GREETING_DURATION
                        } else if (roll < 0.35f) {
                            // Turn forward (face camera) and wave
                            mii.previousDirection = mii.direction
                            mii.modelNode?.rotation = Rotation(0f, 0f, 0f)
                            switchAnimation(mii, PlazaAnimState.WAVING, force = true)
                            mii.stateTimer = WAVING_DURATION
                        } else {
                            mii.speed = SPEED_MIN + Random.nextFloat() * (SPEED_MAX - SPEED_MIN)
                            if (Random.nextBoolean()) {
                                mii.direction *= -1
                                updateNodeRotation(mii)
                            }
                            // Start walking animation after rotation so it doesn't get reset
                            switchAnimation(mii, PlazaAnimState.WALKING, force = true)
                        }
                    }
                }
                PlazaAnimState.GREETING -> {
                    mii.stateTimer -= clampedDt
                    if (mii.stateTimer <= 0f) {
                        switchAnimation(mii, PlazaAnimState.WALKING)
                        mii.speed = SPEED_MIN + Random.nextFloat() * (SPEED_MAX - SPEED_MIN)
                    }
                }
                PlazaAnimState.WAVING -> {
                    mii.stateTimer -= clampedDt
                    if (mii.stateTimer <= 0f) {
                        // Restore original direction and resume walking
                        mii.direction = mii.previousDirection
                        updateNodeRotation(mii)
                        switchAnimation(mii, PlazaAnimState.WALKING, force = true)
                        mii.speed = SPEED_MIN + Random.nextFloat() * (SPEED_MAX - SPEED_MIN)
                    }
                }
            }
        }
    }

    /**
     * Switch a Mii to a new animation state.
     */
    private fun switchAnimation(mii: Plaza3DMiiState, newState: PlazaAnimState, force: Boolean = false) {
        if (mii.animState == newState && !force) return
        mii.animState = newState
        mii.animStarted = false
        mii.animRefreshTimer = 0f

        val node = mii.modelNode ?: return

        // Stop all animations before starting the new one
        for (i in 0 until node.animationCount) {
            node.stopAnimation(i)
        }

        val stateKey = newState.name
        val animIdx = mii.animIndexMap[stateKey] ?: return

        if (node.animationCount > animIdx) {
            node.playAnimation(animIdx, loop = newState != PlazaAnimState.GREETING && newState != PlazaAnimState.WAVING)
            mii.animStarted = true
        }
    }

    /**
     * Update a node's Y rotation to match its movement direction.
     * Mii models face +Z by default. Camera is at +Z looking at origin,
     * so we see the front of the model.
     * direction=1 (right, +X) → +90° rotates front to face +X
     * direction=-1 (left, -X) → -90° rotates front to face -X
     */
    private fun updateNodeRotation(mii: Plaza3DMiiState) {
        val yRot = if (mii.direction == 1) 90f else -90f
        mii.modelNode?.rotation = Rotation(0f, yRot, 0f)
        // Rotation change can stop the animation — force replay
        mii.animStarted = false
    }

    /**
     * Handle a tap on a Mii — trigger greeting animation and return the encounter.
     */
    fun onMiiTapped(encounter: Encounter): Encounter? {
        val mii = miiStates.find { it.encounter.encounterId == encounter.encounterId } ?: return null
        if (!mii.isUser) {
            switchAnimation(mii, PlazaAnimState.GREETING)
            mii.stateTimer = GREETING_DURATION
        }
        return mii.encounter
    }

    /**
     * Find which Mii is closest to a given world X position (for tap detection).
     */
    fun findMiiNearWorldX(worldX: Float, tolerance: Float = 0.8f): Encounter? {
        var closest: Plaza3DMiiState? = null
        var closestDist = Float.MAX_VALUE
        for (mii in miiStates) {
            if (mii.modelNode == null || mii.isUser) continue
            val dist = abs(mii.positionX - worldX)
            if (dist < closestDist && dist < tolerance) {
                closest = mii
                closestDist = dist
            }
        }
        return closest?.encounter
    }

    /**
     * Cancel all in-flight loading coroutines and clear all Miis and nodes.
     * Must be called when the composable exits (via DisposableEffect).
     */
    fun clear() {
        scope.cancel()
        // Destroy nodes to release native Filament resources (without destroying the shared engine)
        nodes.forEach { node ->
            try { node.destroy() } catch (_: Exception) {}
        }
        nodes.clear()
        miiStates.forEach { state ->
            try { state.modelNode?.destroy() } catch (_: Exception) {}
        }
        miiStates.clear()
        loadingIds.clear()
    }
}
