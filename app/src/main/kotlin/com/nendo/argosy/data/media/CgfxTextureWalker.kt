package com.nendo.argosy.data.media

/**
 * One texture found by [CgfxTextureWalker]: what it is and where its bytes sit.
 */
internal data class CgfxTexture(
    val width: Int,
    val height: Int,
    val format: Int,
    val mipCount: Int,
    val dataOffset: Int,
    val dataSize: Int,
    val layout: String,
)

internal sealed interface CgfxWalkResult {
    data class Success(
        val textures: List<CgfxTexture>,
        val declaredTextureCount: Int,
        val txobCount: Int,
        val rejected: List<String>,
    ) : CgfxWalkResult

    data class Failure(val reason: String) : CgfxWalkResult
}

/**
 * Walks a decompressed CGFX container to its texture objects instead of scanning for pixels.
 *
 * The path is the documented one: `CGFX -> DATA -> DICT[1] (textures) -> object offsets -> TXOB`.
 * Offsets in CGFX are self-relative and the basis differs between revisions and between this
 * format's writers, so each one is resolved by trying the plausible bases and keeping the one
 * that actually lands on the expected magic. A texture is then accepted only when its declared
 * byte count equals what its format and dimensions require, which is a predicate a run of
 * arbitrary bytes does not satisfy. Nothing here guesses a pixel offset.
 */
internal object CgfxTextureWalker {

    private const val MIN_AXIS = 8
    private const val MAX_AXIS = 2048
    private const val MAX_ENTRIES = 4096

    /**
     * Field offsets inside a TXOB, relative to its magic. 3dbrew's table places a flags word at
     * 0x0 and the magic at 0x4; containers that write the magic first shift every field by 4, so
     * both readings are tried and the arithmetic picks the right one.
     */
    private class Layout(
        val name: String,
        val width: Int,
        val height: Int,
        val mipCount: Int,
        val format: Int,
        val dataSize: Int,
        val dataOffset: Int,
    )

    private val LAYOUTS: List<Layout> = listOf(
        Layout("magic-first", width = 0x18, height = 0x14, mipCount = 0x24, format = 0x30, dataSize = 0x40, dataOffset = 0x44),
        Layout("3dbrew", width = 0x1C, height = 0x18, mipCount = 0x28, format = 0x34, dataSize = 0x44, dataOffset = 0x48),
        Layout("3dbrew-transposed", width = 0x18, height = 0x1C, mipCount = 0x2C, format = 0x34, dataSize = 0x44, dataOffset = 0x48),
        Layout("magic-first-wide", width = 0x20, height = 0x1C, mipCount = 0x2C, format = 0x38, dataSize = 0x48, dataOffset = 0x4C),
    )

    fun walk(cgfx: ByteArray): CgfxWalkResult {
        if (cgfx.size < 0x18) return CgfxWalkResult.Failure("CGFX header is truncated")
        if (!hasAscii(cgfx, 0, "CGFX")) return CgfxWalkResult.Failure("CGFX magic missing")

        val headerSize = readU16(cgfx, 0x06).coerceAtLeast(0x14)
        val dataSection = when {
            headerSize + 4 <= cgfx.size && hasAscii(cgfx, headerSize, "DATA") -> headerSize
            else -> findAscii(cgfx, "DATA", 0, minOf(cgfx.size, 0x400))
        } ?: return CgfxWalkResult.Failure("CGFX has no DATA section")

        // DICT index 1 is the texture table. Per 3dbrew the table starts at the DATA magic, so
        // index N's count sits at +0x8 + N*8 and its self-relative offset at +0xC + N*8.
        val textureCountField = dataSection + 0x10
        val declaredTextureCount = if (textureCountField + 4 <= cgfx.size) {
            readU32(cgfx, textureCountField).coerceIn(0, MAX_ENTRIES)
        } else {
            0
        }
        val dictOffsetField = dataSection + 0x14
        val dictSection = if (dictOffsetField + 4 <= cgfx.size) {
            resolve(cgfx, dictOffsetField, readU32(cgfx, dictOffsetField), "DICT")
        } else {
            null
        }

        val txobPositions = LinkedHashSet<Int>()
        if (dictSection != null) {
            val entryCount = readU32(cgfx, dictSection + 0x08).coerceIn(0, MAX_ENTRIES)
            val firstEntry = dictSection + 0x1C
            for (index in 0 until entryCount) {
                val entry = firstEntry + index * 0x10
                if (entry < 0 || entry + 0x10 > cgfx.size) break
                val objectField = entry + 0x0C
                val objectPosition = resolve(cgfx, objectField, readU32(cgfx, objectField), "TXOB")
                if (objectPosition != null) txobPositions.add(objectPosition)
            }
        }
        if (txobPositions.isEmpty()) {
            txobPositions.addAll(findAllAscii(cgfx, "TXOB"))
        }

        val textures = mutableListOf<CgfxTexture>()
        val rejected = mutableListOf<String>()
        for (position in txobPositions) {
            val texture = parseTxob(cgfx, position)
            if (texture != null) {
                textures.add(texture)
            } else if (rejected.size < 12) {
                rejected.add("TXOB@0x${position.toString(16)}: no field layout matched its data size")
            }
        }

        return CgfxWalkResult.Success(
            textures = textures,
            declaredTextureCount = declaredTextureCount,
            txobCount = txobPositions.size,
            rejected = rejected,
        )
    }

    private fun parseTxob(cgfx: ByteArray, position: Int): CgfxTexture? {
        for (layout in LAYOUTS) {
            if (position + layout.dataOffset + 4 > cgfx.size) continue
            val width = readU32(cgfx, position + layout.width)
            val height = readU32(cgfx, position + layout.height)
            if (width !in MIN_AXIS..MAX_AXIS || height !in MIN_AXIS..MAX_AXIS) continue
            if (width % 8 != 0 || height % 8 != 0) continue

            val format = readU32(cgfx, position + layout.format)
            if (CgfxTextureDecoder.bitsPerPixel(format) == null) continue

            val mipCount = readU32(cgfx, position + layout.mipCount)
            if (mipCount !in 0..10) continue
            val baseSize = CgfxTextureDecoder.expectedByteSize(format, width, height) ?: continue
            val chainSize = chainByteSize(format, width, height, mipCount) ?: baseSize
            val declaredSize = readU32(cgfx, position + layout.dataSize)
            if (declaredSize != baseSize && declaredSize != chainSize) continue

            val rawDataOffset = readU32(cgfx, position + layout.dataOffset)
            val bases = intArrayOf(position + layout.dataOffset, position, 0)
            for (base in bases) {
                val absolute = base.toLong() + rawDataOffset.toLong()
                if (absolute < 0L || absolute + declaredSize > cgfx.size) continue
                return CgfxTexture(
                    width = width,
                    height = height,
                    format = format,
                    mipCount = mipCount,
                    dataOffset = absolute.toInt(),
                    dataSize = declaredSize,
                    layout = layout.name,
                )
            }
        }
        return null
    }

    private fun chainByteSize(format: Int, width: Int, height: Int, mipCount: Int): Int? {
        val levels = if (mipCount <= 1) 1 else mipCount
        var total = 0
        var levelWidth = width
        var levelHeight = height
        repeat(levels) {
            val size = CgfxTextureDecoder.expectedByteSize(format, levelWidth, levelHeight) ?: return null
            total += size
            levelWidth = (levelWidth / 2).coerceAtLeast(1)
            levelHeight = (levelHeight / 2).coerceAtLeast(1)
        }
        return total
    }

    /**
     * Resolves a self-relative offset to an absolute position by keeping whichever plausible basis
     * lands on [magic]. Trying the bases and verifying the result beats assuming one convention.
     */
    private fun resolve(cgfx: ByteArray, fieldPosition: Int, value: Int, magic: String): Int? {
        val bases = intArrayOf(fieldPosition, fieldPosition - 4, 0x14, 0)
        for (base in bases) {
            val candidate = base.toLong() + value.toLong()
            if (candidate < 0L || candidate > cgfx.size - 4) continue
            if (hasAscii(cgfx, candidate.toInt(), magic)) return candidate.toInt()
        }
        return null
    }

    private fun hasAscii(bytes: ByteArray, offset: Int, text: String): Boolean {
        if (offset < 0 || offset + text.length > bytes.size) return false
        for (index in text.indices) {
            if (bytes[offset + index] != text[index].code.toByte()) return false
        }
        return true
    }

    private fun findAscii(bytes: ByteArray, text: String, from: Int, until: Int): Int? {
        val limit = minOf(until, bytes.size) - text.length
        var offset = from.coerceAtLeast(0)
        while (offset <= limit) {
            if (hasAscii(bytes, offset, text)) return offset
            offset++
        }
        return null
    }

    private fun findAllAscii(bytes: ByteArray, text: String): List<Int> {
        val positions = mutableListOf<Int>()
        var offset = 0
        while (true) {
            val hit = findAscii(bytes, text, offset, bytes.size) ?: break
            positions.add(hit)
            offset = hit + 1
        }
        return positions
    }

    private fun readU16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun readU32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
}
