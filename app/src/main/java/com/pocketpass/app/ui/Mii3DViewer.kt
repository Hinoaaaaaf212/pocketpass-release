package com.pocketpass.app.ui

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.pocketpass.app.rendering.MiiSceneAssembler
import com.pocketpass.app.rendering.MiiStudioDecoder
import com.google.android.filament.View.AntiAliasing
import com.google.android.filament.View.QualityLevel
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberRenderer
import io.github.sceneview.rememberScene
import io.github.sceneview.rememberView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "Mii3DViewer"

/**
 * Reusable composable that renders a 3D Mii model.
 *
 * Body + animation + head are merged into a single GLB at load time so the head
 * is a child of headPs in the glTF node hierarchy. Animations automatically
 * affect the head (nodding, tilting, body sway) without any runtime parenting.
 *
 * @param avatarHex The Mii hex data (used to fetch head GLB from API)
 * @param isMale Whether the Mii uses male body model
 * @param hatFileName Optional hat GLB filename (e.g., "hat_mario_model.glb")
 * @param costumeFileName Optional costume GLB filename (replaces body)
 * @param animationFileName Animation GLB filename to merge into body (default: idle)
 * @param rotationY Y-axis rotation in degrees (180 = facing away, 0 = facing camera)
 * @param autoAnimate Whether to auto-play the first animation
 * @param modifier Compose modifier
 */
@Composable
fun Mii3DViewer(
    avatarHex: String,
    isMale: Boolean = true,
    hatFileName: String? = null,
    costumeFileName: String? = null,
    animationFileName: String = "mii_hand_wait.glb",
    rotationY: Float = 0f,
    autoAnimate: Boolean = true,
    modifier: Modifier = Modifier,
    sharedEngine: com.google.android.filament.Engine? = null,
    sharedModelLoader: io.github.sceneview.loaders.ModelLoader? = null
) {
    val context = LocalContext.current
    val engine = sharedEngine ?: rememberEngine()
    val modelLoader = sharedModelLoader ?: rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val view = rememberView(engine)

    // Nodes list that SceneView will render
    val childNodes = remember { mutableListOf<Node>() }
    var nodesReady by remember { mutableStateOf(false) }

    // Camera positioned to frame a Mii nicely
    val cameraNode = rememberCameraNode(engine) {
        position = Position(0f, 0.5f, 2.5f)
        lookAt(Position(0f, 0.4f, 0f))
    }

    // Light
    val mainLightNode = rememberMainLightNode(engine) {
        intensity = 80_000f
        position = Position(0f, 3f, 2f)
    }

    // Load and merge body + animation + head into a single GLB
    LaunchedEffect(avatarHex, hatFileName, costumeFileName, animationFileName, isMale) {
        try {
            // Merge body + animation + head on background thread
            val mergedResult = MiiSceneAssembler.prepareMergedMiiBuffer(
                context, isMale, animationFileName,
                avatarHex = if (avatarHex.isNotBlank()) avatarHex else null,
                costumeFileName = costumeFileName
            )

            withContext(Dispatchers.Main) {
                childNodes.clear()

                val bodyColor = MiiStudioDecoder.getColorFromAvatarData(avatarHex)
                val pantsColor = MiiStudioDecoder.getPantsColorFromAvatarData(avatarHex)

                val bodyNode = if (mergedResult != null) {
                    MiiSceneAssembler.createAnimatedBodyNodeFromBuffer(
                        modelLoader, mergedResult.buffer,
                        bodyColor = bodyColor, pantsColor = pantsColor
                    )
                } else null

                bodyNode?.let {
                    it.rotation = Rotation(0f, rotationY, 0f)
                    childNodes.add(it)
                    if (autoAnimate && it.animationCount > 0) {
                        it.playAnimation(0, loop = true)
                    }
                    // Apply head textures to the merged model
                    MiiSceneAssembler.applyHeadTextures(engine, it, mergedResult?.headTextureDir, mergedResult?.headFileBase, mergedResult?.materialTextureMap ?: emptyMap())
                    // Boost head size post-load (avoids bounding-box issues with scaleToUnits)
                    MiiSceneAssembler.boostMergedHeadSize(it)
                }

                // Hat is still separate (parented to headPs via node hierarchy)
                if (hatFileName != null && bodyNode != null) {
                    val hatNode = MiiSceneAssembler.createHatNode(modelLoader, hatFileName)
                    if (hatNode != null) {
                        val parented = MiiSceneAssembler.parentToHeadPs(bodyNode, null, hatNode)
                        if (!parented) {
                            hatNode.rotation = Rotation(0f, rotationY, 0f)
                            childNodes.add(hatNode)
                        }
                    }
                }

                nodesReady = true
                Log.d(TAG, "Mii scene assembled (merged): head=${avatarHex.isNotBlank()}, hat=$hatFileName, costume=$costumeFileName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to assemble Mii scene", e)
        }
    }

    if (nodesReady) {
        Scene(
            modifier = modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            materialLoader = materialLoader,
            view = view,
            renderer = rememberRenderer(engine),
            scene = rememberScene(engine),
            environmentLoader = environmentLoader,
            mainLightNode = mainLightNode,
            cameraNode = cameraNode,
            childNodes = childNodes,
            isOpaque = false,
            onViewCreated = {
                this.view.dynamicResolutionOptions = this.view.dynamicResolutionOptions.apply {
                    enabled = true
                    homogeneousScaling = true
                    quality = QualityLevel.LOW
                }
                this.view.antiAliasing = AntiAliasing.NONE
                this.view.renderQuality = this.view.renderQuality.apply {
                    hdrColorBuffer = QualityLevel.LOW
                }
            }
        )
    } else {
        // Placeholder while loading
        Box(modifier = modifier.fillMaxSize())
    }
}

/**
 * Simple 3D viewer that loads a single GLB from assets.
 * Used for the test screen.
 */
@Composable
fun SimpleGlbViewer(
    assetPath: String,
    modifier: Modifier = Modifier,
    scaleToUnits: Float = 1.0f,
    cameraDistance: Float = 2.5f,
    autoAnimate: Boolean = true
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)

    val cameraNode = rememberCameraNode(engine) {
        position = Position(0f, 0.5f, cameraDistance)
        lookAt(Position(0f, 0.3f, 0f))
    }

    val mainLightNode = rememberMainLightNode(engine) {
        intensity = 80_000f
    }

    Scene(
        modifier = modifier.fillMaxSize(),
        engine = engine,
        modelLoader = modelLoader,
        materialLoader = materialLoader,
        view = rememberView(engine),
        renderer = rememberRenderer(engine),
        scene = rememberScene(engine),
        environmentLoader = environmentLoader,
        mainLightNode = mainLightNode,
        cameraNode = cameraNode,
        childNodes = rememberNodes {
            try {
                val instance = modelLoader.createModelInstance(assetFileLocation = assetPath)
                add(
                    ModelNode(
                        modelInstance = instance,
                        scaleToUnits = scaleToUnits,
                        centerOrigin = Position(0f, -0.5f, 0f)
                    ).apply {
                        if (autoAnimate && animationCount > 0) {
                            playAnimation(0, loop = true)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load GLB: $assetPath", e)
            }
        },
        isOpaque = false
    )
}
