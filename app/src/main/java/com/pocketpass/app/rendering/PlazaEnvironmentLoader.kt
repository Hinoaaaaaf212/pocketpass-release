package com.pocketpass.app.rendering

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
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

    // House model assets — one per placement, alternating colors
    private val houseAssets = listOf(
        "models/plaza_house_red.glb",
        "models/plaza_house_blue.glb",
        "models/plaza_house_green.glb",
        "models/plaza_house_yellow.glb"
    )

    // Building positions
    private val buildingPlacements = listOf(
        Triple(3.0f, 0.0f, 0.1f),
        Triple(4.9f, 0.0f, -2.1f),
        Triple(-3.1f, 0.0f, -2.1f),
        Triple(-1.1f, 0.0f, 0.1f)
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

        // Gate
        loadFromAsset(
            assetPath = "models/plaza_gate.glb",
            position = Position(0.00f, -2.0f, -0.70f),
            scale = Scale(0.05f, 0.05f, 0.05f),
            label = "gate",
            isShadowCaster = true
        )

        // Buildings — cycle through 4 house models
        for ((i, pos) in buildingPlacements.withIndex()) {
            val (x, y, z) = pos
            val asset = houseAssets[i % houseAssets.size]
            loadFromAsset(
                assetPath = asset,
                position = Position(x, y, z),
                scale = Scale(0.12f, 0.12f, 0.12f),
                rotation = Rotation(0f, -90f, 0f),
                label = "building_$i",
                isShadowCaster = true
            )
        }

    }

    private fun loadFromAsset(
        assetPath: String,
        position: Position,
        scale: Scale = Scale(1f, 1f, 1f),
        rotation: Rotation = Rotation(0f, 0f, 0f),
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
                    this.rotation = rotation
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
