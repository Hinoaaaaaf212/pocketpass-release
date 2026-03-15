package com.pocketpass.app.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.pocketpass.app.rendering.MiiModelCache
import com.pocketpass.app.rendering.MiiSceneAssembler
import com.pocketpass.app.rendering.MiiStudioDecoder
import com.pocketpass.app.ui.theme.AeroCard
import com.pocketpass.app.ui.theme.BackgroundGradient
import com.pocketpass.app.ui.theme.DarkText
import com.pocketpass.app.ui.theme.OffWhite
import com.pocketpass.app.ui.theme.PocketPassGreen
import com.pocketpass.app.util.LocalSoundManager
import com.pocketpass.app.util.gamepadFocusable
import com.google.android.filament.View.AntiAliasing
import com.google.android.filament.View.QualityLevel
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberRenderer
import io.github.sceneview.rememberScene
import io.github.sceneview.rememberView

private const val TAG = "Mii3DTestScreen"

@Composable
fun Mii3DTestScreen(
    onBack: () -> Unit,
    avatarHex: String? = null,
    sharedEngine: com.google.android.filament.Engine? = null,
    sharedModelLoader: io.github.sceneview.loaders.ModelLoader? = null
) {
    val context = LocalContext.current
    val soundManager = LocalSoundManager.current

    // Selection state
    var selectedBody by remember { mutableStateOf("male") } // "male" or "female"
    var selectedCostume by remember { mutableStateOf<String?>(null) }
    var selectedHat by remember { mutableStateOf<String?>(null) }
    var selectedAnimation by remember { mutableStateOf("mii_hand_wait.glb") }

    // Color state — default from avatarHex, user can override
    val defaultColorIndex = remember(avatarHex) {
        MiiStudioDecoder.extractFavoriteColor(avatarHex ?: "") ?: 5
    }
    var selectedColorIndex by remember { mutableStateOf(defaultColorIndex) }
    var selectedPantsColorIndex by remember { mutableStateOf(defaultColorIndex) }

    // SceneView resources — use shared engine to avoid recreating on tab switches
    val engine = sharedEngine ?: rememberEngine()
    val modelLoader = sharedModelLoader ?: rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)

    val cameraNode = rememberCameraNode(engine) {
        position = Position(0f, 0.5f, 3f)
        lookAt(Position(0f, 0.3f, 0f))
    }

    val mainLightNode = rememberMainLightNode(engine) {
        intensity = 80_000f
        position = Position(0f, 3f, 2f)
    }

    // Assembled nodes
    val childNodes = remember { mutableStateListOf<Node>() }
    var currentBodyNode by remember { mutableStateOf<ModelNode?>(null) }

    // Rebuild the 3D scene only when model selection changes
    LaunchedEffect(selectedBody, selectedCostume, selectedHat, selectedAnimation) {
        val isMale = selectedBody == "male"
        val bodyColor = MiiStudioDecoder.FAVORITE_COLORS[selectedColorIndex]
        val pantsColor = MiiStudioDecoder.PANTS_COLORS[selectedPantsColorIndex]

        // Merge body + animation + head into a single GLB on background thread
        val mergedResult = MiiSceneAssembler.prepareMergedMiiBuffer(
            context, isMale, selectedAnimation,
            avatarHex = avatarHex,
            costumeFileName = selectedCostume
        )

        try {
            childNodes.clear()
            currentBodyNode = null

            val bodyNode = if (mergedResult != null) {
                MiiSceneAssembler.createAnimatedBodyNodeFromBuffer(
                    modelLoader, mergedResult.buffer,
                    bodyColor = bodyColor, pantsColor = pantsColor
                )
            } else null
            bodyNode?.let {
                childNodes.add(it)
                currentBodyNode = it
                if (it.animationCount > 0) it.playAnimation(0, loop = true)
                // Apply head textures to the merged model
                MiiSceneAssembler.applyHeadTextures(engine, it, mergedResult?.headTextureDir, mergedResult?.headFileBase, mergedResult?.materialTextureMap ?: emptyMap())
                // Boost head size post-load (avoids bounding-box issues with scaleToUnits)
                MiiSceneAssembler.boostMergedHeadSize(it)
            }

            // Hat is still separate (parented to headPs via node hierarchy)
            if (selectedHat != null && bodyNode != null) {
                val hatNode = MiiSceneAssembler.createHatNode(modelLoader, selectedHat!!)
                if (hatNode != null) {
                    val parented = MiiSceneAssembler.parentToHeadPs(bodyNode, null, hatNode)
                    if (!parented) {
                        childNodes.add(hatNode)
                    }
                }
            }

            Log.d(TAG, "Assembled (merged): body=$selectedBody, costume=$selectedCostume, hat=$selectedHat, anim=$selectedAnimation, head=${!avatarHex.isNullOrBlank()}, nodes=${childNodes.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to assemble Mii", e)
        }
    }

    // Apply color changes without rebuilding the scene
    LaunchedEffect(selectedColorIndex, selectedPantsColorIndex) {
        val node = currentBodyNode ?: return@LaunchedEffect
        val bodyColor = MiiStudioDecoder.FAVORITE_COLORS[selectedColorIndex]
        val pantsColor = MiiStudioDecoder.PANTS_COLORS[selectedPantsColorIndex]
        MiiSceneAssembler.applyMiiColors(node, bodyColor, pantsColor)
    }

    val bodyOptions = listOf(
        "male" to "Male",
        "female" to "Female"
    )

    val costumeOptions = listOf(null to "Default") + MiiModelCache.BUNDLED_COSTUMES.map {
        it to MiiModelCache.costumeDisplayName(it)
    }

    val hatOptions = listOf(null to "No Hat") + MiiModelCache.BUNDLED_HATS.map {
        it to MiiModelCache.hatDisplayName(it)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CheckeredBackground(
            modifier = Modifier.fillMaxSize(),
            gradientColors = BackgroundGradient
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val backFocus = remember { FocusRequester() }
                IconButton(
                    onClick = { soundManager.playBack(); onBack() },
                    modifier = Modifier.gamepadFocusable(
                        focusRequester = backFocus,
                        shape = CircleShape,
                        onSelect = { soundManager.playBack(); onBack() }
                    )
                ) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = DarkText)
                }
                Text(
                    text = "3D Pii Viewer",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = DarkText,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            // 3D Viewer
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1A2A3A)),
                contentAlignment = Alignment.Center
            ) {
                Scene(
                    modifier = Modifier.fillMaxSize(),
                    engine = engine,
                    modelLoader = modelLoader,
                    materialLoader = materialLoader,
                    view = rememberView(engine),
                    renderer = rememberRenderer(engine),
                    scene = rememberScene(engine),
                    environmentLoader = environmentLoader,
                    mainLightNode = mainLightNode,
                    cameraNode = cameraNode,
                    childNodes = childNodes,
                    isOpaque = false,
                    onViewCreated = {
                        // Performance: enable dynamic resolution to scale down
                        // rendering when the GPU can't keep up
                        view.dynamicResolutionOptions = view.dynamicResolutionOptions.apply {
                            enabled = true
                            homogeneousScaling = true
                            quality = QualityLevel.LOW
                        }
                        // Disable FXAA — not worth the cost for simple Mii models
                        view.antiAliasing = AntiAliasing.NONE
                        // Reduce HDR color buffer quality
                        view.renderQuality = view.renderQuality.apply {
                            hdrColorBuffer = QualityLevel.LOW
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Body selector
            Text(
                text = "Body",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = DarkText,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for ((key, name) in bodyOptions) {
                    ModelChip(
                        name = name,
                        selected = selectedBody == key && selectedCostume == null,
                        onClick = {
                            soundManager.playSelect()
                            selectedBody = key
                            selectedCostume = null
                        }
                    )
                }
            }

            // Shirt color picker
            Text(
                text = "Shirt Color",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = DarkText,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (i in MiiStudioDecoder.SRGB_COLORS.indices) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(MiiStudioDecoder.SRGB_COLORS[i]))
                            .then(
                                if (selectedColorIndex == i) {
                                    Modifier.border(3.dp, PocketPassGreen, CircleShape)
                                } else {
                                    Modifier.border(1.dp, Color.Gray, CircleShape)
                                }
                            )
                            .clickable {
                                soundManager.playSelect()
                                selectedColorIndex = i
                            }
                    )
                }
            }

            // Pants color picker
            Text(
                text = "Pants Color",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = DarkText,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (i in MiiStudioDecoder.SRGB_PANTS_COLORS.indices) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(MiiStudioDecoder.SRGB_PANTS_COLORS[i]))
                            .then(
                                if (selectedPantsColorIndex == i) {
                                    Modifier.border(3.dp, PocketPassGreen, CircleShape)
                                } else {
                                    Modifier.border(1.dp, Color.Gray, CircleShape)
                                }
                            )
                            .clickable {
                                soundManager.playSelect()
                                selectedPantsColorIndex = i
                            }
                    )
                }
            }

            // Costume selector
            Text(
                text = "Costumes",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = DarkText,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for ((costume, name) in costumeOptions) {
                    ModelChip(
                        name = name,
                        selected = selectedCostume == costume,
                        onClick = {
                            soundManager.playSelect()
                            selectedCostume = costume
                        }
                    )
                }
            }

            // Hat selector
            Text(
                text = "Hats",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = DarkText,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for ((hatFile, name) in hatOptions) {
                    ModelChip(
                        name = name,
                        selected = selectedHat == hatFile,
                        onClick = {
                            soundManager.playSelect()
                            selectedHat = hatFile
                        }
                    )
                }
            }

            // Animation selector
            Text(
                text = "Animations",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = DarkText,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for ((animFile, name) in MiiModelCache.CURATED_ANIMATIONS) {
                    ModelChip(
                        name = name,
                        selected = selectedAnimation == animFile,
                        onClick = {
                            soundManager.playSelect()
                            selectedAnimation = animFile
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ModelChip(name: String, selected: Boolean, onClick: () -> Unit) {
    AeroCard(
        modifier = Modifier
            .clickable(onClick = onClick),
        cornerRadius = 20.dp,
        containerColor = if (selected) PocketPassGreen else OffWhite
    ) {
        Text(
            text = name,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color.White else DarkText
        )
    }
}
