package com.pocketpass.app.rendering

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
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
import java.nio.ByteBuffer

private const val TAG = "PlazaEnvLoader"

/**
 * Generates and loads procedural 3D environment models for the plaza scene.
 * All geometry is created at runtime via PlazaGlbGenerator — no asset files needed.
 */
class PlazaEnvironmentLoader(
    private val context: Context,
    private val modelLoader: ModelLoader,
    parentScope: CoroutineScope
) {
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob())

    val nodes = mutableStateListOf<Node>()

    data class BuildingConfig(
        val wallColor: FloatArray,
        val roofColor: FloatArray,
        val width: Float,
        val height: Float,
        val depth: Float,
        val roofHeight: Float
    )

    // 5 building color schemes evoking StreetPass game shops
    private val buildingConfigs = listOf(
        // Puzzle Swap — blue walls / red roof
        BuildingConfig(floatArrayOf(0.35f, 0.55f, 0.80f, 1f), floatArrayOf(0.80f, 0.20f, 0.20f, 1f),
            1.2f, 1.4f, 1.0f, 0.5f),
        // Find Mii — yellow walls / green roof
        BuildingConfig(floatArrayOf(0.90f, 0.82f, 0.35f, 1f), floatArrayOf(0.25f, 0.65f, 0.30f, 1f),
            1.0f, 1.6f, 0.9f, 0.45f),
        // Mii Force — pink walls / purple roof
        BuildingConfig(floatArrayOf(0.90f, 0.60f, 0.70f, 1f), floatArrayOf(0.55f, 0.25f, 0.65f, 1f),
            1.1f, 1.3f, 1.0f, 0.5f),
        // Flower Town — white walls / orange roof
        BuildingConfig(floatArrayOf(0.92f, 0.92f, 0.90f, 1f), floatArrayOf(0.90f, 0.55f, 0.15f, 1f),
            1.0f, 1.5f, 0.85f, 0.4f),
        // Warrior's Way — tan walls / dark blue roof
        BuildingConfig(floatArrayOf(0.78f, 0.68f, 0.50f, 1f), floatArrayOf(0.18f, 0.22f, 0.55f, 1f),
            1.3f, 1.2f, 1.1f, 0.55f)
    )

    // Building positions spread along the path, behind Miis
    private val buildingPlacements = listOf(
        Triple(-3.5f, 0f, -3.0f),
        Triple(-1.5f, 0f, -3.5f),
        Triple(0.5f, 0f, -2.5f),
        Triple(2.5f, 0f, -3.0f),
        Triple(4.0f, 0f, -3.5f)
    )

    // Tree positions scattered around
    private val treePlacements = listOf(
        Triple(-5.0f, 0f, -2.0f),
        Triple(-2.5f, 0f, -4.5f),
        Triple(1.5f, 0f, -4.5f),
        Triple(4.5f, 0f, -2.0f),
        Triple(-4.0f, 0f, -1.0f),
        Triple(5.0f, 0f, -3.5f)
    )

    /**
     * Generate and load all environment models. Call from LaunchedEffect(Unit).
     */
    fun loadEnvironment() {
        // Ground
        loadFromBuffer(
            buffer = PlazaGlbGenerator.generateGround(),
            position = Position(0f, 0f, 0f),
            label = "ground",
            isShadowReceiver = true
        )

        // Path
        loadFromBuffer(
            buffer = PlazaGlbGenerator.generatePath(),
            position = Position(0f, 0f, 0f),
            label = "path",
            isShadowReceiver = true
        )

        // Gate (Crimson Arch from asset)
        loadFromAsset(
            assetPath = "models/plaza_gate.glb",
            position = Position(0f, 1.26f, -1.0f),
            scale = Scale(1.4f, 1.4f, 1.4f),
            label = "gate",
            isShadowCaster = true
        )

        // Buildings
        for ((i, config) in buildingConfigs.withIndex()) {
            if (i >= buildingPlacements.size) break
            val (x, y, z) = buildingPlacements[i]
            val buffer = PlazaGlbGenerator.generateBuilding(
                config.wallColor, config.roofColor,
                config.width, config.height, config.depth, config.roofHeight
            )
            loadFromBuffer(
                buffer = buffer,
                position = Position(x, y, z),
                label = "building_$i",
                isShadowCaster = true
            )
        }

        // Trees
        for ((i, pos) in treePlacements.withIndex()) {
            val (x, y, z) = pos
            loadFromBuffer(
                buffer = PlazaGlbGenerator.generateTree(),
                position = Position(x, y, z),
                label = "tree_$i",
                isShadowCaster = true
            )
        }
    }

    private fun loadFromAsset(
        assetPath: String,
        position: Position,
        scale: Scale = Scale(1f, 1f, 1f),
        label: String,
        isShadowCaster: Boolean = false,
        isShadowReceiver: Boolean = false
    ) {
        scope.launch(Dispatchers.Main) {
            try {
                ensureActive()
                val buffer = withContext(Dispatchers.IO) {
                    context.assets.open(assetPath).use { input ->
                        val bytes = input.readBytes()
                        ByteBuffer.allocateDirect(bytes.size).apply {
                            put(bytes)
                            flip()
                        }
                    }
                }
                val instance = modelLoader.createModelInstance(buffer)
                val node = ModelNode(
                    modelInstance = instance,
                    scaleToUnits = null
                ).apply {
                    this.position = position
                    this.scale = scale
                    this.isShadowCaster = isShadowCaster
                    this.isShadowReceiver = isShadowReceiver
                }
                nodes.add(node)
                Log.d(TAG, "Loaded asset: $label at $position")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load asset: $label", e)
            }
        }
    }

    private fun loadFromBuffer(
        buffer: ByteBuffer,
        position: Position,
        label: String,
        isShadowCaster: Boolean = false,
        isShadowReceiver: Boolean = false
    ) {
        scope.launch(Dispatchers.Main) {
            try {
                ensureActive()
                val instance = modelLoader.createModelInstance(buffer)
                val node = ModelNode(
                    modelInstance = instance,
                    scaleToUnits = null
                ).apply {
                    this.position = position
                    this.scale = Scale(1f, 1f, 1f)
                    this.isShadowCaster = isShadowCaster
                    this.isShadowReceiver = isShadowReceiver
                }
                nodes.add(node)
                Log.d(TAG, "Loaded env element: $label at $position")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load env element: $label", e)
            }
        }
    }

    /**
     * Release all environment nodes and cancel loading. Call from DisposableEffect.
     */
    fun clear() {
        scope.cancel()
        nodes.forEach { node ->
            try { node.destroy() } catch (_: Exception) {}
        }
        nodes.clear()
    }
}
