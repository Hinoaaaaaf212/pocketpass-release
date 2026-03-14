package com.pocketpass.app.rendering

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
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

    /**
     * Build the URL for fetching a head GLB from the Mii API.
     */
    fun buildHeadGlbUrl(avatarHex: String): String {
        val encoded = java.net.URLEncoder.encode(avatarHex, "UTF-8")
        return "$HEAD_API_BASE?data=$encoded&verifyCharInfo=0"
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
                val rawBytes = URL(url).readBytes()
                val bytes = patchHeadGlb(rawBytes, cacheDir, fileBase)
                cacheFile.writeBytes(bytes)
                Log.d(TAG, "Head GLB cached: ${cacheFile.absolutePath} (${bytes.size} bytes)")
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
        // Re-patch if still has _COLOR, COLOR_0, or embedded images (bufferView in images)
        return jsonStr.contains("\"_COLOR\"") ||
               jsonStr.contains("\"COLOR_0\"") ||
               jsonStr.contains("\"bufferView\"")
    }

    /**
     * Patch a head GLB:
     * 1. Strip "_COLOR" vertex attributes (mask data, not real colors)
     * 2. Extract embedded PNG textures to external files and rewrite image
     *    references as URIs, since Filament 1.52's ResourceLoader can't
     *    decode buffer-view-embedded images.
     */
    private fun patchHeadGlb(glbBytes: ByteArray, cacheDir: File, fileBase: String): ByteArray {
        if (glbBytes.size < 20) return glbBytes
        val buf = ByteBuffer.wrap(glbBytes).order(ByteOrder.LITTLE_ENDIAN)

        if (buf.getInt(0) != 0x46546C67) return glbBytes
        val jsonChunkLength = buf.getInt(12)
        if (buf.getInt(16) != 0x4E4F534A) return glbBytes

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

            Log.d(TAG, "Patched head GLB: stripped _COLOR, extracted textures to external files")
            return out.array()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to patch head GLB", e)
            return glbBytes
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

                val fileBase = "head_${avatarHex.hashCode().toUInt()}"
                val cacheFile = File(cacheDir, "$fileBase.glb")

                if (cacheFile.exists() && cacheFile.length() > 0 && !needsRepatch(cacheFile)) {
                    return@withContext HeadGlbResult(cacheFile.readBytes(), cacheDir, fileBase)
                }

                val url = buildHeadGlbUrl(avatarHex)
                Log.d(TAG, "Downloading head GLB for merge: $url")
                val rawBytes = URL(url).readBytes()
                val bytes = patchHeadGlb(rawBytes, cacheDir, fileBase)
                cacheFile.writeBytes(bytes)
                HeadGlbResult(bytes, cacheDir, fileBase)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download head GLB bytes", e)
                null
            }
        }
    }

    data class HeadGlbResult(val glbBytes: ByteArray, val textureDir: File, val fileBase: String)

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
