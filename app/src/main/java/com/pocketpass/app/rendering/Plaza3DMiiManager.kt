package com.pocketpass.app.rendering

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import com.google.android.filament.Engine
import com.pocketpass.app.data.Encounter
import com.pocketpass.app.ui.projectWorldToScreen
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
import androidx.compose.runtime.mutableIntStateOf
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt
import kotlin.random.Random

private const val TAG = "Plaza3DMiiManager"

/**
 * Animation state for a plaza Mii — static grid, no walking.
 */
enum class PlazaAnimState { IDLE, GREETING }

/**
 * State for a single 3D Mii in the plaza.
 */
class Plaza3DMiiState(
    val encounter: Encounter,
    var modelNode: ModelNode? = null,
    var animState: PlazaAnimState = PlazaAnimState.IDLE,
    var positionX: Float,
    var positionZ: Float,
    var stateTimer: Float,
    var elapsedTime: Float = Random.nextFloat() * 10f,
    var animIndexMap: Map<String, Int> = emptyMap(),
    var isLoading: Boolean = false,
    var isUser: Boolean = false,
    var animStarted: Boolean = false,
    var animRefreshTimer: Float = 0f
)

/**
 * Manages up to 20 3D Mii models in a static grid layout (3DS-style).
 * Miis stand still facing the camera in idle animation.
 */
class Plaza3DMiiManager(
    private val context: Context,
    private val engine: Engine,
    private val modelLoader: ModelLoader,
    parentScope: CoroutineScope
) {
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob())

    companion object {
        const val MAX_MIIS = 20
        const val GRID_Z_BACK = 0.0f
        const val GRID_Z_FRONT = 4.0f
        const val GRID_ROW_SPACING = 1.2f
        const val GRID_COL_SPACING = 1.4f
        const val GRID_STAGGER_OFFSET = 0.7f
        const val GREETING_DURATION = 2.5f
        const val MII_SCALE = 1.0f
        const val MII_CENTER_Y = 0.5f  // approximate center of mass height for tap projection
    }

    val nodes = mutableStateListOf<Node>()
    private val miiStates = mutableListOf<Plaza3DMiiState>()
    private val loadingIds = mutableSetOf<String>()

    /** Index of the currently cursor-selected non-user Mii (-1 = none). Observable for Compose. */
    var selectedIndex = mutableIntStateOf(-1)
        private set

    /** Row structure for d-pad navigation: list of (startIndex, count) per row. */
    private val gridRows = mutableListOf<Pair<Int, Int>>()

    fun getMiiStates(): List<Plaza3DMiiState> = miiStates.toList()

    /** Get the non-user Mii states in grid order (matches selectedIndex). */
    fun getNonUserMiiStates(): List<Plaza3DMiiState> = miiStates.filter { !it.isUser }

    /** Get the encounter at the current cursor index, or null. */
    fun getSelectedEncounter(): Encounter? {
        val idx = selectedIndex.intValue
        val nonUser = getNonUserMiiStates()
        return if (idx in nonUser.indices) nonUser[idx].encounter else null
    }

    /**
     * Compute grid positions for [count] Miis, filling rows from back to front.
     * Odd rows are staggered by half a column. Returns (x, z) pairs.
     * Also populates [gridRows] with (startIndex, count) per row for d-pad navigation.
     */
    private fun computeGridPositions(count: Int): List<Pair<Float, Float>> {
        gridRows.clear()
        if (count <= 0) return emptyList()

        val positions = mutableListOf<Pair<Float, Float>>()

        // Determine how many rows we need
        val zRange = GRID_Z_FRONT - GRID_Z_BACK
        val maxRows = (zRange / GRID_ROW_SPACING).toInt().coerceAtLeast(1)

        // Distribute Miis across rows from back to front
        val perRow = ceil(count.toFloat() / maxRows).toInt().coerceAtLeast(1)
        var remaining = count
        var rowIndex = 0
        var z = GRID_Z_BACK

        while (remaining > 0 && z <= GRID_Z_FRONT) {
            val inThisRow = remaining.coerceAtMost(perRow)
            val stagger = if (rowIndex % 2 == 1) GRID_STAGGER_OFFSET else 0f
            val totalWidth = (inThisRow - 1) * GRID_COL_SPACING
            val startX = -totalWidth / 2f + stagger

            val rowStart = positions.size
            for (col in 0 until inThisRow) {
                positions.add(Pair(startX + col * GRID_COL_SPACING, z))
            }
            gridRows.add(Pair(rowStart, inThisRow))

            remaining -= inThisRow
            z += GRID_ROW_SPACING
            rowIndex++
        }

        return positions
    }

    // ── D-pad / thumbstick cursor navigation ──

    /**
     * Move cursor in a direction. Called from key events.
     * Returns the encounter at the new position (or null if grid is empty).
     */
    fun moveCursor(direction: Int): Encounter? {
        val nonUser = getNonUserMiiStates()
        if (nonUser.isEmpty()) return null

        val cur = selectedIndex.intValue
        if (cur < 0) {
            // First press — select front-center Mii (last row, middle)
            selectedIndex.intValue = nonUser.size / 2
            return getSelectedEncounter()
        }

        // Find which row the current index is in
        var curRow = -1
        var colInRow = 0
        for ((r, rowInfo) in gridRows.withIndex()) {
            val (start, count) = rowInfo
            if (cur in start until start + count) {
                curRow = r
                colInRow = cur - start
                break
            }
        }
        if (curRow < 0) {
            selectedIndex.intValue = 0
            return getSelectedEncounter()
        }

        when (direction) {
            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                val (start, count) = gridRows[curRow]
                val newCol = (colInRow - 1).coerceAtLeast(0)
                selectedIndex.intValue = start + newCol
            }
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val (start, count) = gridRows[curRow]
                val newCol = (colInRow + 1).coerceAtMost(count - 1)
                selectedIndex.intValue = start + newCol
            }
            android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                // Up means toward back (lower row index, since rows go back→front)
                if (curRow > 0) {
                    val (start, count) = gridRows[curRow - 1]
                    val newCol = colInRow.coerceAtMost(count - 1)
                    selectedIndex.intValue = start + newCol
                }
            }
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                // Down means toward front (higher row index)
                if (curRow < gridRows.size - 1) {
                    val (start, count) = gridRows[curRow + 1]
                    val newCol = colInRow.coerceAtMost(count - 1)
                    selectedIndex.intValue = start + newCol
                }
            }
        }
        return getSelectedEncounter()
    }

    /** Select a Mii by cursor and trigger greeting. Returns the encounter. */
    fun confirmSelection(): Encounter? {
        val encounter = getSelectedEncounter() ?: return null
        onMiiTapped(encounter)
        return encounter
    }

    /** Clear the cursor selection. */
    fun clearSelection() {
        selectedIndex.intValue = -1
    }

    /**
     * Sync the plaza with a new list of encounters.
     * Computes a full grid layout and assigns positions.
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

        // Add new Miis (without position yet — positions assigned below)
        for (encounter in subset) {
            if (encounter.encounterId !in currentIds && encounter.encounterId !in loadingIds) {
                val correctedEncounter = if (encounter.otherUserAvatarHex.isNotBlank()) {
                    encounter.copy(isMale = MiiStudioDecoder.isMale(encounter.otherUserAvatarHex))
                } else encounter
                val state = Plaza3DMiiState(
                    encounter = correctedEncounter,
                    positionX = 0f,
                    positionZ = 0f,
                    stateTimer = Float.MAX_VALUE,
                    animState = PlazaAnimState.IDLE
                )
                miiStates.add(state)
            }
        }

        // Recompute grid for all non-user Miis
        repositionGrid()

        // Load any Miis that haven't been loaded yet
        for (mii in miiStates) {
            if (!mii.isUser && mii.modelNode == null && !mii.isLoading && mii.encounter.encounterId !in loadingIds) {
                loadMii(mii)
            }
        }
    }

    /**
     * Add the user's own Mii at front-center.
     */
    fun addUserMii(avatarHex: String, costumeFileName: String? = null) {
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
            costumeId = costumeFileName ?: "",
            isMale = MiiStudioDecoder.isMale(avatarHex)
        )

        val state = Plaza3DMiiState(
            encounter = userEncounter,
            positionX = 0f,
            positionZ = GRID_Z_FRONT - 2.0f,
            stateTimer = Float.MAX_VALUE,
            animState = PlazaAnimState.IDLE,
            isUser = true
        )
        miiStates.add(state)
        loadMii(state, costumeFileName)
    }

    /**
     * Recompute grid positions for all non-user Miis and update their positions.
     */
    private fun repositionGrid() {
        val nonUserMiis = miiStates.filter { !it.isUser }
        val positions = computeGridPositions(nonUserMiis.size)

        for ((i, mii) in nonUserMiis.withIndex()) {
            if (i < positions.size) {
                val (x, z) = positions[i]
                mii.positionX = x
                mii.positionZ = z
                mii.modelNode?.position = Position(x, 0f, z)
            }
        }

        // Clamp cursor if grid shrank
        if (selectedIndex.intValue >= nonUserMiis.size) {
            selectedIndex.intValue = (nonUserMiis.size - 1).coerceAtLeast(-1)
        }
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

                ensureActive()

                if (result != null) {
                    withContext(Dispatchers.Main) {
                        ensureActive()

                        try {
                            val bodyColor = MiiStudioDecoder.getColorFromAvatarData(avatarHex)
                            val pantsColor = MiiStudioDecoder.getPantsColorFromAvatarData(avatarHex)

                            val node = MiiSceneAssembler.createAnimatedBodyNodeFromBuffer(
                                modelLoader, result.buffer,
                                bodyColor = bodyColor, pantsColor = pantsColor
                            )

                            if (node != null) {
                                MiiSceneAssembler.applyHeadTextures(engine, node, result.headTextureDir, result.headFileBase, result.materialTextureMap)
                                MiiSceneAssembler.boostMergedHeadSize(node)

                                // All Miis face the camera (rotation 0)
                                node.position = Position(state.positionX, 0f, state.positionZ)
                                node.rotation = Rotation(0f, 0f, 0f)

                                val s = node.scale
                                node.scale = Scale(s.x * MII_SCALE, s.y * MII_SCALE, s.z * MII_SCALE)

                                state.modelNode = node
                                state.animIndexMap = result.animIndexMap

                                // Start idle animation
                                switchAnimation(state, PlazaAnimState.IDLE, force = true)

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
     * Update all Mii state machines per frame.
     * Only handles GREETING timer countdown + idle animation heartbeat.
     */
    fun updateFrame(dt: Float) {
        val clampedDt = dt.coerceAtMost(0.1f)

        for (mii in miiStates) {
            if (mii.modelNode == null) continue
            mii.elapsedTime += clampedDt

            when (mii.animState) {
                PlazaAnimState.IDLE -> {
                    // Start idle animation once — Filament loops it internally
                    if (!mii.animStarted) {
                        val node = mii.modelNode
                        val idleIdx = mii.animIndexMap["IDLE"]
                        if (node != null && idleIdx != null && idleIdx < node.animationCount) {
                            node.playAnimation(idleIdx, loop = true)
                            mii.animStarted = true
                        }
                    }
                }
                PlazaAnimState.GREETING -> {
                    mii.stateTimer -= clampedDt
                    if (mii.stateTimer <= 0f) {
                        switchAnimation(mii, PlazaAnimState.IDLE, force = true)
                        mii.stateTimer = Float.MAX_VALUE
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

        for (i in 0 until node.animationCount) {
            node.stopAnimation(i)
        }

        val stateKey = newState.name
        val animIdx = mii.animIndexMap[stateKey] ?: return

        if (node.animationCount > animIdx) {
            node.playAnimation(animIdx, loop = newState == PlazaAnimState.IDLE)
            mii.animStarted = true
        }
    }

    /**
     * Handle a tap on a Mii — trigger greeting animation and return the encounter.
     */
    fun onMiiTapped(encounter: Encounter): Encounter? {
        val mii = miiStates.find { it.encounter.encounterId == encounter.encounterId } ?: return null
        if (!mii.isUser) {
            switchAnimation(mii, PlazaAnimState.GREETING, force = true)
            mii.stateTimer = GREETING_DURATION
        }
        return mii.encounter
    }

    /**
     * Find which Mii is closest to a normalized screen position.
     * Projects each Mii's world position to screen coords and finds the closest match.
     *
     * @param screenNormX Normalized screen X (0=left, 1=right)
     * @param screenNormY Normalized screen Y (0=top, 1=bottom)
     * @param aspectRatio Screen width / height
     * @param tolerance Maximum screen-space distance (in normalized coords) to count as a hit
     */
    fun findMiiNearScreenPosition(
        screenNormX: Float,
        screenNormY: Float,
        aspectRatio: Float,
        tolerance: Float = 0.08f
    ): Encounter? {
        var closest: Plaza3DMiiState? = null
        var closestDist = Float.MAX_VALUE

        for (mii in miiStates) {
            if (mii.modelNode == null || mii.isUser) continue
            // Project at Mii's center of mass (y≈0.5) rather than feet (y=0)
            // so taps on the torso/head map correctly
            val (sx, sy) = projectWorldToScreen(
                mii.positionX, MII_CENTER_Y, mii.positionZ, aspectRatio
            )
            val dx = (screenNormX - sx) * aspectRatio
            val dy = screenNormY - sy
            val dist = sqrt(dx * dx + dy * dy)
            if (dist < closestDist && dist < tolerance) {
                closest = mii
                closestDist = dist
            }
        }
        return closest?.encounter
    }

    /**
     * Cancel all in-flight loading coroutines and clear all Miis and nodes.
     */
    fun clear() {
        scope.cancel()
        nodes.forEach { node ->
            try { node.destroy() } catch (_: Exception) {}
        }
        nodes.clear()
        miiStates.forEach { state ->
            try { state.modelNode?.destroy() } catch (_: Exception) {}
        }
        miiStates.clear()
        loadingIds.clear()
        gridRows.clear()
        selectedIndex.intValue = -1
    }
}
