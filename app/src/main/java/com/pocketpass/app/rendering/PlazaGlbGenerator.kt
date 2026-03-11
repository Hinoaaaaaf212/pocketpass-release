package com.pocketpass.app.rendering

import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Procedurally generates valid glTF 2.0 GLB ByteBuffers for plaza environment
 * elements using simple colored geometry — no external asset files needed.
 */
object PlazaGlbGenerator {

    private const val GLB_MAGIC = 0x46546C67   // "glTF"
    private const val JSON_CHUNK = 0x4E4F534A  // "JSON"
    private const val BIN_CHUNK = 0x004E4942   // "BIN\0"

    // --- Data classes ---

    data class MeshData(
        val positions: FloatArray,
        val normals: FloatArray,
        val indices: ShortArray,
        val min: FloatArray,
        val max: FloatArray
    )

    data class MeshEntry(
        val mesh: MeshData,
        val color: FloatArray,
        val translation: FloatArray? = null,
        val metallicFactor: Float = 0f,
        val roughnessFactor: Float = 0.9f
    )

    // --- Primitive builders ---

    /**
     * Axis-aligned box centered at origin with half-extents (hx, hy, hz).
     * 24 vertices (4 per face × 6 faces), 36 indices.
     */
    fun buildBox(hx: Float, hy: Float, hz: Float): MeshData {
        val positions = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val indices = mutableListOf<Short>()

        data class Face(val normal: FloatArray, val vertices: Array<FloatArray>)

        val faces = listOf(
            // +Z face
            Face(floatArrayOf(0f, 0f, 1f), arrayOf(
                floatArrayOf(-hx, -hy, hz), floatArrayOf(hx, -hy, hz),
                floatArrayOf(hx, hy, hz), floatArrayOf(-hx, hy, hz)
            )),
            // -Z face
            Face(floatArrayOf(0f, 0f, -1f), arrayOf(
                floatArrayOf(hx, -hy, -hz), floatArrayOf(-hx, -hy, -hz),
                floatArrayOf(-hx, hy, -hz), floatArrayOf(hx, hy, -hz)
            )),
            // +Y face
            Face(floatArrayOf(0f, 1f, 0f), arrayOf(
                floatArrayOf(-hx, hy, hz), floatArrayOf(hx, hy, hz),
                floatArrayOf(hx, hy, -hz), floatArrayOf(-hx, hy, -hz)
            )),
            // -Y face
            Face(floatArrayOf(0f, -1f, 0f), arrayOf(
                floatArrayOf(-hx, -hy, -hz), floatArrayOf(hx, -hy, -hz),
                floatArrayOf(hx, -hy, hz), floatArrayOf(-hx, -hy, hz)
            )),
            // +X face
            Face(floatArrayOf(1f, 0f, 0f), arrayOf(
                floatArrayOf(hx, -hy, hz), floatArrayOf(hx, -hy, -hz),
                floatArrayOf(hx, hy, -hz), floatArrayOf(hx, hy, hz)
            )),
            // -X face
            Face(floatArrayOf(-1f, 0f, 0f), arrayOf(
                floatArrayOf(-hx, -hy, -hz), floatArrayOf(-hx, -hy, hz),
                floatArrayOf(-hx, hy, hz), floatArrayOf(-hx, hy, -hz)
            ))
        )

        var baseVertex: Short = 0
        for (face in faces) {
            for (v in face.vertices) {
                positions.addAll(v.toList())
                normals.addAll(face.normal.toList())
            }
            indices.add(baseVertex)
            indices.add((baseVertex + 1).toShort())
            indices.add((baseVertex + 2).toShort())
            indices.add(baseVertex)
            indices.add((baseVertex + 2).toShort())
            indices.add((baseVertex + 3).toShort())
            baseVertex = (baseVertex + 4).toShort()
        }

        return MeshData(
            positions = positions.toFloatArray(),
            normals = normals.toFloatArray(),
            indices = indices.toShortArray(),
            min = floatArrayOf(-hx, -hy, -hz),
            max = floatArrayOf(hx, hy, hz)
        )
    }

    /**
     * Flat plane at Y=0, extending ±hx on X and ±hz on Z. Upward normal.
     */
    fun buildPlane(hx: Float, hz: Float): MeshData {
        val positions = floatArrayOf(
            -hx, 0f, hz,
            hx, 0f, hz,
            hx, 0f, -hz,
            -hx, 0f, -hz
        )
        val normals = floatArrayOf(
            0f, 1f, 0f,
            0f, 1f, 0f,
            0f, 1f, 0f,
            0f, 1f, 0f
        )
        val indices = shortArrayOf(0, 1, 2, 0, 2, 3)
        return MeshData(
            positions, normals, indices,
            min = floatArrayOf(-hx, 0f, -hz),
            max = floatArrayOf(hx, 0f, hz)
        )
    }

    /**
     * Octahedron (8 triangular faces) for tree canopies.
     * Each face has its own vertices with flat-shading normals.
     */
    fun buildOctahedron(radius: Float): MeshData {
        val top = floatArrayOf(0f, radius, 0f)
        val bottom = floatArrayOf(0f, -radius, 0f)
        val front = floatArrayOf(0f, 0f, radius)
        val back = floatArrayOf(0f, 0f, -radius)
        val right = floatArrayOf(radius, 0f, 0f)
        val left = floatArrayOf(-radius, 0f, 0f)

        val triangles = listOf(
            // Upper 4
            Triple(top, front, right),
            Triple(top, right, back),
            Triple(top, back, left),
            Triple(top, left, front),
            // Lower 4
            Triple(bottom, right, front),
            Triple(bottom, back, right),
            Triple(bottom, left, back),
            Triple(bottom, front, left)
        )

        val positions = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val indices = mutableListOf<Short>()

        var idx: Short = 0
        for ((a, b, c) in triangles) {
            // Compute face normal
            val abx = b[0] - a[0]; val aby = b[1] - a[1]; val abz = b[2] - a[2]
            val acx = c[0] - a[0]; val acy = c[1] - a[1]; val acz = c[2] - a[2]
            var nx = aby * acz - abz * acy
            var ny = abz * acx - abx * acz
            var nz = abx * acy - aby * acx
            val len = sqrt((nx * nx + ny * ny + nz * nz).toDouble()).toFloat()
            if (len > 0f) { nx /= len; ny /= len; nz /= len }

            for (v in listOf(a, b, c)) {
                positions.addAll(v.toList())
                normals.addAll(listOf(nx, ny, nz))
            }
            indices.add(idx); indices.add((idx + 1).toShort()); indices.add((idx + 2).toShort())
            idx = (idx + 3).toShort()
        }

        return MeshData(
            positions = positions.toFloatArray(),
            normals = normals.toFloatArray(),
            indices = indices.toShortArray(),
            min = floatArrayOf(-radius, -radius, -radius),
            max = floatArrayOf(radius, radius, radius)
        )
    }

    /**
     * Triangular prism for pitched roofs. Ridge along X axis.
     * width = full X extent, height = peak height, depth = full Z extent.
     * Base at Y=0, peak at Y=height.
     */
    fun buildPrism(width: Float, height: Float, depth: Float): MeshData {
        val hw = width / 2f
        val hd = depth / 2f

        // 5 unique positions (but we duplicate for flat-shading normals)
        val positions = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val indices = mutableListOf<Short>()
        var idx: Short = 0

        // Slope normal calculation
        val slopeLen = sqrt((height * height + hd * hd).toDouble()).toFloat()
        val slopeNy = hd / slopeLen
        val slopeNzFront = height / slopeLen
        val slopeNzBack = -height / slopeLen

        // Front slope (+Z side)
        val frontVerts = arrayOf(
            floatArrayOf(-hw, 0f, hd), floatArrayOf(hw, 0f, hd),
            floatArrayOf(hw, height, 0f), floatArrayOf(-hw, height, 0f)
        )
        val frontN = floatArrayOf(0f, slopeNy, slopeNzFront)
        for (v in frontVerts) { positions.addAll(v.toList()); normals.addAll(frontN.toList()) }
        indices.addAll(listOf(idx, (idx + 1).toShort(), (idx + 2).toShort(),
            idx, (idx + 2).toShort(), (idx + 3).toShort()))
        idx = (idx + 4).toShort()

        // Back slope (-Z side)
        val backVerts = arrayOf(
            floatArrayOf(hw, 0f, -hd), floatArrayOf(-hw, 0f, -hd),
            floatArrayOf(-hw, height, 0f), floatArrayOf(hw, height, 0f)
        )
        val backN = floatArrayOf(0f, slopeNy, slopeNzBack)
        for (v in backVerts) { positions.addAll(v.toList()); normals.addAll(backN.toList()) }
        indices.addAll(listOf(idx, (idx + 1).toShort(), (idx + 2).toShort(),
            idx, (idx + 2).toShort(), (idx + 3).toShort()))
        idx = (idx + 4).toShort()

        // Left triangle (-X)
        val leftVerts = arrayOf(
            floatArrayOf(-hw, 0f, -hd), floatArrayOf(-hw, 0f, hd), floatArrayOf(-hw, height, 0f)
        )
        val leftN = floatArrayOf(-1f, 0f, 0f)
        for (v in leftVerts) { positions.addAll(v.toList()); normals.addAll(leftN.toList()) }
        indices.addAll(listOf(idx, (idx + 1).toShort(), (idx + 2).toShort()))
        idx = (idx + 3).toShort()

        // Right triangle (+X)
        val rightVerts = arrayOf(
            floatArrayOf(hw, 0f, hd), floatArrayOf(hw, 0f, -hd), floatArrayOf(hw, height, 0f)
        )
        val rightN = floatArrayOf(1f, 0f, 0f)
        for (v in rightVerts) { positions.addAll(v.toList()); normals.addAll(rightN.toList()) }
        indices.addAll(listOf(idx, (idx + 1).toShort(), (idx + 2).toShort()))
        idx = (idx + 3).toShort()

        // Bottom face
        val bottomVerts = arrayOf(
            floatArrayOf(-hw, 0f, hd), floatArrayOf(-hw, 0f, -hd),
            floatArrayOf(hw, 0f, -hd), floatArrayOf(hw, 0f, hd)
        )
        val bottomN = floatArrayOf(0f, -1f, 0f)
        for (v in bottomVerts) { positions.addAll(v.toList()); normals.addAll(bottomN.toList()) }
        indices.addAll(listOf(idx, (idx + 1).toShort(), (idx + 2).toShort(),
            idx, (idx + 2).toShort(), (idx + 3).toShort()))

        // Compute bounds
        val allPos = positions.toFloatArray()
        val minB = floatArrayOf(-hw, 0f, -hd)
        val maxB = floatArrayOf(hw, height, hd)

        return MeshData(allPos, normals.toFloatArray(), indices.toShortArray(), minB, maxB)
    }

    // --- GLB assemblers ---

    /**
     * Build a GLB with a single mesh and single PBR material.
     */
    fun buildSingleMeshGlb(mesh: MeshData, color: FloatArray,
                           metallicFactor: Float = 0f, roughnessFactor: Float = 0.9f): ByteBuffer {
        return buildMultiMeshGlb(listOf(MeshEntry(mesh, color, null, metallicFactor, roughnessFactor)))
    }

    /**
     * Build a GLB with multiple meshes, each with its own material and optional translation.
     */
    fun buildMultiMeshGlb(entries: List<MeshEntry>): ByteBuffer {
        // Collect all binary data and track offsets
        val binParts = mutableListOf<ByteArray>()
        var binOffset = 0

        // Per-entry tracking
        data class AccessorInfo(
            val posAccessorIdx: Int, val normalAccessorIdx: Int, val indexAccessorIdx: Int,
            val meshIdx: Int, val materialIdx: Int
        )

        val accessors = JSONArray()
        val bufferViews = JSONArray()
        val meshes = JSONArray()
        val materials = JSONArray()
        val nodes = JSONArray()
        val rootChildren = JSONArray()

        for ((entryIdx, entry) in entries.withIndex()) {
            val mesh = entry.mesh
            val posBytes = floatArrayToBytes(mesh.positions)
            val normalBytes = floatArrayToBytes(mesh.normals)
            val indexBytes = shortArrayToBytes(mesh.indices)

            // Position buffer view
            val posBvIdx = bufferViews.length()
            bufferViews.put(JSONObject().apply {
                put("buffer", 0)
                put("byteOffset", binOffset)
                put("byteLength", posBytes.size)
                put("target", 34962) // ARRAY_BUFFER
            })
            binParts.add(posBytes)
            binOffset += posBytes.size
            binOffset = align4(binOffset)

            // Normal buffer view
            val normalBvIdx = bufferViews.length()
            bufferViews.put(JSONObject().apply {
                put("buffer", 0)
                put("byteOffset", binOffset)
                put("byteLength", normalBytes.size)
                put("target", 34962)
            })
            binParts.add(normalBytes)
            binOffset += normalBytes.size
            binOffset = align4(binOffset)

            // Index buffer view
            val indexBvIdx = bufferViews.length()
            bufferViews.put(JSONObject().apply {
                put("buffer", 0)
                put("byteOffset", binOffset)
                put("byteLength", indexBytes.size)
                put("target", 34963) // ELEMENT_ARRAY_BUFFER
            })
            binParts.add(indexBytes)
            binOffset += indexBytes.size
            binOffset = align4(binOffset)

            // Position accessor
            val posAccIdx = accessors.length()
            accessors.put(JSONObject().apply {
                put("bufferView", posBvIdx)
                put("componentType", 5126) // FLOAT
                put("count", mesh.positions.size / 3)
                put("type", "VEC3")
                put("min", floatArrayToJsonArray(mesh.min))
                put("max", floatArrayToJsonArray(mesh.max))
            })

            // Normal accessor
            val normalAccIdx = accessors.length()
            accessors.put(JSONObject().apply {
                put("bufferView", normalBvIdx)
                put("componentType", 5126)
                put("count", mesh.normals.size / 3)
                put("type", "VEC3")
            })

            // Index accessor
            val indexAccIdx = accessors.length()
            accessors.put(JSONObject().apply {
                put("bufferView", indexBvIdx)
                put("componentType", 5123) // UNSIGNED_SHORT
                put("count", mesh.indices.size)
                put("type", "SCALAR")
            })

            // Material
            val matIdx = materials.length()
            materials.put(JSONObject().apply {
                put("pbrMetallicRoughness", JSONObject().apply {
                    put("baseColorFactor", floatArrayToJsonArray(entry.color))
                    put("metallicFactor", entry.metallicFactor.toDouble())
                    put("roughnessFactor", entry.roughnessFactor.toDouble())
                })
            })

            // Mesh
            val meshIdx = meshes.length()
            meshes.put(JSONObject().apply {
                put("primitives", JSONArray().apply {
                    put(JSONObject().apply {
                        put("attributes", JSONObject().apply {
                            put("POSITION", posAccIdx)
                            put("NORMAL", normalAccIdx)
                        })
                        put("indices", indexAccIdx)
                        put("material", matIdx)
                    })
                })
            })

            // Node
            val nodeIdx = nodes.length()
            val node = JSONObject().apply {
                put("mesh", meshIdx)
            }
            if (entry.translation != null) {
                node.put("translation", floatArrayToJsonArray(entry.translation))
            }
            nodes.put(node)
            rootChildren.put(nodeIdx)
        }

        // If multiple entries, create a root parent node
        val sceneNodes: JSONArray
        if (entries.size > 1) {
            val rootIdx = nodes.length()
            nodes.put(JSONObject().apply {
                put("children", rootChildren)
            })
            sceneNodes = JSONArray().apply { put(rootIdx) }
        } else {
            sceneNodes = JSONArray().apply { put(0) }
        }

        // Build JSON
        val json = JSONObject().apply {
            put("asset", JSONObject().apply { put("version", "2.0") })
            put("scene", 0)
            put("scenes", JSONArray().apply {
                put(JSONObject().apply { put("nodes", sceneNodes) })
            })
            put("nodes", nodes)
            put("meshes", meshes)
            put("materials", materials)
            put("accessors", accessors)
            put("bufferViews", bufferViews)
            put("buffers", JSONArray().apply {
                put(JSONObject().apply { put("byteLength", binOffset) })
            })
        }

        // Assemble binary data
        val binData = ByteArray(binOffset)
        var writeOffset = 0
        for (part in binParts) {
            System.arraycopy(part, 0, binData, writeOffset, part.size)
            writeOffset += part.size
            writeOffset = align4(writeOffset)
        }

        return assembleGlb(json, binData)
    }

    // --- Public API: Plaza elements ---

    /** Large green ground plane */
    fun generateGround(): ByteBuffer {
        val mesh = buildPlane(12f, 8f)
        return buildSingleMeshGlb(mesh, floatArrayOf(0.30f, 0.68f, 0.25f, 1f))
    }

    /** Stone-colored walking path with darker borders */
    fun generatePath(): ByteBuffer {
        val mainPath = MeshEntry(
            mesh = buildPlane(6f, 0.6f),
            color = floatArrayOf(0.72f, 0.62f, 0.48f, 1f),
            translation = floatArrayOf(0f, 0.01f, 0f)
        )
        val borderLeft = MeshEntry(
            mesh = buildPlane(6f, 0.08f),
            color = floatArrayOf(0.50f, 0.42f, 0.32f, 1f),
            translation = floatArrayOf(0f, 0.015f, 0.65f)
        )
        val borderRight = MeshEntry(
            mesh = buildPlane(6f, 0.08f),
            color = floatArrayOf(0.50f, 0.42f, 0.32f, 1f),
            translation = floatArrayOf(0f, 0.015f, -0.65f)
        )
        return buildMultiMeshGlb(listOf(mainPath, borderLeft, borderRight))
    }

    /** Gate with two pillars, a crossbar, and a red banner */
    fun generateGate(): ByteBuffer {
        val pillarLeft = MeshEntry(
            mesh = buildBox(0.15f, 1.2f, 0.15f),
            color = floatArrayOf(0.65f, 0.65f, 0.65f, 1f),
            translation = floatArrayOf(-1.2f, 1.2f, 0f)
        )
        val pillarRight = MeshEntry(
            mesh = buildBox(0.15f, 1.2f, 0.15f),
            color = floatArrayOf(0.65f, 0.65f, 0.65f, 1f),
            translation = floatArrayOf(1.2f, 1.2f, 0f)
        )
        val crossbar = MeshEntry(
            mesh = buildBox(1.35f, 0.1f, 0.12f),
            color = floatArrayOf(0.60f, 0.60f, 0.60f, 1f),
            translation = floatArrayOf(0f, 2.3f, 0f)
        )
        val banner = MeshEntry(
            mesh = buildBox(0.8f, 0.3f, 0.02f),
            color = floatArrayOf(0.85f, 0.15f, 0.15f, 1f),
            translation = floatArrayOf(0f, 2.0f, 0.16f),
            roughnessFactor = 0.7f
        )
        return buildMultiMeshGlb(listOf(pillarLeft, pillarRight, crossbar, banner))
    }

    /** Building with box body and prism pitched roof */
    fun generateBuilding(
        wallColor: FloatArray, roofColor: FloatArray,
        w: Float, h: Float, d: Float, roofHeight: Float
    ): ByteBuffer {
        val body = MeshEntry(
            mesh = buildBox(w / 2f, h / 2f, d / 2f),
            color = wallColor,
            translation = floatArrayOf(0f, h / 2f, 0f)
        )
        val roof = MeshEntry(
            mesh = buildPrism(w + 0.2f, roofHeight, d + 0.2f),
            color = roofColor,
            translation = floatArrayOf(0f, h, 0f)
        )
        return buildMultiMeshGlb(listOf(body, roof))
    }

    /** Tree with thin box trunk and octahedron canopy */
    fun generateTree(): ByteBuffer {
        val trunk = MeshEntry(
            mesh = buildBox(0.06f, 0.4f, 0.06f),
            color = floatArrayOf(0.45f, 0.28f, 0.12f, 1f),
            translation = floatArrayOf(0f, 0.4f, 0f)
        )
        val canopy = MeshEntry(
            mesh = buildOctahedron(0.4f),
            color = floatArrayOf(0.20f, 0.60f, 0.18f, 1f),
            translation = floatArrayOf(0f, 1.1f, 0f)
        )
        return buildMultiMeshGlb(listOf(trunk, canopy))
    }

    // --- Helpers ---

    private fun assembleGlb(json: JSONObject, binData: ByteArray): ByteBuffer {
        var jsonBytes = json.toString().toByteArray(Charsets.UTF_8)
        val jsonPadding = (4 - (jsonBytes.size % 4)) % 4
        if (jsonPadding > 0) {
            jsonBytes = jsonBytes + ByteArray(jsonPadding) { 0x20 }
        }

        val binPadding = (4 - (binData.size % 4)) % 4
        val alignedBinSize = binData.size + binPadding

        val totalLength = 12 + 8 + jsonBytes.size + 8 + alignedBinSize
        val out = ByteBuffer.allocateDirect(totalLength).order(ByteOrder.LITTLE_ENDIAN)

        // GLB header
        out.putInt(GLB_MAGIC)
        out.putInt(2) // version
        out.putInt(totalLength)

        // JSON chunk
        out.putInt(jsonBytes.size)
        out.putInt(JSON_CHUNK)
        out.put(jsonBytes)

        // BIN chunk
        out.putInt(alignedBinSize)
        out.putInt(BIN_CHUNK)
        out.put(binData)
        if (binPadding > 0) out.put(ByteArray(binPadding))

        out.flip()
        return out
    }

    private fun floatArrayToBytes(arr: FloatArray): ByteArray {
        val buf = ByteBuffer.allocate(arr.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (f in arr) buf.putFloat(f)
        return buf.array()
    }

    private fun shortArrayToBytes(arr: ShortArray): ByteArray {
        val buf = ByteBuffer.allocate(arr.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in arr) buf.putShort(s)
        return buf.array()
    }

    private fun floatArrayToJsonArray(arr: FloatArray): JSONArray {
        val ja = JSONArray()
        for (f in arr) ja.put(f.toDouble())
        return ja
    }

    private fun align4(size: Int): Int = (size + 3) and 3.inv()
}
