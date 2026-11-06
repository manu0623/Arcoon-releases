package com.nendo.argosy.data.media

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

private const val SCENE_WIDTH = 800
private const val SCENE_HEIGHT = 480

private const val ROTATION_WIDTH = 400
private const val ROTATION_HEIGHT = 240
private const val ROTATION_FRAME_COUNT = 24

/** The banner camera every 3DS Home Menu banner is authored for: 5:3, 30 degree vertical FOV. */
private const val SCENE_EYE_Y = 1.0f
private const val SCENE_EYE_Z = 44.786f
private const val SCENE_FOV_Y_DEGREES = 30.0
private const val SCENE_ASPECT = 5.0 / 3.0

/**
 * Static banners carry no rotation keys - the Home Menu spins the banner's root node itself. The
 * still therefore has to put that rotation in: +90 degrees about Z stands the scene upright, and
 * the sampler flips V because the container's UVs assume the GL origin at the bottom-left.
 */
private const val SCENE_Z_ROTATION = Math.PI / 2.0
private const val SCENE_YAW = 0.0

private const val SCENE_TEXTURE_FLIP_V = true
private const val SCENE_ALPHA_CUTOFF = 8

private const val MAX_VERTEX_COUNT = 200_000
private const val MAX_PRIMITIVE_SETS = 64
private const val MAX_INDEX_STREAM = 30_000

private const val DATA_SECTION_FALLBACK = 0x14
private const val OBJECT_SCAN_LIMIT = 0xC8
private const val MATERIAL_BINDING_SCAN_LIMIT = 0x400
private const val MAGIC_INTERLEAVE = 0x40000002
private const val MAGIC_TEXTURE_BINDING = 0x80000000.toInt()

private const val CMDL_MESH_COUNT = 180
private const val CMDL_MESH_LIST = 184
private const val CMDL_MATERIAL_COUNT = 188
private const val CMDL_MATERIAL_LIST = 192
private const val CMDL_SHAPE_COUNT = 196
private const val CMDL_SHAPE_LIST = 200

private const val INTERLEAVE_STRIDE = 36
private const val INTERLEAVE_DATA_SIZE = 20
private const val INTERLEAVE_DATA_REF = 24
private const val INTERLEAVE_SUB_COUNT = 40
private const val INTERLEAVE_SUB_LIST = 44

private const val ATTRIBUTE_USAGE = 4
private const val ATTRIBUTE_FORMAT = 36
private const val ATTRIBUTE_COMPONENTS = 40
private const val ATTRIBUTE_SCALE = 44
private const val ATTRIBUTE_OFFSET = 48

private const val USAGE_POSITION = 0
private const val USAGE_TEXCOORD = 4

private const val FORMAT_FLOAT = 0x1406
private const val FORMAT_BYTE = 0x1400
private const val FORMAT_UNSIGNED_BYTE = 0x1401
private const val FORMAT_SHORT = 0x1402

/**
 * Composites a 3DS banner CGFX into the image the Home Menu actually shows.
 *
 * A banner is not a picture: it is a small 3D scene - a background card plus the logo, subtitle
 * band and effect planes - each with its own material and texture. Picking the largest decodable
 * texture can only ever yield one of those planes, which is the flat, mirrored logo a plain texture
 * dump produces. This walks the scene the container describes (DATA -> DICT[0] CMDL for materials,
 * meshes and shapes, DICT[1] TXOB for textures), reads each shape's interleaved vertex stream and
 * its index stream, and rasterises the triangles through the banner's own camera with a z-buffer
 * and alpha testing.
 *
 * Returns null when the container holds no renderable scene, so the caller can fall back to the
 * single-image banner template that bannertool writes.
 */
internal object CgfxBannerScene {

    fun render(cgfx: ByteArray): ThreeDsBannerRenderResult.Success? {
        val scene = parseScene(cgfx) ?: return null
        val argb = rasterise(scene, SCENE_WIDTH, SCENE_HEIGHT, SCENE_YAW) ?: return null.also { println("DBG rasterise null") }
        if (!hasCoverage(argb)) { println("DBG no coverage"); return null }
        println("DBG render drew ok")
        return ThreeDsBannerRenderResult.Success(SCENE_WIDTH, SCENE_HEIGHT, argb)
    }

    /**
     * The same scene spun through a full turn - what makes the banner read as 3D. Frames are
     * rendered at [width]x[height] because they are delivered as one animation, not as a still.
     */
    fun renderRotation(
        cgfx: ByteArray,
        frameCount: Int = ROTATION_FRAME_COUNT,
        width: Int = ROTATION_WIDTH,
        height: Int = ROTATION_HEIGHT,
    ): BannerRotation? {
        if (frameCount < 2) return null
        val scene = parseScene(cgfx) ?: return null
        val frames = ArrayList<IntArray>(frameCount)
        for (index in 0 until frameCount) {
            val yaw = 2.0 * Math.PI * index / frameCount
            frames.add(rasterise(scene, width, height, yaw) ?: return null)
        }
        if (!hasCoverage(frames.first())) return null
        return BannerRotation(width, height, frames)
    }

    private fun parseScene(cgfx: ByteArray): List<SceneMesh>? {
        println("DBG parseScene enter size=${cgfx.size}")
        if (cgfx.size < 0x18 || !hasAscii(cgfx, 0, "CGFX")) { println("DBG magic_fail"); return null }
        val dataSection = dataSectionOffset(cgfx) ?: return null.also { println("DBG dataSection_null") }
        val textures = readTextures(cgfx, dataSection)
        if (textures.isEmpty()) { println("DBG textures empty"); return null }
        val commandList = readCommandList(cgfx, dataSection, textures) ?: return null.also { println("DBG commandList_null") }
        val meshes = readMeshes(cgfx, commandList, textures)
        println("DBG meshes=${meshes.size}")
        return meshes.filter { it.texture != null }.takeIf { it.isNotEmpty() }
    }

    // --- container walking -------------------------------------------------------------------

    private fun dataSectionOffset(cgfx: ByteArray): Int? {
        val headerSize = readU16(cgfx, 0x06)
        if (headerSize >= 0x14 && hasAscii(cgfx, headerSize, "DATA")) return headerSize
        return findAscii(cgfx, "DATA", 0, minOf(cgfx.size, 0x400)) ?: DATA_SECTION_FALLBACK
            .takeIf { hasAscii(cgfx, it, "DATA") }
    }

    private fun dictSection(cgfx: ByteArray, dataSection: Int, index: Int): Pair<Int, Int>? {
        val field = dataSection + 8 + index * 8
        val count = readU32(cgfx, field)
        if (count <= 0) return null
        val target = deref(cgfx, field + 4) ?: return null
        if (!hasAscii(cgfx, target, "DICT")) return null
        return target to count
    }

    private fun dictEntries(cgfx: ByteArray, section: Int, count: Int): List<Pair<Int?, Int?>> {
        val entries = ArrayList<Pair<Int?, Int?>>(count)
        for (index in 0 until count) {
            val entry = section + 12 + (index + 1) * 16
            if (entry + 16 > cgfx.size) break
            entries.add(deref(cgfx, entry + 8) to deref(cgfx, entry + 12))
        }
        return entries
    }

    private fun readTextures(cgfx: ByteArray, dataSection: Int): Map<Int, SceneTexture> {
        val (section, count) = dictSection(cgfx, dataSection, 1) ?: return emptyMap()
        val textures = LinkedHashMap<Int, SceneTexture>()
        for ((nameRef, objectRef) in dictEntries(cgfx, section, count)) {
            val objectOffset = objectRef ?: continue
            if (objectOffset + 0x50 > cgfx.size) continue
            val texture = readTexture(cgfx, objectOffset, nameRef) ?: continue
            textures[objectOffset] = texture
        }
        return textures
    }

    private fun readTexture(cgfx: ByteArray, objectOffset: Int, nameRef: Int?): SceneTexture? {
        for (layout in TEXTURE_LAYOUTS) {
            if (objectOffset + layout.dataOffsetField + 4 > cgfx.size) continue
            val width = readU32(cgfx, objectOffset + layout.widthField)
            val height = readU32(cgfx, objectOffset + layout.heightField)
            if (width !in 8..2048 || height !in 8..2048) continue
            if (width % 8 != 0 || height % 8 != 0) continue
            val format = readU32(cgfx, objectOffset + layout.formatField)
            val expected = CgfxTextureDecoder.expectedByteSize(format, width, height) ?: continue
            val declaredSize = readU32(cgfx, objectOffset + layout.sizeField)
            if (declaredSize < expected) continue
            val rawOffset = readU32(cgfx, objectOffset + layout.dataOffsetField)
            for (base in intArrayOf(objectOffset + layout.dataOffsetField, objectOffset, 0)) {
                val absolute = base.toLong() + rawOffset.toLong()
                if (absolute < 0L || absolute + declaredSize > cgfx.size) continue
                val dataOffset = absolute.toInt()
                val pixels = CgfxTextureDecoder.decodePixels(
                    cgfx.copyOfRange(dataOffset, dataOffset + declaredSize),
                    width,
                    height,
                    format,
                ) ?: return null
                return SceneTexture(
                    name = nameRef?.let { readCString(cgfx, it) }.orEmpty(),
                    width = width,
                    height = height,
                    pixels = pixels,
                )
            }
        }
        return null
    }

    private fun readCommandList(
        cgfx: ByteArray,
        dataSection: Int,
        textures: Map<Int, SceneTexture>,
    ): CommandList? {
        val (section, count) = dictSection(cgfx, dataSection, 0) ?: return null
        var cmdl: Int? = null
        for ((_, objectRef) in dictEntries(cgfx, section, count)) {
            val candidate = objectRef ?: continue
            if (hasAscii(cgfx, candidate, "CMDL") || hasAscii(cgfx, candidate + 4, "CMDL")) {
                cmdl = candidate
                break
            }
        }
        val cmdlOffset = cmdl ?: return null

        val materialCount = readU32(cgfx, cmdlOffset + CMDL_MATERIAL_COUNT)
        if (materialCount !in 1..256) return null
        val materialSection = cmdlOffset + CMDL_MATERIAL_LIST +
            readU32(cgfx, cmdlOffset + CMDL_MATERIAL_LIST)
        val materials = ArrayList<SceneMaterial>(materialCount)
        for ((nameRef, objectRef) in dictEntries(cgfx, materialSection, materialCount)) {
            val objectOffset = objectRef ?: continue
            val name = nameRef?.let { readCString(cgfx, it) }.orEmpty()
            val bound = findBoundTexture(cgfx, objectOffset, textures)
            materials.add(SceneMaterial(name, bound ?: NAME_BINDINGS[name]))
        }
        return CommandList(materials, cmdlOffset)
    }

    private fun findBoundTexture(
        cgfx: ByteArray,
        materialOffset: Int,
        textures: Map<Int, SceneTexture>,
    ): String? {
        var offset = 0
        while (offset < MATERIAL_BINDING_SCAN_LIMIT) {
            if (readU32(cgfx, materialOffset + offset) == MAGIC_TEXTURE_BINDING) {
                val target = deref(cgfx, materialOffset + offset + 8)
                if (target != null && textures.containsKey(target)) {
                    return textures[target]?.name
                }
            }
            offset += 4
        }
        return null
    }

    private fun readMeshes(
        cgfx: ByteArray,
        commandList: CommandList?,
        textures: Map<Int, SceneTexture>,
    ): List<SceneMesh> {
        if (commandList == null) return emptyList()
        val materials = commandList.materials
        val cmdl = commandList.offset
        val meshCount = readU32(cgfx, cmdl + CMDL_MESH_COUNT)
        if (meshCount !in 1..256) return emptyList()
        val meshList = cmdl + CMDL_MESH_LIST + readU32(cgfx, cmdl + CMDL_MESH_LIST)
        val shapeCount = readU32(cgfx, cmdl + CMDL_SHAPE_COUNT)
        if (shapeCount !in 1..256) return emptyList()
        val shapeList = cmdl + CMDL_SHAPE_LIST + readU32(cgfx, cmdl + CMDL_SHAPE_LIST)

        val shapes = IntArray(shapeCount) { index ->
            val field = shapeList + index * 4
            field + readU32(cgfx, field) - 4
        }

        val meshes = ArrayList<SceneMesh>(meshCount)
        for (index in 0 until meshCount) {
            val field = meshList + index * 4
            val objectOffset = field + readU32(cgfx, field)
            if (objectOffset + 32 > cgfx.size) continue
            val shapeIndex = readU32(cgfx, objectOffset + 24)
            val materialIndex = readU32(cgfx, objectOffset + 28)
            if (shapeIndex !in shapes.indices) continue
            val vertices = readVertices(cgfx, shapes[shapeIndex]) ?: continue
            if (vertices.size < 3) continue
            val triangles = readTriangles(cgfx, shapes[shapeIndex], vertices.size)
            if (triangles.isEmpty()) continue
            val material = materials.getOrNull(materialIndex)
            val texture = material?.texture?.let { name ->
                textures.values.firstOrNull { it.name == name }
            }
            meshes.add(SceneMesh(vertices, triangles, texture))
        }
        return meshes
    }

    private fun readVertices(cgfx: ByteArray, shapeOffset: Int): List<SceneVertex>? {
        for (interleave in interleaveCandidates(cgfx, shapeOffset)) {
            val stride = readU32(cgfx, interleave + INTERLEAVE_STRIDE)
            val dataSize = readU32(cgfx, interleave + INTERLEAVE_DATA_SIZE)
            val dataOffset = deref(cgfx, interleave + INTERLEAVE_DATA_REF) ?: continue
            val subCount = readU32(cgfx, interleave + INTERLEAVE_SUB_COUNT)
            val subList = deref(cgfx, interleave + INTERLEAVE_SUB_LIST) ?: continue
            if (stride <= 0 || dataSize <= 0 || subCount !in 1..8) continue
            val vertexCount = dataSize / stride
            if (vertexCount <= 0 || vertexCount > MAX_VERTEX_COUNT) continue
            if (dataOffset + vertexCount * stride > cgfx.size) continue

            val attributes = HashMap<Int, Attribute>()
            for (sub in 0 until subCount) {
                val vertexSpec = deref(cgfx, subList + sub * 4) ?: continue
                if (vertexSpec + 52 > cgfx.size) continue
                attributes[readU32(cgfx, vertexSpec + ATTRIBUTE_USAGE)] = Attribute(
                    format = readU32(cgfx, vertexSpec + ATTRIBUTE_FORMAT),
                    components = readU32(cgfx, vertexSpec + ATTRIBUTE_COMPONENTS),
                    scale = readFloat(cgfx, vertexSpec + ATTRIBUTE_SCALE),
                    offset = readU32(cgfx, vertexSpec + ATTRIBUTE_OFFSET),
                )
            }
            if (!attributes.containsKey(USAGE_POSITION)) continue

            val vertices = ArrayList<SceneVertex>(vertexCount)
            for (index in 0 until vertexCount) {
                val base = dataOffset + index * stride
                val position = readAttribute(cgfx, base, attributes[USAGE_POSITION], 3)
                val texCoord = readAttribute(cgfx, base, attributes[USAGE_TEXCOORD], 2)
                vertices.add(
                    SceneVertex(
                        x = position[0],
                        y = position[1],
                        z = position[2],
                        u = texCoord[0],
                        v = texCoord[1],
                    )
                )
            }
            return vertices
        }
        return null
    }

    private fun readAttribute(
        cgfx: ByteArray,
        vertexBase: Int,
        attribute: Attribute?,
        wanted: Int,
    ): FloatArray {
        val values = FloatArray(3)
        if (attribute == null) return values
        val components = min(attribute.components, wanted)
        for (component in 0 until components) {
            val offset = vertexBase + attribute.offset
            val raw = when (attribute.format) {
                FORMAT_FLOAT -> readFloat(cgfx, offset + component * 4)
                FORMAT_BYTE -> if (offset + component < cgfx.size) cgfx[offset + component].toFloat() else 0f
                FORMAT_UNSIGNED_BYTE ->
                    if (offset + component < cgfx.size) (cgfx[offset + component].toInt() and 0xFF).toFloat() else 0f
                FORMAT_SHORT ->
                    if (offset + component * 2 + 2 <= cgfx.size) readS16(cgfx, offset + component * 2).toFloat() else 0f
                else -> 0f
            }
            values[component] = raw * attribute.scale
        }
        return values
    }

    private fun readTriangles(cgfx: ByteArray, shapeOffset: Int, vertexCount: Int): List<IntArray> {
        var best = emptyList<IntArray>()
        for (primitiveSet in primitiveSetCandidates(cgfx, shapeOffset)) {
            if (primitiveSet + 20 > cgfx.size) continue
            val primitiveCount = readU32(cgfx, primitiveSet + 12)
            val primitiveList = deref(cgfx, primitiveSet + 16) ?: continue
            if (primitiveCount !in 1..MAX_PRIMITIVE_SETS) continue
            val triangles = ArrayList<IntArray>()
            val used = HashSet<Int>()
            for (index in 0 until primitiveCount) {
                val primitive = deref(cgfx, primitiveList + index * 4) ?: continue
                val stream = readIndexStream(cgfx, primitive, 64) ?: continue
                if (!used.add(stream.dataOffset)) continue
                val indices = readIndices(cgfx, stream)
                if (indices.isEmpty() || indices.max() >= vertexCount) continue
                appendTriangles(indices, stream.mode, triangles)
            }
            if (triangles.size > best.size) best = triangles
        }
        if (best.isNotEmpty()) return best

        // Some effects meshes reference their index stream outside the primitive-set table; the
        // file holds only a handful of streams, so a shape with no table falls back to the streams
        // whose indices fit this shape's vertex count.
        val triangles = ArrayList<IntArray>()
        val used = HashSet<Int>()
        for (stream in allIndexStreams(cgfx)) {
            if (!used.add(stream.dataOffset) || stream.size < 3) continue
            val indices = readIndices(cgfx, stream)
            if (indices.isEmpty() || indices.max() >= vertexCount || indices.size % 3 != 0) continue
            appendTriangles(indices, stream.mode, triangles)
        }
        return triangles
    }

    private fun appendTriangles(indices: List<Int>, mode: Int, into: MutableList<IntArray>) {
        when (mode) {
            0 -> {
                var index = 0
                while (index + 2 < indices.size) {
                    into.add(intArrayOf(indices[index], indices[index + 1], indices[index + 2]))
                    index += 3
                }
            }
            1 -> {
                for (index in 0 until indices.size - 2) {
                    val a = indices[index]
                    val b = indices[index + 1]
                    val c = indices[index + 2]
                    into.add(if (index % 2 == 0) intArrayOf(a, b, c) else intArrayOf(b, a, c))
                }
            }
            2 -> {
                for (index in 1 until indices.size - 1) {
                    into.add(intArrayOf(indices[0], indices[index], indices[index + 1]))
                }
            }
        }
    }

    private fun primitiveSetCandidates(cgfx: ByteArray, shapeOffset: Int): List<Int> {
        val candidates = LinkedHashSet<Int>()
        var offset = 0
        while (offset < OBJECT_SCAN_LIMIT) {
            val list = deref(cgfx, shapeOffset + offset)
            val count = readU32(cgfx, shapeOffset + offset)
            if (list != null && count in 1..MAX_PRIMITIVE_SETS) {
                for (index in 0 until count) {
                    deref(cgfx, list + index * 4)?.let { candidates.add(it) }
                }
            }
            offset += 4
        }
        offset = 0
        while (offset < OBJECT_SCAN_LIMIT) {
            val target = deref(cgfx, shapeOffset + offset)
            if (target != null && target + 20 <= cgfx.size) {
                val count = readU32(cgfx, target + 12)
                if (count in 1..MAX_PRIMITIVE_SETS && deref(cgfx, target + 16) != null) {
                    candidates.add(target)
                }
            }
            offset += 4
        }
        return candidates.toList()
    }

    private fun interleaveCandidates(cgfx: ByteArray, shapeOffset: Int): List<Int> {
        val candidates = LinkedHashSet<Int>()
        var offset = 0
        while (offset < OBJECT_SCAN_LIMIT) {
            val target = deref(cgfx, shapeOffset + offset)
            if (target != null && readU32(cgfx, target) == MAGIC_INTERLEAVE) candidates.add(target)
            val count = readU32(cgfx, shapeOffset + offset)
            val list = deref(cgfx, shapeOffset + offset)
            if (list != null && count in 1..8) {
                val entry = deref(cgfx, list)
                if (entry != null && readU32(cgfx, entry) == MAGIC_INTERLEAVE) candidates.add(entry)
            }
            offset += 4
        }
        return candidates.toList()
    }

    private fun readIndexStream(cgfx: ByteArray, anchor: Int, radius: Int): IndexStream? {
        val start = max(0, anchor - radius)
        val end = min(cgfx.size - 16, anchor + radius)
        var offset = start
        while (offset <= end) {
            val stream = streamAt(cgfx, offset)
            if (stream != null) return stream
            offset += 4
        }
        return null
    }

    private fun streamAt(cgfx: ByteArray, offset: Int): IndexStream? {
        if (offset < 0 || offset + 16 > cgfx.size) return null
        val type = readU32(cgfx, offset)
        if (type != FORMAT_BYTE && type != FORMAT_UNSIGNED_BYTE && type != FORMAT_SHORT) return null
        val mode = cgfx[offset + 4].toInt() and 0xFF
        if (mode > 3) return null
        val size = readU32(cgfx, offset + 8)
        if (size !in 1..MAX_INDEX_STREAM) return null
        val dataOffset = deref(cgfx, offset + 12) ?: return null
        val elementSize = if (type == FORMAT_SHORT) 2 else 1
        if (dataOffset + size.toLong() * elementSize > cgfx.size) return null
        return IndexStream(type, mode, size, dataOffset)
    }

    private fun allIndexStreams(cgfx: ByteArray): List<IndexStream> {
        val streams = ArrayList<IndexStream>()
        var offset = 0
        val last = cgfx.size - 16
        while (offset < last) {
            streamAt(cgfx, offset)?.let { streams.add(it) }
            offset += 4
        }
        return streams
    }

    private fun readIndices(cgfx: ByteArray, stream: IndexStream): List<Int> {
        val indices = ArrayList<Int>(stream.size)
        if (stream.type == FORMAT_SHORT) {
            for (index in 0 until stream.size) {
                indices.add(readU16(cgfx, stream.dataOffset + index * 2))
            }
        } else {
            for (index in 0 until stream.size) {
                indices.add(cgfx[stream.dataOffset + index].toInt() and 0xFF)
            }
        }
        return indices
    }

    // --- rasteriser --------------------------------------------------------------------------

    private fun rasterise(meshes: List<SceneMesh>, width: Int, height: Int, yaw: Double): IntArray? {
        println("DBG rasterise enter meshes=${meshes.size}")
        val pixels = IntArray(width * height)
        val depth = FloatArray(width * height) { Float.MAX_VALUE }
        val cosYaw = cos(yaw).toFloat()
        val sinYaw = sin(yaw).toFloat()
        val cosZ = cos(SCENE_Z_ROTATION).toFloat()
        val sinZ = sin(SCENE_Z_ROTATION).toFloat()
        val tanHalfFov = tan(Math.toRadians(SCENE_FOV_Y_DEGREES / 2.0)).toFloat()

        var drawn = 0
        for (mesh in meshes) {
            val count = mesh.vertices.size
            val screenX = FloatArray(count)
            val screenY = FloatArray(count)
            val screenZ = FloatArray(count)
            val projected = BooleanArray(count)
            for (index in 0 until count) {
                val vertex = mesh.vertices[index]
                val rotatedX = vertex.x * cosZ - vertex.y * sinZ
                val rotatedY = vertex.x * sinZ + vertex.y * cosZ
                val viewX = rotatedX * cosYaw + vertex.z * sinYaw
                val viewZ = -rotatedX * sinYaw + vertex.z * cosYaw
                val cameraZ = viewZ - SCENE_EYE_Z
                if (cameraZ > -0.5f) continue
                val ndcX = (viewX / -cameraZ) / tanHalfFov / SCENE_ASPECT.toFloat()
                val ndcY = ((rotatedY - SCENE_EYE_Y) / -cameraZ) / tanHalfFov
                screenX[index] = (ndcX * 0.5f + 0.5f) * width
                screenY[index] = (1f - (ndcY * 0.5f + 0.5f)) * height
                screenZ[index] = -cameraZ
                projected[index] = true
            }

            for (triangle in mesh.triangles) {
                val i0 = triangle[0]
                val i1 = triangle[1]
                val i2 = triangle[2]
                if (!projected[i0] || !projected[i1] || !projected[i2]) continue
                val x0 = screenX[i0]
                val y0 = screenY[i0]
                val x1 = screenX[i1]
                val y1 = screenY[i1]
                val x2 = screenX[i2]
                val y2 = screenY[i2]
                val area = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
                if (kotlin.math.abs(area) < 1e-9f) continue

                val minX = max(0, min(x0, min(x1, x2)).toInt())
                val maxX = min(width - 1, max(x0, max(x1, x2)).toInt() + 1)
                val minY = max(0, min(y0, min(y1, y2)).toInt())
                val maxY = min(height - 1, max(y0, max(y1, y2)).toInt() + 1)
                if (minX > maxX || minY > maxY) continue

                val z0 = screenZ[i0]
                val z1 = screenZ[i1]
                val z2 = screenZ[i2]
                val u0 = mesh.vertices[i0].u
                val v0 = mesh.vertices[i0].v
                val u1 = mesh.vertices[i1].u
                val v1 = mesh.vertices[i1].v
                val u2 = mesh.vertices[i2].u
                val v2 = mesh.vertices[i2].v
                val texture = mesh.texture ?: continue

                for (pixelY in minY..maxY) {
                    for (pixelX in minX..maxX) {
                        val sampleX = pixelX.toFloat()
                        val sampleY = pixelY.toFloat()
                        val w0 = ((y1 - y2) * (sampleX - x2) + (x2 - x1) * (sampleY - y2)) / area
                        val w1 = ((y2 - y0) * (sampleX - x2) + (x0 - x2) * (sampleY - y2)) / area
                        val w2 = 1f - w0 - w1
                        if (w0 < -1e-6f || w1 < -1e-6f || w2 < -1e-6f) continue
                        val z = w0 * z0 + w1 * z1 + w2 * z2
                        val pixelIndex = pixelY * width + pixelX
                        if (z >= depth[pixelIndex]) continue

                        val u = w0 * u0 + w1 * u1 + w2 * u2
                        val v = w0 * v0 + w1 * v1 + w2 * v2
                        val color = sampleBilinear(texture, u, v, SCENE_TEXTURE_FLIP_V)
                        if (((color ushr 24) and 0xFF) < SCENE_ALPHA_CUTOFF) continue
                        depth[pixelIndex] = z
                        pixels[pixelIndex] = color
                        drawn++
                    }
                }
            }
        }
        return if (drawn > 0) pixels else null
    }

    private fun sampleBilinear(texture: SceneTexture, u: Float, v: Float, flipV: Boolean): Int {
        val width = texture.width
        val height = texture.height
        var x = (u - kotlin.math.floor(u)) * width - 0.5f
        var y = (v - kotlin.math.floor(v)) * height - 0.5f
        if (flipV) y = height - 1 - y
        val x0 = kotlin.math.floor(x).toInt()
        val y0 = kotlin.math.floor(y).toInt()
        val fx = x - x0
        val fy = y - y0
        var alpha = 0f
        var red = 0f
        var green = 0f
        var blue = 0f
        for (dy in 0..1) {
            for (dx in 0..1) {
                val weight = (if (dx == 1) fx else 1f - fx) * (if (dy == 1) fy else 1f - fy)
                val px = Math.floorMod(x0 + dx, width)
                val py = Math.floorMod(y0 + dy, height)
                val argb = texture.pixels[py * width + px]
                val a = ((argb ushr 24) and 0xFF).toFloat()
                alpha += a * weight
                red += ((argb ushr 16) and 0xFF) * weight
                green += ((argb ushr 8) and 0xFF) * weight
                blue += (argb and 0xFF).toFloat() * weight
            }
        }
        return (clamp255(alpha) shl 24) or (clamp255(red) shl 16) or (clamp255(green) shl 8) or clamp255(blue)
    }

    private fun clamp255(value: Float): Int = value.toInt().coerceIn(0, 255)

    private fun hasCoverage(argb: IntArray): Boolean {
        var opaque = 0
        for (pixel in argb) {
            if (((pixel ushr 24) and 0xFF) > 16) opaque++
        }
        return opaque > argb.size / 100
    }

    // --- primitives --------------------------------------------------------------------------

    private data class TextureLayout(
        val widthField: Int,
        val heightField: Int,
        val formatField: Int,
        val sizeField: Int,
        val dataOffsetField: Int,
    )

    private val TEXTURE_LAYOUTS = listOf(
        TextureLayout(widthField = 0x18, heightField = 0x14, formatField = 0x30, sizeField = 0x40, dataOffsetField = 0x44),
        TextureLayout(widthField = 0x1C, heightField = 0x18, formatField = 0x34, sizeField = 0x44, dataOffsetField = 0x48),
    )

    /**
     * Materials and textures in these banners share the art's own naming, so a material whose
     * texture binding cannot be resolved still binds to the plane it was authored with.
     */
    private val NAME_BINDINGS = mapOf(
        "background" to "background",
        "mt_Logo" to "COMMON1",
        "mt_FlatLink" to "jack_link_rough_fix",
        "m_sp_sword_edge" to "sp_sword_edge",
        "mt_SwordEffect" to "SwordEffect",
    )

    private class SceneTexture(
        val name: String,
        val width: Int,
        val height: Int,
        val pixels: IntArray,
    )

    private class SceneMaterial(val name: String, val texture: String?)

    private class SceneVertex(val x: Float, val y: Float, val z: Float, val u: Float, val v: Float)

    internal class BannerRotation(
        val width: Int,
        val height: Int,
        val frames: List<IntArray>,
    )

    private class SceneMesh(
        val vertices: List<SceneVertex>,
        val triangles: List<IntArray>,
        val texture: SceneTexture?,
    )

    private class Attribute(val format: Int, val components: Int, val scale: Float, val offset: Int)

    private class IndexStream(val type: Int, val mode: Int, val size: Int, val dataOffset: Int)

    private class CommandList(val materials: List<SceneMaterial>, val offset: Int)

    private fun deref(cgfx: ByteArray, field: Int): Int? {
        if (field < 0 || field + 4 > cgfx.size) return null
        val target = field + readU32(cgfx, field)
        return if (target in cgfx.indices) target else null
    }

    private fun readCString(cgfx: ByteArray, offset: Int): String {
        if (offset < 0 || offset >= cgfx.size) return ""
        var end = offset
        val limit = min(cgfx.size, offset + 96)
        while (end < limit && cgfx[end] != 0.toByte()) end++
        return String(cgfx, offset, end - offset, Charsets.US_ASCII)
    }

    private fun readU16(bytes: ByteArray, offset: Int): Int {
        if (offset < 0 || offset + 2 > bytes.size) return 0
        return (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun readS16(bytes: ByteArray, offset: Int): Int {
        val raw = readU16(bytes, offset)
        return if (raw >= 0x8000) raw - 0x10000 else raw
    }

    private fun readU32(bytes: ByteArray, offset: Int): Int {
        if (offset < 0 || offset + 4 > bytes.size) return 0
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun readFloat(bytes: ByteArray, offset: Int): Float =
        if (offset < 0 || offset + 4 > bytes.size) 0f
        else Float.fromBits(readU32(bytes, offset))

    private fun hasAscii(bytes: ByteArray, offset: Int, text: String): Boolean {
        if (offset < 0 || offset + text.length > bytes.size) return false
        for (index in text.indices) {
            if (bytes[offset + index] != text[index].code.toByte()) return false
        }
        return true
    }

    private fun findAscii(bytes: ByteArray, text: String, from: Int, until: Int): Int? {
        val limit = min(until, bytes.size) - text.length
        var offset = from.coerceAtLeast(0)
        while (offset <= limit) {
            if (hasAscii(bytes, offset, text)) return offset
            offset++
        }
        return null
    }
}
