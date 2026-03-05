package com.pocketpass.app.ui

import android.content.Context
import android.view.Choreographer
import android.view.MotionEvent
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

    // Get user preferences from DataStore
    val userPreferences = remember { com.pocketpass.app.data.UserPreferences(context) }
    val userName by userPreferences.userNameFlow.collectAsState(initial = null)

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
                if (userName != null) {
                    FilamentPlazaView(
                        userName = userName!!,
                        encounters = encounters,
                        onMiiTapped = { encounter ->
                            selectedEncounter = encounter
                        }
                    )
                } else {
                    // Show loading or placeholder
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Loading...", color = DarkText)
                    }
                }
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
    userName: String,
    encounters: List<Encounter>,
    onMiiTapped: (Encounter) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val userPreferences = remember { com.pocketpass.app.data.UserPreferences(context) }
    val userAvatarHex by userPreferences.avatarHexFlow.collectAsState(initial = null)

    // Store the renderer in a state so we can update it
    var renderer by remember { mutableStateOf<FilamentRenderer?>(null) }

    AndroidView(
        factory = { ctx ->
            android.util.Log.d("FilamentPlazaView", "Creating 3D view with ${encounters.size} encounters")
            SurfaceView(ctx).apply {
                val newRenderer = FilamentRenderer(ctx, this, userName, userAvatarHex, encounters, onMiiTapped, coroutineScope)
                renderer = newRenderer

                // Set up touch listener for Mii selection
                setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        newRenderer.handleTouch(event.x, event.y)
                        true
                    } else {
                        false
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { view ->
            // Update encounters when they change
            renderer?.updateEncounters(encounters)
            android.util.Log.d("FilamentPlazaView", "Updated encounters: ${encounters.size} total")
        }
    )

    // Log encounters for debugging
    LaunchedEffect(encounters.size) {
        android.util.Log.d("FilamentPlazaView", "Encounters changed: ${encounters.size} total")
        encounters.forEach { enc ->
            android.util.Log.d("FilamentPlazaView", "  - ${enc.otherUserName} (${enc.encounterId})")
        }
    }
}

/**
 * Filament 3D Renderer for the Roaming Plaza
 */
class FilamentRenderer(
    private val context: Context,
    private val surfaceView: SurfaceView,
    private val userName: String,
    private val userAvatarHex: String?,
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
    private lateinit var materialProvider: UbershaderProvider
    private val loadedAssets = mutableListOf<FilamentAsset>()

    // Mii characters
    private val miiCharacters = mutableListOf<MiiCharacter3D>()
    private val miiEntities = mutableListOf<Int>()

    // Track if we're ready to render
    private var isReadyToRender = false

    // Frame callback
    private val frameCallback = object : Choreographer.FrameCallback {
        private var lastFrameTime = System.nanoTime()
        private var frameCount = 0

        override fun doFrame(frameTimeNanos: Long) {
            try {
                val deltaTime = (frameTimeNanos - lastFrameTime) / 1_000_000_000f
                lastFrameTime = frameTimeNanos
                frameCount++

                // Log every 60 frames (about once per second at 60fps)
                if (frameCount % 60 == 0) {
                    android.util.Log.d("FilamentRenderer", "--- Frame $frameCount: isReadyToRender=$isReadyToRender, swapChain=$swapChain, uiHelper.isReadyToRender=${uiHelper.isReadyToRender}")
                }

                // Update Miis
                updateMiis(deltaTime)

                // Render frame only if swap chain is ready
                if (isReadyToRender && swapChain != null && uiHelper.isReadyToRender) {
                    if (frameCount == 1 || frameCount % 60 == 0) {
                        android.util.Log.d("FilamentRenderer", "--- Rendering frame $frameCount")
                    }

                    if (renderer.beginFrame(swapChain!!, 0)) {
                        renderer.render(view)
                        renderer.endFrame()

                        if (frameCount == 1) {
                            android.util.Log.d("FilamentRenderer", "--- First frame rendered successfully!")
                        }
                    } else {
                        if (frameCount % 60 == 0) {
                            android.util.Log.w("FilamentRenderer", "--- beginFrame returned false")
                        }
                    }
                } else {
                    if (frameCount % 60 == 0) {
                        android.util.Log.w("FilamentRenderer", "--- Skipping frame (not ready): isReady=$isReadyToRender, swap=$swapChain, uiReady=${uiHelper.isReadyToRender}")
                    }
                }

                Choreographer.getInstance().postFrameCallback(this)
            } catch (e: Exception) {
                android.util.Log.e("FilamentRenderer", "!!! Error in render loop: ${e.message}", e)
                e.printStackTrace()
            }
        }
    }

    init {
        try {
            android.util.Log.d("FilamentRenderer", "=== Starting Filament initialization ===")
            setupFilament()
            android.util.Log.d("FilamentRenderer", "✓ setupFilament() complete")

            createScene()
            android.util.Log.d("FilamentRenderer", "✓ createScene() complete")

            loadModels()
            android.util.Log.d("FilamentRenderer", "✓ loadModels() complete - ${miiCharacters.size} Miis created")

            // Start rendering
            Choreographer.getInstance().postFrameCallback(frameCallback)
            android.util.Log.d("FilamentRenderer", "✓ Frame callback registered")
            android.util.Log.d("FilamentRenderer", "=== Filament initialization complete ===")
        } catch (e: Exception) {
            android.util.Log.e("FilamentRenderer", "!!! Failed to initialize Filament: ${e.message}", e)
            e.printStackTrace()
            throw e
        }
    }

    private fun setupFilament() {
        // Initialize Filament
        android.util.Log.d("FilamentRenderer", "  - Initializing Filament Utils")
        Utils.init()

        android.util.Log.d("FilamentRenderer", "  - Creating Engine")
        engine = Engine.create()

        android.util.Log.d("FilamentRenderer", "  - Creating Renderer")
        renderer = engine.createRenderer()

        android.util.Log.d("FilamentRenderer", "  - Creating Scene")
        scene = engine.createScene()

        android.util.Log.d("FilamentRenderer", "  - Creating View")
        view = engine.createView()

        android.util.Log.d("FilamentRenderer", "  - Creating Camera")
        camera = engine.createCamera(engine.entityManager.create())

        // Initialize GLTF asset loader and material provider
        android.util.Log.d("FilamentRenderer", "  - Initializing MaterialProvider, AssetLoader and ResourceLoader")
        materialProvider = UbershaderProvider(engine)
        assetLoader = AssetLoader(engine, materialProvider, EntityManager.get())
        resourceLoader = ResourceLoader(engine)

        // Configure view
        view.scene = scene
        view.camera = camera

        // Set vibrant sky blue background color
        android.util.Log.d("FilamentRenderer", "  - Setting viewport clear color to vibrant sky blue")
        view.viewport = Viewport(0, 0, 1, 1) // Will be resized properly in onResized
        view.blendMode = View.BlendMode.OPAQUE

        // Set clear options with a more vibrant, saturated sky blue
        val clearOptions = Renderer.ClearOptions()
        clearOptions.clearColor = floatArrayOf(0.4f, 0.7f, 1.0f, 1.0f) // Bright saturated sky blue
        clearOptions.clear = true
        renderer.clearOptions = clearOptions
        android.util.Log.d("FilamentRenderer", "  - Clear options set: color=[${clearOptions.clearColor?.joinToString()}], clear=${clearOptions.clear}")

        // Setup camera position (bird's eye view) - zoomed out more to see all Miis
        val eye = doubleArrayOf(0.0, 20.0, 20.0)  // Camera position (higher and further back)
        val center = doubleArrayOf(0.0, 0.0, 0.0)  // Look at center
        val up = doubleArrayOf(0.0, 1.0, 0.0)      // Up vector
        camera.lookAt(eye[0], eye[1], eye[2], center[0], center[1], center[2], up[0], up[1], up[2])
        android.util.Log.d("FilamentRenderer", "  - Camera positioned at (${eye[0]}, ${eye[1]}, ${eye[2]}) looking at (0, 0, 0)")

        // Setup display helper
        displayHelper = DisplayHelper(context)

        // Setup UI helper for surface management
        android.util.Log.d("FilamentRenderer", "  - Setting up UiHelper")
        uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                try {
                    android.util.Log.d("FilamentRenderer", ">>> Surface changed, creating swap chain")
                    swapChain = engine.createSwapChain(surface)
                    android.util.Log.d("FilamentRenderer", ">>> SwapChain created: $swapChain")

                    // Try to attach display helper if available, but don't require it
                    val display = displayHelper.display
                    android.util.Log.d("FilamentRenderer", ">>> Display: $display")
                    if (display != null) {
                        displayHelper.attach(renderer, display)
                        android.util.Log.d("FilamentRenderer", ">>> DisplayHelper attached to renderer")
                    } else {
                        android.util.Log.w("FilamentRenderer", ">>> Display is null, continuing without DisplayHelper (this is OK)")
                    }

                    // Mark as ready to render - we don't need the display helper for basic rendering
                    isReadyToRender = true
                    android.util.Log.d("FilamentRenderer", ">>> Swap chain ready, isReadyToRender = true")
                } catch (e: Exception) {
                    android.util.Log.e("FilamentRenderer", "!!! Error creating swap chain: ${e.message}", e)
                    e.printStackTrace()
                }
            }

            override fun onDetachedFromSurface() {
                android.util.Log.d("FilamentRenderer", ">>> Surface detached")
                isReadyToRender = false
                try {
                    displayHelper.detach()
                } catch (e: Exception) {
                    android.util.Log.w("FilamentRenderer", ">>> Error detaching display helper (safe to ignore): ${e.message}")
                }
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = null
            }

            override fun onResized(width: Int, height: Int) {
                android.util.Log.d("FilamentRenderer", ">>> Surface resized: ${width}x${height}")
                view.viewport = Viewport(0, 0, width, height)
                val aspect = width.toDouble() / height.toDouble()
                camera.setProjection(45.0, aspect, 0.1, 100.0, Camera.Fov.VERTICAL)
                android.util.Log.d("FilamentRenderer", ">>> Viewport and camera projection set")
            }
        }

        android.util.Log.d("FilamentRenderer", "  - Attaching UiHelper to SurfaceView")
        uiHelper.attachTo(surfaceView)
    }

    private fun createScene() {
        android.util.Log.d("FilamentRenderer", "  - Creating sunlight")
        // Add lighting
        val sunlight = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.SUN)
            .color(1.0f, 1.0f, 1.0f)
            .intensity(100_000.0f)
            .direction(0.0f, -1.0f, 0.3f)
            .castShadows(true)
            .build(engine, sunlight)
        scene.addEntity(sunlight)
        android.util.Log.d("FilamentRenderer", "  - Sunlight added to scene")

        android.util.Log.d("FilamentRenderer", "  - Creating ambient light")
        // Add ambient light
        val ibl = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.SUN)
            .color(0.7f, 0.8f, 1.0f)
            .intensity(20_000.0f)
            .build(engine, ibl)
        scene.addEntity(ibl)
        android.util.Log.d("FilamentRenderer", "  - Ambient light added to scene")

        // Create simple reference grid on the floor level
        // This helps visualize the 3D space without needing materials
        createReferenceGrid()
    }

    private fun createReferenceGrid() {
        // Create a simple grid of lines to visualize the plaza floor
        // We'll add Mii placeholder entities here too
        android.util.Log.d("FilamentRenderer", "  - Reference grid placeholder (no geometry yet)")
        // TODO: Add actual floor geometry once we have proper material support
        // For now, the sky blue background and Mii entities will be enough to visualize
    }

    private fun loadModels() {
        // First, add the user's Mii in the center of the plaza
        android.util.Log.d("FilamentRenderer", "Creating user's Mii: $userName")
        createUserMii()

        // Then add encounter Miis
        encounters.forEach { encounter ->
            val miiChar = MiiCharacter3D.createRandom(encounter)
            miiCharacters.add(miiChar)

            // Load a simple cube model for now as a placeholder
            // TODO: Replace with actual Mii .glb models
            loadMiiPlaceholder(miiChar)
        }

        android.util.Log.d("FilamentRenderer", "Loaded 1 user Mii + ${miiCharacters.size} encounter Miis")
    }

    private fun createUserMii() {
        // Create the user's Mii at the center of the plaza (0, 0)
        android.util.Log.d("FilamentRenderer", "Creating user Mii with hex: ${userAvatarHex?.take(20)}...")

        if (userAvatarHex != null && userAvatarHex.isNotBlank()) {
            // Try to load the .glb model from the API
            scope.launch(Dispatchers.IO) {
                try {
                    // Use the correct API endpoint: /miis/image.glb?data={hex}
                    val encodedHex = java.net.URLEncoder.encode(userAvatarHex, "UTF-8")
                    val glbUrl = "https://mii-unsecure.ariankordi.net/miis/image.glb?data=$encodedHex"
                    android.util.Log.d("FilamentRenderer", "Fetching user Mii model from: $glbUrl")

                    val glbData = fetchGlbFromUrl(glbUrl)

                    withContext(Dispatchers.Main) {
                        if (glbData != null) {
                            loadGlbModel(glbData, 0.0f, 0.0f, 0.0f, isUserMii = true)
                        } else {
                            // Fallback to cube
                            createFallbackCube(0.0f, 0.0f, 0.0f, floatArrayOf(1.0f, 0.5f, 0.0f, 1.0f), isUserMii = true)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FilamentRenderer", "Error loading user Mii model: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        createFallbackCube(0.0f, 0.0f, 0.0f, floatArrayOf(1.0f, 0.5f, 0.0f, 1.0f), isUserMii = true)
                    }
                }
            }
        } else {
            // No hex data, use cube
            createFallbackCube(0.0f, 0.0f, 0.0f, floatArrayOf(1.0f, 0.5f, 0.0f, 1.0f), isUserMii = true)
        }
    }

    private fun fetchGlbFromUrl(url: String): ByteArray? {
        return try {
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode == 200) {
                val bytes = connection.inputStream.readBytes()
                android.util.Log.d("FilamentRenderer", "Successfully fetched ${bytes.size} bytes from $url")
                bytes
            } else {
                android.util.Log.e("FilamentRenderer", "HTTP ${connection.responseCode} for $url")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("FilamentRenderer", "Failed to fetch from $url: ${e.message}")
            null
        }
    }

    private fun loadGlbModel(glbData: ByteArray, x: Float, y: Float, z: Float, isUserMii: Boolean) {
        try {
            android.util.Log.d("FilamentRenderer", "Loading .glb model: ${glbData.size} bytes")

            // Filament requires a DIRECT ByteBuffer with native byte order
            val buffer = ByteBuffer.allocateDirect(glbData.size)
            buffer.order(java.nio.ByteOrder.nativeOrder())
            buffer.put(glbData)
            buffer.flip()

            android.util.Log.d("FilamentRenderer", "Buffer created: capacity=${buffer.capacity()}, remaining=${buffer.remaining()}, isDirect=${buffer.isDirect}")

            android.util.Log.d("FilamentRenderer", "Buffer position=${buffer.position()}, limit=${buffer.limit()}, remaining=${buffer.remaining()}")

            val asset = assetLoader.createAsset(buffer)

            if (asset != null) {
                android.util.Log.d("FilamentRenderer", "Asset created with ${asset.entities.size} entities, root=${asset.root}")

                // Load resources synchronously
                resourceLoader.loadResources(asset)

                // Add root entity to scene (this adds all children too)
                scene.addEntities(asset.entities)

                // Store the root entity
                miiEntities.add(asset.root)
                loadedAssets.add(asset)

                // Position and scale the model
                val transform = engine.transformManager
                val instance = transform.getInstance(asset.root)
                if (instance != 0) {
                    val matrix = FloatArray(16)
                    android.opengl.Matrix.setIdentityM(matrix, 0)
                    android.opengl.Matrix.translateM(matrix, 0, x, y + 1.0f, z)
                    // Scale the model (Mii heads might be very small or very large)
                    android.opengl.Matrix.scaleM(matrix, 0, 3.0f, 3.0f, 3.0f)
                    transform.setTransform(instance, matrix)
                    android.util.Log.d("FilamentRenderer", "Transform applied: translate($x, ${y+1.0f}, $z) scale(3.0)")
                } else {
                    android.util.Log.w("FilamentRenderer", "No transform instance for root entity")
                }

                val name = if (isUserMii) userName else "encounter"
                android.util.Log.d("FilamentRenderer", "✓ Loaded .glb model for $name at ($x, $y, $z)")
            } else {
                android.util.Log.e("FilamentRenderer", "Failed to create asset from .glb data (${glbData.size} bytes)")
                // Log first few bytes to verify it's a valid glb
                val header = glbData.take(12).map { String.format("%02X", it) }.joinToString(" ")
                android.util.Log.e("FilamentRenderer", "GLB header bytes: $header")
                createFallbackCube(x, y, z, floatArrayOf(1.0f, 0.5f, 0.0f, 1.0f), isUserMii)
            }
        } catch (e: Exception) {
            android.util.Log.e("FilamentRenderer", "Error loading .glb model: ${e.message}", e)
            e.printStackTrace()
            createFallbackCube(x, y, z, floatArrayOf(1.0f, 0.5f, 0.0f, 1.0f), isUserMii)
        }
    }

    private fun createFallbackCube(x: Float, y: Float, z: Float, color: FloatArray, isUserMii: Boolean) {
        val cubeEntity = createSimpleMiiCube(x, y, z, color)
        if (cubeEntity != 0) {
            scene.addEntity(cubeEntity)
            miiEntities.add(cubeEntity)
            val name = if (isUserMii) "User" else "Encounter"
            android.util.Log.d("FilamentRenderer", "✓ $name Mii fallback cube created at ($x, $y, $z)")
        }
    }

    @Entity
    private fun createSimpleMiiCube(x: Float, y: Float, z: Float, color: FloatArray): Int {
        // Create a simple colored cube to represent a Mii
        // This is a placeholder until we have actual .glb models

        val entity = EntityManager.get().create()

        // Make cubes larger so they're more visible
        val cubeSize = 2.0f
        val half = cubeSize / 2f

        // Vertex data: position (3) + color (4) = 7 floats per vertex
        val vertices = floatArrayOf(
            // Front face
            -half, -half, half,   color[0], color[1], color[2], color[3],
             half, -half, half,   color[0], color[1], color[2], color[3],
             half,  half, half,   color[0], color[1], color[2], color[3],
            -half,  half, half,   color[0], color[1], color[2], color[3],
            // Back face
            -half, -half, -half,  color[0], color[1], color[2], color[3],
             half, -half, -half,  color[0], color[1], color[2], color[3],
             half,  half, -half,  color[0], color[1], color[2], color[3],
            -half,  half, -half,  color[0], color[1], color[2], color[3]
        )

        val vertexBuffer = ByteBuffer.allocate(vertices.size * 4)
        vertexBuffer.order(java.nio.ByteOrder.nativeOrder())
        vertexBuffer.asFloatBuffer().put(vertices)
        vertexBuffer.rewind()

        val vb = VertexBuffer.Builder()
            .vertexCount(8)
            .bufferCount(1)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0,
                VertexBuffer.AttributeType.FLOAT3, 0, 28)
            .attribute(VertexBuffer.VertexAttribute.COLOR, 0,
                VertexBuffer.AttributeType.FLOAT4, 12, 28)
            .build(engine)

        vb.setBufferAt(engine, 0, vertexBuffer)

        // Index buffer for cube
        val indices = shortArrayOf(
            0, 1, 2, 0, 2, 3,  // Front
            5, 4, 7, 5, 7, 6,  // Back
            4, 0, 3, 4, 3, 7,  // Left
            1, 5, 6, 1, 6, 2,  // Right
            3, 2, 6, 3, 6, 7,  // Top
            4, 5, 1, 4, 1, 0   // Bottom
        )

        val indexBuffer = ByteBuffer.allocate(indices.size * 2)
        indexBuffer.order(java.nio.ByteOrder.nativeOrder())
        indexBuffer.asShortBuffer().put(indices)
        indexBuffer.rewind()

        val ib = IndexBuffer.Builder()
            .indexCount(indices.size)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)

        ib.setBuffer(engine, indexBuffer)

        // Create a simple material using Filament's basic approach
        // For now, we'll just create the geometry without a proper colored material
        // This is a limitation of not having pre-built materials
        try {
            // Build the renderable without material (it will use default)
            // Note: This may not show color, but will show geometry
            val renderableBuilder = RenderableManager.Builder(1)
                .boundingBox(Box(x - half, y - half, z - half, x + half, y + half, z + half))
                .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vb, ib)
                .culling(false)
                .receiveShadows(false)
                .castShadows(false)

            renderableBuilder.build(engine, entity)

            android.util.Log.d("FilamentRenderer", "Created cube geometry at ($x, $y, $z) size=$cubeSize, color=${color.contentToString()}")

        } catch (e: Exception) {
            android.util.Log.e("FilamentRenderer", "Failed to create cube: ${e.message}", e)
            e.printStackTrace()
            return 0
        }

        // Set position
        val transform = engine.transformManager
        val instance = transform.getInstance(entity)
        val matrix = FloatArray(16)
        android.opengl.Matrix.setIdentityM(matrix, 0)
        android.opengl.Matrix.translateM(matrix, 0, x, y + 1.0f, z) // Raise 1 unit above ground
        transform.setTransform(instance, matrix)

        return entity
    }

    private fun loadMiiPlaceholder(miiChar: MiiCharacter3D) {
        // Try to load .glb model from the API using the encounter's avatar hex
        val avatarHex = miiChar.encounter.otherUserAvatarHex

        if (avatarHex.isNotBlank()) {
            scope.launch(Dispatchers.IO) {
                try {
                    // Use the correct API endpoint: /miis/image.glb?data={hex}
                    val encodedHex = java.net.URLEncoder.encode(avatarHex, "UTF-8")
                    val glbUrl = "https://mii-unsecure.ariankordi.net/miis/image.glb?data=$encodedHex"
                    android.util.Log.d("FilamentRenderer", "Fetching Mii model for ${miiChar.encounter.otherUserName} from: $glbUrl")

                    val glbData = fetchGlbFromUrl(glbUrl)

                    withContext(Dispatchers.Main) {
                        if (glbData != null) {
                            loadGlbModel(glbData, miiChar.x, 0.0f, miiChar.z, isUserMii = false)
                        } else {
                            // Fallback to colored cube
                            createEncounterFallbackCube(miiChar)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FilamentRenderer", "Error loading Mii model for ${miiChar.encounter.otherUserName}: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        createEncounterFallbackCube(miiChar)
                    }
                }
            }
        } else {
            // No hex data, use cube
            createEncounterFallbackCube(miiChar)
        }
    }

    private fun createEncounterFallbackCube(miiChar: MiiCharacter3D) {
        // Use different colors for different Miis
        val colors = listOf(
            floatArrayOf(0.3f, 0.6f, 1.0f, 1.0f),  // Blue
            floatArrayOf(1.0f, 0.3f, 0.6f, 1.0f),  // Pink
            floatArrayOf(0.3f, 1.0f, 0.6f, 1.0f),  // Green
            floatArrayOf(1.0f, 1.0f, 0.3f, 1.0f),  // Yellow
            floatArrayOf(0.6f, 0.3f, 1.0f, 1.0f),  // Purple
            floatArrayOf(1.0f, 0.6f, 0.3f, 1.0f),  // Orange
        )

        // Pick a color based on the encounter ID
        val colorIndex = miiChar.encounter.encounterId.hashCode() % colors.size
        val color = colors[colorIndex.coerceAtLeast(0)]

        createFallbackCube(miiChar.x, 0.0f, miiChar.z, color, isUserMii = false)
        android.util.Log.d("FilamentRenderer", "Created fallback cube for ${miiChar.encounter.otherUserName}")
    }

    private fun updateMiis(deltaTime: Float) {
        miiCharacters.forEachIndexed { index, mii ->
            // Update Mii character logic (position, direction, etc.)
            mii.update(deltaTime)

            // Update 3D model position and rotation if entity exists
            // Note: index+1 because miiEntities[0] is the user's Mii
            val entityIndex = index + 1
            if (entityIndex < miiEntities.size) {
                val entity = miiEntities[entityIndex]
                val transform = engine.transformManager
                val transformInstance = transform.getInstance(entity)

                if (transformInstance != 0) {
                    // Create transformation matrix
                    val matrix = FloatArray(16)
                    android.opengl.Matrix.setIdentityM(matrix, 0)

                    // Translate to Mii's position
                    android.opengl.Matrix.translateM(
                        matrix, 0,
                        mii.x,
                        1.0f,  // Height above ground
                        mii.z
                    )

                    // Rotate to face movement direction
                    val angleInDegrees = Math.toDegrees(
                        kotlin.math.atan2(mii.velocityZ.toDouble(), mii.velocityX.toDouble())
                    ).toFloat()
                    android.opengl.Matrix.rotateM(matrix, 0, angleInDegrees - 90f, 0f, 1f, 0f)

                    // Apply transformation
                    transform.setTransform(transformInstance, matrix)
                }
            }
        }
    }

    fun handleTouch(x: Float, y: Float) {
        // Handle touch events for Mii selection
        try {
            // Simple 2D distance check for now
            // In a real implementation, we'd do proper 3D ray picking

            // Get viewport dimensions
            val viewport = view.viewport
            if (viewport.width == 0 || viewport.height == 0) return

            // Find the closest Mii to the touch point
            var closestMii: MiiCharacter3D? = null
            var closestDistance = Float.MAX_VALUE

            miiCharacters.forEach { mii ->
                // Project 3D position to screen space (simplified)
                // For a proper implementation, we'd use the view-projection matrix
                val screenX = viewport.width / 2 + mii.x * 20
                val screenY = viewport.height / 2 - mii.z * 20

                val dx = screenX - x
                val dy = screenY - y
                val distance = kotlin.math.sqrt(dx * dx + dy * dy).toFloat()

                if (distance < closestDistance && distance < 100f) { // 100px tap radius
                    closestDistance = distance
                    closestMii = mii
                }
            }

            // Trigger callback if a Mii was selected
            closestMii?.let { mii ->
                android.util.Log.d("FilamentRenderer", "Tapped Mii: ${mii.encounter.otherUserName}")
                scope.launch {
                    onMiiTapped(mii.encounter)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("FilamentRenderer", "Error handling touch: ${e.message}", e)
        }
    }

    fun updateEncounters(newEncounters: List<Encounter>) {
        android.util.Log.d("FilamentRenderer", "updateEncounters called with ${newEncounters.size} encounters")

        // Remove old encounter entities (keep user Mii at index 0)
        if (miiEntities.size > 1) {
            for (i in 1 until miiEntities.size) {
                val entity = miiEntities[i]
                scene.removeEntity(entity)
                EntityManager.get().destroy(entity)
            }
            miiEntities.subList(1, miiEntities.size).clear()
        }
        miiCharacters.clear()

        // Add new encounter Miis
        newEncounters.forEach { encounter ->
            val miiChar = MiiCharacter3D.createRandom(encounter)
            miiCharacters.add(miiChar)
            loadMiiPlaceholder(miiChar)
        }

        android.util.Log.d("FilamentRenderer", "Updated to ${miiCharacters.size} encounter Miis, ${miiEntities.size} total entities")
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
