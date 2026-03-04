package com.pocketpass.app.ui

import android.content.Context
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.filament.*
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.*
import com.google.android.filament.utils.*
import com.pocketpass.app.data.Encounter
import com.pocketpass.app.data.PocketPassDatabase
import com.pocketpass.app.ui.theme.DarkText
import com.pocketpass.app.ui.theme.PocketPassGreen
import com.pocketpass.app.ui.theme.SkyBlue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RoamingPlaza3DScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { PocketPassDatabase.getDatabase(context) }
    val encounters by db.encounterDao().getAllEncountersFlow().collectAsState(initial = emptyList())

    var selectedEncounter by remember { mutableStateOf<Encounter?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Checkered background
        CheckeredBackground(
            modifier = Modifier.fillMaxSize(),
            gradientColors = listOf(PocketPassGreen, SkyBlue)
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = DarkText
                    )
                }
                Text(
                    text = "Roaming Plaza",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                )
                Spacer(modifier = Modifier.width(48.dp)) // Balance the back button
            }

            // 3D Filament View
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                FilamentPlazaView(
                    encounters = encounters,
                    onMiiTapped = { encounter ->
                        selectedEncounter = encounter
                    }
                )
            }

            // Info text
            Text(
                text = if (encounters.isEmpty()) {
                    "No friends in the plaza yet!"
                } else {
                    "${encounters.size} Miis roaming around"
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = DarkText
            )
        }

        // Show selected Mii card
        if (selectedEncounter != null) {
            AlertDialog(
                onDismissRequest = { selectedEncounter = null },
                title = {
                    Text(selectedEncounter!!.otherUserName)
                },
                text = {
                    Column {
                        Text("Greeting: ${selectedEncounter!!.greeting}")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("From: ${selectedEncounter!!.origin}")
                        if (selectedEncounter!!.hobbies.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Hobbies: ${selectedEncounter!!.hobbies}")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedEncounter = null }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

@Composable
fun FilamentPlazaView(
    encounters: List<Encounter>,
    onMiiTapped: (Encounter) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    AndroidView(
        factory = { ctx ->
            SurfaceView(ctx).apply {
                val renderer = FilamentRenderer(ctx, this, encounters, onMiiTapped, coroutineScope)
                // Renderer will handle lifecycle
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * Filament 3D Renderer for the Roaming Plaza
 */
class FilamentRenderer(
    private val context: Context,
    private val surfaceView: SurfaceView,
    private val encounters: List<Encounter>,
    private val onMiiTapped: (Encounter) -> Unit,
    private val scope: CoroutineScope
) {
    // Filament core components
    private lateinit var engine: Engine
    private lateinit var renderer: Renderer
    private lateinit var scene: Scene
    private lateinit var view: View
    private lateinit var camera: Camera
    private var swapChain: SwapChain? = null

    // UI and display
    private lateinit var uiHelper: UiHelper
    private lateinit var displayHelper: DisplayHelper

    // GLTF model loading
    private lateinit var assetLoader: AssetLoader
    private lateinit var resourceLoader: ResourceLoader
    private val loadedAssets = mutableListOf<FilamentAsset>()

    // Mii characters
    private val miiCharacters = mutableListOf<MiiCharacter3D>()
    private val miiEntities = mutableListOf<@Entity Int>()

    // Track if we're ready to render
    private var isReadyToRender = false

    // Frame callback
    private val frameCallback = object : Choreographer.FrameCallback {
        private var lastFrameTime = System.nanoTime()

        override fun doFrame(frameTimeNanos: Long) {
            try {
                val deltaTime = (frameTimeNanos - lastFrameTime) / 1_000_000_000f
                lastFrameTime = frameTimeNanos

                // Update Miis
                updateMiis(deltaTime)

                // Render frame only if swap chain is ready
                if (isReadyToRender && swapChain != null && uiHelper.isReadyToRender) {
                    if (renderer.beginFrame(swapChain!!, 0)) {
                        renderer.render(view)
                        renderer.endFrame()
                    }
                }

                Choreographer.getInstance().postFrameCallback(this)
            } catch (e: Exception) {
                android.util.Log.e("FilamentRenderer", "Error in render loop: ${e.message}", e)
            }
        }
    }

    init {
        try {
            android.util.Log.d("FilamentRenderer", "Initializing Filament renderer")
            setupFilament()
            createScene()
            loadModels()

            // Start rendering
            Choreographer.getInstance().postFrameCallback(frameCallback)
            android.util.Log.d("FilamentRenderer", "Filament renderer initialized successfully")
        } catch (e: Exception) {
            android.util.Log.e("FilamentRenderer", "Failed to initialize Filament: ${e.message}", e)
            throw e
        }
    }

    private fun setupFilament() {
        // Initialize Filament
        Utils.init()

        engine = Engine.create()
        renderer = engine.createRenderer()
        scene = engine.createScene()
        view = engine.createView()
        camera = engine.createCamera(engine.entityManager.create())

        // Initialize GLTF asset loader
        assetLoader = AssetLoader(engine, MaterialProvider(engine), EntityManager.get())
        resourceLoader = ResourceLoader(engine)

        // Configure view
        view.scene = scene
        view.camera = camera

        // Set a sky blue background color so the screen isn't black
        view.setClearColor(0.53f, 0.81f, 0.92f, 1.0f) // Sky blue (RGB: 135, 206, 235)

        // Setup camera position (bird's eye view)
        val eye = doubleArrayOf(0.0, 15.0, 15.0)  // Camera position
        val center = doubleArrayOf(0.0, 0.0, 0.0)  // Look at center
        val up = doubleArrayOf(0.0, 1.0, 0.0)      // Up vector
        camera.lookAt(eye[0], eye[1], eye[2], center[0], center[1], center[2], up[0], up[1], up[2])

        // Setup display helper
        displayHelper = DisplayHelper(context)

        // Setup UI helper for surface management
        uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                try {
                    android.util.Log.d("FilamentRenderer", "Surface changed, creating swap chain")
                    swapChain = engine.createSwapChain(surface)

                    val display = displayHelper.display
                    if (display != null) {
                        displayHelper.attach(renderer, display)
                        isReadyToRender = true
                        android.util.Log.d("FilamentRenderer", "Swap chain ready")
                    } else {
                        android.util.Log.e("FilamentRenderer", "Display is null!")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FilamentRenderer", "Error creating swap chain: ${e.message}", e)
                }
            }

            override fun onDetachedFromSurface() {
                android.util.Log.d("FilamentRenderer", "Surface detached")
                isReadyToRender = false
                displayHelper.detach()
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = null
            }

            override fun onResized(width: Int, height: Int) {
                android.util.Log.d("FilamentRenderer", "Surface resized: ${width}x${height}")
                view.viewport = Viewport(0, 0, width, height)
                val aspect = width.toDouble() / height.toDouble()
                camera.setProjection(45.0, aspect, 0.1, 100.0, Camera.Fov.VERTICAL)
            }
        }

        uiHelper.attachTo(surfaceView)
    }

    private fun createScene() {
        // Add lighting
        val sunlight = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.SUN)
            .color(1.0f, 1.0f, 1.0f)
            .intensity(100_000.0f)
            .direction(0.0f, -1.0f, 0.3f)
            .castShadows(true)
            .build(engine, sunlight)
        scene.addEntity(sunlight)

        // Add ambient light
        val ibl = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.SUN)
            .color(0.7f, 0.8f, 1.0f)
            .intensity(20_000.0f)
            .build(engine, ibl)
        scene.addEntity(ibl)

        // Create simple reference grid on the floor level
        // This helps visualize the 3D space without needing materials
        createReferenceGrid()
    }

    private fun createReferenceGrid() {
        // Create a simple grid of lines to visualize the plaza floor
        // We'll add Mii placeholder entities here too
        android.util.Log.d("FilamentRenderer", "Creating reference grid (visual placeholder)")
        // TODO: Add actual floor geometry once we have proper material support
        // For now, the sky blue background and Mii entities will be enough to visualize
    }

    private fun loadModels() {
        // For each encounter, create a Mii character and load their 3D model
        encounters.forEach { encounter ->
            val miiChar = MiiCharacter3D.createRandom(encounter)
            miiCharacters.add(miiChar)

            // Load a simple cube model for now as a placeholder
            // TODO: Replace with actual Mii .glb models
            loadMiiPlaceholder(miiChar)
        }

        android.util.Log.d("FilamentRenderer", "Loaded ${miiCharacters.size} Mii characters")
    }

    private fun loadMiiPlaceholder(miiChar: MiiCharacter3D) {
        // Create a simple colored sphere/cube as a placeholder for each Mii
        // This will be replaced with actual .glb Mii models later

        // For now, we'll use a simple approach: create a tiny cube entity at the Mii's position
        // The cube will move around based on the miiChar's position updates

        // We can't create geometry without materials, so let's try loading a default primitive
        // from assets or wait until we have actual .glb files

        android.util.Log.d("FilamentRenderer", "Placeholder for Mii at (${miiChar.position.x}, ${miiChar.position.z})")

        // TODO: Load actual .glb model from assets
        // Example code for when we have .glb files:
        // try {
        //     val buffer = context.assets.open("models/mii_${encounter.id}.glb").readBytes()
        //     val asset = assetLoader.createAsset(ByteBuffer.wrap(buffer))
        //     asset?.let {
        //         resourceLoader.loadResources(it)
        //         scene.addEntities(it.entities)
        //         loadedAssets.add(it)
        //         miiEntities.add(it.root)
        //     }
        // } catch (e: Exception) {
        //     android.util.Log.e("FilamentRenderer", "Failed to load Mii model: ${e.message}")
        // }
    }

    private fun updateMiis(deltaTime: Float) {
        miiCharacters.forEachIndexed { index, mii ->
            // Update Mii character logic (position, direction, etc.)
            mii.update(deltaTime)

            // Update 3D model position and rotation if entity exists
            if (index < miiEntities.size) {
                val entity = miiEntities[index]
                val transform = engine.transformManager
                val transformInstance = transform.getInstance(entity)

                if (transformInstance != 0) {
                    // Create transformation matrix
                    val matrix = FloatArray(16)
                    android.opengl.Matrix.setIdentityM(matrix, 0)

                    // Translate to Mii's position
                    android.opengl.Matrix.translateM(
                        matrix, 0,
                        mii.position.x,
                        0.5f,  // Height above ground
                        mii.position.z
                    )

                    // Rotate to face movement direction
                    val angleInDegrees = Math.toDegrees(
                        kotlin.math.atan2(mii.direction.z.toDouble(), mii.direction.x.toDouble())
                    ).toFloat()
                    android.opengl.Matrix.rotateM(matrix, 0, angleInDegrees - 90f, 0f, 1f, 0f)

                    // Apply transformation
                    transform.setTransform(transformInstance, matrix)
                }
            }
        }
    }

    fun cleanup() {
        try {
            android.util.Log.d("FilamentRenderer", "Cleaning up Filament resources")
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            uiHelper.detach()

            // Cleanup loaded assets
            loadedAssets.forEach { asset ->
                assetLoader.destroyAsset(asset)
            }
            loadedAssets.clear()
            miiEntities.clear()

            // Cleanup swap chain
            swapChain?.let { engine.destroySwapChain(it) }

            // Cleanup Filament resources
            engine.destroyRenderer(renderer)
            engine.destroyView(view)
            engine.destroyScene(scene)

            // Destroy camera entity
            val cameraEntity = camera.entity
            engine.destroyCameraComponent(cameraEntity)
            EntityManager.get().destroy(cameraEntity)

            engine.destroy()
            android.util.Log.d("FilamentRenderer", "Filament cleanup complete")
        } catch (e: Exception) {
            android.util.Log.e("FilamentRenderer", "Error during cleanup: ${e.message}", e)
        }
    }
}
