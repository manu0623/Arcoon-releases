package com.nendo.argosy.data.media.cgfx

/**
 * Reads a decompressed CGFX container into [CgfxFile].
 *
 * Every offset below is the field order SPICA's reflection-based deserializer walks for the same
 * class (base class fields first): a reference is a u32 pointer relative to its own position, a
 * list is a u32 count followed by such a pointer, a dictionary is a count followed by a pointer to
 * a "DICT" Patricia tree whose nodes carry (name, value) pointers, and a typed object starts with
 * its u32 type id. Structures marked inline in SPICA are read in place.
 */
object CgfxParser {

    private const val MAX_COUNT = 1 shl 16

    fun parse(bytes: ByteArray): CgfxFile? = try {
        Parser(bytes).file()
    } catch (e: CgfxFormatException) {
        null
    } catch (e: IndexOutOfBoundsException) {
        null
    }

    /** Like [parse], but reports why a container could not be read. */
    fun parseOrThrow(bytes: ByteArray): CgfxFile = Parser(bytes).file()

    class CgfxFormatException(message: String) : Exception(message)

    private class Parser(private val b: ByteArray) {

        // --- primitives ------------------------------------------------------------------

        fun u8(at: Int): Int = b[at].toInt() and 0xFF
        fun u16(at: Int): Int = u8(at) or (u8(at + 1) shl 8)
        fun s16(at: Int): Int = u16(at).toShort().toInt()
        fun u24(at: Int): Int = u8(at) or (u8(at + 1) shl 8) or (u8(at + 2) shl 16)
        fun i32(at: Int): Int = u8(at) or (u8(at + 1) shl 8) or (u8(at + 2) shl 16) or (u8(at + 3) shl 24)
        fun f32(at: Int): Float = java.lang.Float.intBitsToFloat(i32(at))
        fun vec3(at: Int) = Vec3(f32(at), f32(at + 4), f32(at + 8))
        fun mat3x4(at: Int) = Mat3x4(FloatArray(12) { f32(at + it * 4) })
        fun rgbaF(at: Int) = Rgba(f32(at), f32(at + 4), f32(at + 8), f32(at + 12))

        /** A self-relative pointer, or null when zero. */
        fun ptr(at: Int): Int? {
            val value = i32(at)
            if (value == 0) return null
            val target = at + value
            if (target < 0 || target >= b.size) throw CgfxFormatException("pointer at 0x${at.toString(16)} leaves the file")
            return target
        }

        fun str(at: Int): String? {
            val start = ptr(at) ?: return null
            var end = start
            while (end < b.size && b[end] != 0.toByte()) end++
            return String(b, start, end - start, Charsets.US_ASCII)
        }

        fun count(at: Int): Int {
            val n = i32(at)
            if (n < 0 || n > MAX_COUNT) throw CgfxFormatException("count $n at 0x${at.toString(16)}")
            return n
        }

        /** A list of references: count, then a pointer to that many pointers. */
        fun refList(at: Int): List<Int> {
            val n = count(at)
            if (n == 0) return emptyList()
            val table = ptr(at + 4) ?: return emptyList()
            return (0 until n).mapNotNull { ptr(table + it * 4) }
        }

        /** A list of inline values: count, then a pointer to the first; returns (start, count). */
        fun valueList(at: Int): Pair<Int, Int>? {
            val n = count(at)
            if (n == 0) return null
            val start = ptr(at + 4) ?: return null
            return start to n
        }

        fun bytesList(at: Int): ByteArray? {
            val n = i32(at)
            if (n <= 0) return null
            val start = ptr(at + 4) ?: return null
            if (start + n > b.size) throw CgfxFormatException("buffer at 0x${at.toString(16)} leaves the file")
            return b.copyOfRange(start, start + n)
        }

        /** A GfxDict: count, then a pointer to a DICT whose node 0 is the root. */
        fun dict(at: Int): List<Pair<String?, Int>> {
            val n = count(at)
            if (n == 0) return emptyList()
            val tree = ptr(at + 4) ?: return emptyList()
            if (!ascii(tree, "DICT")) throw CgfxFormatException("no DICT at 0x${tree.toString(16)}")
            val values = i32(tree + 8)
            val nodes = tree + 12
            return (1..values).mapNotNull { index ->
                val node = nodes + index * 16
                val value = ptr(node + 12) ?: return@mapNotNull null
                str(node + 8) to value
            }
        }

        fun ascii(at: Int, magic: String): Boolean =
            at + magic.length <= b.size && magic.indices.all { b[at + it].toInt() == magic[it].code }

        // --- file ------------------------------------------------------------------------

        fun file(): CgfxFile {
            if (!ascii(0, "CGFX")) throw CgfxFormatException("not a CGFX container")
            val headerLength = u16(6)
            val revision = i32(8)
            val data = headerLength
            if (!ascii(data, "DATA")) throw CgfxFormatException("no DATA section")
            // Gfx: sixteen dictionaries after the section header (magic + length).
            val dicts = data + 8
            fun section(index: Int) = dict(dicts + index * 8)

            val textures = LinkedHashMap<String, CgfxTexture>()
            for ((name, offset) in section(1)) {
                val texture = runCatching { texture(offset, name) }.getOrNull() ?: continue
                textures[texture.name] = texture
            }
            val models = section(0).mapNotNull { (_, offset) -> runCatching { model(offset) }.getOrNull() }
            fun animations(index: Int) = section(index).mapNotNull { (_, offset) ->
                runCatching { animation(offset) }.getOrNull()
            }
            val luts = LinkedHashMap<String, Map<String, CgfxLut>>()
            for ((name, offset) in section(2)) {
                val table = runCatching { lutTable(offset) }.getOrNull() ?: continue
                luts[objectName(offset).ifEmpty { name ?: "" }] = table
            }
            return CgfxFile(
                revision = revision,
                models = models,
                textures = textures,
                skeletalAnimations = animations(9),
                materialAnimations = animations(10),
                visibilityAnimations = animations(11),
                cameras = section(5).mapNotNull { (_, offset) -> runCatching { camera(offset) }.getOrNull() },
                luts = luts,
                lights = section(6).mapNotNull { (_, offset) -> runCatching { light(offset) }.getOrNull() },
                scenes = section(8).mapNotNull { (_, offset) -> runCatching { scene(offset) }.getOrNull() },
            )
        }

        // --- objects ---------------------------------------------------------------------

        /** GfxObject: type id, revision header, name, metadata dict. Fields follow at +24. */
        fun objectName(at: Int): String = str(at + 12) ?: ""

        fun model(at: Int): CgfxModel {
            val type = i32(at)
            if (type != 0x40000012 && type != 0x40000092) throw CgfxFormatException("not a model")
            val worldTransform = mat3x4(at + 132)
            val meshes = refList(at + 180).map { mesh(it) }
            val materials = dict(at + 188).map { (_, offset) -> material(offset) }
            val shapes = refList(at + 196).map { shape(it) }
            val meshNodes = dict(at + 204).map { (name, offset) ->
                (str(offset) ?: name ?: "") to (i32(offset + 4) != 0)
            }
            val flags = i32(at + 212)
            val skeleton = if (type == 0x40000092) ptr(at + 224)?.let { skeleton(it) } else null
            return CgfxModel(
                name = objectName(at),
                isVisible = flags and 1 != 0,
                transform = worldTransform,
                meshes = meshes,
                materials = materials,
                shapes = shapes,
                meshNodes = meshNodes,
                skeleton = skeleton,
            )
        }

        fun mesh(at: Int) = CgfxMesh(
            name = objectName(at),
            shapeIndex = i32(at + 24),
            materialIndex = i32(at + 28),
            isVisible = u8(at + 36) != 0,
            renderPriority = u8(at + 37),
            meshNodeIndex = s16(at + 38),
            meshNodeName = str(at + 112),
        )

        fun shape(at: Int): CgfxShape {
            val subMeshes = refList(at + 44).map { subMesh(it) }
            val buffers = refList(at + 56).mapNotNull { vertexBuffer(it) }
            return CgfxShape(
                name = objectName(at),
                positionOffset = vec3(at + 32),
                subMeshes = subMeshes,
                buffers = buffers,
            )
        }

        fun subMesh(at: Int): CgfxSubMesh {
            val bones = valueList(at)?.let { (start, n) -> IntArray(n) { i32(start + it * 4) } } ?: IntArray(0)
            val skinning = when (i32(at + 8)) {
                1 -> CgfxSkinning.RIGID
                2 -> CgfxSkinning.SMOOTH
                else -> CgfxSkinning.NONE
            }
            val faces = refList(at + 12).flatMap { face -> refList(face).map { faceDescriptor(it) } }
            return CgfxSubMesh(bones, skinning, faces)
        }

        fun faceDescriptor(at: Int): CgfxFaceDescriptor {
            val format = i32(at)
            val mode = u8(at + 4)
            val raw = bytesList(at + 8) ?: ByteArray(0)
            val indices = if (format == CgfxGlType.UNSIGNED_SHORT) {
                IntArray(raw.size / 2) { (raw[it * 2].toInt() and 0xFF) or ((raw[it * 2 + 1].toInt() and 0xFF) shl 8) }
            } else {
                IntArray(raw.size) { raw[it].toInt() and 0xFF }
            }
            return CgfxFaceDescriptor(mode, indices)
        }

        fun vertexBuffer(at: Int): CgfxVertexBuffer? = when (i32(at)) {
            0x40000001 -> attribute(at)
            0x40000002 -> CgfxVertexBuffer.Interleaved(
                raw = bytesList(at + 20) ?: ByteArray(0),
                stride = i32(at + 36),
                attributes = refList(at + 40).mapNotNull { if (i32(it) == 0x40000001) attribute(it) else null },
            )
            0x80000000.toInt() -> {
                val values = valueList(at + 24)?.let { (start, n) -> FloatArray(n) { f32(start + it * 4) } } ?: FloatArray(0)
                val scale = f32(at + 20)
                CgfxVertexBuffer.Fixed(i32(at + 4), FloatArray(values.size) { values[it] * scale })
            }
            else -> null
        }

        fun attribute(at: Int) = CgfxVertexBuffer.Attribute(
            attrName = i32(at + 4),
            raw = bytesList(at + 20),
            format = i32(at + 36),
            elements = i32(at + 40),
            scale = f32(at + 44),
            offset = i32(at + 48),
        )

        fun skeleton(at: Int): CgfxSkeleton {
            val bones = dict(at + 24).map { (_, offset) -> bone(offset) }.sortedBy { it.index }
            return CgfxSkeleton(bones)
        }

        fun bone(at: Int) = CgfxBone(
            name = str(at) ?: "",
            flags = i32(at + 4),
            index = i32(at + 8),
            parentIndex = i32(at + 12),
            scale = vec3(at + 32),
            rotation = vec3(at + 44),
            translation = vec3(at + 56),
            worldTransform = mat3x4(at + 116),
            inverseWorldTransform = mat3x4(at + 164),
            billboard = when (i32(at + 212)) {
                2 -> CgfxBillboard.WORLD
                3 -> CgfxBillboard.WORLD_VIEWPOINT
                4 -> CgfxBillboard.SCREEN
                5 -> CgfxBillboard.SCREEN_VIEWPOINT
                6 -> CgfxBillboard.Y_AXIAL
                7 -> CgfxBillboard.Y_AXIAL_VIEWPOINT
                else -> CgfxBillboard.OFF
            },
        )

        fun texture(at: Int, dictName: String?): CgfxTexture? {
            if (i32(at) != 0x20000011) return null // only plain 2D images; cube maps are not banner material
            val image = ptr(at + 56) ?: return null
            val data = bytesList(image + 8) ?: return null
            return CgfxTexture(
                name = objectName(at).ifEmpty { dictName ?: "" },
                width = i32(at + 28),
                height = i32(at + 24),
                format = i32(at + 52),
                mipmaps = i32(at + 40),
                data = data,
            )
        }

        // --- materials -------------------------------------------------------------------

        fun material(at: Int): CgfxMaterial {
            val colors = CgfxMaterialColors(
                emission = rgbaF(at + 36),
                ambient = rgbaF(at + 52),
                diffuse = rgbaF(at + 68),
                specular0 = rgbaF(at + 84),
                specular1 = rgbaF(at + 100),
                constants = (0 until 6).map { rgbaF(at + 116 + it * 16) },
            )
            // The rasterisation command is what the GPU obeys (the enum beside it is ignored):
            // PICA face culling 0 = none, 1 = front faces, 2 = back faces.
            val cullCommand = PicaCommands.read(this, at + 272, 2)[PicaCommands.FACECULLING_CONFIG]
            val faceCulling = cullCommand?.and(3) ?: when (i32(at + 264)) {
                0 -> 1
                1 -> 2
                3 -> 0
                else -> 1
            }

            val depthCommands = PicaCommands.read(this, at + 284, 4)
            val depthFlags = i32(at + 280)
            val depthMask = depthCommands[PicaCommands.DEPTH_COLOR_MASK]
            val depth = CgfxDepth(
                testEnabled = depthFlags and 1 != 0,
                function = depthMask?.let { (it ushr 4) and 7 } ?: 5,
                write = depthFlags and 2 != 0,
            )

            val blendMode = i32(at + 300)
            val blendCommands = PicaCommands.read(this, at + 320, 6)
            val colorOp = blendCommands[PicaCommands.COLOR_OPERATION] ?: 0
            val function = blendCommands[PicaCommands.BLEND_FUNC] ?: 0
            val blend = CgfxBlend(
                mode = blendMode,
                blendEnabled = (colorOp ushr 8) and 1 != 0,
                colorEquation = function and 7,
                alphaEquation = (function ushr 8) and 7,
                colorSrc = (function ushr 16) and 0xF,
                colorDst = (function ushr 20) and 0xF,
                alphaSrc = (function ushr 24) and 0xF,
                alphaDst = (function ushr 28) and 0xF,
                color = Rgba.fromPacked(blendCommands[PicaCommands.BLEND_COLOR] ?: 0),
            )

            val usedCoords = i32(at + 360)
            val coords = (0 until 3).map { index ->
                val c = at + 364 + index * 88
                CgfxTextureCoord(
                    sourceCoordIndex = i32(c),
                    mappingType = i32(c + 4),
                    transformType = i32(c + 12),
                    scaleU = f32(c + 16),
                    scaleV = f32(c + 20),
                    rotation = f32(c + 24),
                    translateU = f32(c + 28),
                    translateV = f32(c + 32),
                )
            }
            val mappers = (0 until 3).map { index -> ptr(at + 628 + index * 4)?.let { textureMapper(it, index) } }
            val shaderName = ptr(at + 644)?.let { str(it + 24) ?: objectName(it) }
            val fragmentShader = ptr(at + 648) ?: throw CgfxFormatException("material without fragment shader")

            // Fragment shader: buffer colour, lighting, LUTs, six combiner stages, alpha test, commands.
            val shaderCommands = PicaCommands.read(this, fragmentShader + 220, 6)
            val update = shaderCommands[PicaCommands.TEXENV_UPDATE_BUFFER] ?: 0
            val bufferColor = shaderCommands[PicaCommands.TEXENV_BUFFER_COLOR]?.let { Rgba.fromPacked(it) }
                ?: rgbaF(fragmentShader)
            val stages = (0 until 6).map { index ->
                val stage = fragmentShader + 44 + index * 28
                val commands = PicaCommands.read(this, stage + 4, 6)
                val base = PicaCommands.texEnvBase(index)
                val source = commands[base] ?: 0x000F000F
                val operand = commands[base + 1] ?: 0
                val combiner = commands[base + 2] ?: 0
                val color = commands[base + 3] ?: 0
                val scale = commands[base + 4] ?: 0
                CgfxTexEnvStage(
                    colorSources = intArrayOf(source and 0xF, (source ushr 4) and 0xF, (source ushr 8) and 0xF),
                    alphaSources = intArrayOf((source ushr 16) and 0xF, (source ushr 20) and 0xF, (source ushr 24) and 0xF),
                    colorOperands = intArrayOf(operand and 0xF, (operand ushr 4) and 0xF, (operand ushr 8) and 0xF),
                    alphaOperands = intArrayOf((operand ushr 12) and 7, (operand ushr 16) and 7, (operand ushr 20) and 7),
                    colorCombiner = combiner and 0xF,
                    alphaCombiner = (combiner ushr 16) and 0xF,
                    colorScale = scale and 3,
                    alphaScale = (scale ushr 16) and 3,
                    constantSlot = i32(stage),
                    color = Rgba.fromPacked(color),
                    updateColorBuffer = index in 1..4 && update and (0x100 shl (index - 1)) != 0,
                    updateAlphaBuffer = index in 1..4 && update and (0x1000 shl (index - 1)) != 0,
                )
            }
            // Fragment lighting: flags, layer configuration, Fresnel selector, then the six tables
            // (reflection R/G/B, distribution 0/1, Fresnel), each an input, a scale and a reference.
            val layer = i32(fragmentShader + 20)
            val lightingTables = ptr(fragmentShader + 40)?.let { table ->
                List(6) { index -> ptr(table + index * 4)?.let { materialLut(it) } }
            } ?: List(6) { null }
            val alphaCommands = PicaCommands.read(this, fragmentShader + 212, 2)
            val alpha = alphaCommands[PicaCommands.FRAGOP_ALPHA_TEST] ?: 0
            return CgfxMaterial(
                name = objectName(at),
                flags = i32(at + 24),
                texCoordConfig = i32(at + 28),
                translucencyKind = i32(at + 32),
                colors = colors,
                faceCulling = faceCulling,
                depth = depth,
                blend = blend,
                usedTextureCoords = usedCoords,
                textureCoords = coords,
                textureMappers = mappers,
                shaderName = shaderName,
                texEnvStages = stages,
                texEnvBufferColor = bufferColor,
                alphaTest = CgfxAlphaTest(alpha and 1 != 0, (alpha ushr 4) and 7, (alpha ushr 8) and 0xFF),
                fragmentLightingFlags = i32(fragmentShader + 16),
                lightingConfig = if (layer >= 7) 8 else layer.coerceAtLeast(0),
                fresnelSelector = i32(fragmentShader + 24) and 3,
                lightingLuts = lightingTables,
                lightSetIndex = i32(at + 664),
                fogIndex = i32(at + 668),
            )
        }

        /** GfxFragLightLUT: input, scale, then a reference naming the table set and sampler. */
        fun materialLut(at: Int) = CgfxMaterialLut(
            input = i32(at) and 7,
            scale = i32(at + 4) and 7,
            reference = ptr(at + 8)?.let { lutRef(it) },
        )

        /** GfxLUTReference: type, table set name, sampler name, runtime target. */
        fun lutRef(at: Int): CgfxLutRef? {
            val table = str(at + 4) ?: return null
            val sampler = str(at + 8) ?: return null
            return CgfxLutRef(table, sampler)
        }

        /** GfxLUT: its samplers, each a name, the absolute-input flag and the PICA upload commands. */
        fun lutTable(at: Int): Map<String, CgfxLut> = dict(at + 24).mapNotNull { (dictName, sampler) ->
            val name = str(sampler + 4) ?: dictName ?: return@mapNotNull null
            val raw = bytesList(sampler + 12) ?: return@mapNotNull null
            name to CgfxLut(i32(sampler + 8) != 0, PicaCommands.lutEntries(raw))
        }.toMap()

        fun light(at: Int): CgfxLight? {
            val kind = when (i32(at)) {
                0x400000a2 -> CgfxLightKind.FRAGMENT
                0x40000422 -> CgfxLightKind.AMBIENT
                0x40000222 -> CgfxLightKind.VERTEX
                0x40000122 -> CgfxLightKind.HEMISPHERE
                else -> return null
            }
            val world = mat3x4(at + 132)
            if (kind == CgfxLightKind.AMBIENT) {
                val color = rgbaF(at + 184)
                return CgfxLight(
                    objectName(at), kind, i32(at + 180) != 0, 0, i32(at + 24), world,
                    color, Rgba.BLACK, Rgba.BLACK, Rgba.BLACK, Vec3.ZERO, null, null, 0f, 0f,
                )
            }
            if (kind != CgfxLightKind.FRAGMENT) {
                return CgfxLight(
                    objectName(at), kind, i32(at + 180) != 0, i32(at + 184), i32(at + 24), world,
                    Rgba.BLACK, Rgba.BLACK, Rgba.BLACK, Rgba.BLACK, Vec3.ZERO, null, null, 0f, 0f,
                )
            }
            return CgfxLight(
                name = objectName(at),
                kind = kind,
                isEnabled = i32(at + 180) != 0,
                type = i32(at + 184),
                nodeFlags = i32(at + 24),
                worldTransform = world,
                ambient = rgbaF(at + 188),
                diffuse = rgbaF(at + 204),
                specular0 = rgbaF(at + 220),
                specular1 = rgbaF(at + 236),
                direction = vec3(at + 268),
                distanceLut = ptr(at + 280)?.let { lutRef(it) },
                angleLut = ptr(at + 284)?.let { materialLut(it) },
                attenuationStart = f32(at + 288),
                attenuationEnd = f32(at + 292),
            )
        }

        fun camera(at: Int): CgfxCamera? {
            if (i32(at) != 0x4000000a) return null
            val view = ptr(at + 188)
            val projection = ptr(at + 192) ?: return null
            val viewKind = view?.let { i32(it) } ?: 0
            var target = Vec3(0f, 0f, 0f)
            var up = Vec3(0f, 1f, 0f)
            var twist = 0f
            when (viewKind) {
                0x80000000.toInt() -> { target = vec3(view!! + 8); twist = f32(view + 20) }
                0x40000000 -> { target = vec3(view!! + 8); up = vec3(view + 20) }
                0x20000000 -> { target = vec3(view!! + 8) } // rotation, stored in target
            }
            val projectionKind = i32(projection)
            return CgfxCamera(
                name = objectName(at),
                position = vec3(at + 72),
                rotation = vec3(at + 60),
                viewType = when (viewKind) {
                    0x80000000.toInt() -> 0
                    0x40000000 -> 1
                    else -> 2
                },
                target = target,
                upVector = up,
                twist = twist,
                isPerspective = projectionKind == 0x20000000,
                near = f32(projection + 4),
                far = f32(projection + 8),
                aspect = f32(projection + 12),
                fovY = if (projectionKind == 0x20000000) f32(projection + 16) else 0f,
                height = if (projectionKind == 0x20000000) 0f else f32(projection + 16),
            )
        }

        fun scene(at: Int): CgfxScene {
            val cameras = refList(at + 24).mapNotNull { str(it + 4) }
            val sets = LinkedHashMap<Int, List<String>>()
            for (set in refList(at + 32)) {
                sets[i32(set)] = refList(set + 4).mapNotNull { str(it + 4) }
            }
            return CgfxScene(objectName(at), cameras, sets)
        }

        fun textureMapper(at: Int, unit: Int): CgfxTextureMapper {
            val reference = ptr(at + 8)
            val textureName = reference?.let { str(it + 24) ?: objectName(it) }
            val commands = PicaCommands.read(this, at + 16, 14)
            val param = commands[PicaCommands.texUnitParam(unit)] ?: 0
            return CgfxTextureMapper(
                textureName = textureName,
                borderColor = Rgba.fromPacked(commands[PicaCommands.texUnitBorder(unit)] ?: 0),
                magFilter = (param ushr 1) and 1,
                minFilter = (param ushr 2) and 1,
                wrapV = (param ushr 8) and 3,
                wrapU = (param ushr 12) and 3,
            )
        }

        // --- animations ------------------------------------------------------------------

        fun animation(at: Int): CgfxAnimation {
            if (!ascii(at, "CANM")) throw CgfxFormatException("not an animation")
            val revision = i32(at + 4)
            val elements = dict(at + 24).mapNotNull { (_, offset) ->
                runCatching { animElement(offset, revision) }.getOrNull()
            }
            return CgfxAnimation(
                name = str(at + 8) ?: "",
                targetGroup = str(at + 12) ?: "",
                loops = i32(at + 16) == 1,
                frameCount = f32(at + 20),
                elements = elements,
            )
        }

        fun animElement(at: Int, revision: Int): CgfxAnimElement? {
            val flags = i32(at)
            val name = str(at + 4) ?: return null
            val typeAt = if (revision <= 0x05000000) at + 16 else at + 8
            val content = typeAt + 4
            return when (i32(typeAt)) {
                0 -> slots(content, flags, 1, 0, 1)[0]?.let { CgfxAnimElement.FloatValue(name, it) }
                2 -> boolCurve(content)?.let { CgfxAnimElement.Bool(name, it) }
                3 -> CgfxAnimElement.Vector(name, slots(content, flags, 2, 0, 2))
                4 -> CgfxAnimElement.Vector(name, slots(content, flags, 3, 0, 3))
                5 -> {
                    val all = slots(content, flags, 10, 6, 16)
                    CgfxAnimElement.Transform(
                        name,
                        scale = all.subList(0, 3),
                        rotation = all.subList(3, 6),
                        translation = all.subList(7, 10),
                    )
                }
                6 -> CgfxAnimElement.Vector(name, slots(content, flags, 4, 0, 4))
                8 -> quatTransform(name, content, flags)
                9 -> {
                    val frames = valueList(content + 12)?.let { (start, n) -> List(n) { mat3x4(start + it * 48) } } ?: emptyList()
                    CgfxAnimElement.MtxTransform(name, f32(content), frames)
                }
                else -> null
            }
        }

        /**
         * [n] four-byte slots at [at], each a constant float or a pointer to a key frame group.
         * Bit `constantBit + i` of [flags] marks slot i constant, bit `missingBit + i` absent.
         */
        fun slots(at: Int, flags: Int, n: Int, constantBit: Int, missingBit: Int): List<CgfxCurve?> = List(n) { i ->
            val slot = at + i * 4
            when {
                flags and (1 shl (missingBit + i)) != 0 -> null
                flags and (1 shl (constantBit + i)) != 0 -> CgfxCurve.constant(f32(slot))
                else -> ptr(slot)?.let { keyFrameGroup(it) }
            }
        }

        /** GfxFloatKeyFrameGroup: frames, repeat modes, curve flags, then a constant or a curve. */
        fun keyFrameGroup(at: Int): CgfxCurve? {
            val curveFlags = i32(at + 12)
            if (curveFlags and 2 != 0) return CgfxCurve.constant(f32(at + 16))
            val curve = ptr(at + 20) ?: return null
            val formatFlags = i32(curve + 8)
            val keyCount = count(curve + 12)
            val quantization = formatFlags ushr 5
            val isLinear = formatFlags and 4 != 0
            var cursor = curve + 20
            var valueScale = 1f
            var valueOffset = 0f
            var frameScale = 1f
            if (quantization != 0 && quantization != 3 && quantization != 6) {
                valueScale = f32(cursor)
                valueOffset = f32(cursor + 4)
                frameScale = f32(cursor + 8)
                cursor += 12
            }
            val keys = ArrayList<CgfxKeyFrame>(keyCount)
            repeat(keyCount) {
                val key: CgfxKeyFrame
                when (quantization) {
                    0 -> { key = CgfxKeyFrame(f32(cursor), f32(cursor + 4), f32(cursor + 8), f32(cursor + 12)); cursor += 16 }
                    1 -> {
                        val frameValue = i32(cursor)
                        key = CgfxKeyFrame(
                            (frameValue and 0xFFF).toFloat(),
                            ((frameValue ushr 12) and 0xFFFFF).toFloat(),
                            s16(cursor + 4) / 256f,
                            s16(cursor + 6) / 256f,
                        )
                        cursor += 8
                    }
                    2 -> {
                        val slopes = u24(cursor + 3)
                        key = CgfxKeyFrame(
                            u8(cursor).toFloat(),
                            u16(cursor + 1).toFloat(),
                            ((slopes shl 20) shr 20) / 32f,
                            ((slopes shl 8) shr 20) / 32f,
                        )
                        cursor += 6
                    }
                    3 -> {
                        val slope = f32(cursor + 8)
                        key = CgfxKeyFrame(f32(cursor), f32(cursor + 4), slope, slope); cursor += 12
                    }
                    4 -> {
                        val slope = s16(cursor + 4) / 256f
                        key = CgfxKeyFrame(u16(cursor) / 32f, u16(cursor + 2).toFloat(), slope, slope); cursor += 6
                    }
                    5 -> {
                        val valueSlope = u24(cursor + 1)
                        val slope = ((valueSlope shl 8) shr 20) / 32f
                        key = CgfxKeyFrame(u8(cursor).toFloat(), (valueSlope and 0xFFF).toFloat(), slope, slope); cursor += 4
                    }
                    6 -> { key = CgfxKeyFrame(f32(cursor), f32(cursor + 4), 0f, 0f); cursor += 8 }
                    7 -> {
                        val frameValue = i32(cursor)
                        key = CgfxKeyFrame((frameValue and 0xFFF).toFloat(), ((frameValue ushr 12) and 0xFFFFF).toFloat(), 0f, 0f)
                        cursor += 4
                    }
                    else -> throw CgfxFormatException("key quantization $quantization")
                }
                keys += CgfxKeyFrame(
                    frame = key.frame * frameScale,
                    value = key.value * valueScale + valueOffset,
                    inSlope = key.inSlope,
                    outSlope = key.outSlope,
                )
            }
            val interpolation = when {
                quantization == 6 || quantization == 7 -> if (isLinear) CgfxInterpolation.LINEAR else CgfxInterpolation.STEP
                else -> CgfxInterpolation.HERMITE
            }
            return CgfxCurve(interpolation, keys)
        }

        fun boolCurve(at: Int): CgfxBoolCurve? {
            val header = ptr(at) ?: return null
            val start = f32(header)
            val end = f32(header + 4)
            val bits = ptr(header + 12) ?: return null
            val n = (end - start).toInt().coerceIn(0, MAX_COUNT)
            return CgfxBoolCurve(start, BooleanArray(n) { (u8(bits + it / 8) ushr (it % 8)) and 1 != 0 })
        }

        fun quatTransform(name: String, at: Int, flags: Int): CgfxAnimElement {
            // Pointers in the order rotation, translation, scale; flags list scale, rotation, translation.
            val addresses = arrayOf(ptr(at + 8), ptr(at), ptr(at + 4))
            val scales = ArrayList<Vec3>()
            val rotations = ArrayList<FloatArray>()
            val translations = ArrayList<Vec3>()
            var missingMask = 1 shl 5
            for (element in 0 until 3) {
                val address = addresses[element]
                if (flags and missingMask == 0 && address != null) {
                    val start = f32(address)
                    val end = f32(address + 4)
                    val constant = i32(address + 12) != 0
                    val n = if (constant) 1 else (end - start).toInt().coerceIn(0, MAX_COUNT)
                    var cursor = address + 16
                    repeat(n) {
                        when (element) {
                            0 -> { scales += vec3(cursor); cursor += 12 }
                            1 -> { rotations += floatArrayOf(f32(cursor), f32(cursor + 4), f32(cursor + 8), f32(cursor + 12)); cursor += 16 }
                            else -> { translations += vec3(cursor); cursor += 12 }
                        }
                        cursor += 4 // segment flags
                    }
                }
                missingMask = missingMask ushr 1
            }
            return CgfxAnimElement.QuatTransform(name, scales, rotations, translations)
        }
    }

    /** Decodes the PICA register writes a material stores as raw command words. */
    private object PicaCommands {
        const val FACECULLING_CONFIG = 0x040
        const val TEXENV_UPDATE_BUFFER = 0x0E0
        const val TEXENV_BUFFER_COLOR = 0x0FD
        const val COLOR_OPERATION = 0x100
        const val BLEND_FUNC = 0x101
        const val BLEND_COLOR = 0x103
        const val FRAGOP_ALPHA_TEST = 0x104
        const val DEPTH_COLOR_MASK = 0x107

        fun texEnvBase(stage: Int): Int = if (stage < 4) 0x0C0 + stage * 8 else 0x0F0 + (stage - 4) * 8
        fun texUnitBorder(unit: Int): Int = intArrayOf(0x081, 0x091, 0x099)[unit]
        fun texUnitParam(unit: Int): Int = intArrayOf(0x083, 0x093, 0x09B)[unit]

        /** Reads [words] command words at [at]: (parameter, header) pairs, header = id | mask | extra | consecutive. */
        fun read(p: Parser, at: Int, words: Int): Map<Int, Int> {
            val raw = IntArray(words) { p.i32(at + it * 4) }
            val out = HashMap<Int, Int>()
            var index = 0
            while (index + 1 < raw.size) {
                var parameter = raw[index++]
                val command = raw[index++]
                var id = command and 0xFFFF
                val extra = (command ushr 20) and 0x7FF
                val consecutive = command ushr 31 != 0
                if (command == 0 && parameter == 0) break
                if (consecutive) {
                    for (i in 0..extra) {
                        out[id++] = parameter
                        if (i < extra) {
                            if (index >= raw.size) return out
                            parameter = raw[index++]
                        }
                    }
                } else {
                    // Non-consecutive: every parameter writes the same register; the last one stays.
                    out[id] = parameter
                    for (i in 0 until extra) {
                        if (index >= raw.size) return out
                        out[id] = raw[index++]
                    }
                }
                if (index and 1 != 0) index++
            }
            return out
        }

        const val LIGHTING_LUT_INDEX = 0x1C5
        const val LIGHTING_LUT_DATA0 = 0x1C8
        const val LIGHTING_LUT_DATA7 = 0x1CF

        /** Replays a lookup table upload: an index write, then data writes that advance it. */
        fun lutEntries(bytes: ByteArray): IntArray {
            val raw = IntArray(bytes.size / 4) {
                (bytes[it * 4].toInt() and 0xFF) or ((bytes[it * 4 + 1].toInt() and 0xFF) shl 8) or
                    ((bytes[it * 4 + 2].toInt() and 0xFF) shl 16) or ((bytes[it * 4 + 3].toInt() and 0xFF) shl 24)
            }
            val table = IntArray(256)
            var lutIndex = 0
            var index = 0
            fun write(register: Int, value: Int) {
                when (register) {
                    LIGHTING_LUT_INDEX -> lutIndex = value and 0xFF
                    in LIGHTING_LUT_DATA0..LIGHTING_LUT_DATA7 -> {
                        if (lutIndex < 256) table[lutIndex] = value and 0xFFFFFF
                        lutIndex++
                    }
                }
            }
            while (index + 1 < raw.size) {
                val first = raw[index++]
                val command = raw[index++]
                var id = command and 0xFFFF
                val extra = (command ushr 20) and 0x7FF
                val consecutive = command ushr 31 != 0
                write(id, first)
                for (i in 0 until extra) {
                    if (index >= raw.size) break
                    if (consecutive) id++
                    write(id, raw[index++])
                }
                if (index and 1 != 0) index++
            }
            return table
        }
    }
}
