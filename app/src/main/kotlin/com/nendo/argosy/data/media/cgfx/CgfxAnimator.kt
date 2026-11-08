package com.nendo.argosy.data.media.cgfx

import kotlin.math.cos
import kotlin.math.sin

/**
 * The state of a CGFX scene at one frame: every bone's world matrix and every animated material
 * and visibility value. Follows SPICA.Rendering's SkeletalAnimation / MaterialAnimation /
 * VisibilityAnimation: animations bind to bones and materials by name and all of them play at
 * once, which is what the Home Menu does with a banner.
 */
class CgfxAnimator(private val file: CgfxFile) {

    /** Frames in one loop of the longest animation (0 when nothing moves). */
    val frameCount: Float = (file.skeletalAnimations + file.materialAnimations + file.visibilityAnimations)
        .maxOfOrNull { it.frameCount } ?: 0f

    class MaterialState(
        val coords: Array<FloatArray>,
        val colors: MutableMap<String, Rgba>,
    )

    class ModelState(
        /** Column-vector 4x4 world matrix per bone, row-major. */
        val boneWorld: Array<FloatArray>,
        val boneInverseBase: Array<FloatArray>,
        val materials: List<MaterialState>,
        val meshNodeVisible: Map<String, Boolean>,
    )

    fun stateAt(frame: Float): List<ModelState> = file.models.map { modelState(it, frame) }

    private fun localFrame(animation: CgfxAnimation, frame: Float): Float {
        val length = animation.frameCount
        if (length <= 0f) return 0f
        return if (animation.loops) ((frame % length) + length) % length else frame.coerceIn(0f, length)
    }

    // --- skeleton --------------------------------------------------------------------------

    private class BoneState(
        var scale: FloatArray,
        var rotation: FloatArray, // quaternion x, y, z, w
        var translation: FloatArray,
    )

    private fun modelState(model: CgfxModel, frame: Float): ModelState {
        val bones = model.skeleton?.bones ?: emptyList()
        val base = Array(bones.size) { Mat4.fromMat3x4(bones[it].inverseWorldTransform).let { inv -> Mat4.invert(inv) ?: Mat4.fromMat3x4(bones[it].worldTransform) } }
        val inverseBase = Array(bones.size) { Mat4.fromMat3x4(bones[it].inverseWorldTransform) }

        val states = bones.map { bone ->
            BoneState(
                floatArrayOf(bone.scale.x, bone.scale.y, bone.scale.z),
                eulerToQuaternion(bone.rotation.x, bone.rotation.y, bone.rotation.z),
                floatArrayOf(bone.translation.x, bone.translation.y, bone.translation.z),
            )
        }
        val byName = bones.withIndex().associate { (index, bone) -> bone.name to index }
        val baked = arrayOfNulls<FloatArray>(bones.size)
        var animated = false
        for (animation in file.skeletalAnimations) {
            val f = localFrame(animation, frame)
            for (element in animation.elements) {
                val index = byName[element.name] ?: continue
                val state = states[index]
                val bone = bones[index]
                when (element) {
                    is CgfxAnimElement.Transform -> {
                        animated = true
                        element.scale.forEachIndexed { axis, curve -> curve?.let { state.scale[axis] = it.valueAt(f) } }
                        element.translation.forEachIndexed { axis, curve -> curve?.let { state.translation[axis] = it.valueAt(f) } }
                        if (element.rotation.any { it != null }) {
                            val euler = floatArrayOf(bone.rotation.x, bone.rotation.y, bone.rotation.z)
                            element.rotation.forEachIndexed { axis, curve -> curve?.let { euler[axis] = it.valueAt(f) } }
                            state.rotation = eulerToQuaternion(euler[0], euler[1], euler[2])
                        }
                    }
                    is CgfxAnimElement.QuatTransform -> {
                        animated = true
                        val whole = f.toInt()
                        val weight = f - whole
                        if (element.scales.isNotEmpty()) state.scale = lerp3(pick(element.scales, whole), pick(element.scales, whole + 1), weight)
                        if (element.rotations.isNotEmpty()) state.rotation = slerp(pick(element.rotations, whole), pick(element.rotations, whole + 1), weight)
                        if (element.translations.isNotEmpty()) state.translation = lerp3(pick(element.translations, whole), pick(element.translations, whole + 1), weight)
                    }
                    is CgfxAnimElement.MtxTransform -> {
                        if (element.frames.isNotEmpty()) {
                            animated = true
                            val i = (f - element.startFrame).toInt().coerceIn(0, element.frames.size - 1)
                            baked[index] = Mat4.fromMat3x4(element.frames[i])
                        }
                    }
                    else -> {}
                }
            }
        }

        val world = if (!animated) base else {
            val out = Array(bones.size) { Mat4.identity() }
            for (index in bones.indices) {
                baked[index]?.let { out[index] = it; return@let } ?: run {
                    val state = states[index]
                    val bone = bones[index]
                    val parent = bone.parentIndex.takeIf { it in 0 until index }
                    val compensate = bone.isSegmentScaleCompensate && parent != null
                    val parentScale = parent?.let { states[it].scale } ?: floatArrayOf(1f, 1f, 1f)
                    val translation = if (compensate) {
                        floatArrayOf(state.translation[0] * parentScale[0], state.translation[1] * parentScale[1], state.translation[2] * parentScale[2])
                    } else {
                        state.translation
                    }
                    var local = Mat4.multiply(Mat4.translation(translation), Mat4.multiply(Mat4.rotation(state.rotation), Mat4.scale(state.scale)))
                    if (compensate) {
                        local = Mat4.multiply(Mat4.scale(floatArrayOf(1f / parentScale[0], 1f / parentScale[1], 1f / parentScale[2])), local)
                    }
                    out[index] = if (parent != null) Mat4.multiply(out[parent], local) else local
                }
            }
            out
        }

        // --- materials ---
        val materials = model.materials.map { material ->
            MaterialState(
                coords = Array(3) { i ->
                    val c = material.textureCoords[i]
                    floatArrayOf(c.scaleU, c.scaleV, c.rotation, c.translateU, c.translateV)
                },
                colors = HashMap(),
            )
        }
        val materialIndex = model.materials.withIndex().associate { (i, m) -> m.name to i }
        for (animation in file.materialAnimations) {
            val f = localFrame(animation, frame)
            for (element in animation.elements) {
                val match = MATERIAL_PATH.matchEntire(element.name) ?: continue
                val state = materialIndex[match.groupValues[1]]?.let { materials[it] } ?: continue
                val field = match.groupValues[2]
                COORD_FIELD.matchEntire(field)?.let { coordMatch ->
                    val coord = coordMatch.groupValues[1].toInt().takeIf { it in 0..2 } ?: return@let
                    val values = state.coords[coord]
                    when (coordMatch.groupValues[2]) {
                        "Scale" -> (element as? CgfxAnimElement.Vector)?.axes?.let { axes ->
                            axes.getOrNull(0)?.let { values[0] = it.valueAt(f) }
                            axes.getOrNull(1)?.let { values[1] = it.valueAt(f) }
                        }
                        "Rotate" -> (element as? CgfxAnimElement.FloatValue)?.let { values[2] = it.curve.valueAt(f) }
                        "Translate" -> (element as? CgfxAnimElement.Vector)?.axes?.let { axes ->
                            axes.getOrNull(0)?.let { values[3] = it.valueAt(f) }
                            axes.getOrNull(1)?.let { values[4] = it.valueAt(f) }
                        }
                    }
                }
                COLOR_FIELD.matchEntire(field)?.let { colorMatch ->
                    val axes = (element as? CgfxAnimElement.Vector)?.axes ?: return@let
                    val base = model.materials[materialIndex.getValue(match.groupValues[1])].colors.byName(colorMatch.groupValues[1]) ?: return@let
                    state.colors[colorMatch.groupValues[1]] = Rgba(
                        axes.getOrNull(0)?.valueAt(f) ?: base.r,
                        axes.getOrNull(1)?.valueAt(f) ?: base.g,
                        axes.getOrNull(2)?.valueAt(f) ?: base.b,
                        axes.getOrNull(3)?.valueAt(f) ?: base.a,
                    )
                }
            }
        }

        // --- visibility ---
        val visibility = HashMap<String, Boolean>()
        for ((name, visible) in model.meshNodes) visibility[name] = visible
        for (animation in file.visibilityAnimations) {
            val f = localFrame(animation, frame)
            for (element in animation.elements) {
                val match = NODE_VISIBILITY.matchEntire(element.name) ?: continue
                val curve = (element as? CgfxAnimElement.Bool)?.curve ?: continue
                visibility[match.groupValues[1]] = curve.valueAt(f)
            }
        }

        return ModelState(world, inverseBase, materials, visibility)
    }

    private fun <T> pick(list: List<T>, index: Int): T = list[index.coerceIn(0, list.size - 1)]

    private fun lerp3(a: Vec3, b: Vec3, t: Float) = floatArrayOf(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t)

    private fun slerp(a: FloatArray, b: FloatArray, t: Float): FloatArray {
        var dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3]
        val sign = if (dot < 0f) -1f else 1f
        dot *= sign
        val out = FloatArray(4) { a[it] * (1 - t) + b[it] * sign * t }
        val length = kotlin.math.sqrt(out.sumOf { (it * it).toDouble() }).toFloat().takeIf { it > 1e-6f } ?: 1f
        for (i in 0..3) out[i] /= length
        return out
    }

    companion object {
        private val MATERIAL_PATH = Regex("""Materials\["(.+)"]\.(.+)""")
        private val COORD_FIELD = Regex("""TextureCoordinators\[(\d)]\.(Scale|Rotate|Translate)""")
        private val COLOR_FIELD = Regex("""MaterialColor\.(\w+)""")
        private val NODE_VISIBILITY = Regex("""MeshNodeVisibilities\["(.+)"]\.IsVisible""")

        /** SPICA's SkeletalAnimation.CalculateQuaternion (X, then Y, then Z). */
        fun eulerToQuaternion(x: Float, y: Float, z: Float): FloatArray {
            val sx = sin(x * 0.5); val sy = sin(y * 0.5); val sz = sin(z * 0.5)
            val cx = cos(x * 0.5); val cy = cos(y * 0.5); val cz = cos(z * 0.5)
            return floatArrayOf(
                (cz * sx * cy - sz * cx * sy).toFloat(),
                (cz * cx * sy + sz * sx * cy).toFloat(),
                (sz * cx * cy - cz * sx * sy).toFloat(),
                (cz * cx * cy + sz * sx * sy).toFloat(),
            )
        }
    }
}

fun CgfxMaterialColors.byName(name: String): Rgba? = when (name) {
    "Emission" -> emission
    "Ambient" -> ambient
    "Diffuse" -> diffuse
    "Specular0" -> specular0
    "Specular1" -> specular1
    "Constant0" -> constants[0]
    "Constant1" -> constants[1]
    "Constant2" -> constants[2]
    "Constant3" -> constants[3]
    "Constant4" -> constants[4]
    "Constant5" -> constants[5]
    else -> null
}

/** Column-vector 4x4 matrices stored row-major in a FloatArray(16). */
object Mat4 {
    fun identity() = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)

    fun fromMat3x4(m: Mat3x4): FloatArray = FloatArray(16).also {
        for (i in 0 until 12) it[i] = m.m[i]
        it[15] = 1f
    }

    fun multiply(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(16)
        for (r in 0..3) for (c in 0..3) {
            var sum = 0f
            for (k in 0..3) sum += a[r * 4 + k] * b[k * 4 + c]
            out[r * 4 + c] = sum
        }
        return out
    }

    fun translation(t: FloatArray) = identity().also { it[3] = t[0]; it[7] = t[1]; it[11] = t[2] }

    fun scale(s: FloatArray) = identity().also { it[0] = s[0]; it[5] = s[1]; it[10] = s[2] }

    fun rotation(q: FloatArray): FloatArray {
        val (x, y, z, w) = q
        return floatArrayOf(
            1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w), 0f,
            2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w), 0f,
            2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y), 0f,
            0f, 0f, 0f, 1f,
        )
    }


    fun invert(m: FloatArray): FloatArray? {
        val inv = FloatArray(16)
        inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15] + m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10]
        inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15] - m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10]
        inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15] + m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9]
        inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14] - m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9]
        inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15] - m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10]
        inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15] + m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10]
        inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15] - m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9]
        inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14] + m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9]
        inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15] + m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6]
        inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15] - m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6]
        inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15] + m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5]
        inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14] - m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5]
        inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] - m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6]
        inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] + m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6]
        inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] - m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5]
        inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] + m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5]
        val det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12]
        if (kotlin.math.abs(det) < 1e-12f) return null
        for (i in 0..15) inv[i] /= det
        return inv
    }
}
