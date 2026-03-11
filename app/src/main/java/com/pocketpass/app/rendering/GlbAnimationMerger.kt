package com.pocketpass.app.rendering

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Merges a standalone animation GLB into a body GLB at load time.
 *
 * Body GLBs (male_combined.glb, female_combined.glb) have 0 embedded animations.
 * Animation GLBs in models/animations/ each contain 1 animation targeting the same
 * node hierarchy (rootPs, rotatePs, bodyPs, handLPs, handRPs, headPs).
 *
 * SceneView/Filament can only play animations embedded in the same FilamentAsset,
 * so we merge animation data (accessors, bufferViews, animation entries) into the
 * body GLB JSON, concatenate binary chunks, and rebuild as a single GLB.
 *
 * The animation GLBs were exported from 3DS data using a Z-up coordinate system,
 * while the body GLBs use glTF's Y-up convention. Translation keyframes are
 * transformed during merge:
 *   body.X = -anim.Y
 *   body.Y =  anim.Z
 *   body.Z =  anim.X
 */
object GlbAnimationMerger {
    private const val TAG = "GlbAnimationMerger"

    private const val GLB_MAGIC = 0x46546C67   // "glTF"
    private const val JSON_CHUNK = 0x4E4F534A  // "JSON"
    private const val BIN_CHUNK = 0x004E4942   // "BIN\0"

    data class GlbChunks(val json: JSONObject, val bin: ByteArray)

    // Z offset applied to headPs translation keyframes during animation.
    // This pushes the head forward only while animated, keeping it centered at rest.
    private const val HEAD_Z_BIAS = 5.0f

    /**
     * Merge a single animation GLB into a body GLB, returning a new GLB as a direct ByteBuffer
     * ready for Filament/SceneView consumption.
     */
    fun mergeAnimationIntoBody(
        bodyGlbBytes: ByteArray, animGlbBytes: ByteArray,
        animationFileName: String? = null
    ): ByteBuffer {
        val body = parseGlb(bodyGlbBytes) ?: return wrapDirect(bodyGlbBytes)
        val anim = parseGlb(animGlbBytes) ?: return wrapDirect(bodyGlbBytes)

        val bodyJson = body.json
        val animJson = anim.json

        // Build node-name → index maps
        val bodyNodeMap = buildNodeNameMap(bodyJson)
        val animNodeMap = buildNodeNameMap(animJson)

        // Map anim node indices to body node indices by name
        val animToBodyNodeMap = mutableMapOf<Int, Int>()
        for ((name, animIdx) in animNodeMap) {
            val bodyIdx = bodyNodeMap[name]
            if (bodyIdx != null) {
                animToBodyNodeMap[animIdx] = bodyIdx
            }
        }

        if (animToBodyNodeMap.isEmpty()) {
            Log.w(TAG, "No matching nodes between body and animation, skipping merge")
            return wrapDirect(bodyGlbBytes)
        }

        // Make a mutable copy of anim binary for in-place coordinate transforms
        val animBin = anim.bin.copyOf()

        // Collect output accessor indices for translation channels so we can transform them
        val animAnimations = animJson.optJSONArray("animations") ?: JSONArray()
        val animAccessors = animJson.optJSONArray("accessors") ?: JSONArray()
        val animBufferViews = animJson.optJSONArray("bufferViews") ?: JSONArray()

        val translationOutputAccessors = mutableSetOf<Int>()
        for (a in 0 until animAnimations.length()) {
            val animation = animAnimations.getJSONObject(a)
            val channels = animation.optJSONArray("channels") ?: continue
            val samplers = animation.optJSONArray("samplers") ?: continue
            for (c in 0 until channels.length()) {
                val channel = channels.getJSONObject(c)
                val target = channel.getJSONObject("target")
                if (target.optString("path") == "translation") {
                    val samplerIdx = channel.getInt("sampler")
                    val sampler = samplers.getJSONObject(samplerIdx)
                    translationOutputAccessors.add(sampler.getInt("output"))
                }
            }
        }

        // Transform translation keyframes in-place: (X,Y,Z) → (-Y, Z, X)
        for (accIdx in translationOutputAccessors) {
            val accessor = animAccessors.getJSONObject(accIdx)
            val bvIdx = accessor.getInt("bufferView")
            val bv = animBufferViews.getJSONObject(bvIdx)
            val byteOffset = bv.optInt("byteOffset", 0) + accessor.optInt("byteOffset", 0)
            val count = accessor.getInt("count")
            val buf = ByteBuffer.wrap(animBin).order(ByteOrder.LITTLE_ENDIAN)

            for (k in 0 until count) {
                val off = byteOffset + k * 12 // VEC3 = 3 floats = 12 bytes
                val ax = buf.getFloat(off)
                val ay = buf.getFloat(off + 4)
                val az = buf.getFloat(off + 8)
                // Transform: body.X = -anim.Y, body.Y = anim.Z, body.Z = anim.X
                buf.putFloat(off, -ay)
                buf.putFloat(off + 4, az)
                buf.putFloat(off + 8, ax)
            }
        }

        // Offsets for remapping
        val bodyAccessors = bodyJson.optJSONArray("accessors") ?: JSONArray()
        val bodyBufferViews = bodyJson.optJSONArray("bufferViews") ?: JSONArray()
        val accessorOffset = bodyAccessors.length()
        val bufferViewOffset = bodyBufferViews.length()

        // Binary data offset (4-byte aligned)
        val bodyBinSize = body.bin.size
        val alignedBodyBinSize = align4(bodyBinSize)
        val binDataOffset = alignedBodyBinSize

        // Extra binary data for Z-biased headPs translation keyframes.
        // Will be appended after animBin in the final GLB.
        val alignedAnimBinSize = align4(animBin.size)
        val extraBinBaseOffset = alignedBodyBinSize + alignedAnimBinSize
        val extraBinData = mutableListOf<Byte>()

        // Copy anim accessors, remapping bufferView indices
        for (i in 0 until animAccessors.length()) {
            val accessor = JSONObject(animAccessors.getJSONObject(i).toString())
            if (accessor.has("bufferView")) {
                accessor.put("bufferView", accessor.getInt("bufferView") + bufferViewOffset)
            }
            bodyAccessors.put(accessor)
        }

        // Copy anim bufferViews, remapping byteOffset
        for (i in 0 until animBufferViews.length()) {
            val bv = JSONObject(animBufferViews.getJSONObject(i).toString())
            val origOffset = bv.optInt("byteOffset", 0)
            bv.put("byteOffset", origOffset + binDataOffset)
            bv.put("buffer", 0)
            bodyBufferViews.put(bv)
        }

        // Copy animation entries with remapped samplers and channels
        var bodyAnimations = bodyJson.optJSONArray("animations")
        if (bodyAnimations == null) {
            bodyAnimations = JSONArray()
            bodyJson.put("animations", bodyAnimations)
        }

        for (a in 0 until animAnimations.length()) {
            val animation = animAnimations.getJSONObject(a)
            val newAnim = JSONObject()

            if (animation.has("name")) {
                newAnim.put("name", animation.getString("name"))
            }

            // Remap samplers: input/output accessor indices
            val samplers = animation.optJSONArray("samplers") ?: JSONArray()
            val newSamplers = JSONArray()
            for (s in 0 until samplers.length()) {
                val sampler = JSONObject(samplers.getJSONObject(s).toString())
                sampler.put("input", sampler.getInt("input") + accessorOffset)
                sampler.put("output", sampler.getInt("output") + accessorOffset)
                newSamplers.put(sampler)
            }
            newAnim.put("samplers", newSamplers)

            // Remap channels: target.node via animToBodyNodeMap
            val channels = animation.optJSONArray("channels") ?: JSONArray()
            val newChannels = JSONArray()
            for (c in 0 until channels.length()) {
                val channel = JSONObject(channels.getJSONObject(c).toString())
                val target = channel.getJSONObject("target")
                if (target.has("node")) {
                    val animNodeIdx = target.getInt("node")
                    val bodyNodeIdx = animToBodyNodeMap[animNodeIdx]
                    if (bodyNodeIdx != null) {
                        target.put("node", bodyNodeIdx)
                        channel.put("target", target)
                        newChannels.put(channel)
                    }
                }
            }
            // Duplicate bodyPs translation and rotation channels to headPs so the
            // head moves in sync with the body (they are siblings under rotatePs,
            // so bodyPs transforms don't propagate to headPs otherwise).
            // For non-idle animations, translation gets a Z bias (HEAD_Z_BIAS) to
            // push the head forward. Idle (mii_hand_wait) keeps Z=0.
            val isIdle = animationFileName?.contains("hand_wait", ignoreCase = true) == true
            val bodyPsIdx = bodyNodeMap["bodyPs"]
            val headPsIdx = bodyNodeMap["headPs"]
            if (bodyPsIdx != null && headPsIdx != null) {
                val channelCount = newChannels.length()
                for (c in 0 until channelCount) {
                    val ch = newChannels.getJSONObject(c)
                    val t = ch.getJSONObject("target")
                    if (t.getInt("node") != bodyPsIdx) continue
                    val path = t.getString("path")

                    if (path == "rotation") {
                        // Rotation: share same sampler, just retarget node
                        val clone = JSONObject(ch.toString())
                        clone.getJSONObject("target").put("node", headPsIdx)
                        newChannels.put(clone)
                    } else if (path == "translation") {
                        if (isIdle) {
                            // Idle: share same sampler, no Z bias
                            val clone = JSONObject(ch.toString())
                            clone.getJSONObject("target").put("node", headPsIdx)
                            newChannels.put(clone)
                            continue
                        }
                        // Non-idle: create Z-biased copy of the output data
                        val samplerIdx = ch.getInt("sampler")
                        val sampler = newSamplers.getJSONObject(samplerIdx)
                        val outputAccIdx = sampler.getInt("output")
                        val inputAccIdx = sampler.getInt("input")
                        val outputAcc = bodyAccessors.getJSONObject(outputAccIdx)
                        val bvIdx = outputAcc.getInt("bufferView")
                        val bv = bodyBufferViews.getJSONObject(bvIdx)

                        // Read the original translation keyframe data
                        val bvOffset = bv.optInt("byteOffset", 0)
                        val accOffset = outputAcc.optInt("byteOffset", 0)
                        val count = outputAcc.getInt("count")
                        val dataSize = count * 12 // VEC3 = 3 floats = 12 bytes

                        // The data lives in animBin; bvOffset is relative to the
                        // start of the merged binary. Subtract binDataOffset to get
                        // the position within animBin.
                        val animBinStart = bvOffset - binDataOffset + accOffset
                        val biasedData = ByteArray(dataSize)
                        System.arraycopy(animBin, animBinStart, biasedData, 0, dataSize)
                        val biasedBuf = ByteBuffer.wrap(biasedData).order(ByteOrder.LITTLE_ENDIAN)
                        for (k in 0 until count) {
                            val off = k * 12 + 8 // Z component
                            biasedBuf.putFloat(off, biasedBuf.getFloat(off) + HEAD_Z_BIAS)
                        }

                        // Append biased data to extraBin
                        val extraOffset = extraBinData.size
                        extraBinData.addAll(biasedData.toList())
                        // Pad to 4-byte alignment
                        while (extraBinData.size % 4 != 0) extraBinData.add(0)

                        // Create new bufferView pointing to extra data region
                        val newBvIdx = bodyBufferViews.length()
                        val newBv = JSONObject()
                        newBv.put("buffer", 0)
                        newBv.put("byteOffset", extraBinBaseOffset + extraOffset)
                        newBv.put("byteLength", dataSize)
                        bodyBufferViews.put(newBv)

                        // Create new accessor
                        val newAccIdx = bodyAccessors.length()
                        val newAcc = JSONObject(outputAcc.toString())
                        newAcc.put("bufferView", newBvIdx)
                        newAcc.remove("byteOffset") // data starts at bufferView start
                        bodyAccessors.put(newAcc)

                        // Create new sampler sharing the same input (timestamps)
                        val newSamplerIdx = newSamplers.length()
                        val newSampler = JSONObject(sampler.toString())
                        newSampler.put("output", newAccIdx)
                        newSamplers.put(newSampler)

                        // Create channel targeting headPs with new sampler
                        val clone = JSONObject(ch.toString())
                        clone.getJSONObject("target").put("node", headPsIdx)
                        clone.put("sampler", newSamplerIdx)
                        newChannels.put(clone)
                    }
                }
            }

            newAnim.put("channels", newChannels)

            if (newChannels.length() > 0) {
                bodyAnimations.put(newAnim)
            }
        }

        // Update JSON arrays
        bodyJson.put("accessors", bodyAccessors)
        bodyJson.put("bufferViews", bodyBufferViews)

        // Update buffer size (body + anim + extra Z-biased data)
        val extraBytes = extraBinData.toByteArray()
        val totalBinSize = alignedBodyBinSize + alignedAnimBinSize + extraBytes.size
        val buffers = bodyJson.optJSONArray("buffers") ?: JSONArray().also { bodyJson.put("buffers", it) }
        if (buffers.length() > 0) {
            buffers.getJSONObject(0).put("byteLength", totalBinSize)
        }

        // Rebuild GLB with transformed anim binary + extra head translation data
        return rebuildGlb(bodyJson, body.bin, animBin, extraBytes)
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

    private fun buildNodeNameMap(json: JSONObject): Map<String, Int> {
        val nodes = json.optJSONArray("nodes") ?: return emptyMap()
        val map = mutableMapOf<String, Int>()
        for (i in 0 until nodes.length()) {
            val node = nodes.getJSONObject(i)
            val name = node.optString("name", "")
            if (name.isNotEmpty()) {
                map[name] = i
            }
        }
        return map
    }

    private fun rebuildGlb(
        json: JSONObject, bodyBin: ByteArray, animBin: ByteArray,
        extraBin: ByteArray = ByteArray(0)
    ): ByteBuffer {
        var jsonBytes = json.toString().toByteArray(Charsets.UTF_8)
        val jsonPadding = (4 - (jsonBytes.size % 4)) % 4
        if (jsonPadding > 0) {
            jsonBytes = jsonBytes + ByteArray(jsonPadding) { 0x20 }
        }

        val bodyBinPadding = (4 - (bodyBin.size % 4)) % 4
        val animBinPadding = (4 - (animBin.size % 4)) % 4
        val totalBinSize = bodyBin.size + bodyBinPadding + animBin.size + animBinPadding + extraBin.size
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
        out.put(animBin)
        if (animBinPadding > 0) out.put(ByteArray(animBinPadding))
        if (extraBin.isNotEmpty()) out.put(extraBin)
        if (totalBinPadding > 0) out.put(ByteArray(totalBinPadding))

        out.flip()
        Log.d(TAG, "Merged GLB: $totalLength bytes (json=${jsonBytes.size}, bin=$alignedTotalBinSize)")
        return out
    }

    private fun align4(size: Int): Int = (size + 3) and 3.inv()

    /**
     * Merge multiple animation GLBs into a body GLB, producing a single GLB with
     * one animation entry per input. Each call iteratively merges one animation
     * at a time using [mergeAnimationIntoBody], building up the animations array.
     *
     * @param bodyGlbBytes The raw body GLB bytes
     * @param animations List of (animGlbBytes, fileName) pairs
     * @return A direct ByteBuffer with animationCount == animations.size
     */
    fun mergeMultipleAnimationsIntoBody(
        bodyGlbBytes: ByteArray,
        animations: List<Pair<ByteArray, String>>
    ): ByteBuffer {
        if (animations.isEmpty()) return wrapDirect(bodyGlbBytes)

        var currentBuffer = mergeAnimationIntoBody(
            bodyGlbBytes, animations[0].first, animations[0].second
        )

        for (i in 1 until animations.size) {
            val (animBytes, fileName) = animations[i]
            // Extract bytes from the current direct ByteBuffer
            val currentBytes = ByteArray(currentBuffer.remaining())
            val pos = currentBuffer.position()
            currentBuffer.get(currentBytes)
            currentBuffer.position(pos)

            currentBuffer = mergeAnimationIntoBody(currentBytes, animBytes, fileName)
        }

        Log.d(TAG, "Merged ${animations.size} animations into body GLB")
        return currentBuffer
    }

    private fun wrapDirect(bytes: ByteArray): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(bytes)
        buf.flip()
        return buf
    }
}
