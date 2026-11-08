package com.nendo.argosy.data.media.cgfx

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Draws a CGFX scene the way SPICA.Rendering does, in software so it needs no GPU context and
 * gives the same pixels on every device.
 *
 * Per vertex it runs what the 3DS default vertex shader does (bone palette skinning, billboard
 * bones, vertex colour or material diffuse, three texture coordinators with their
 * scale/rotate/translate); per fragment it runs the material's six texture-combiner stages (the
 * fragment shader SPICA generates), the alpha test, the depth test and the blend function. Meshes
 * are drawn in translucency layer order, then render priority, as the Home Menu does.
 *
 * The camera is the Home Menu banner camera: eye at (0, 1, 44.786) looking down -Z, 30 degree
 * vertical field of view, 5:3, near 26.5, far 1000.
 */
class CgfxSceneRenderer(
    private val file: CgfxFile,
    /** Decodes a texture to ARGB rows, row 0 being v = 0. */
    private val decodeTexture: (CgfxTexture) -> IntArray?,
) {

    class Camera(
        val eyeX: Float = 0f,
        val eyeY: Float = 1f,
        val eyeZ: Float = 44.786f,
        val fovYDegrees: Float = 30f,
        val near: Float = 26.5f,
        val far: Float = 1000f,
    )

    val animator = CgfxAnimator(file)

    private class Texture(val width: Int, val height: Int, val rgba: FloatArray)

    private val textures: Map<String, Texture> = file.textures.mapNotNull { (name, texture) ->
        val argb = runCatching { decodeTexture(texture) }.getOrNull() ?: return@mapNotNull null
        if (argb.size < texture.width * texture.height) return@mapNotNull null
        val rgba = FloatArray(texture.width * texture.height * 4)
        for (i in 0 until texture.width * texture.height) {
            val c = argb[i]
            rgba[i * 4] = ((c ushr 16) and 0xFF) / 255f
            rgba[i * 4 + 1] = ((c ushr 8) and 0xFF) / 255f
            rgba[i * 4 + 2] = (c and 0xFF) / 255f
            rgba[i * 4 + 3] = ((c ushr 24) and 0xFF) / 255f
        }
        name to Texture(texture.width, texture.height, rgba)
    }.toMap()

    // --- vertex streams --------------------------------------------------------------------

    /** A shape's vertices decoded once: each attribute as a flat array of 4 floats per vertex. */
    private class Streams(
        val count: Int,
        val attributes: Map<Int, FloatArray>,
        val elementCounts: Map<Int, Int>,
    )

    private val streams = HashMap<CgfxShape, Streams>()

    private fun streamsFor(shape: CgfxShape): Streams = streams.getOrPut(shape) { decodeStreams(shape) }

    private fun decodeStreams(shape: CgfxShape): Streams {
        val attributes = HashMap<Int, FloatArray>()
        val elements = HashMap<Int, Int>()
        val fixed = HashMap<Int, FloatArray>()
        var count = 0
        fun put(name: Int, values: FloatArray, elementCount: Int, vertices: Int) {
            attributes[name] = values
            elements[name] = elementCount
            count = max(count, vertices)
        }
        for (buffer in shape.buffers) when (buffer) {
            is CgfxVertexBuffer.Attribute -> {
                val raw = buffer.raw ?: continue
                val size = typeSize(buffer.format) * buffer.elements
                if (size == 0) continue
                val vertices = raw.size / size
                val out = FloatArray(vertices * 4)
                for (v in 0 until vertices) readVector(raw, v * size, buffer.format, buffer.elements, buffer.scale, out, v * 4)
                put(buffer.attrName, out, buffer.elements, vertices)
            }
            is CgfxVertexBuffer.Interleaved -> {
                if (buffer.stride <= 0) continue
                val vertices = buffer.raw.size / buffer.stride
                val outs = buffer.attributes.map { FloatArray(vertices * 4) }
                for (v in 0 until vertices) {
                    var offset = 0
                    buffer.attributes.forEachIndexed { index, attribute ->
                        val unit = typeSize(attribute.format)
                        if (unit > 1) offset = (offset + unit - 1) / unit * unit
                        readVector(buffer.raw, v * buffer.stride + offset, attribute.format, attribute.elements, attribute.scale, outs[index], v * 4)
                        offset += unit * attribute.elements
                    }
                }
                buffer.attributes.forEachIndexed { index, attribute -> put(attribute.attrName, outs[index], attribute.elements, vertices) }
            }
            // A fixed colour is a placeholder the default shader does not read as a vertex colour:
            // those meshes take the material colour, as on hardware (an all-zero fixed colour would
            // otherwise make every logo plane transparent).
            is CgfxVertexBuffer.Fixed -> if (buffer.attrName != CgfxAttr.COLOR) {
                fixed[buffer.attrName] = FloatArray(4) { buffer.values.getOrElse(it) { if (it == 3) 1f else 0f } }
            }
        }
        for ((name, value) in fixed) {
            if (attributes.containsKey(name)) continue
            attributes[name] = FloatArray(count * 4) { value[it % 4] }
            elements[name] = 4
        }
        attributes[CgfxAttr.POSITION]?.let { positions ->
            for (v in 0 until count) {
                positions[v * 4] += shape.positionOffset.x
                positions[v * 4 + 1] += shape.positionOffset.y
                positions[v * 4 + 2] += shape.positionOffset.z
            }
        }
        return Streams(count, attributes, elements)
    }

    private fun typeSize(format: Int): Int = when (format) {
        CgfxGlType.BYTE, CgfxGlType.UNSIGNED_BYTE -> 1
        CgfxGlType.SHORT, CgfxGlType.UNSIGNED_SHORT -> 2
        CgfxGlType.FLOAT -> 4
        else -> 0
    }

    private fun readVector(raw: ByteArray, at: Int, format: Int, elements: Int, scale: Float, out: FloatArray, to: Int) {
        out[to + 3] = 1f
        for (e in 0 until min(elements, 4)) {
            val o = at + e * typeSize(format)
            if (o + typeSize(format) > raw.size) return
            val value = when (format) {
                CgfxGlType.BYTE -> raw[o].toFloat()
                CgfxGlType.UNSIGNED_BYTE -> (raw[o].toInt() and 0xFF).toFloat()
                CgfxGlType.SHORT -> ((raw[o].toInt() and 0xFF) or (raw[o + 1].toInt() shl 8)).toShort().toFloat()
                CgfxGlType.UNSIGNED_SHORT -> ((raw[o].toInt() and 0xFF) or ((raw[o + 1].toInt() and 0xFF) shl 8)).toFloat()
                CgfxGlType.FLOAT -> java.lang.Float.intBitsToFloat(
                    (raw[o].toInt() and 0xFF) or ((raw[o + 1].toInt() and 0xFF) shl 8) or
                        ((raw[o + 2].toInt() and 0xFF) shl 16) or ((raw[o + 3].toInt() and 0xFF) shl 24)
                )
                else -> 0f
            }
            out[to + e] = value * scale
        }
    }

    // --- frame -----------------------------------------------------------------------------

    /** The banner's own camera (its scene's first camera), when the container carries one. */
    private val fileCamera: CgfxCamera? = run {
        val name = file.scenes.firstOrNull()?.cameraNames?.firstOrNull()
        file.cameras.firstOrNull { it.name == name } ?: file.cameras.firstOrNull()
    }

    /**
     * ARGB, non-premultiplied, [width] x [height], transparent where nothing was drawn.
     * [camera] overrides the banner's own camera (used by tests); null uses the banner's camera,
     * or the Home Menu banner camera when the container has none.
     */
    fun render(frame: Float, width: Int, height: Int, supersample: Int = 2, camera: Camera? = null): IntArray {
        val ss = supersample.coerceIn(1, 4)
        val target = Target(width * ss, height * ss)
        val states = animator.stateAt(frame)
        val aspect = width.toFloat() / height
        val fromFile = fileCamera?.takeIf { camera == null && it.near > 0f && it.far > it.near }
        val projection: FloatArray
        val view: FloatArray
        if (fromFile != null) {
            projection = if (fromFile.isPerspective) {
                perspective(Math.toDegrees(fromFile.fovY.toDouble()).toFloat(), aspect, fromFile.near, fromFile.far)
            } else {
                orthographic(fromFile.height, aspect, fromFile.near, fromFile.far)
            }
            view = cameraView(fromFile)
        } else {
            val c = camera ?: Camera()
            projection = perspective(c.fovYDegrees, aspect, c.near, c.far)
            view = Mat4.translation(floatArrayOf(-c.eyeX, -c.eyeY, -c.eyeZ))
        }
        val lighting = SceneLighting(file, view)

        class Draw(val model: Int, val mesh: CgfxMesh, val material: CgfxMaterial, val order: Int)
        val draws = ArrayList<Draw>()
        file.models.forEachIndexed { modelIndex, model ->
            if (!model.isVisible) return@forEachIndexed
            val state = states[modelIndex]
            model.meshes.forEachIndexed { order, mesh ->
                if (!mesh.isVisible) return@forEachIndexed
                val node = mesh.meshNodeName?.takeIf { it.isNotEmpty() }
                    ?: model.meshNodes.getOrNull(mesh.meshNodeIndex)?.first
                if (node != null && state.meshNodeVisible[node] == false) return@forEachIndexed
                val material = model.materials.getOrNull(mesh.materialIndex) ?: return@forEachIndexed
                draws += Draw(modelIndex, mesh, material, order)
            }
        }
        // Stereo depth layers: a banner may stack the same plane (same material, same texture
        // coordinates) at two depths so it pops out on the 3D screen. On a flat screen the copies
        // only read as a doubled image, so only the copy nearest the camera is drawn.
        val layered = draws.groupBy { draw ->
            val shape = file.models[draw.model].shapes.getOrNull(draw.mesh.shapeIndex)
            val uv = shape?.let { streamsFor(it).attributes[CgfxAttr.TEXCOORD0] }
            Triple(draw.model, draw.mesh.materialIndex, uv?.let { coords -> coords.map { (it * 1000).toInt() }.hashCode() })
        }
        val hidden = HashSet<Draw>()
        for ((key, copies) in layered) {
            if (key.third == null || copies.size < 2) continue
            val nearest = copies.maxByOrNull { draw ->
                val shape = file.models[draw.model].shapes[draw.mesh.shapeIndex]
                val positions = streamsFor(shape).attributes[CgfxAttr.POSITION] ?: return@maxByOrNull 0f
                var sum = 0f
                for (v in 0 until positions.size / 4) sum += positions[v * 4 + 2]
                sum / (positions.size / 4).coerceAtLeast(1)
            }
            copies.filterTo(hidden) { it !== nearest }
        }
        draws.removeAll(hidden)
        // NintendoWare's render queue: translucency layer, then priority; translucent layers are
        // drawn far to near so what is behind is already there to blend with.
        val depthOf = HashMap<Draw, Float>()
        for (draw in draws) depthOf[draw] = meshDepth(file.models[draw.model], states[draw.model], draw.mesh, view)
        draws.sortWith(
            compareBy<Draw>({ it.material.translucencyKind }, { it.mesh.renderPriority })
                .thenBy { if (it.material.translucencyKind == 0) 0f else depthOf.getValue(it) }
                .thenBy { it.order }
        )

        for (draw in draws) {
            val model = file.models[draw.model]
            val shape = model.shapes.getOrNull(draw.mesh.shapeIndex) ?: continue
            drawMesh(target, model, states[draw.model], shape, draw.mesh, draw.material, view, projection, lighting)
        }
        return target.resolve(width, height, ss)
    }

    /** View-space depth of a mesh's centre (more negative = farther). */
    private fun meshDepth(model: CgfxModel, state: CgfxAnimator.ModelState, mesh: CgfxMesh, view: FloatArray): Float {
        val shape = model.shapes.getOrNull(mesh.shapeIndex) ?: return 0f
        val positions = streamsFor(shape).attributes[CgfxAttr.POSITION] ?: return 0f
        val count = positions.size / 4
        if (count == 0) return 0f
        var x = 0f; var y = 0f; var z = 0f
        for (v in 0 until count) { x += positions[v * 4]; y += positions[v * 4 + 1]; z += positions[v * 4 + 2] }
        x /= count; y /= count; z /= count
        val bone = shape.subMeshes.firstOrNull()?.boneIndices?.firstOrNull()
        val world = bone?.let { state.boneWorld.getOrNull(it) } ?: Mat4.identity()
        val m = Mat4.multiply(view, world)
        return m[8] * x + m[9] * y + m[10] * z + m[11]
    }

    private fun cameraView(c: CgfxCamera): FloatArray {
        val eye = floatArrayOf(c.position.x, c.position.y, c.position.z)
        return when (c.viewType) {
            1 -> lookAt(eye, floatArrayOf(c.target.x, c.target.y, c.target.z), floatArrayOf(c.upVector.x, c.upVector.y, c.upVector.z))
            0 -> {
                // Aim: look at the target, then roll by the twist angle.
                val base = lookAt(eye, floatArrayOf(c.target.x, c.target.y, c.target.z), floatArrayOf(0f, 1f, 0f))
                val half = c.twist / 2f
                Mat4.multiply(Mat4.rotation(floatArrayOf(0f, 0f, kotlin.math.sin(half), kotlin.math.cos(half))), base)
            }
            else -> {
                // Rotate: the camera's own Euler rotation (stored as the target), inverted.
                val r = c.target
                val rotation = Mat4.rotation(CgfxAnimator.eulerToQuaternion(r.x, r.y, r.z))
                val world = Mat4.multiply(Mat4.translation(eye), rotation)
                Mat4.invert(world) ?: Mat4.translation(floatArrayOf(-eye[0], -eye[1], -eye[2]))
            }
        }
    }

    private fun lookAt(eye: FloatArray, target: FloatArray, up: FloatArray): FloatArray {
        val f = normalize(floatArrayOf(target[0] - eye[0], target[1] - eye[1], target[2] - eye[2]))
        val sAxis = normalize(cross(f, up))
        val u = cross(sAxis, f)
        return floatArrayOf(
            sAxis[0], sAxis[1], sAxis[2], -(sAxis[0] * eye[0] + sAxis[1] * eye[1] + sAxis[2] * eye[2]),
            u[0], u[1], u[2], -(u[0] * eye[0] + u[1] * eye[1] + u[2] * eye[2]),
            -f[0], -f[1], -f[2], f[0] * eye[0] + f[1] * eye[1] + f[2] * eye[2],
            0f, 0f, 0f, 1f,
        )
    }

    private fun orthographic(height: Float, aspect: Float, near: Float, far: Float): FloatArray {
        val h = height / 2f
        val w = h * aspect
        return floatArrayOf(
            1f / w, 0f, 0f, 0f,
            0f, 1f / h, 0f, 0f,
            0f, 0f, -2f / (far - near), -(far + near) / (far - near),
            0f, 0f, 0f, 1f,
        )
    }

    private fun perspective(fovY: Float, aspect: Float, near: Float, far: Float): FloatArray {
        val f = (1.0 / tan(Math.toRadians(fovY / 2.0))).toFloat()
        return floatArrayOf(
            f / aspect, 0f, 0f, 0f,
            0f, f, 0f, 0f,
            0f, 0f, (far + near) / (near - far), 2 * far * near / (near - far),
            0f, 0f, -1f, 0f,
        )
    }

    // --- framebuffer -----------------------------------------------------------------------

    private class Target(val width: Int, val height: Int) {
        val color = FloatArray(width * height * 4)
        val depth = FloatArray(width * height) { 1f }

        /**
         * The colour buffer holds what the GPU would have drawn over black (so it is already
         * premultiplied by coverage) and alpha holds that coverage; dividing gives the colour to
         * lay over any background without darkening soft edges twice.
         */
        fun resolve(outWidth: Int, outHeight: Int, ss: Int): IntArray {
            val out = IntArray(outWidth * outHeight)
            val samples = (ss * ss).toFloat()
            for (y in 0 until outHeight) for (x in 0 until outWidth) {
                var r = 0f; var g = 0f; var b = 0f; var a = 0f
                for (sy in 0 until ss) for (sx in 0 until ss) {
                    val i = ((y * ss + sy) * width + x * ss + sx) * 4
                    r += color[i]; g += color[i + 1]; b += color[i + 2]; a += color[i + 3]
                }
                if (a <= 0f) continue
                val alpha = a / samples
                out[y * outWidth + x] = (to8(alpha) shl 24) or (to8(r / a) shl 16) or (to8(g / a) shl 8) or to8(b / a)
            }
            return out
        }

        private fun to8(v: Float): Int = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
    }

    // --- per mesh --------------------------------------------------------------------------

    /** Vertex layout after the vertex stage: clip xyzw, view position, view normal, colour, three UVs. */
    private companion object {
        const val CX = 0; const val CY = 1; const val CZ = 2; const val CW = 3
        const val VX = 4; const val VY = 5; const val VZ = 6
        const val NX = 7; const val NY = 8; const val NZ = 9
        const val R = 10; const val G = 11; const val B = 12; const val A = 13
        const val U0 = 14; const val V0 = 15; const val U1 = 16; const val V1 = 17; const val U2 = 18; const val V2 = 19
        const val STRIDE = 20

        /** PICA lookup-table output scale codes. */
        fun lutScale(code: Int): Float = when (code) {
            1 -> 2f
            2 -> 4f
            3 -> 8f
            6 -> 0.25f
            7 -> 0.5f
            else -> 1f
        }
    }

    private fun drawMesh(
        target: Target,
        model: CgfxModel,
        state: CgfxAnimator.ModelState,
        shape: CgfxShape,
        mesh: CgfxMesh,
        material: CgfxMaterial,
        view: FloatArray,
        projection: FloatArray,
        lighting: SceneLighting,
    ) {
        val data = streamsFor(shape)
        if (data.count == 0) return
        val positions = data.attributes[CgfxAttr.POSITION] ?: return
        val normals = data.attributes[CgfxAttr.NORMAL]
        val colors = data.attributes[CgfxAttr.COLOR]
        val colorHasAlpha = (data.elementCounts[CgfxAttr.COLOR] ?: 0) >= 4
        val boneIndices = data.attributes[CgfxAttr.BONE_INDEX]
        val boneIndexCount = data.elementCounts[CgfxAttr.BONE_INDEX] ?: 0
        val boneWeights = data.attributes[CgfxAttr.BONE_WEIGHT]
        val texCoords = arrayOf(
            data.attributes[CgfxAttr.TEXCOORD0],
            data.attributes[CgfxAttr.TEXCOORD1],
            data.attributes[CgfxAttr.TEXCOORD2],
        )
        val materialIndex = model.materials.indexOf(material)
        val materialState = state.materials.getOrNull(materialIndex)
        val shading = Shading(material, materialState, lighting.forMaterial(material))
        val bones = model.skeleton?.bones ?: emptyList()

        val coordMatrices = Array(3) { i ->
            val c = material.textureCoords[i]
            val values = materialState?.coords?.get(i) ?: floatArrayOf(c.scaleU, c.scaleV, c.rotation, c.translateU, c.translateV)
            textureMatrix(values[0], values[1], values[2], values[3], values[4], c.transformType)
        }

        for (subMesh in shape.subMeshes) {
            // Bone palette as model -> view matrices.
            val palette = Array(max(1, subMesh.boneIndices.size)) { slot ->
                val boneIndex = subMesh.boneIndices.getOrNull(slot)
                if (boneIndex == null || boneIndex !in bones.indices) return@Array view
                var transform = state.boneWorld[boneIndex]
                if (subMesh.skinning == CgfxSkinning.SMOOTH) transform = Mat4.multiply(transform, state.boneInverseBase[boneIndex])
                val bone = bones[boneIndex]
                if (bone.billboard != CgfxBillboard.OFF) billboard(view, transform, bone) else Mat4.multiply(view, transform)
            }

            val out = FloatArray(data.count * STRIDE)
            val p = FloatArray(4)
            val n = FloatArray(3)
            for (v in 0 until data.count) {
                val o = v * STRIDE
                val px = positions[v * 4]; val py = positions[v * 4 + 1]; val pz = positions[v * 4 + 2]
                val nx = normals?.get(v * 4) ?: 0f; val ny = normals?.get(v * 4 + 1) ?: 0f; val nz = normals?.get(v * 4 + 2) ?: 1f
                p[0] = 0f; p[1] = 0f; p[2] = 0f; n[0] = 0f; n[1] = 0f; n[2] = 0f
                if (subMesh.skinning == CgfxSkinning.SMOOTH && boneIndices != null && boneWeights != null) {
                    var total = 0f
                    for (k in 0 until min(4, max(1, boneIndexCount))) {
                        val weight = boneWeights[v * 4 + k]
                        if (weight <= 0f) continue
                        val m = palette.getOrElse(boneIndices[v * 4 + k].toInt()) { palette[0] }
                        accumulate(m, px, py, pz, nx, ny, nz, weight, p, n)
                        total += weight
                    }
                    if (total <= 0f) accumulate(palette[0], px, py, pz, nx, ny, nz, 1f, p, n)
                } else {
                    val slot = if (subMesh.skinning == CgfxSkinning.RIGID && boneIndices != null) boneIndices[v * 4].toInt() else 0
                    accumulate(palette.getOrElse(slot) { palette[0] }, px, py, pz, nx, ny, nz, 1f, p, n)
                }
                out[o + VX] = p[0]; out[o + VY] = p[1]; out[o + VZ] = p[2]
                val nl = sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]).takeIf { it > 1e-6f } ?: 1f
                out[o + NX] = n[0] / nl; out[o + NY] = n[1] / nl; out[o + NZ] = n[2] / nl
                for (r in 0..3) {
                    out[o + r] = projection[r * 4] * p[0] + projection[r * 4 + 1] * p[1] + projection[r * 4 + 2] * p[2] + projection[r * 4 + 3]
                }
                // Primary colour: the vertex colour (times the material's colour scale) or the material diffuse.
                if (colors != null) {
                    val scale = material.colors.ambient.a.takeIf { it > 0f } ?: 1f
                    out[o + R] = max(0f, colors[v * 4] * scale)
                    out[o + G] = max(0f, colors[v * 4 + 1] * scale)
                    out[o + B] = max(0f, colors[v * 4 + 2] * scale)
                    out[o + A] = max(0f, if (colorHasAlpha) colors[v * 4 + 3] else 1f)
                } else {
                    val diffuse = shading.diffuse
                    out[o + R] = diffuse.r; out[o + G] = diffuse.g; out[o + B] = diffuse.b; out[o + A] = diffuse.a
                }
                // Texture coordinators.
                for (unit in 0..2) {
                    val c = material.textureCoords[unit]
                    var s: Float
                    var t: Float
                    if (c.mappingType == 2) {
                        s = out[o + NX] * 0.5f + 0.5f
                        t = out[o + NY] * 0.5f + 0.5f
                    } else {
                        val source = texCoords.getOrNull(c.sourceCoordIndex.coerceIn(0, 2)) ?: texCoords[0]
                        s = source?.get(v * 4) ?: 0f
                        t = source?.get(v * 4 + 1) ?: 0f
                    }
                    val m = coordMatrices[unit]
                    out[o + U0 + unit * 2] = m[0] * s + m[1] * t + m[2]
                    out[o + V0 + unit * 2] = m[3] * s + m[4] * t + m[5]
                }
            }

            for (face in subMesh.faces) {
                val indices = face.indices
                val triangle = IntArray(3)
                fun emit(a: Int, b: Int, c: Int) {
                    if (a >= data.count || b >= data.count || c >= data.count) return
                    triangle[0] = a; triangle[1] = b; triangle[2] = c
                    drawTriangle(target, out, triangle, material, shading)
                }
                when (face.primitiveMode) {
                    1 -> for (i in 2 until indices.size) {
                        if (i % 2 == 0) emit(indices[i - 2], indices[i - 1], indices[i]) else emit(indices[i - 1], indices[i - 2], indices[i])
                    }
                    2 -> for (i in 2 until indices.size) emit(indices[0], indices[i - 1], indices[i])
                    else -> {
                        var i = 0
                        while (i + 2 < indices.size) { emit(indices[i], indices[i + 1], indices[i + 2]); i += 3 }
                    }
                }
            }
        }
    }

    private fun accumulate(m: FloatArray, x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, w: Float, p: FloatArray, n: FloatArray) {
        p[0] += (m[0] * x + m[1] * y + m[2] * z + m[3]) * w
        p[1] += (m[4] * x + m[5] * y + m[6] * z + m[7]) * w
        p[2] += (m[8] * x + m[9] * y + m[10] * z + m[11]) * w
        n[0] += (m[0] * nx + m[1] * ny + m[2] * nz) * w
        n[1] += (m[4] * nx + m[5] * ny + m[6] * nz) * w
        n[2] += (m[8] * nx + m[9] * ny + m[10] * nz) * w
    }

    /**
     * SPICA's billboard bone: the bone keeps its position and scale, but its axes are rebuilt so it
     * faces the camera (screen modes keep the bone's own up vector).
     */
    private fun billboard(view: FloatArray, transform: FloatArray, bone: CgfxBone): FloatArray {
        val modelView = Mat4.multiply(view, transform)
        val viewpoint = when (bone.billboard) {
            CgfxBillboard.WORLD_VIEWPOINT, CgfxBillboard.SCREEN_VIEWPOINT, CgfxBillboard.Y_AXIAL_VIEWPOINT -> true
            else -> false
        }
        val up: FloatArray = when (bone.billboard) {
            CgfxBillboard.SCREEN, CgfxBillboard.SCREEN_VIEWPOINT -> {
                val r = Mat4.rotation(CgfxAnimator.eulerToQuaternion(bone.rotation.x, bone.rotation.y, bone.rotation.z))
                floatArrayOf(r[1], r[5], r[9])
            }
            else -> floatArrayOf(view[1], view[5], view[9]) // world up seen from the camera
        }
        var zAxis = floatArrayOf(0f, 0f, 1f)
        if (viewpoint) {
            zAxis = normalize(floatArrayOf(-modelView[3], -modelView[7], -modelView[11]))
        }
        val y = normalize(up)
        val x = normalize(cross(y, zAxis))
        val y2 = normalize(cross(zAxis, x))
        val sx = sqrt(transform[0] * transform[0] + transform[4] * transform[4] + transform[8] * transform[8])
        val sy = sqrt(transform[1] * transform[1] + transform[5] * transform[5] + transform[9] * transform[9])
        val sz = sqrt(transform[2] * transform[2] + transform[6] * transform[6] + transform[10] * transform[10])
        return floatArrayOf(
            x[0] * sx, y2[0] * sy, zAxis[0] * sz, modelView[3],
            x[1] * sx, y2[1] * sy, zAxis[1] * sz, modelView[7],
            x[2] * sx, y2[2] * sy, zAxis[2] * sz, modelView[11],
            0f, 0f, 0f, 1f,
        )
    }

    private fun cross(a: FloatArray, b: FloatArray) = floatArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])

    private fun normalize(v: FloatArray): FloatArray {
        val l = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).takeIf { it > 1e-6f } ?: return v
        return floatArrayOf(v[0] / l, v[1] / l, v[2] / l)
    }

    /** SPICA's TextureTransform.GetTransform as two rows (s' = a*s + b*t + c, t' = d*s + e*t + f). */
    private fun textureMatrix(sx: Float, sy: Float, r: Float, tx: Float, ty: Float, type: Int): FloatArray {
        val ca = kotlin.math.cos(r)
        val sa = kotlin.math.sin(r)
        val (ox, oy) = when (type) {
            0 -> sx * ((0.5f * sa - 0.5f * ca) + 0.5f - tx) to sy * ((0.5f * -sa - 0.5f * ca) + 0.5f - ty)
            1 -> sx * (-ca * tx - sa * ty) to sy * (sa * tx - ca * ty)
            else -> (sx * ca * (-tx - 0.5f) - sx * sa * (ty - 0.5f) + 0.5f) to (sy * sa * (-tx - 0.5f) + sy * ca * (ty - 0.5f) + 0.5f)
        }
        return floatArrayOf(sx * ca, sx * -sa, ox, sy * sa, sy * ca, oy)
    }

    // --- rasterisation ---------------------------------------------------------------------

    private fun drawTriangle(target: Target, verts: FloatArray, tri: IntArray, material: CgfxMaterial, shading: Shading) {
        // Clip against the near plane (w > epsilon) in clip space.
        val polygon = ArrayList<FloatArray>(4)
        for (i in 0..2) polygon += verts.copyOfRange(tri[i] * STRIDE, tri[i] * STRIDE + STRIDE)
        val clipped = clipNear(polygon)
        if (clipped.size < 3) return
        for (i in 1 until clipped.size - 1) rasterise(target, clipped[0], clipped[i], clipped[i + 1], material, shading)
    }

    private fun clipNear(polygon: List<FloatArray>): List<FloatArray> {
        fun d(v: FloatArray) = v[CZ] + v[CW] // inside when z >= -w
        if (polygon.all { d(it) >= 0f }) return polygon
        val out = ArrayList<FloatArray>()
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[(i + 1) % polygon.size]
            val da = d(a); val db = d(b)
            if (da >= 0f) out += a
            if ((da >= 0f) != (db >= 0f)) {
                val t = da / (da - db)
                out += FloatArray(STRIDE) { a[it] + (b[it] - a[it]) * t }
            }
        }
        return out
    }

    private fun rasterise(target: Target, a: FloatArray, b: FloatArray, c: FloatArray, material: CgfxMaterial, shading: Shading) {
        if (a[CW] <= 1e-6f || b[CW] <= 1e-6f || c[CW] <= 1e-6f) return
        val w = target.width; val h = target.height
        val iwa = 1f / a[CW]; val iwb = 1f / b[CW]; val iwc = 1f / c[CW]
        val ax = a[CX] * iwa; val ay = a[CY] * iwa
        val bx = b[CX] * iwb; val by = b[CY] * iwb
        val cx = c[CX] * iwc; val cy = c[CY] * iwc
        // Facing in NDC (y up): counter-clockwise is the front face.
        val area = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay)
        if (area == 0f) return
        val front = area > 0f
        when (material.faceCulling) {
            1 -> if (front) return
            2 -> if (!front) return
        }
        val sxa = (ax * 0.5f + 0.5f) * w; val sya = (1f - (ay * 0.5f + 0.5f)) * h
        val sxb = (bx * 0.5f + 0.5f) * w; val syb = (1f - (by * 0.5f + 0.5f)) * h
        val sxc = (cx * 0.5f + 0.5f) * w; val syc = (1f - (cy * 0.5f + 0.5f)) * h
        val za = a[CZ] * iwa * 0.5f + 0.5f; val zb = b[CZ] * iwb * 0.5f + 0.5f; val zc = c[CZ] * iwc * 0.5f + 0.5f
        val minX = max(0, floor(min(sxa, min(sxb, sxc))).toInt())
        val maxX = min(w - 1, floor(max(sxa, max(sxb, sxc))).toInt())
        val minY = max(0, floor(min(sya, min(syb, syc))).toInt())
        val maxY = min(h - 1, floor(max(sya, max(syb, syc))).toInt())
        if (minX > maxX || minY > maxY) return
        val denom = (syb - syc) * (sxa - sxc) + (sxc - sxb) * (sya - syc)
        if (abs(denom) < 1e-12f) return
        val attrs = FloatArray(STRIDE)
        val fragment = FloatArray(4)
        for (y in minY..maxY) {
            val py = y + 0.5f
            for (x in minX..maxX) {
                val px = x + 0.5f
                val l0 = ((syb - syc) * (px - sxc) + (sxc - sxb) * (py - syc)) / denom
                val l1 = ((syc - sya) * (px - sxc) + (sxa - sxc) * (py - syc)) / denom
                val l2 = 1f - l0 - l1
                if (l0 < 0f || l1 < 0f || l2 < 0f) continue
                val z = l0 * za + l1 * zb + l2 * zc
                if (z < 0f || z > 1f) continue
                val pixel = y * w + x
                val depth = material.depth
                if (depth.testEnabled && !compare(depth.function, z, target.depth[pixel])) continue
                // Perspective-correct attributes.
                val p0 = l0 * iwa; val p1 = l1 * iwb; val p2 = l2 * iwc
                val ps = 1f / (p0 + p1 + p2)
                for (k in VX until STRIDE) attrs[k] = (a[k] * p0 + b[k] * p1 + c[k] * p2) * ps
                if (!shading.shade(attrs, fragment, textures)) continue
                blend(target, pixel, fragment, material.blend)
                if (depth.testEnabled && depth.write) target.depth[pixel] = z
            }
        }
    }

    private fun compare(function: Int, value: Float, reference: Float): Boolean = when (function) {
        0 -> false
        1 -> true
        2 -> value == reference
        3 -> value != reference
        4 -> value < reference
        5 -> value <= reference
        6 -> value > reference
        else -> value >= reference
    }

    /**
     * The output merger, keeping the framebuffer as "what was drawn over black" plus coverage so the
     * banner can later be laid over any background. Writing src*sf + dst*df onto a background B
     * shown through coverage A gives P' = src*sf + P*df and A' = 1 - df*(1 - A).
     */
    private fun blend(target: Target, pixel: Int, src: FloatArray, blend: CgfxBlend) {
        val i = pixel * 4
        val dst = target.color
        if (blend.mode == 0 || blend.mode == 3 || !blend.blendEnabled) {
            // Opaque write (blending off, or a logic op, which banners only use as plain copy).
            dst[i] = src[0]; dst[i + 1] = src[1]; dst[i + 2] = src[2]; dst[i + 3] = 1f
            return
        }
        val da = dst[i + 3]
        fun factor(f: Int, channel: Int, alpha: Boolean): Float = when (f) {
            0 -> 0f
            1 -> 1f
            2 -> if (alpha) src[3] else src[channel]
            3 -> 1f - (if (alpha) src[3] else src[channel])
            4 -> if (alpha) da else dst[i + channel]
            5 -> 1f - (if (alpha) da else dst[i + channel])
            6 -> src[3]
            7 -> 1f - src[3]
            8 -> da
            9 -> 1f - da
            10 -> if (alpha) blend.color.a else channelOf(blend.color, channel)
            11 -> 1f - (if (alpha) blend.color.a else channelOf(blend.color, channel))
            12 -> blend.color.a
            13 -> 1f - blend.color.a
            14 -> if (alpha) 1f else min(src[3], 1f - da)
            else -> 1f
        }
        var dfSum = 0f
        for (c in 0..2) {
            val sf = factor(blend.colorSrc, c, false)
            val df = factor(blend.colorDst, c, false)
            val s = src[c]; val d = dst[i + c]
            dst[i + c] = when (blend.colorEquation) {
                1 -> s * sf - d * df
                2 -> d * df - s * sf
                3 -> min(s, d)
                4 -> max(s, d)
                else -> s * sf + d * df
            }.coerceIn(0f, 1f)
            dfSum += when (blend.colorEquation) {
                3, 4 -> 1f - src[3]
                else -> df
            }
        }
        var coverage = (1f - (dfSum / 3f).coerceIn(0f, 1f) * (1f - da)).coerceIn(0f, 1f)
        // Additive light can be brighter than its coverage; keep the colour by raising coverage.
        coverage = max(coverage, max(dst[i], max(dst[i + 1], dst[i + 2])))
        dst[i + 3] = coverage
    }

    private fun channelOf(c: Rgba, channel: Int): Float = when (channel) {
        0 -> c.r
        1 -> c.g
        2 -> c.b
        else -> c.a
    }

    // --- lighting --------------------------------------------------------------------------

    /** One hardware light as the GPU registers hold it: view space, colours already times the material's. */
    private class HwLight(
        val directional: Boolean,
        /** Directional: unit vector towards the light. Positional: the light's view-space position. */
        val position: FloatArray,
        val spotDirection: FloatArray?,
        val ambient: FloatArray,
        val diffuse: FloatArray,
        val specular0: FloatArray,
        val specular1: FloatArray,
        val twoSided: Boolean,
        val distanceLut: CgfxLut?,
        val distanceScale: Float,
        val distanceBias: Float,
        val spotLut: CgfxLut?,
        val spotInput: Int,
        val spotScale: Float,
    )

    /** The lights of the banner's scene, seen from the camera. */
    private class SceneLighting(private val file: CgfxFile, private val view: FloatArray) {
        private val scene = file.scenes.firstOrNull()
        private val cache = HashMap<CgfxMaterial, MaterialLighting?>()

        fun forMaterial(material: CgfxMaterial): MaterialLighting? = cache.getOrPut(material) { build(material) }

        private fun lightsFor(material: CgfxMaterial): List<CgfxLight> {
            val enabled = file.lights.filter { it.isEnabled }
            val names = scene?.lightSets?.get(material.lightSetIndex) ?: scene?.lightSets?.get(0)
            return if (names != null) names.mapNotNull { n -> enabled.firstOrNull { it.name == n } } else enabled
        }

        private fun build(material: CgfxMaterial): MaterialLighting? {
            if (!material.isFragmentLightingEnabled) return null
            val c = material.colors
            val lights = lightsFor(material)
            val fragmentLights = lights.filter { it.kind == CgfxLightKind.FRAGMENT }.take(8)
            val sceneAmbient = lights.firstOrNull { it.kind == CgfxLightKind.AMBIENT }?.ambient
                ?: file.lights.firstOrNull { it.kind == CgfxLightKind.AMBIENT && it.isEnabled }?.ambient
            val flags = material.fragmentLightingFlags
            val reflection = flags and (1 shl 5) != 0
            val hw = if (fragmentLights.isEmpty()) {
                // No lights in the file: SPICA's viewer light, at the eye.
                listOf(
                    HwLight(
                        directional = false,
                        position = floatArrayOf(0f, 0f, 0f),
                        spotDirection = null,
                        ambient = mul(c.ambient, 0.1f),
                        diffuse = mul(c.diffuse, 0.9f),
                        specular0 = mul(c.specular0, 0.8f),
                        specular1 = mul(c.specular1, 0.4f),
                        twoSided = true,
                        distanceLut = null, distanceScale = 0f, distanceBias = 0f,
                        spotLut = null, spotInput = 0, spotScale = 1f,
                    )
                )
            } else fragmentLights.map { light -> hardwareLight(light, c, reflection) }
            val ambientColor = sceneAmbient ?: Rgba.BLACK
            val global = floatArrayOf(
                c.emission.r + c.ambient.r * ambientColor.r,
                c.emission.g + c.ambient.g * ambientColor.g,
                c.emission.b + c.ambient.b * ambientColor.b,
            )
            return MaterialLighting(file, material, hw, global)
        }

        private fun hardwareLight(light: CgfxLight, c: CgfxMaterialColors, reflection: Boolean): HwLight {
            val w = light.worldTransform
            // World -> view, rotation only for directions.
            fun rotate(x: Float, y: Float, z: Float): FloatArray {
                val wx = w[0, 0] * x + w[0, 1] * y + w[0, 2] * z
                val wy = w[1, 0] * x + w[1, 1] * y + w[1, 2] * z
                val wz = w[2, 0] * x + w[2, 1] * y + w[2, 2] * z
                val v = floatArrayOf(
                    view[0] * wx + view[1] * wy + view[2] * wz,
                    view[4] * wx + view[5] * wy + view[6] * wz,
                    view[8] * wx + view[9] * wy + view[10] * wz,
                )
                val l = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).takeIf { it > 1e-6f } ?: return floatArrayOf(0f, 0f, 1f)
                return floatArrayOf(v[0] / l, v[1] / l, v[2] / l)
            }
            val d = light.direction
            val directional = light.type == 1
            val position = if (directional) {
                rotate(-d.x, -d.y, -d.z)
            } else {
                val tx = w[0, 3]; val ty = w[1, 3]; val tz = w[2, 3]
                floatArrayOf(
                    view[0] * tx + view[1] * ty + view[2] * tz + view[3],
                    view[4] * tx + view[5] * ty + view[6] * tz + view[7],
                    view[8] * tx + view[9] * ty + view[10] * tz + view[11],
                )
            }
            // NintendoWare applies distance attenuation to point and spot lights only: a directional
            // light has no distance (the flag stays set in files exported from point-light presets).
            val distanceLut = if (light.isDistanceAttenuationEnabled && light.type != 1) file.lut(light.distanceLut) else null
            val span = light.attenuationEnd - light.attenuationStart
            val spotLut = if (light.type == 2) file.lut(light.angleLut?.reference) else null
            return HwLight(
                directional = directional,
                position = position,
                spotDirection = if (light.type == 2) rotate(-d.x, -d.y, -d.z) else null,
                ambient = mul(c.ambient, light.ambient),
                diffuse = mul(c.diffuse, light.diffuse),
                specular0 = mul(c.specular0, light.specular0),
                // With the reflection tables on, they take the material's place in specular 1.
                specular1 = if (reflection) floatArrayOf(light.specular1.r, light.specular1.g, light.specular1.b)
                else mul(c.specular1, light.specular1),
                twoSided = light.isTwoSidedDiffuse,
                distanceLut = distanceLut?.takeIf { abs(span) > 1e-6f },
                distanceScale = if (abs(span) > 1e-6f) 1f / span else 0f,
                distanceBias = if (abs(span) > 1e-6f) -light.attenuationStart / span else 0f,
                spotLut = spotLut,
                spotInput = light.angleLut?.input ?: 4,
                spotScale = lutScale(light.angleLut?.scale ?: 0),
            )
        }

        private fun mul(a: Rgba, b: Rgba) = floatArrayOf(a.r * b.r, a.g * b.g, a.b * b.b)
        private fun mul(a: Rgba, k: Float) = floatArrayOf(a.r * k, a.g * k, a.b * k)
    }


    /**
     * A material's fragment lighting, computed like the PICA200 does it (a port of Azahar's
     * software lighting): lookup tables with their interpolation delta, the samplers the
     * material's lighting configuration actually wires up, Fresnel from the last light.
     */
    private class MaterialLighting(file: CgfxFile, material: CgfxMaterial, private val lights: List<HwLight>, private val global: FloatArray) {
        private val config = material.lightingConfig
        private val flags = material.fragmentLightingFlags
        private val clampHighlights = flags and 1 != 0
        private val geo0 = flags and (1 shl 3) != 0
        private val geo1 = flags and (1 shl 4) != 0

        private class Sampler(val lut: CgfxLut, val input: Int, val scale: Float)

        private fun sampler(index: Int, enabled: Boolean, supported: Boolean, file: CgfxFile, material: CgfxMaterial): Sampler? {
            if (!enabled || !supported) return null
            val ref = material.lightingLuts.getOrNull(index) ?: return null
            val lut = file.lut(ref.reference) ?: return null
            return Sampler(lut, ref.input, lutScale(ref.scale))
        }

        private val c = config
        private val reflectionOn = flags and (1 shl 5) != 0
        private val rr = sampler(0, reflectionOn, c != 3, file, material)
        private val rg = sampler(1, reflectionOn, c == 4 || c == 5 || c == 8, file, material)
        private val rb = sampler(2, reflectionOn, c == 4 || c == 5 || c == 8, file, material)
        private val d0 = sampler(3, flags and (1 shl 1) != 0, c != 1, file, material)
        private val d1 = sampler(4, flags and (1 shl 2) != 0, c != 0 && c != 1 && c != 5, file, material)
        private val fr = sampler(5, material.fresnelSelector != 0, c != 0 && c != 2 && c != 4, file, material)
        private val fresnelPrimary = material.fresnelSelector and 1 != 0
        private val fresnelSecondary = material.fresnelSelector and 2 != 0
        private val spotSupported = c != 2 && c != 3

        private val n = FloatArray(3)
        private val v = FloatArray(3)
        private val l = FloatArray(3)
        private val h = FloatArray(3)
        private val hn = FloatArray(3)

        /** Writes primary and secondary fragment colours (0..255). */
        fun compute(attrs: FloatArray, primary: IntArray, secondary: IntArray) {
            var nl = sqrt(attrs[NX] * attrs[NX] + attrs[NY] * attrs[NY] + attrs[NZ] * attrs[NZ])
            if (nl < 1e-6f) nl = 1f
            n[0] = attrs[NX] / nl; n[1] = attrs[NY] / nl; n[2] = attrs[NZ] / nl
            // The view vector points from the surface to the eye.
            var vl = sqrt(attrs[VX] * attrs[VX] + attrs[VY] * attrs[VY] + attrs[VZ] * attrs[VZ])
            if (vl < 1e-6f) vl = 1f
            v[0] = -attrs[VX] / vl; v[1] = -attrs[VY] / vl; v[2] = -attrs[VZ] / vl

            var dr = 0f; var dg = 0f; var db = 0f; var da = 1f
            var sr = 0f; var sg = 0f; var sb = 0f; var sa = 1f
            for ((index, light) in lights.withIndex()) {
                val length: Float
                if (light.directional) {
                    light.position.copyInto(l)
                    length = 1f
                } else {
                    l[0] = light.position[0] - attrs[VX]
                    l[1] = light.position[1] - attrs[VY]
                    l[2] = light.position[2] - attrs[VZ]
                    length = sqrt(l[0] * l[0] + l[1] * l[1] + l[2] * l[2])
                    if (length > 1e-6f) { l[0] /= length; l[1] /= length; l[2] /= length }
                }
                h[0] = v[0] + l[0]; h[1] = v[1] + l[1]; h[2] = v[2] + l[2]
                val hLength2 = h[0] * h[0] + h[1] * h[1] + h[2] * h[2]
                val hl = sqrt(hLength2).takeIf { it > 1e-6f } ?: 1f
                hn[0] = h[0] / hl; hn[1] = h[1] / hl; hn[2] = h[2] / hl

                var distAtten = 1f
                val distLut = light.distanceLut
                if (distLut != null) {
                    val loc = (light.distanceScale * length + light.distanceBias).coerceIn(0f, 1f)
                    val i = floor(loc * 256f).coerceIn(0f, 255f).toInt()
                    distAtten = distLut.value(i) + distLut.delta(i) * (loc * 256f - i)
                }

                fun input(kind: Int): Float = when (kind) {
                    0 -> dot(n, hn)
                    1 -> dot(v, hn)
                    2 -> dot(n, v)
                    3 -> dot(l, n)
                    4 -> light.spotDirection?.let { dot(l, it) } ?: 0f
                    5 -> 0f
                    else -> 0f
                }

                fun lookup(lut: CgfxLut, kind: Int, scale: Float): Float {
                    var x = input(kind)
                    val index: Int
                    val delta: Float
                    if (lut.isAbsolute) {
                        x = if (light.twoSided) abs(x) else max(x, 0f)
                        index = floor(x * 256f).coerceIn(0f, 255f).toInt()
                        delta = x * 256f - index
                    } else {
                        val signed = floor(x * 128f).coerceIn(-128f, 127f).toInt()
                        delta = x * 128f - signed
                        index = signed and 0xFF
                    }
                    return scale * (lut.value(index) + lut.delta(index) * delta)
                }

                var spotAtten = 1f
                val spotLut = light.spotLut
                if (spotLut != null && spotSupported) spotAtten = lookup(spotLut, light.spotInput, light.spotScale)

                val d0v = d0?.let { lookup(it.lut, it.input, it.scale) } ?: 1f
                val reflR = rr?.let { lookup(it.lut, it.input, it.scale) } ?: 1f
                val reflG = rg?.let { lookup(it.lut, it.input, it.scale) } ?: reflR
                val reflB = rb?.let { lookup(it.lut, it.input, it.scale) } ?: reflR
                val d1v = d1?.let { lookup(it.lut, it.input, it.scale) } ?: 1f

                if (index == lights.size - 1 && fr != null) {
                    val f = lookup(fr.lut, fr.input, fr.scale)
                    if (fresnelPrimary) da = f
                    if (fresnelSecondary) sa = f
                }

                var nDotL = dot(l, n)
                nDotL = if (light.twoSided) abs(nDotL) else max(nDotL, 0f)
                val clamp = if (clampHighlights && nDotL == 0f) 0f else 1f
                var g0 = 1f; var g1 = 1f
                if (geo0 || geo1) {
                    val g = if (hLength2 == 0f) 0f else min(nDotL / hLength2, 1f)
                    if (geo0) g0 = g
                    if (geo1) g1 = g
                }
                val att = distAtten * spotAtten
                val s0 = d0v * g0
                val s1 = d1v * g1
                dr += (light.diffuse[0] * nDotL + light.ambient[0]) * att
                dg += (light.diffuse[1] * nDotL + light.ambient[1]) * att
                db += (light.diffuse[2] * nDotL + light.ambient[2]) * att
                val k = clamp * att
                sr += (light.specular0[0] * s0 + light.specular1[0] * reflR * s1) * k
                sg += (light.specular0[1] * s0 + light.specular1[1] * reflG * s1) * k
                sb += (light.specular0[2] * s0 + light.specular1[2] * reflB * s1) * k
            }
            dr += global[0]; dg += global[1]; db += global[2]
            primary[0] = toU8(dr); primary[1] = toU8(dg); primary[2] = toU8(db); primary[3] = toU8(da)
            secondary[0] = toU8(sr); secondary[1] = toU8(sg); secondary[2] = toU8(sb); secondary[3] = toU8(sa)
        }

        private fun dot(a: FloatArray, b: FloatArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
        private fun toU8(x: Float): Int = (x.coerceIn(0f, 1f) * 255f).toInt()
    }

    // --- fragment stage --------------------------------------------------------------------

    /**
     * One material's fragment pipeline, in the PICA200's own 8-bit arithmetic (a port of Azahar's
     * software combiner): textures, fragment lighting and the six combiner stages.
     */
    private class Shading(
        private val material: CgfxMaterial,
        state: CgfxAnimator.MaterialState?,
        private val lighting: MaterialLighting?,
    ) {
        private val colors: Map<String, Rgba> = state?.colors ?: emptyMap()

        private fun color(name: String): Rgba = colors[name] ?: material.colors.byName(name) ?: Rgba.WHITE

        val diffuse = color("Diffuse")
        private val stages = material.texEnvStages
        private val constants: Array<IntArray> = Array(6) { index ->
            val stage = stages[index]
            val c = when (stage.constantSlot) {
                in 0..5 -> color("Constant${stage.constantSlot}")
                6 -> color("Emission")
                7 -> color("Ambient")
                8 -> diffuse
                9 -> color("Specular0")
                10 -> color("Specular1")
                else -> stage.color
            }
            intArrayOf(u8(c.r), u8(c.g), u8(c.b), u8(c.a))
        }
        private val usesFragmentColors = stages.any { s ->
            (s.colorSources + s.alphaSources).any { it == CgfxTexEnvStage.SOURCE_FRAGMENT_PRIMARY || it == CgfxTexEnvStage.SOURCE_FRAGMENT_SECONDARY }
        }
        private val bufferColor = material.texEnvBufferColor.let { intArrayOf(u8(it.r), u8(it.g), u8(it.b), u8(it.a)) }

        // Scratch per fragment.
        private val texF = FloatArray(4)
        private val tex = Array(3) { IntArray(4) }
        private val texSampled = BooleanArray(3)
        private val primary = IntArray(4)
        private val fragPrimary = IntArray(4)
        private val fragSecondary = IntArray(4)
        private val output = IntArray(4)
        private val buffer = IntArray(4)
        private val nextBuffer = IntArray(4)
        private val zero = IntArray(4)
        private val colorIn = Array(3) { IntArray(3) }
        private val alphaIn = IntArray(3)

        fun shade(attrs: FloatArray, out: FloatArray, textures: Map<String, Texture>): Boolean {
            primary[0] = rnd(attrs[R]); primary[1] = rnd(attrs[G]); primary[2] = rnd(attrs[B]); primary[3] = rnd(attrs[A])
            texSampled.fill(false)
            if (usesFragmentColors) {
                if (lighting != null) lighting.compute(attrs, fragPrimary, fragSecondary)
                else { fragPrimary.fill(0); fragSecondary.fill(0) }
            }
            output.fill(0)
            buffer.fill(0)
            bufferColor.copyInto(nextBuffer)
            for (index in 0 until 6) {
                val stage = stages[index]
                val cs = stage.colorSources
                // Stage 0 has no previous result: "previous" in its first two slots reads the third source.
                val s0 = if (index == 0 && cs[0] == CgfxTexEnvStage.SOURCE_PREVIOUS) cs[2] else cs[0]
                val s1 = if (index == 0 && cs[1] == CgfxTexEnvStage.SOURCE_PREVIOUS) cs[2] else cs[1]
                colorModifier(stage.colorOperands[0], source(s0, index, attrs, textures), colorIn[0])
                colorModifier(stage.colorOperands[1], source(s1, index, attrs, textures), colorIn[1])
                colorModifier(stage.colorOperands[2], source(cs[2], index, attrs, textures), colorIn[2])
                val cr: Int; val cg: Int; val cb: Int
                when (stage.colorCombiner) {
                    6, 7 -> {
                        val a = colorIn[0]; val b = colorIn[1]
                        var d = 0
                        for (k in 0..2) d += ((a[k] * 2 - 255) * (b[k] * 2 - 255) + 128) / 256
                        d = d.coerceIn(0, 255)
                        cr = d; cg = d; cb = d
                    }
                    else -> {
                        cr = combine(stage.colorCombiner, colorIn[0][0], colorIn[1][0], colorIn[2][0])
                        cg = combine(stage.colorCombiner, colorIn[0][1], colorIn[1][1], colorIn[2][1])
                        cb = combine(stage.colorCombiner, colorIn[0][2], colorIn[1][2], colorIn[2][2])
                    }
                }
                val alpha = if (stage.colorCombiner == 7) cr else {
                    val asrc = stage.alphaSources
                    for (k in 0..2) alphaIn[k] = alphaModifier(stage.alphaOperands[k], source(asrc[k], index, attrs, textures))
                    if (stage.alphaCombiner == 6 || stage.alphaCombiner == 7) alphaIn[0]
                    else combine(stage.alphaCombiner, alphaIn[0], alphaIn[1], alphaIn[2])
                }
                val cm = 1 shl stage.colorScale.coerceIn(0, 2)
                val am = 1 shl stage.alphaScale.coerceIn(0, 2)
                output[0] = min(255, cr * cm); output[1] = min(255, cg * cm); output[2] = min(255, cb * cm)
                output[3] = min(255, alpha * am)
                nextBuffer.copyInto(buffer)
                // The parsed flags sit one stage late (as SPICA stores them): the flag on stage
                // i + 1 is the hardware bit for stage i writing its result into the buffer.
                val following = stages.getOrNull(index + 1)
                if (following?.updateColorBuffer == true) { nextBuffer[0] = output[0]; nextBuffer[1] = output[1]; nextBuffer[2] = output[2] }
                if (following?.updateAlphaBuffer == true) nextBuffer[3] = output[3]
            }
            val test = material.alphaTest
            if (test.enabled) {
                val reference = test.reference
                val a = output[3]
                val pass = when (test.function) {
                    0 -> false
                    1 -> true
                    2 -> a == reference
                    3 -> a != reference
                    4 -> a < reference
                    5 -> a <= reference
                    6 -> a > reference
                    else -> a >= reference
                }
                if (!pass) return false
            }
            for (k in 0..3) out[k] = output[k] / 255f
            return true
        }

        private fun combine(op: Int, a: Int, b: Int, c: Int): Int = when (op) {
            0 -> a
            1 -> a * b / 255
            2 -> min(255, a + b)
            3 -> (a + b - 128).coerceIn(0, 255)
            4 -> (a * c + b * (255 - c)) / 255
            5 -> max(0, a - b)
            8 -> min(255, (a * b + 255 * c) / 255)
            9 -> min(255, a + b) * c / 255
            else -> a
        }

        private fun colorModifier(op: Int, v: IntArray, into: IntArray) {
            when (op) {
                0 -> { into[0] = v[0]; into[1] = v[1]; into[2] = v[2] }
                1 -> { into[0] = 255 - v[0]; into[1] = 255 - v[1]; into[2] = 255 - v[2] }
                2 -> { into[0] = v[3]; into[1] = v[3]; into[2] = v[3] }
                3 -> { val x = 255 - v[3]; into[0] = x; into[1] = x; into[2] = x }
                4 -> { into[0] = v[0]; into[1] = v[0]; into[2] = v[0] }
                5 -> { val x = 255 - v[0]; into[0] = x; into[1] = x; into[2] = x }
                8 -> { into[0] = v[1]; into[1] = v[1]; into[2] = v[1] }
                9 -> { val x = 255 - v[1]; into[0] = x; into[1] = x; into[2] = x }
                12 -> { into[0] = v[2]; into[1] = v[2]; into[2] = v[2] }
                13 -> { val x = 255 - v[2]; into[0] = x; into[1] = x; into[2] = x }
                else -> { into[0] = v[0]; into[1] = v[1]; into[2] = v[2] }
            }
        }

        private fun alphaModifier(op: Int, v: IntArray): Int = when (op) {
            0 -> v[3]
            1 -> 255 - v[3]
            2 -> v[0]
            3 -> 255 - v[0]
            4 -> v[1]
            5 -> 255 - v[1]
            6 -> v[2]
            7 -> 255 - v[2]
            else -> v[3]
        }

        private fun source(source: Int, stageIndex: Int, attrs: FloatArray, textures: Map<String, Texture>): IntArray =
            when (source) {
                CgfxTexEnvStage.SOURCE_PRIMARY_COLOR -> primary
                CgfxTexEnvStage.SOURCE_FRAGMENT_PRIMARY -> fragPrimary
                CgfxTexEnvStage.SOURCE_FRAGMENT_SECONDARY -> fragSecondary
                CgfxTexEnvStage.SOURCE_TEXTURE0, CgfxTexEnvStage.SOURCE_TEXTURE1, CgfxTexEnvStage.SOURCE_TEXTURE2 -> {
                    val unit = source - CgfxTexEnvStage.SOURCE_TEXTURE0
                    if (!texSampled[unit]) {
                        sample(unit, attrs, textures, texF)
                        val t = tex[unit]
                        t[0] = rnd(texF[0]); t[1] = rnd(texF[1]); t[2] = rnd(texF[2]); t[3] = rnd(texF[3])
                        texSampled[unit] = true
                    }
                    tex[unit]
                }
                CgfxTexEnvStage.SOURCE_PREVIOUS_BUFFER -> buffer
                CgfxTexEnvStage.SOURCE_CONSTANT -> constants[stageIndex]
                CgfxTexEnvStage.SOURCE_PREVIOUS -> output
                else -> zero
            }

        private fun rnd(x: Float): Int = (x.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        private fun u8(x: Float): Int = (x.coerceIn(0f, 1f) * 255f + 0.5f).toInt()

        private fun sample(unit: Int, attrs: FloatArray, textures: Map<String, Texture>, into: FloatArray) {
            val mapper = material.textureMappers.getOrNull(unit)
            val texture = mapper?.textureName?.let { textures[it] }
            if (mapper == null || texture == null) { into[0] = 0f; into[1] = 0f; into[2] = 0f; into[3] = 1f; return }
            // Unit 2 reads coordinate 1 under the 0110 / 0111 / 0112 configurations.
            val coord = if (unit == 2 && material.texCoordConfig in 1..3) 1 else unit
            val u = attrs[U0 + coord * 2]
            val v = attrs[V0 + coord * 2]
            // PICA textures are stored top row first while v = 0 is the bottom edge.
            val fx = u * texture.width - 0.5f
            val fy = (1f - v) * texture.height - 0.5f
            if (mapper.magFilter == 0) {
                fetch(texture, mapper, floor(fx + 0.5f).toInt(), floor(fy + 0.5f).toInt(), into, 1f, true)
                return
            }
            val x0 = floor(fx).toInt(); val y0 = floor(fy).toInt()
            val tx = fx - x0; val ty = fy - y0
            fetch(texture, mapper, x0, y0, into, (1 - tx) * (1 - ty), true)
            fetch(texture, mapper, x0 + 1, y0, into, tx * (1 - ty), false)
            fetch(texture, mapper, x0, y0 + 1, into, (1 - tx) * ty, false)
            fetch(texture, mapper, x0 + 1, y0 + 1, into, tx * ty, false)
        }

        private fun fetch(texture: Texture, mapper: CgfxTextureMapper, x: Int, y: Int, into: FloatArray, weight: Float, first: Boolean) {
            val ix = wrap(x, texture.width, mapper.wrapU)
            val iy = wrap(y, texture.height, mapper.wrapV)
            if (first) into.fill(0f)
            if (ix < 0 || iy < 0) {
                val b = mapper.borderColor
                into[0] += b.r * weight; into[1] += b.g * weight; into[2] += b.b * weight; into[3] += b.a * weight
                return
            }
            val i = (iy * texture.width + ix) * 4
            into[0] += texture.rgba[i] * weight
            into[1] += texture.rgba[i + 1] * weight
            into[2] += texture.rgba[i + 2] * weight
            into[3] += texture.rgba[i + 3] * weight
        }

        /** Returns -1 for "use the border colour". */
        private fun wrap(value: Int, size: Int, mode: Int): Int = when (mode) {
            1 -> if (value < 0 || value >= size) -1 else value
            2 -> ((value % size) + size) % size
            3 -> {
                val period = size * 2
                val m = ((value % period) + period) % period
                if (m < size) m else period - 1 - m
            }
            else -> value.coerceIn(0, size - 1)
        }
    }
}
