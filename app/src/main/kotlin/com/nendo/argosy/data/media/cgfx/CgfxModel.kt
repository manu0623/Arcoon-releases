package com.nendo.argosy.data.media.cgfx

/*
 * A 3DS CGFX container (the format of every Home Menu banner), read into plain data.
 *
 * The layouts follow SPICA's CtrGfx reader (gdkchan, public domain): every structure here is the
 * field list SPICA declares, read in the same order with the same self-relative pointers. Only the
 * parts a banner needs are kept: models (meshes, shapes, materials, skeleton), textures and the
 * skeletal / material / visibility animations.
 */

/** A row-major 3x4 matrix: three rows of (x, y, z, translation). */
class Mat3x4(val m: FloatArray = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f)) {
    operator fun get(row: Int, col: Int): Float = m[row * 4 + col]

    companion object {
        val IDENTITY = Mat3x4()
    }
}

class Vec3(val x: Float, val y: Float, val z: Float) {
    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
        val ONE = Vec3(1f, 1f, 1f)
    }
}

/** A colour as four floats, 0..1. */
class Rgba(val r: Float, val g: Float, val b: Float, val a: Float) {
    companion object {
        val WHITE = Rgba(1f, 1f, 1f, 1f)
        val BLACK = Rgba(0f, 0f, 0f, 0f)

        /** PICA packs colours as R in the low byte. */
        fun fromPacked(value: Int): Rgba = Rgba(
            (value and 0xFF) / 255f,
            ((value ushr 8) and 0xFF) / 255f,
            ((value ushr 16) and 0xFF) / 255f,
            ((value ushr 24) and 0xFF) / 255f,
        )
    }
}

class CgfxFile(
    val revision: Int,
    val models: List<CgfxModel>,
    val textures: Map<String, CgfxTexture>,
    val skeletalAnimations: List<CgfxAnimation>,
    val materialAnimations: List<CgfxAnimation>,
    val visibilityAnimations: List<CgfxAnimation>,
    val cameras: List<CgfxCamera>,
    /** Lighting lookup tables: table set name -> sampler name -> table. */
    val luts: Map<String, Map<String, CgfxLut>> = emptyMap(),
    val lights: List<CgfxLight> = emptyList(),
    val scenes: List<CgfxScene> = emptyList(),
) {
    fun lut(reference: CgfxLutRef?): CgfxLut? = reference?.let { luts[it.table]?.get(it.sampler) }

    companion object {
        /**
         * A banner's common scene with one language's container laid on top, as the Home Menu
         * shows them: the language container adds its own models (usually the localised logo) and
         * its textures replace common ones of the same name.
         */
        fun overlay(common: CgfxFile, local: CgfxFile): CgfxFile = CgfxFile(
            revision = common.revision,
            models = common.models + local.models.filter { m -> common.models.none { it.name == m.name } },
            textures = common.textures + local.textures,
            skeletalAnimations = common.skeletalAnimations + local.skeletalAnimations,
            materialAnimations = common.materialAnimations + local.materialAnimations,
            visibilityAnimations = common.visibilityAnimations + local.visibilityAnimations,
            cameras = common.cameras + local.cameras,
            luts = local.luts + common.luts,
            lights = common.lights + local.lights.filter { l -> common.lights.none { it.name == l.name } },
            scenes = common.scenes + local.scenes,
        )
    }
}

/** A 256-entry PICA lighting table: each entry is a 12-bit value plus a signed 11-bit delta. */
class CgfxLut(val isAbsolute: Boolean, val entries: IntArray) {
    fun value(index: Int): Float = (entries[index] and 0xFFF) / 4095f
    fun delta(index: Int): Float {
        val raw = entries[index]
        val diff = ((raw ushr 12) and 0x7FF) / 2047f
        return if ((raw ushr 23) and 1 != 0) -diff else diff
    }
}

class CgfxLutRef(val table: String, val sampler: String)

/** One lighting table a material uses: what it is indexed by and how its output is scaled. */
class CgfxMaterialLut(
    /** 0 N.H, 1 V.H, 2 N.V, 3 L.N, 4 -L.P (spot), 5 cos phi. */
    val input: Int,
    /** PICA scale code: 0 x1, 1 x2, 2 x4, 3 x8, 6 x0.25, 7 x0.5. */
    val scale: Int,
    val reference: CgfxLutRef?,
)

enum class CgfxLightKind { FRAGMENT, AMBIENT, VERTEX, HEMISPHERE }

class CgfxLight(
    val name: String,
    val kind: CgfxLightKind,
    val isEnabled: Boolean,
    /** 0 positional (point), 1 directional, 2 spot. */
    val type: Int,
    val nodeFlags: Int,
    val worldTransform: Mat3x4,
    val ambient: Rgba,
    val diffuse: Rgba,
    val specular0: Rgba,
    val specular1: Rgba,
    val direction: Vec3,
    val distanceLut: CgfxLutRef?,
    val angleLut: CgfxMaterialLut?,
    val attenuationStart: Float,
    val attenuationEnd: Float,
) {
    val isTwoSidedDiffuse: Boolean get() = nodeFlags and (1 shl 1) != 0
    val isDistanceAttenuationEnabled: Boolean get() = nodeFlags and (1 shl 2) != 0
}

class CgfxScene(
    val name: String,
    val cameraNames: List<String>,
    /** Light set index -> names of the lights in it. */
    val lightSets: Map<Int, List<String>>,
)

class CgfxModel(
    val name: String,
    val isVisible: Boolean,
    val transform: Mat3x4,
    val meshes: List<CgfxMesh>,
    val materials: List<CgfxMaterial>,
    val shapes: List<CgfxShape>,
    /** Mesh node visibility, by node name (what visibility animations switch). */
    val meshNodes: List<Pair<String, Boolean>>,
    val skeleton: CgfxSkeleton?,
)

class CgfxMesh(
    val name: String,
    val shapeIndex: Int,
    val materialIndex: Int,
    val isVisible: Boolean,
    val renderPriority: Int,
    val meshNodeIndex: Int,
    val meshNodeName: String?,
)

class CgfxShape(
    val name: String,
    val positionOffset: Vec3,
    val subMeshes: List<CgfxSubMesh>,
    val buffers: List<CgfxVertexBuffer>,
)

enum class CgfxSkinning { NONE, RIGID, SMOOTH }

class CgfxSubMesh(
    /** Palette: index stored in a vertex's bone attribute -> index in the skeleton. */
    val boneIndices: IntArray,
    val skinning: CgfxSkinning,
    val faces: List<CgfxFaceDescriptor>,
)

class CgfxFaceDescriptor(
    /** 0 triangles, 1 strip, 2 fan. */
    val primitiveMode: Int,
    val indices: IntArray,
)

/** PICA attribute names (PICAAttributeName). */
object CgfxAttr {
    const val POSITION = 0
    const val NORMAL = 1
    const val TANGENT = 2
    const val COLOR = 3
    const val TEXCOORD0 = 4
    const val TEXCOORD1 = 5
    const val TEXCOORD2 = 6
    const val BONE_INDEX = 7
    const val BONE_WEIGHT = 8
}

/** GL data types used by vertex and index buffers. */
object CgfxGlType {
    const val BYTE = 0x1400
    const val UNSIGNED_BYTE = 0x1401
    const val SHORT = 0x1402
    const val UNSIGNED_SHORT = 0x1403
    const val FLOAT = 0x1406
}

sealed class CgfxVertexBuffer(val attrName: Int) {
    /** One attribute with its own buffer (or a sub-attribute of an interleaved buffer, at [offset]). */
    class Attribute(
        attrName: Int,
        val raw: ByteArray?,
        val format: Int,
        val elements: Int,
        val scale: Float,
        val offset: Int,
    ) : CgfxVertexBuffer(attrName)

    class Interleaved(
        val raw: ByteArray,
        val stride: Int,
        val attributes: List<Attribute>,
    ) : CgfxVertexBuffer(CgfxAttr.POSITION)

    /** The same value for every vertex. */
    class Fixed(attrName: Int, val values: FloatArray) : CgfxVertexBuffer(attrName)
}

class CgfxSkeleton(val bones: List<CgfxBone>)

enum class CgfxBillboard { OFF, WORLD, WORLD_VIEWPOINT, SCREEN, SCREEN_VIEWPOINT, Y_AXIAL, Y_AXIAL_VIEWPOINT }

class CgfxBone(
    val name: String,
    val flags: Int,
    val index: Int,
    val parentIndex: Int,
    val scale: Vec3,
    val rotation: Vec3,
    val translation: Vec3,
    val worldTransform: Mat3x4,
    val inverseWorldTransform: Mat3x4,
    val billboard: CgfxBillboard,
) {
    val isSegmentScaleCompensate: Boolean get() = flags and (1 shl 5) != 0
}

class CgfxTexture(
    val name: String,
    val width: Int,
    val height: Int,
    /** PICATextureFormat: 0 RGBA8, 1 RGB8, 2 RGBA5551, 3 RGB565, 4 RGBA4, 5 LA8, 6 HiLo8, 7 L8, 8 A8, 9 LA4, 10 L4, 11 A4, 12 ETC1, 13 ETC1A4. */
    val format: Int,
    val mipmaps: Int,
    /** Base level first, PICA-tiled, as stored. */
    val data: ByteArray,
)

// --- Materials -------------------------------------------------------------------------------

class CgfxMaterialColors(
    val emission: Rgba,
    val ambient: Rgba,
    val diffuse: Rgba,
    val specular0: Rgba,
    val specular1: Rgba,
    val constants: List<Rgba>,
)

class CgfxTextureCoord(
    val sourceCoordIndex: Int,
    /** 0 UV map, 1 camera cube env, 2 camera sphere env, 3 projection, 4 shadow, 5 shadow box. */
    val mappingType: Int,
    /** 0 Maya, 1 SoftImage, 2 3ds Max: decides how scale/rotate/translate combine. */
    val transformType: Int,
    val scaleU: Float,
    val scaleV: Float,
    val rotation: Float,
    val translateU: Float,
    val translateV: Float,
)

class CgfxTextureMapper(
    val textureName: String?,
    val borderColor: Rgba,
    /** 0 nearest, 1 linear. */
    val magFilter: Int,
    val minFilter: Int,
    /** 0 clamp to edge, 1 clamp to border, 2 repeat, 3 mirror. */
    val wrapU: Int,
    val wrapV: Int,
)

/** One texture combiner stage (PICATexEnvStage). */
class CgfxTexEnvStage(
    val colorSources: IntArray,
    val alphaSources: IntArray,
    val colorOperands: IntArray,
    val alphaOperands: IntArray,
    val colorCombiner: Int,
    val alphaCombiner: Int,
    /** 0 = x1, 1 = x2, 2 = x4. */
    val colorScale: Int,
    val alphaScale: Int,
    /** Which material colour feeds the Constant source (GfxTexEnvConstant). */
    val constantSlot: Int,
    val color: Rgba,
    val updateColorBuffer: Boolean,
    val updateAlphaBuffer: Boolean,
) {
    val isColorPassThrough: Boolean
        get() = colorCombiner == 0 && colorSources[0] == SOURCE_PREVIOUS && colorOperands[0] == 0 &&
            colorScale == 0 && !updateColorBuffer
    val isAlphaPassThrough: Boolean
        get() = alphaCombiner == 0 && alphaSources[0] == SOURCE_PREVIOUS && alphaOperands[0] == 0 &&
            alphaScale == 0 && !updateAlphaBuffer

    companion object {
        const val SOURCE_PRIMARY_COLOR = 0
        const val SOURCE_FRAGMENT_PRIMARY = 1
        const val SOURCE_FRAGMENT_SECONDARY = 2
        const val SOURCE_TEXTURE0 = 3
        const val SOURCE_TEXTURE1 = 4
        const val SOURCE_TEXTURE2 = 5
        const val SOURCE_TEXTURE3 = 6
        const val SOURCE_PREVIOUS_BUFFER = 13
        const val SOURCE_CONSTANT = 14
        const val SOURCE_PREVIOUS = 15
    }
}

class CgfxAlphaTest(val enabled: Boolean, val function: Int, val reference: Int)

class CgfxBlend(
    /** 0 none (opaque), 1 blend, 2 blend separate, 3 logical op. */
    val mode: Int,
    /** PICAColorOperation.BlendMode: 1 = blending, 0 = logic op. */
    val blendEnabled: Boolean,
    val colorEquation: Int,
    val alphaEquation: Int,
    val colorSrc: Int,
    val colorDst: Int,
    val alphaSrc: Int,
    val alphaDst: Int,
    val color: Rgba,
)

class CgfxDepth(val testEnabled: Boolean, val function: Int, val write: Boolean)

class CgfxMaterial(
    val name: String,
    val flags: Int,
    val texCoordConfig: Int,
    val translucencyKind: Int,
    val colors: CgfxMaterialColors,
    /** PICA face culling: 0 none, 1 cull front faces, 2 cull back faces. */
    val faceCulling: Int,
    val depth: CgfxDepth,
    val blend: CgfxBlend,
    val usedTextureCoords: Int,
    val textureCoords: List<CgfxTextureCoord>,
    val textureMappers: List<CgfxTextureMapper?>,
    val shaderName: String?,
    val texEnvStages: List<CgfxTexEnvStage>,
    val texEnvBufferColor: Rgba,
    val alphaTest: CgfxAlphaTest,
    /** ClampHighLight 0, Distribution0 1, Distribution1 2, GeometricFactor0 3, GeometricFactor1 4, Reflection 5. */
    val fragmentLightingFlags: Int,
    /** Hardware lighting configuration (0..6, 8): which lookup tables the GPU evaluates. */
    val lightingConfig: Int = 0,
    /** 0 none, 1 primary alpha, 2 secondary alpha, 3 both. */
    val fresnelSelector: Int = 0,
    /** Reflection R, G, B, Distribution 0, Distribution 1, Fresnel. */
    val lightingLuts: List<CgfxMaterialLut?> = List(6) { null },
    val lightSetIndex: Int = 0,
    val fogIndex: Int = 0,
) {
    val isFragmentLightingEnabled: Boolean get() = flags and 1 != 0
    val isVertexLightingEnabled: Boolean get() = flags and 2 != 0
}

class CgfxCamera(
    val name: String,
    val position: Vec3,
    val rotation: Vec3,
    /** 0 aim, 1 look-at, 2 rotate. */
    val viewType: Int,
    val target: Vec3,
    val upVector: Vec3,
    val twist: Float,
    val isPerspective: Boolean,
    val near: Float,
    val far: Float,
    val aspect: Float,
    /** Vertical field of view in radians (perspective). */
    val fovY: Float,
    /** View height (orthographic). */
    val height: Float,
)

// --- Animations ------------------------------------------------------------------------------

class CgfxAnimation(
    val name: String,
    /** "SkeletalAnimation", "MaterialAnimation", "VisibilityAnimation", ... */
    val targetGroup: String,
    val loops: Boolean,
    val frameCount: Float,
    val elements: List<CgfxAnimElement>,
)

sealed class CgfxAnimElement(val name: String) {
    class FloatValue(name: String, val curve: CgfxCurve) : CgfxAnimElement(name)
    class Bool(name: String, val curve: CgfxBoolCurve) : CgfxAnimElement(name)
    class Vector(name: String, val axes: List<CgfxCurve?>) : CgfxAnimElement(name)

    /** Euler transform: each channel null when the animation leaves it at the bone's own value. */
    class Transform(
        name: String,
        val scale: List<CgfxCurve?>,
        val rotation: List<CgfxCurve?>,
        val translation: List<CgfxCurve?>,
    ) : CgfxAnimElement(name)

    /** Baked per-frame scale / quaternion rotation / translation. */
    class QuatTransform(
        name: String,
        val scales: List<Vec3>,
        val rotations: List<FloatArray>,
        val translations: List<Vec3>,
    ) : CgfxAnimElement(name)

    class MtxTransform(name: String, val startFrame: Float, val frames: List<Mat3x4>) : CgfxAnimElement(name)
}

class CgfxKeyFrame(val frame: Float, val value: Float, val inSlope: Float, val outSlope: Float)

enum class CgfxInterpolation { STEP, LINEAR, HERMITE }

class CgfxCurve(val interpolation: CgfxInterpolation, val keys: List<CgfxKeyFrame>) {
    /** SPICA's H3DFloatKeyFrameGroup.GetFrameValue. */
    fun valueAt(frame: Float): Float {
        if (keys.isEmpty()) return 0f
        if (keys.size == 1) return keys[0].value
        var lhs = keys.first()
        var rhs = keys.last()
        for (key in keys) {
            if (key.frame <= frame) lhs = key
            if (key.frame >= frame && key.frame < rhs.frame) rhs = key
        }
        if (lhs.frame == rhs.frame) return lhs.value
        val diff = frame - lhs.frame
        val weight = diff / (rhs.frame - lhs.frame)
        return when (interpolation) {
            CgfxInterpolation.STEP -> lhs.value
            CgfxInterpolation.LINEAR -> lhs.value + (rhs.value - lhs.value) * weight
            CgfxInterpolation.HERMITE -> {
                var result = lhs.value + (lhs.value - rhs.value) * (2 * weight - 3) * weight * weight
                result += (diff * (weight - 1)) * (lhs.outSlope * (weight - 1) + rhs.inSlope * weight)
                result
            }
        }
    }

    companion object {
        fun constant(value: Float) = CgfxCurve(CgfxInterpolation.STEP, listOf(CgfxKeyFrame(0f, value, 0f, 0f)))
    }
}

class CgfxBoolCurve(val startFrame: Float, val values: BooleanArray) {
    fun valueAt(frame: Float): Boolean {
        if (values.isEmpty()) return true
        val index = (frame - startFrame).toInt().coerceIn(0, values.size - 1)
        return values[index]
    }
}
