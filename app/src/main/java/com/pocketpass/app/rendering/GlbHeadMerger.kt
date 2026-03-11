package com.pocketpass.app.rendering

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Merges a head GLB into a body GLB at the binary level, making the head meshes
 * children of the body's `headPs` empty node. The result is a single GLB where
 * Filament treats body + head as one FilamentAsset — head meshes automatically
 * inherit all animation transforms (nodding, tilting, body sway) through the
 * glTF node hierarchy.
 *
 * Head GLB structure (from the API):
 *   6 flat nodes, each with one mesh: OpaFaceline, OpaNose, OpaForehead,
 *   OpaHair, XluMask, XluNoseLine
 *
 * Body GLB structure:
 *   rootPs → rotatePs → bodyPs (mesh), handLPs (mesh), handRPs (mesh), headPs (empty)
 *
 * After merge:
 *   rootPs → rotatePs → bodyPs, handLPs, handRPs, headPs → [head mesh nodes]
 */
object GlbHeadMerger {
    private const val TAG = "GlbHeadMerger"

    private const val GLB_MAGIC = 0x46546C67   // "glTF"
    private const val JSON_CHUNK = 0x4E4F534A  // "JSON"
    private const val BIN_CHUNK = 0x004E4942   // "BIN\0"

    // Head positioning in body's raw GLB coordinate space.
    // Body spans Y 16–76 (~60 units). Head GLB spans ~50 units wide, Y 0–70.
    // Scale head down and translate up to sit on the body's neck.
    private const val HEAD_SCALE = 0.65        // 65% of raw head size
    private const val HEAD_Y_TRANSLATE = 70.0  // Y offset to place head at neck level
    private const val HEAD_Z_TRANSLATE = -3.0   // Z offset — push head back so it sits centered on neck during animations

    /**
     * Merge head GLB meshes into the body GLB as children of headPs.
     *
     * @param bodyGlbBuffer The body GLB (possibly already merged with animation) as a ByteBuffer
     * @param headGlbBytes  The raw head GLB bytes (already patched by MiiModelLoader)
     * @return A new direct ByteBuffer containing the merged GLB, or the original body buffer on failure
     */
    fun mergeHeadIntoBody(bodyGlbBuffer: ByteBuffer, headGlbBytes: ByteArray): ByteBuffer {
        try {
            // Read body GLB bytes from the ByteBuffer
            val bodyBytes = ByteArray(bodyGlbBuffer.remaining())
            val pos = bodyGlbBuffer.position()
            bodyGlbBuffer.get(bodyBytes)
            bodyGlbBuffer.position(pos) // restore position

            val body = parseGlb(bodyBytes) ?: return bodyGlbBuffer
            val head = parseGlb(headGlbBytes) ?: return bodyGlbBuffer

            val bodyJson = body.json
            val headJson = head.json

            // Find headPs node index in body
            val bodyNodes = bodyJson.optJSONArray("nodes") ?: return bodyGlbBuffer
            var headPsIndex = -1
            for (i in 0 until bodyNodes.length()) {
                if (bodyNodes.getJSONObject(i).optString("name") == "headPs") {
                    headPsIndex = i
                    break
                }
            }
            if (headPsIndex < 0) {
                Log.w(TAG, "No headPs node found in body GLB, skipping head merge")
                return bodyGlbBuffer
            }

            // Current counts for offset remapping
            val bodyMeshCount = bodyJson.optJSONArray("meshes")?.length() ?: 0
            val bodyMaterialCount = bodyJson.optJSONArray("materials")?.length() ?: 0
            val bodyAccessorCount = bodyJson.optJSONArray("accessors")?.length() ?: 0
            val bodyBufferViewCount = bodyJson.optJSONArray("bufferViews")?.length() ?: 0
            val bodyNodeCount = bodyNodes.length()
            val bodyTextureCount = bodyJson.optJSONArray("textures")?.length() ?: 0
            val bodyImageCount = bodyJson.optJSONArray("images")?.length() ?: 0
            val bodySamplerCount = bodyJson.optJSONArray("samplers")?.length() ?: 0

            // Binary offset for head data
            val bodyBinSize = body.bin.size
            val alignedBodyBinSize = align4(bodyBinSize)

            // --- Merge head accessors ---
            val headAccessors = headJson.optJSONArray("accessors") ?: JSONArray()
            var bodyAccessors = bodyJson.optJSONArray("accessors")
            if (bodyAccessors == null) {
                bodyAccessors = JSONArray()
                bodyJson.put("accessors", bodyAccessors)
            }
            for (i in 0 until headAccessors.length()) {
                val acc = JSONObject(headAccessors.getJSONObject(i).toString())
                if (acc.has("bufferView")) {
                    acc.put("bufferView", acc.getInt("bufferView") + bodyBufferViewCount)
                }
                bodyAccessors.put(acc)
            }

            // --- Merge head bufferViews ---
            val headBufferViews = headJson.optJSONArray("bufferViews") ?: JSONArray()
            var bodyBufferViews = bodyJson.optJSONArray("bufferViews")
            if (bodyBufferViews == null) {
                bodyBufferViews = JSONArray()
                bodyJson.put("bufferViews", bodyBufferViews)
            }
            for (i in 0 until headBufferViews.length()) {
                val bv = JSONObject(headBufferViews.getJSONObject(i).toString())
                val origOffset = bv.optInt("byteOffset", 0)
                bv.put("byteOffset", origOffset + alignedBodyBinSize)
                bv.put("buffer", 0)
                bodyBufferViews.put(bv)
            }

            // --- Merge head samplers ---
            val headSamplers = headJson.optJSONArray("samplers") ?: JSONArray()
            var bodySamplers = bodyJson.optJSONArray("samplers")
            if (bodySamplers == null) {
                bodySamplers = JSONArray()
                bodyJson.put("samplers", bodySamplers)
            }
            for (i in 0 until headSamplers.length()) {
                bodySamplers.put(JSONObject(headSamplers.getJSONObject(i).toString()))
            }

            // --- Merge head images ---
            val headImages = headJson.optJSONArray("images") ?: JSONArray()
            var bodyImages = bodyJson.optJSONArray("images")
            if (bodyImages == null) {
                bodyImages = JSONArray()
                bodyJson.put("images", bodyImages)
            }
            for (i in 0 until headImages.length()) {
                val img = JSONObject(headImages.getJSONObject(i).toString())
                // If image references a bufferView, remap it
                if (img.has("bufferView")) {
                    img.put("bufferView", img.getInt("bufferView") + bodyBufferViewCount)
                }
                bodyImages.put(img)
            }

            // --- Merge head textures ---
            val headTextures = headJson.optJSONArray("textures") ?: JSONArray()
            var bodyTextures = bodyJson.optJSONArray("textures")
            if (bodyTextures == null) {
                bodyTextures = JSONArray()
                bodyJson.put("textures", bodyTextures)
            }
            for (i in 0 until headTextures.length()) {
                val tex = JSONObject(headTextures.getJSONObject(i).toString())
                if (tex.has("source")) {
                    tex.put("source", tex.getInt("source") + bodyImageCount)
                }
                if (tex.has("sampler")) {
                    tex.put("sampler", tex.getInt("sampler") + bodySamplerCount)
                }
                bodyTextures.put(tex)
            }

            // --- Merge head materials ---
            val headMaterials = headJson.optJSONArray("materials") ?: JSONArray()
            var bodyMaterials = bodyJson.optJSONArray("materials")
            if (bodyMaterials == null) {
                bodyMaterials = JSONArray()
                bodyJson.put("materials", bodyMaterials)
            }
            for (i in 0 until headMaterials.length()) {
                val mat = JSONObject(headMaterials.getJSONObject(i).toString())
                // Remap texture indices in pbrMetallicRoughness.baseColorTexture
                remapTextureIndices(mat, bodyTextureCount)
                bodyMaterials.put(mat)
            }

            // --- Merge head meshes ---
            val headMeshes = headJson.optJSONArray("meshes") ?: JSONArray()
            var bodyMeshes = bodyJson.optJSONArray("meshes")
            if (bodyMeshes == null) {
                bodyMeshes = JSONArray()
                bodyJson.put("meshes", bodyMeshes)
            }
            for (i in 0 until headMeshes.length()) {
                val mesh = JSONObject(headMeshes.getJSONObject(i).toString())
                val prims = mesh.optJSONArray("primitives") ?: continue
                for (p in 0 until prims.length()) {
                    val prim = prims.getJSONObject(p)
                    // Remap accessor indices in attributes
                    val attrs = prim.optJSONObject("attributes")
                    if (attrs != null) {
                        val keys = attrs.keys().asSequence().toList()
                        for (key in keys) {
                            attrs.put(key, attrs.getInt(key) + bodyAccessorCount)
                        }
                    }
                    // Remap indices accessor
                    if (prim.has("indices")) {
                        prim.put("indices", prim.getInt("indices") + bodyAccessorCount)
                    }
                    // Remap material
                    if (prim.has("material")) {
                        prim.put("material", prim.getInt("material") + bodyMaterialCount)
                    }
                }
                bodyMeshes.put(mesh)
            }

            // --- Merge head nodes as children of headPs via a transform wrapper ---
            // The head GLB uses scene-unit coordinates (~50 units wide, Y 0–70).
            // The body GLB uses raw units (~36 wide, Y 16–76).
            // We add a wrapper node with scale + translation so the head sits
            // correctly on top of the body neck.
            val headNodes = headJson.optJSONArray("nodes") ?: JSONArray()
            val headChildIndices = JSONArray()

            // Collect top-level head scene nodes
            val headScene = headJson.optJSONArray("scenes")?.optJSONObject(0)
            val headSceneNodes = headScene?.optJSONArray("nodes")
            val topLevelHeadIndices = mutableSetOf<Int>()
            if (headSceneNodes != null) {
                for (s in 0 until headSceneNodes.length()) {
                    topLevelHeadIndices.add(headSceneNodes.getInt(s))
                }
            }

            // Add all head nodes to the body, tracking new indices
            for (i in 0 until headNodes.length()) {
                val node = JSONObject(headNodes.getJSONObject(i).toString())
                if (node.has("mesh")) {
                    node.put("mesh", node.getInt("mesh") + bodyMeshCount)
                }
                if (node.has("children")) {
                    val children = node.getJSONArray("children")
                    val newChildren = JSONArray()
                    for (c in 0 until children.length()) {
                        newChildren.put(children.getInt(c) + bodyNodeCount)
                    }
                    node.put("children", newChildren)
                }
                val newNodeIndex = bodyNodes.length()
                bodyNodes.put(node)

                if (topLevelHeadIndices.isEmpty() || i in topLevelHeadIndices) {
                    headChildIndices.put(newNodeIndex)
                }
            }

            // Create a wrapper node with scale + translation to position the head.
            // Head face Y range: ~0 to 50. At scale 0.4, that's 0 to 20.
            // Body neck top is at Y≈76. We translate to Y≈58 so head chin starts
            // at 58 and top is at ~78, sitting nicely above the body.
            val wrapperNode = JSONObject()
            wrapperNode.put("name", "headWrapper")
            wrapperNode.put("translation", JSONArray().apply {
                put(0.0)   // X
                put(HEAD_Y_TRANSLATE)   // Y — move head up to neck
                put(HEAD_Z_TRANSLATE)   // Z — push head forward to align with body front
            })
            wrapperNode.put("scale", JSONArray().apply {
                put(HEAD_SCALE)
                put(HEAD_SCALE)
                put(HEAD_SCALE)
            })
            wrapperNode.put("children", headChildIndices)
            val wrapperIndex = bodyNodes.length()
            bodyNodes.put(wrapperNode)

            // Add wrapper as child of headPs
            val headPsNode = bodyNodes.getJSONObject(headPsIndex)
            val existingChildren = headPsNode.optJSONArray("children") ?: JSONArray()
            existingChildren.put(wrapperIndex)
            headPsNode.put("children", existingChildren)

            // --- Update buffer size ---
            val totalBinSize = alignedBodyBinSize + head.bin.size
            val buffers = bodyJson.optJSONArray("buffers") ?: JSONArray().also { bodyJson.put("buffers", it) }
            if (buffers.length() > 0) {
                buffers.getJSONObject(0).put("byteLength", totalBinSize)
            }

            // Update JSON arrays
            bodyJson.put("nodes", bodyNodes)
            bodyJson.put("accessors", bodyAccessors)
            bodyJson.put("bufferViews", bodyBufferViews)
            bodyJson.put("meshes", bodyMeshes)
            bodyJson.put("materials", bodyMaterials)
            bodyJson.put("samplers", bodySamplers)
            bodyJson.put("images", bodyImages)
            bodyJson.put("textures", bodyTextures)

            val result = rebuildGlb(bodyJson, body.bin, head.bin)
            Log.d(TAG, "Merged head into body: ${headMeshes.length()} meshes, ${headNodes.length()} nodes added to headPs")
            return result

        } catch (e: Exception) {
            Log.e(TAG, "Failed to merge head into body GLB", e)
            return bodyGlbBuffer
        }
    }

    /**
     * Remap texture indices within a material's PBR and extension fields.
     */
    private fun remapTextureIndices(mat: JSONObject, textureOffset: Int) {
        // pbrMetallicRoughness
        val pbr = mat.optJSONObject("pbrMetallicRoughness")
        if (pbr != null) {
            remapTextureRef(pbr, "baseColorTexture", textureOffset)
            remapTextureRef(pbr, "metallicRoughnessTexture", textureOffset)
        }
        // Other texture references
        remapTextureRef(mat, "normalTexture", textureOffset)
        remapTextureRef(mat, "occlusionTexture", textureOffset)
        remapTextureRef(mat, "emissiveTexture", textureOffset)
    }

    private fun remapTextureRef(obj: JSONObject, key: String, offset: Int) {
        val texRef = obj.optJSONObject(key) ?: return
        if (texRef.has("index")) {
            texRef.put("index", texRef.getInt("index") + offset)
        }
    }

    private fun parseGlb(bytes: ByteArray): GlbChunks? {
        if (bytes.size < 20) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        if (buf.getInt(0) != GLB_MAGIC) return null
        val jsonChunkLen = buf.getInt(12)
        if (buf.getInt(16) != JSON_CHUNK) return null

        val jsonStr = String(bytes, 20, jsonChunkLen, Charsets.UTF_8).trimEnd('\u0000', ' ')
        val json = JSONObject(jsonStr)

        val binOffset = 20 + jsonChunkLen
        val bin = if (binOffset + 8 <= bytes.size) {
            val binChunkLen = ByteBuffer.wrap(bytes, binOffset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt()
            val binDataStart = binOffset + 8
            if (binDataStart + binChunkLen <= bytes.size) {
                bytes.copyOfRange(binDataStart, binDataStart + binChunkLen)
            } else {
                bytes.copyOfRange(binDataStart, bytes.size)
            }
        } else {
            ByteArray(0)
        }

        return GlbChunks(json, bin)
    }

    private data class GlbChunks(val json: JSONObject, val bin: ByteArray)

    private fun rebuildGlb(json: JSONObject, bodyBin: ByteArray, headBin: ByteArray): ByteBuffer {
        var jsonBytes = json.toString().toByteArray(Charsets.UTF_8)
        val jsonPadding = (4 - (jsonBytes.size % 4)) % 4
        if (jsonPadding > 0) {
            jsonBytes = jsonBytes + ByteArray(jsonPadding) { 0x20 }
        }

        val bodyBinPadding = (4 - (bodyBin.size % 4)) % 4
        val totalBinSize = bodyBin.size + bodyBinPadding + headBin.size
        val totalBinPadding = (4 - (totalBinSize % 4)) % 4
        val alignedTotalBinSize = totalBinSize + totalBinPadding

        val totalLength = 12 + 8 + jsonBytes.size + 8 + alignedTotalBinSize
        val out = ByteBuffer.allocateDirect(totalLength).order(ByteOrder.LITTLE_ENDIAN)

        // GLB header
        out.putInt(GLB_MAGIC)
        out.putInt(2)
        out.putInt(totalLength)

        // JSON chunk
        out.putInt(jsonBytes.size)
        out.putInt(JSON_CHUNK)
        out.put(jsonBytes)

        // BIN chunk
        out.putInt(alignedTotalBinSize)
        out.putInt(BIN_CHUNK)
        out.put(bodyBin)
        if (bodyBinPadding > 0) out.put(ByteArray(bodyBinPadding))
        out.put(headBin)
        if (totalBinPadding > 0) out.put(ByteArray(totalBinPadding))

        out.flip()
        return out
    }

    private fun align4(size: Int): Int = (size + 3) and 3.inv()
}
