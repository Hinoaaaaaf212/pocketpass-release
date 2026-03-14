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
const val PLAZA_CAM_Y = 5.5f
const val PLAZA_CAM_Z = 8.0f
const val PLAZA_CAM_LOOK_Y = 0.5f
const val PLAZA_CAM_FOV_DEG = 38f // wider to capture grid

/**
 * Project a 3D world position to normalized screen coordinates using a proper
 * lookAt view transform matching the Filament camera.
 *
 * Returns (screenX, screenY) where 0,0 = top-left and 1,1 = bottom-right.
 */
fun projectWorldToScreen(worldX: Float, worldY: Float, worldZ: Float, aspectRatio: Float): Pair<Float, Float> {
    // Forward direction (camera → target), normalized
    val fwdY = PLAZA_CAM_LOOK_Y - PLAZA_CAM_Y  // negative (looking down)
    val fwdZ = -PLAZA_CAM_Z                      // negative (looking into scene)
    val fwdLen = kotlin.math.sqrt(fwdY * fwdY + fwdZ * fwdZ)
    val fY = fwdY / fwdLen
    val fZ = fwdZ / fwdLen
    // fX = 0 since camera and lookAt share X=0

    // Right = normalize(forward × worldUp), worldUp = (0,1,0)
    // cross(f, up) = (fY*0 - fZ*1, fZ*0 - 0*0, 0*1 - fY*0) = (-fZ, 0, 0)
    // Since fZ is negative, -fZ is positive → right points in +X. Correct.
    val rX = -fZ / kotlin.math.abs(fZ)  // = 1.0 (normalized, since it's the only component)

    // Up = normalize(right × forward)
    // cross(r, f) where r=(rX,0,0), f=(0,fY,fZ)
    // = (0*fZ - 0*fY, 0*0 - rX*fZ, rX*fY - 0*0) = (0, -rX*fZ, rX*fY)
    val uY = -rX * fZ
    val uZ = rX * fY
    val uLen = kotlin.math.sqrt(uY * uY + uZ * uZ)
    val upY = uY / uLen
    val upZ = uZ / uLen

    // Vector from camera to world point
    val dx = worldX       // camX = 0
    val dy = worldY - PLAZA_CAM_Y
    val dz = worldZ - PLAZA_CAM_Z

    // View-space coordinates (dot with basis vectors)
    val viewX = rX * dx                     // right is purely X
    val viewY = upY * dy + upZ * dz         // up is in Y-Z plane
    val viewDepth = fY * dy + fZ * dz       // positive when point is in front of camera

    if (viewDepth <= 0.001f) return Pair(0.5f, 0.5f)

    // Perspective divide
    val halfFovRad = Math.toRadians(PLAZA_CAM_FOV_DEG / 2.0).toFloat()
    val tanHalfFov = kotlin.math.tan(halfFovRad)

    val ndcY = (viewY / viewDepth) / tanHalfFov
    val ndcX = (viewX / viewDepth) / (tanHalfFov * aspectRatio)

    return Pair(
        0.5f + ndcX * 0.5f,   // screen X: 0=left, 1=right
        0.5f - ndcY * 0.5f    // screen Y: 0=top, 1=bottom
    )
}

/** Convenience: project Y only (passes worldX=0). */
fun projectWorldToScreenY(worldY: Float, worldZ: Float): Float {
    // Use a default aspect ratio — Y projection doesn't depend on it
    return projectWorldToScreen(0f, worldY, worldZ, 1f).second
}

/** Convenience: project X for a ground-level point. */
fun projectWorldToScreenX(worldX: Float, worldZ: Float, aspectRatio: Float): Float {
    return projectWorldToScreen(worldX, 0f, worldZ, aspectRatio).first
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

    // Camera: tilted down to show grass, sky only above the plaza
    val cameraNode = rememberCameraNode(engine) {
        position = Position(0f, PLAZA_CAM_Y, PLAZA_CAM_Z)
        lookAt(Position(0f, PLAZA_CAM_LOOK_Y, 0f))
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
