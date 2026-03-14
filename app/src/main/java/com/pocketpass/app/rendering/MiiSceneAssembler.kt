package com.pocketpass.app.rendering

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.google.android.filament.Engine
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.android.TextureHelper
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.getOrNull

/**
 * Assembles a complete Mii scene graph from GLB parts.
 *
 * The combined body GLBs (male_combined.glb / female_combined.glb) already contain
 * the node hierarchy with hands at their correct rest positions:
 *   rootPs → rotatePs → bodyPs (body + arm mesh)
 *                      → handLPs (left hand sphere)
 *                      → handRPs (right hand sphere)
 *                      → headPs
 *
 * Animation GLBs target these same node names to animate the Mii.
 */
object MiiSceneAssembler {
    private const val TAG = "MiiSceneAssembler"

    // Empirical offsets — adjust based on how GLBs align
    private const val HEAD_Y_OFFSET = 0.6f  // Head sits on body neck
    private const val HAT_Y_OFFSET = 0.35f   // Hat sits above head
    private const val DEFAULT_SCALE = 1.0f
    private const val BODY_X_SCALE = 0.67f   // Slimmer body silhouette
    private const val HEAD_SIZE_BOOST = 1.25f // 25% bigger head relative to body

    data class MiiAssembly(
        val rootNode: Node,
        val bodyNode: ModelNode?,
        val headNode: ModelNode?,
        val hatNode: ModelNode?
    )

    /**
     * Parent head/hat ModelNodes to the body's headPs EmptyNode so they
     * inherit skeletal animation (nodding, tilting, body sway).
     *
     * The body's scaleToUnits normalizes ~60 raw GLB units into 1 scene unit,
     * applying a tiny scale factor (~0.017) to the body root entity. All children
     * of that entity inherit this scale. To make head/hat appear at the correct
     * visual size, we must compensate by dividing their desired scene-unit size
     * by the body's scale factor.
     *
     * Does nothing if the body has no headPs node (e.g., costume GLBs).
     *
     * @return true if parenting was set up, false if falling back to sibling mode
     */
    fun parentToHeadPs(
        bodyNode: ModelNode,
        headNode: ModelNode?,
        hatNode: ModelNode?
    ): Boolean {
        if (headNode == null && hatNode == null) return false
        val headPsNode = bodyNode.emptyNodes.getOrNull("headPs") ?: return false

        // The body's scale from scaleToUnits + BODY_X_SCALE:
        //   X = baseScale * BODY_X_SCALE, Y = baseScale, Z = baseScale
        // We need per-axis inverse to compensate so head/hat appear at correct size.
        val bodyScaleVec = bodyNode.scale
        if (bodyScaleVec.x <= 0f || bodyScaleVec.y <= 0f || bodyScaleVec.z <= 0f) return false

        val invScaleX = 1f / bodyScaleVec.x
        val invScaleY = 1f / bodyScaleVec.y
        val invScaleZ = 1f / bodyScaleVec.z

        headNode?.let {
            // scaleToUnits already set it.scale to normalize the head.
            // Inside the body's entity tree, the body's root scale compounds on top.
            // Multiply by inverse body scale to compensate per axis.
            val s = it.scale
            it.scale = io.github.sceneview.math.Scale(
                s.x * invScaleX, s.y * invScaleY, s.z * invScaleZ
            )
            it.position = io.github.sceneview.math.Position(0f, HEAD_Y_OFFSET * invScaleY, 0f)
            headPsNode.addChildNode(it)
        }

        hatNode?.let {
            val s = it.scale
            it.scale = io.github.sceneview.math.Scale(
                s.x * invScaleX, s.y * invScaleY, s.z * invScaleZ
            )
            it.position = io.github.sceneview.math.Position(0f, (HEAD_Y_OFFSET + HAT_Y_OFFSET) * invScaleY, 0f)
            headPsNode.addChildNode(it)
        }

        Log.d(TAG, "Parented head/hat to headPs (bodyScale=$bodyScaleVec, invScale=($invScaleX, $invScaleY, $invScaleZ))")
        return true
    }

    /**
     * Scale the headWrapper node inside a merged body+head GLB.
     * This adjusts head size without affecting the body's scaleToUnits bounding box.
     */
    fun boostMergedHeadSize(bodyNode: ModelNode) {
        val headWrapper = bodyNode.emptyNodes.getOrNull("headWrapper") ?: return
        val s = headWrapper.scale
        headWrapper.scale = io.github.sceneview.math.Scale(
            s.x * HEAD_SIZE_BOOST, s.y * HEAD_SIZE_BOOST, s.z * HEAD_SIZE_BOOST
        )
        // Nudge head slightly back so it sits better on the body
        val p = headWrapper.position
        headWrapper.position = io.github.sceneview.math.Position(p.x, p.y, p.z - 1f)
        Log.d(TAG, "Boosted merged head size by ${HEAD_SIZE_BOOST}x")
    }

    /**
     * Create a body+hands ModelNode from combined asset.
     * The combined GLB includes the body torso, arm sleeves, and hand spheres
     * positioned via the rootPs/bodyPs/handLPs/handRPs node hierarchy.
     */
    fun createBodyNode(
        modelLoader: ModelLoader,
        isMale: Boolean,
        costumeFileName: String? = null,
        bodyColor: FloatArray? = null,
        pantsColor: FloatArray? = null
    ): ModelNode? {
        return try {
            val assetPath = if (costumeFileName != null) {
                MiiModelCache.getCostumeAssetPath(costumeFileName)
            } else {
                MiiModelCache.getBodyAssetPath(isMale)
            }
            val instance = modelLoader.createModelInstance(assetFileLocation = assetPath)
            ModelNode(
                modelInstance = instance,
                scaleToUnits = DEFAULT_SCALE,
                centerOrigin = io.github.sceneview.math.Position(0f, -0.5f, 0f)
            ).also { node ->
                val s = node.scale
                node.scale = io.github.sceneview.math.Scale(s.x * BODY_X_SCALE, s.y, s.z)
                node.isShadowCaster = false
                node.isShadowReceiver = false
                applyMiiColors(node, bodyColor, pantsColor)
                Log.d(TAG, "Body node created from: $assetPath (animations: ${node.animationCount})")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create body node", e)
            null
        }
    }

    /**
     * Result for plaza Mii buffer preparation with multi-animation support.
     * @param buffer The merged GLB ByteBuffer with multiple animations
     * @param headTextureDir Directory containing extracted head textures (if head was merged)
     * @param animIndexMap Mapping from animation state name to animation track index
     */
    data class PlazaMiiResult(
        val buffer: java.nio.ByteBuffer,
        val headTextureDir: java.io.File?,
        val headFileBase: String?,
        val animIndexMap: Map<String, Int>
    )

    /**
     * Prepare a plaza Mii GLB with 4 animations (walk, idle, greeting, wave) merged in.
     * Optionally merges head GLB from network if avatarHex is provided.
     *
     * Animation indices in the result:
     *   0 = walking, 1 = idle, 2 = greeting, 3 = waving
     */
    suspend fun preparePlazaMiiBuffer(
        context: Context,
        isMale: Boolean,
        avatarHex: String? = null,
        costumeFileName: String? = null
    ): PlazaMiiResult? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        try {
            val bodyPath = if (costumeFileName != null) {
                MiiModelCache.getCostumeAssetPath(costumeFileName)
            } else {
                MiiModelCache.getBodyAssetPath(isMale)
            }

            val bodyBytes = context.assets.open(bodyPath).use { it.readBytes() }

            // Load 4 animation GLBs: walk, idle, greeting, wave
            val walkFile = "mii_hand_walk.glb"
            val idleFile = "mii_hand_wait.glb"
            val greetingFile = "mii_hand_greeting.glb"
            val waveFile = "mii_hand_handwaving.glb"

            val animations = listOf(
                context.assets.open(MiiModelCache.getAnimationAssetPath(walkFile)).use { it.readBytes() } to walkFile,
                context.assets.open(MiiModelCache.getAnimationAssetPath(idleFile)).use { it.readBytes() } to idleFile,
                context.assets.open(MiiModelCache.getAnimationAssetPath(greetingFile)).use { it.readBytes() } to greetingFile,
                context.assets.open(MiiModelCache.getAnimationAssetPath(waveFile)).use { it.readBytes() } to waveFile
            )

            var mergedBuffer = GlbAnimationMerger.mergeMultipleAnimationsIntoBody(bodyBytes, animations)

            val animIndexMap = mapOf(
                "WALKING" to 0,
                "IDLE" to 1,
                "GREETING" to 2,
                "WAVING" to 3
            )

            // Merge head if avatar data is provided
            var textureDir: java.io.File? = null
            var headFileBase: String? = null
            if (!avatarHex.isNullOrBlank()) {
                val headResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    MiiModelLoader.downloadHeadGlbBytes(context, avatarHex)
                }
                if (headResult != null) {
                    mergedBuffer = GlbHeadMerger.mergeHeadIntoBody(mergedBuffer, headResult.glbBytes)
                    textureDir = headResult.textureDir
                    headFileBase = headResult.fileBase
                }
            }

            PlazaMiiResult(mergedBuffer, textureDir, headFileBase, animIndexMap)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare plaza Mii buffer", e)
            null
        }
    }

    /**
     * Pre-merge body + animation GLBs on a background thread.
     * Returns a ByteBuffer ready for [ModelLoader.createModelInstance] on the main thread.
     */
    suspend fun prepareMergedBodyBuffer(
        context: Context,
        isMale: Boolean,
        animationFileName: String,
        costumeFileName: String? = null
    ): java.nio.ByteBuffer? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        try {
            val bodyPath = if (costumeFileName != null) {
                MiiModelCache.getCostumeAssetPath(costumeFileName)
            } else {
                MiiModelCache.getBodyAssetPath(isMale)
            }
            val animPath = MiiModelCache.getAnimationAssetPath(animationFileName)

            val bodyBytes = context.assets.open(bodyPath).use { it.readBytes() }
            val animBytes = context.assets.open(animPath).use { it.readBytes() }
            GlbAnimationMerger.mergeAnimationIntoBody(bodyBytes, animBytes, animationFileName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare merged body buffer", e)
            null
        }
    }

    /**
     * Merge body + animation + head GLBs into a single buffer on a background thread.
     * The head meshes become children of the body's headPs node, so animations
     * automatically affect the head (nodding, tilting, body sway).
     *
     * @return A MergedMiiResult with the merged ByteBuffer and texture dir for post-load texture application
     */
    data class MergedMiiResult(val buffer: java.nio.ByteBuffer, val headTextureDir: java.io.File?, val headFileBase: String? = null)

    suspend fun prepareMergedMiiBuffer(
        context: Context,
        isMale: Boolean,
        animationFileName: String,
        avatarHex: String?,
        costumeFileName: String? = null
    ): MergedMiiResult? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        try {
            val bodyPath = if (costumeFileName != null) {
                MiiModelCache.getCostumeAssetPath(costumeFileName)
            } else {
                MiiModelCache.getBodyAssetPath(isMale)
            }
            val animPath = MiiModelCache.getAnimationAssetPath(animationFileName)

            val bodyBytes = context.assets.open(bodyPath).use { it.readBytes() }
            val animBytes = context.assets.open(animPath).use { it.readBytes() }
            var mergedBuffer = GlbAnimationMerger.mergeAnimationIntoBody(bodyBytes, animBytes, animationFileName)

            // Merge head if avatar data is provided
            var textureDir: java.io.File? = null
            var headFileBase: String? = null
            if (!avatarHex.isNullOrBlank()) {
                val headResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    MiiModelLoader.downloadHeadGlbBytes(context, avatarHex)
                }
                if (headResult != null) {
                    mergedBuffer = GlbHeadMerger.mergeHeadIntoBody(mergedBuffer, headResult.glbBytes)
                    textureDir = headResult.textureDir
                    headFileBase = headResult.fileBase
                }
            }

            MergedMiiResult(mergedBuffer, textureDir, headFileBase)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare merged Mii buffer", e)
            null
        }
    }

    /**
     * Create body ModelNode from a pre-merged ByteBuffer.
     * Must be called on the main thread (Filament GL context).
     */
    fun createAnimatedBodyNodeFromBuffer(
        modelLoader: ModelLoader,
        mergedBuffer: java.nio.ByteBuffer,
        bodyColor: FloatArray? = null,
        pantsColor: FloatArray? = null
    ): ModelNode? {
        return try {
            val instance = modelLoader.createModelInstance(mergedBuffer)
            ModelNode(
                modelInstance = instance,
                scaleToUnits = DEFAULT_SCALE,
                centerOrigin = io.github.sceneview.math.Position(0f, -0.5f, 0f)
            ).also { node ->
                val s = node.scale
                node.scale = io.github.sceneview.math.Scale(s.x * BODY_X_SCALE, s.y, s.z)
                node.isShadowCaster = false
                node.isShadowReceiver = false
                applyMiiColors(node, bodyColor, pantsColor)
                Log.d(TAG, "Animated body node created from buffer (animations: ${node.animationCount})")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create animated body node from buffer", e)
            null
        }
    }

    fun createAnimatedBodyNode(
        context: Context,
        modelLoader: ModelLoader,
        isMale: Boolean,
        animationFileName: String,
        costumeFileName: String? = null,
        bodyColor: FloatArray? = null,
        pantsColor: FloatArray? = null
    ): ModelNode? {
        return try {
            val bodyPath = if (costumeFileName != null) {
                MiiModelCache.getCostumeAssetPath(costumeFileName)
            } else {
                MiiModelCache.getBodyAssetPath(isMale)
            }
            val animPath = MiiModelCache.getAnimationAssetPath(animationFileName)

            val bodyBytes = context.assets.open(bodyPath).use { it.readBytes() }
            val animBytes = context.assets.open(animPath).use { it.readBytes() }
            val mergedBuffer = GlbAnimationMerger.mergeAnimationIntoBody(bodyBytes, animBytes, animationFileName)

            val instance = modelLoader.createModelInstance(mergedBuffer)
            ModelNode(
                modelInstance = instance,
                scaleToUnits = DEFAULT_SCALE,
                centerOrigin = io.github.sceneview.math.Position(0f, -0.5f, 0f)
            ).also { node ->
                val s = node.scale
                node.scale = io.github.sceneview.math.Scale(s.x * BODY_X_SCALE, s.y, s.z)
                node.isShadowCaster = false
                node.isShadowReceiver = false
                applyMiiColors(node, bodyColor, pantsColor)
                Log.d(TAG, "Animated body node created: body=$bodyPath, anim=$animPath (animations: ${node.animationCount})")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create animated body node", e)
            null
        }
    }

    /**
     * Apply shirt and pants colors to a body/costume ModelNode.
     *
     * The combined GLBs have two materials:
     *   - "mii_bodyMt" → shirt, sleeves, hands (uses [bodyColor])
     *   - "mii_pantsMt" → pants/lower body (uses [pantsColor])
     *
     * If a color is null the baked-in GLB default is kept.
     */
    fun applyMiiColors(modelNode: ModelNode, bodyColor: FloatArray?, pantsColor: FloatArray?) {
        if (bodyColor == null && pantsColor == null) return
        try {
            for (renderable in modelNode.renderableNodes) {
                for (i in 0 until renderable.primitiveCount) {
                    val matInstance = renderable.getMaterialInstanceAt(i)
                    val name = matInstance.name
                    val color = when {
                        name == "mii_pantsMt" && pantsColor != null -> pantsColor
                        name == "mii_bodyMt" && bodyColor != null -> bodyColor
                        else -> null
                    }
                    if (color != null) {
                        matInstance.setParameter("baseColorFactor", color[0], color[1], color[2], color[3])
                    }
                }
            }
            Log.d(TAG, "Applied mii colors: body=${bodyColor?.contentToString()}, pants=${pantsColor?.contentToString()}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply mii colors", e)
        }
    }

    /**
     * Apply head textures to a merged Mii model node.
     * The head materials (XluMask, XluNoseLine) are now part of the merged model,
     * so we find them among all renderables and apply extracted PNG textures.
     */
    fun applyHeadTextures(engine: Engine, modelNode: ModelNode, textureDir: java.io.File?, headFileBase: String? = null) {
        if (textureDir == null) return
        val sampler = TextureSampler(
            TextureSampler.MinFilter.LINEAR_MIPMAP_LINEAR,
            TextureSampler.MagFilter.LINEAR,
            TextureSampler.WrapMode.MIRRORED_REPEAT
        )
        // Cache the file list once instead of listing per-material
        val files = textureDir.listFiles()
        for (renderable in modelNode.renderableNodes) {
            for (i in 0 until renderable.primitiveCount) {
                val mat = renderable.getMaterialInstanceAt(i)
                val isHeadMaterial = mat.name in listOf("Material_XluMask_0", "Material_XluNoseLine")
                val texFile = when (mat.name) {
                    "Material_XluMask_0" -> files?.firstOrNull {
                        (headFileBase == null || it.name.startsWith(headFileBase)) &&
                        it.name.contains("MaskTexture") && it.extension == "png"
                    }
                    "Material_XluNoseLine" -> files?.firstOrNull {
                        (headFileBase == null || it.name.startsWith(headFileBase)) &&
                        it.name.contains("Texture_0") && it.extension == "png"
                    }
                    else -> null
                }
                if (texFile != null) {
                    try {
                        val bitmap = BitmapFactory.decodeFile(texFile.absolutePath, BitmapFactory.Options().apply {
                            inPremultiplied = true
                        })
                        if (bitmap != null) {
                            val texture = Texture.Builder()
                                .width(bitmap.width)
                                .height(bitmap.height)
                                .sampler(Texture.Sampler.SAMPLER_2D)
                                .format(Texture.InternalFormat.SRGB8_A8)
                                .levels(0xff)
                                .build(engine)
                            TextureHelper.setBitmap(engine, texture, 0, bitmap)
                            texture.generateMipmaps(engine)
                            mat.setParameter("baseColorMap", texture, sampler)
                            Log.d(TAG, "Applied head texture ${texFile.name} to ${mat.name}")
                        } else if (isHeadMaterial) {
                            // Bitmap decode failed — hide the mesh so it doesn't render as red
                            try { mat.setParameter("baseColorFactor", 0f, 0f, 0f, 0f) } catch (_: Exception) {}
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to apply head texture to ${mat.name}", e)
                        // Hide the broken material so it doesn't render as a red square
                        if (isHeadMaterial) {
                            try { mat.setParameter("baseColorFactor", 0f, 0f, 0f, 0f) } catch (_: Exception) {}
                        }
                    }
                } else if (isHeadMaterial) {
                    // No texture file found — make the material fully transparent
                    // to prevent Filament's red fallback from showing as a red square
                    try { mat.setParameter("baseColorFactor", 0f, 0f, 0f, 0f) } catch (_: Exception) {}
                    Log.w(TAG, "No texture found for ${mat.name} (fileBase=$headFileBase), hiding material")
                }
            }
        }
    }

    /**
     * Create a head ModelNode from a cached file path (downloaded GLB).
     */
    fun createHeadNode(
        modelLoader: ModelLoader,
        headFilePath: String,
        engine: Engine? = null
    ): ModelNode? {
        return try {
            val headFile = java.io.File(headFilePath)
            val instance = modelLoader.createModelInstance(file = headFile)
            val node = ModelNode(
                modelInstance = instance,
                scaleToUnits = 0.65f * HEAD_SIZE_BOOST
            ).apply {
                position = io.github.sceneview.math.Position(0f, HEAD_Y_OFFSET, 0f)
                isShadowCaster = false
                isShadowReceiver = false
            }

            // Filament 1.52 can't decode embedded PNGs, so we apply textures
            // manually from the extracted files alongside the GLB.
            if (engine != null) {
                val fileBase = headFile.nameWithoutExtension
                applyHeadTextures(engine, node, headFile.parentFile, fileBase)
            }

            Log.d(TAG, "Head node created from: $headFilePath (renderables: ${node.renderableNodes.size})")
            node
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create head node from: $headFilePath", e)
            null
        }
    }

    /**
     * Create a hat ModelNode from asset path.
     */
    fun createHatNode(
        modelLoader: ModelLoader,
        hatFileName: String
    ): ModelNode? {
        return try {
            val assetPath = MiiModelCache.getHatAssetPath(hatFileName)
            val instance = modelLoader.createModelInstance(assetFileLocation = assetPath)
            ModelNode(
                modelInstance = instance,
                scaleToUnits = 0.4f
            ).apply {
                position = io.github.sceneview.math.Position(0f, HEAD_Y_OFFSET + HAT_Y_OFFSET, 0f)
                isShadowCaster = false
                isShadowReceiver = false
            }.also {
                Log.d(TAG, "Hat node created from: $assetPath")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create hat node: $hatFileName", e)
            null
        }
    }

    /**
     * Assemble a complete Mii from body (with hands) + optional head + optional hat.
     */
    fun assemble(
        engine: Engine,
        modelLoader: ModelLoader,
        isMale: Boolean = true,
        headFilePath: String? = null,
        hatFileName: String? = null,
        costumeFileName: String? = null,
        bodyColor: FloatArray? = null,
        pantsColor: FloatArray? = null
    ): MiiAssembly {
        val rootNode = Node(engine)

        val bodyNode = createBodyNode(modelLoader, isMale, costumeFileName, bodyColor, pantsColor)
        bodyNode?.let { rootNode.addChildNode(it) }

        val headNode = if (headFilePath != null) {
            createHeadNode(modelLoader, headFilePath, engine)
        } else null

        val hatNode = if (hatFileName != null) {
            createHatNode(modelLoader, hatFileName)
        } else null

        // Parent head/hat to body's headPs for animation; fall back to siblings
        val parented = if (bodyNode != null) {
            parentToHeadPs(bodyNode, headNode, hatNode)
        } else false

        if (!parented) {
            headNode?.let { rootNode.addChildNode(it) }
            hatNode?.let { rootNode.addChildNode(it) }
        }

        return MiiAssembly(rootNode, bodyNode, headNode, hatNode)
    }
}
