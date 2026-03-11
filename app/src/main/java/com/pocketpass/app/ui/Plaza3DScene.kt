package com.pocketpass.app.ui

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.android.filament.View.AntiAliasing
import com.google.android.filament.View.QualityLevel
import com.pocketpass.app.data.Encounter
import com.pocketpass.app.rendering.Plaza3DMiiManager
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.node.Node
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberRenderer
import io.github.sceneview.rememberScene
import io.github.sceneview.rememberView

private const val TAG = "Plaza3DScene"

// Camera constants — must match the cameraNode setup below
const val PLAZA_CAM_Y = 1.5f
const val PLAZA_CAM_Z = 14f
const val PLAZA_CAM_FOV_DEG = 28f // Filament default vertical FOV

/**
 * Project a 3D world position to normalized screen Y (0 = top, 1 = bottom)
 * using the known plaza camera parameters.
 */
fun projectWorldToScreenY(worldY: Float, worldZ: Float): Float {
    // View-space Y: camera looks along -Z, Y is up
    val viewY = worldY - PLAZA_CAM_Y
    val viewZ = PLAZA_CAM_Z - worldZ // distance in front of camera

    // Perspective projection: tan(fov/2) maps to edge of screen
    val halfFovRad = Math.toRadians(PLAZA_CAM_FOV_DEG / 2.0).toFloat()
    val tanHalfFov = kotlin.math.tan(halfFovRad)

    // NDC Y: +1 = top, -1 = bottom
    val ndcY = (viewY / viewZ) / tanHalfFov

    // Screen Y: 0 = top, 1 = bottom (invert NDC)
    return 0.5f - ndcY * 0.5f
}

/**
 * Project a 3D world X position to normalized screen X (0 = left, 1 = right)
 * using perspective projection matching the Filament camera.
 *
 * @param aspectRatio width/height of the scene view
 */
fun projectWorldToScreenX(worldX: Float, worldZ: Float, aspectRatio: Float): Float {
    val viewZ = PLAZA_CAM_Z - worldZ // distance in front of camera

    // Vertical half-FOV → horizontal half-FOV via aspect ratio
    val halfFovRad = Math.toRadians(PLAZA_CAM_FOV_DEG / 2.0).toFloat()
    val tanHalfFovV = kotlin.math.tan(halfFovRad)
    val tanHalfFovH = tanHalfFovV * aspectRatio

    // NDC X: -1 = left, +1 = right
    val ndcX = (worldX / viewZ) / tanHalfFovH

    // Screen X: 0 = left, 1 = right
    return 0.5f + ndcX * 0.5f
}

/**
 * SceneView composable for the 3D plaza with multiple animated Miis.
 *
 * The scene is opaque and renders its own sky background via Filament's clear color,
 * with setZOrderOnTop(false) so Compose label overlays draw above the GL surface.
 *
 * Tap targets are placed as invisible overlay boxes at each Mii's projected X.
 *
 * @param miiManager The shared Plaza3DMiiManager (created in AnimatedPlazaScreen)
 * @param childNodes Combined list of environment + Mii nodes
 * @param isDark Whether dark mode is active (affects sky clear color)
 */
@Composable
fun Plaza3DScene(
    encounters: List<Encounter>,
    userAvatarHex: String? = null,
    userCostume: String? = null,
    miiManager: Plaza3DMiiManager,
    engine: com.google.android.filament.Engine,
    modelLoader: io.github.sceneview.loaders.ModelLoader,
    childNodes: List<Node>,
    isDark: Boolean = false,
    onMiiTapped: (Encounter) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    // Reuse the engine & modelLoader passed from AnimatedPlazaScreen
    // so nodes and scene share the same Filament engine instance
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val view = rememberView(engine)

    // Camera: flat eye-level view looking at the walking path
    val cameraNode = rememberCameraNode(engine) {
        position = Position(0f, 1.5f, 14f)
        lookAt(Position(0f, 1.5f, 0f))
    }

    val mainLightNode = rememberMainLightNode(engine) {
        intensity = 100_000f
        position = Position(0f, 5f, 5f)
    }

    // Track scene size for tap target projection
    var sceneSize by remember { mutableStateOf(IntSize(1080, 1920)) }

    // Sky color for Filament clear color (replaces the Compose gradient background)
    val skyR = if (isDark) 0x0A / 255f else 0x87 / 255f
    val skyG = if (isDark) 0x16 / 255f else 0xCE / 255f
    val skyB = if (isDark) 0x28 / 255f else 0xEB / 255f

    Box(modifier = modifier
        .fillMaxSize()
        .onSizeChanged { sceneSize = it }
    ) {
        // 3D Scene layer — opaque with sky clear color, z-ordered behind Compose
        Scene(
            modifier = Modifier.fillMaxSize(),
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
            cameraManipulator = null,
            isOpaque = true,
            onViewCreated = {
                // Push the GL surface behind Compose views so labels render on top
                setZOrderOnTop(false)
                setZOrderMediaOverlay(false)

                // Set sky background color on the Filament renderer
                this.renderer.clearOptions = this.renderer.clearOptions.apply {
                    clear = true
                }
                this.scene.skybox = com.google.android.filament.Skybox.Builder()
                    .color(skyR, skyG, skyB, 1f)
                    .build(engine)

                this.view.dynamicResolutionOptions = this.view.dynamicResolutionOptions.apply {
                    enabled = false
                }
                this.view.antiAliasing = AntiAliasing.FXAA
            }
        )

        // Tap targets are handled in AnimatedPlazaScreen (above the SurfaceView layer)
    }
}
