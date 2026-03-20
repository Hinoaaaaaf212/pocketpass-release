package com.pocketpass.app.rendering

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Handles loading Mii model data — fetching head GLBs from the network,
 * downloading non-bundled hats, etc.
 */
object MiiModelLoader {
    private const val TAG = "MiiModelLoader"
    private const val HEAD_API_BASE = "https://mii-unsecure.ariankordi.net/miis/image.glb"
    private const val MAX_GLB_SIZE = 5 * 1024 * 1024 // 5MB

    /**
     * Build the URL for fetching a head GLB from the Mii API.
     */
    fun buildHeadGlbUrl(avatarHex: String): String {
        val encoded = java.net.URLEncoder.encode(avatarHex, "UTF-8")
        return "$HEAD_API_BASE?data=$encoded&verifyCharInfo=0"
    }

    /**
     * Download bytes from a URL with a size limit to prevent OOM from oversized responses.
     */
    private fun downloadWithSizeLimit(url: String): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        try {
            val contentLength = conn.contentLength
            if (contentLength > MAX_GLB_SIZE) {
                throw IOException("GLB too large: $contentLength bytes (max $MAX_GLB_SIZE)")
            }
            val rawBytes = conn.inputStream.use { it.readBytes() }
            if (rawBytes.size > MAX_GLB_SIZE) {
                throw IOException("GLB exceeded max size: ${rawBytes.size} bytes")
            }
            return rawBytes
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Download a head GLB to local cache and return the file path.
     * The GLB is patched to:
     * 1. Strip non-standard "_COLOR" vertex attributes
     * 2. Extract embedded PNG textures to external files (Filament 1.52 can't
     *    decode buffer-view-embedded images)
     */
    suspend fun downloadHeadGlb(context: Context, avatarHex: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val cacheDir = File(context.cacheDir, "mii_heads")
                if (!cacheDir.exists()) cacheDir.mkdirs()

                val fileBase = "head_${avatarHex.hashCode().toUInt()}"
                val cacheFile = File(cacheDir, "$fileBase.glb")

                if (cacheFile.exists() && cacheFile.length() > 0) {
                    if (needsRepatch(cacheFile)) {
                        Log.d(TAG, "Deleting stale cached head GLB for re-download")
                        cacheFile.delete()
                    } else {
                        return@withContext cacheFile.absolutePath
                    }
                }

                val url = buildHeadGlbUrl(avatarHex)
                Log.d(TAG, "Downloading head GLB: $url")
                val rawBytes = downloadWithSizeLimit(url)
                val patchResult = patchHeadGlb(rawBytes, cacheDir, fileBase)
                cacheFile.writeBytes(patchResult.bytes)
                Log.d(TAG, "Head GLB cached: ${cacheFile.absolutePath} (${patchResult.bytes.size} bytes)")
                cacheFile.absolutePath
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download head GLB", e)
                null
            }
        }
    }

    /**
     * Check if a cached GLB needs re-patching.
     */
    private fun needsRepatch(glbFile: File): Boolean {
        val glbBytes = glbFile.readBytes()
        if (glbBytes.size < 20) return true
        val buf = ByteBuffer.wrap(glbBytes).order(ByteOrder.LITTLE_ENDIAN)
        if (buf.getInt(0) != 0x46546C67) return true
        val jsonLen = buf.getInt(12)
        if (buf.getInt(16) != 0x4E4F534A) return true
        val jsonStr = String(glbBytes, 20, jsonLen, Charsets.UTF_8)
        // Re-patch if still has _COLOR or COLOR_0
        if (jsonStr.contains("\"_COLOR\"") || jsonStr.contains("\"COLOR_0\"")) return true
        // Check if any image still has a bufferView (i.e. embedded, not extracted)
        try {
            val root = org.json.JSONObject(jsonStr)
            val images = root.optJSONArray("images")
            if (images != null) {
                for (i in 0 until images.length()) {
                    if (images.getJSONObject(i).has("bufferView")) return true
                }
            }
        } catch (_: Exception) { return true }
        return false
    }

    data class PatchResult(val bytes: ByteArray, val materialTextureMap: Map<String, String>)

    /**
     * Patch a head GLB:
     * 1. Strip "_COLOR" vertex attributes (mask data, not real colors)
     * 2. Extract embedded PNG textures to external files and rewrite image
     *    references as URIs, since Filament 1.52's ResourceLoader can't
     *    decode buffer-view-embedded images.
     * 3. Build a material name → texture file name mapping by following
     *    the glTF reference chain: material → baseColorTexture → texture → image → URI
     */
    private fun patchHeadGlb(glbBytes: ByteArray, cacheDir: File, fileBase: String): PatchResult {
        if (glbBytes.size < 20) return PatchResult(glbBytes, emptyMap())
        val buf = ByteBuffer.wrap(glbBytes).order(ByteOrder.LITTLE_ENDIAN)

        if (buf.getInt(0) != 0x46546C67) return PatchResult(glbBytes, emptyMap())
        val jsonChunkLength = buf.getInt(12)
        if (buf.getInt(16) != 0x4E4F534A) return PatchResult(glbBytes, emptyMap())

        val jsonBytes = ByteArray(jsonChunkLength)
        System.arraycopy(glbBytes, 20, jsonBytes, 0, jsonChunkLength)
        val jsonStr = String(jsonBytes, Charsets.UTF_8).trimEnd('\u0000', ' ')

        try {
            val root = JSONObject(jsonStr)

            // 1. Strip _COLOR from mesh attributes
            val meshes = root.optJSONArray("meshes")
            if (meshes != null) {
                for (m in 0 until meshes.length()) {
                    val mesh = meshes.getJSONObject(m)
                    val primitives = mesh.optJSONArray("primitives") ?: continue
                    for (p in 0 until primitives.length()) {
                        val prim = primitives.getJSONObject(p)
                        val attrs = prim.optJSONObject("attributes") ?: continue
                        attrs.remove("_COLOR")
                    }
                }
            }

            // 2. Extract embedded images to external files
            val binChunkOffset = 20 + jsonChunkLength + 8 // +8 for BIN chunk header
            val images = root.optJSONArray("images")
            val bufferViews = root.optJSONArray("bufferViews")
            if (images != null && bufferViews != null) {
                for (i in 0 until images.length()) {
                    val image = images.getJSONObject(i)
                    if (!image.has("bufferView")) continue

                    val bvIndex = image.getInt("bufferView")
                    val bv = bufferViews.getJSONObject(bvIndex)
                    val byteOffset = bv.optInt("byteOffset", 0)
                    val byteLength = bv.getInt("byteLength")
                    val mimeType = image.optString("mimeType", "image/png")
                    val ext = if (mimeType.contains("jpeg") || mimeType.contains("jpg")) "jpg" else "png"
                    val imageName = image.optString("name", "texture_$i")
                    val imageFileName = "${fileBase}_${imageName}.$ext"

                    // Extract image data from the binary chunk
                    val imageData = ByteArray(byteLength)
                    System.arraycopy(glbBytes, binChunkOffset + byteOffset, imageData, 0, byteLength)
                    File(cacheDir, imageFileName).writeBytes(imageData)

                    // Replace bufferView reference with URI
                    image.remove("bufferView")
                    image.put("uri", imageFileName)

                    Log.d(TAG, "Extracted texture: $imageFileName ($byteLength bytes)")
                }
            }

            // 3. Build material name → texture file name mapping
            val materialTextureMap = mutableMapOf<String, String>()
            val materials = root.optJSONArray("materials")
            val textures = root.optJSONArray("textures")
            if (materials != null && textures != null && images != null) {
                for (m in 0 until materials.length()) {
                    val material = materials.getJSONObject(m)
                    val matName = material.optString("name", "")
                    if (matName.isEmpty()) continue
                    val pbr = material.optJSONObject("pbrMetallicRoughness") ?: continue
                    val baseColorTex = pbr.optJSONObject("baseColorTexture") ?: continue
                    val texIndex = baseColorTex.optInt("index", -1)
                    if (texIndex < 0 || texIndex >= textures.length()) continue
                    val texture = textures.getJSONObject(texIndex)
                    val sourceIndex = texture.optInt("source", -1)
                    if (sourceIndex < 0 || sourceIndex >= images.length()) continue
                    val image = images.getJSONObject(sourceIndex)
                    val uri = image.optString("uri", "")
                    if (uri.isNotEmpty()) {
                        materialTextureMap[matName] = uri
                        Log.d(TAG, "Material mapping: $matName → $uri")
                    }
                }
            }

            // Rebuild GLB with patched JSON
            val newJsonStr = root.toString()
            var newJsonBytes = newJsonStr.toByteArray(Charsets.UTF_8)
            // GLB JSON chunk must be 4-byte aligned, pad with spaces
            val padding = (4 - (newJsonBytes.size % 4)) % 4
            if (padding > 0) {
                newJsonBytes = newJsonBytes + ByteArray(padding) { 0x20 }
            }

            val origBinChunkOffset = 20 + jsonChunkLength
            val binChunkSize = glbBytes.size - origBinChunkOffset

            val newTotalLength = 12 + 8 + newJsonBytes.size + binChunkSize
            val out = ByteBuffer.allocate(newTotalLength).order(ByteOrder.LITTLE_ENDIAN)
            out.putInt(0x46546C67) // magic
            out.putInt(2)          // version
            out.putInt(newTotalLength)
            out.putInt(newJsonBytes.size) // json chunk length
            out.putInt(0x4E4F534A)       // json chunk type
            out.put(newJsonBytes)
            out.put(glbBytes, origBinChunkOffset, binChunkSize)

            Log.d(TAG, "Patched head GLB: stripped _COLOR, extracted textures to external files, ${materialTextureMap.size} material mappings")
            return PatchResult(out.array(), materialTextureMap)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to patch head GLB", e)
            return PatchResult(glbBytes, emptyMap())
        }
    }

    /**
     * Download and patch the head GLB, returning the patched bytes for binary merging.
     * Also extracts PNG textures to external files for manual application.
     * Returns null if download/patching fails.
     */
    suspend fun downloadHeadGlbBytes(context: Context, avatarHex: String): HeadGlbResult? {
        return withContext(Dispatchers.IO) {
            try {
                val cacheDir = File(context.cacheDir, "mii_heads")
                if (!cacheDir.exists()) cacheDir.mkdirs()

                val fileBase = "head_v2_${avatarHex.hashCode().toUInt()}"
                val cacheFile = File(cacheDir, "$fileBase.glb")

                if (cacheFile.exists() && cacheFile.length() > 0 && !needsRepatch(cacheFile)) {
                    // Verify all extracted texture files still exist
                    val hasTextures = cacheDir.listFiles()?.any {
                        it.name.startsWith(fileBase) && it.extension == "png"
                    } ?: false
                    if (hasTextures) {
                        val cachedBytes = cacheFile.readBytes()
                        val mapping = buildMaterialTextureMapFromGlb(cachedBytes)
                        return@withContext HeadGlbResult(cachedBytes, cacheDir, fileBase, mapping)
                    }
                }

                val url = buildHeadGlbUrl(avatarHex)
                Log.d(TAG, "Downloading head GLB for merge: $url")
                val rawBytes = downloadWithSizeLimit(url)
                val patchResult = patchHeadGlb(rawBytes, cacheDir, fileBase)
                cacheFile.writeBytes(patchResult.bytes)
                HeadGlbResult(patchResult.bytes, cacheDir, fileBase, patchResult.materialTextureMap)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download head GLB bytes", e)
                null
            }
        }
    }

    /**
     * Rebuild the material name → texture file name mapping from an already-patched GLB's JSON chunk.
     * Used when loading from cache where the PatchResult was not saved.
     */
    fun buildMaterialTextureMapFromGlb(glbBytes: ByteArray): Map<String, String> {
        if (glbBytes.size < 20) return emptyMap()
        val buf = ByteBuffer.wrap(glbBytes).order(ByteOrder.LITTLE_ENDIAN)
        if (buf.getInt(0) != 0x46546C67) return emptyMap()
        val jsonLen = buf.getInt(12)
        if (buf.getInt(16) != 0x4E4F534A) return emptyMap()
        val jsonStr = String(glbBytes, 20, jsonLen, Charsets.UTF_8).trimEnd('\u0000', ' ')
        return try {
            val root = JSONObject(jsonStr)
            val materials = root.optJSONArray("materials") ?: return emptyMap()
            val textures = root.optJSONArray("textures") ?: return emptyMap()
            val images = root.optJSONArray("images") ?: return emptyMap()
            val map = mutableMapOf<String, String>()
            for (m in 0 until materials.length()) {
                val material = materials.getJSONObject(m)
                val matName = material.optString("name", "")
                if (matName.isEmpty()) continue
                val pbr = material.optJSONObject("pbrMetallicRoughness") ?: continue
                val baseColorTex = pbr.optJSONObject("baseColorTexture") ?: continue
                val texIndex = baseColorTex.optInt("index", -1)
                if (texIndex < 0 || texIndex >= textures.length()) continue
                val texture = textures.getJSONObject(texIndex)
                val sourceIndex = texture.optInt("source", -1)
                if (sourceIndex < 0 || sourceIndex >= images.length()) continue
                val image = images.getJSONObject(sourceIndex)
                val uri = image.optString("uri", "")
                if (uri.isNotEmpty()) map[matName] = uri
            }
            map
        } catch (_: Exception) { emptyMap() }
    }

    data class HeadGlbResult(
        val glbBytes: ByteArray,
        val textureDir: File,
        val fileBase: String,
        val materialTextureMap: Map<String, String> = emptyMap()
    )

    /**
     * Download a non-bundled hat GLB from a remote source.
     * For now, all hats should be in assets or local Miis/ dir.
     * This copies from the local Miis/ directory if available.
     */
    suspend fun cacheHatFromLocal(context: Context, hatFileName: String, sourcePath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val destFile = File(MiiModelCache.getHatCacheDir(context), hatFileName)
                if (destFile.exists()) return@withContext true
                val sourceFile = File(sourcePath)
                if (sourceFile.exists()) {
                    sourceFile.copyTo(destFile, overwrite = true)
                    return@withContext true
                }
                false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cache hat", e)
                false
            }
        }
    }
}
