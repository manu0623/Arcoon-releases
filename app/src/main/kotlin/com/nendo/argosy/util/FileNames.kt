package com.nendo.argosy.util

/**
 * Folds a name supplied by a server or an archive into one a volume will accept.
 *
 * Android refuses these characters on emulated and FAT-family storage and fails the create with
 * EPERM rather than substituting anything, so a name has to be folded before it reaches the
 * filesystem. The set mirrors `android.os.FileUtils.buildValidFatFilename`.
 *
 * Refused characters are dropped rather than replaced, so "Legends: Z-A" keeps reading as
 * "Legends Z-A" instead of carrying a placeholder. [normalizeForMatch] exists because that makes
 * the on-disk name differ from the one the server knows: matching has to fold both sides the same
 * way, and it treats an underscore as a space so files a user placed with the older convention
 * still answer to their server name.
 */
object FileNames {

    val INVALID_CHARS = Regex("[\\\\:*?\"<>|/\\x00-\\x1f]")

    private val WHITESPACE_RUN = Regex("\\s+")

    private const val FALLBACK = "file"

    /**
     * A single name as it can exist on disk. Path separators go the way of any other refused
     * character, so the result never reaches outside its directory.
     */
    fun sanitize(name: String): String {
        val folded = INVALID_CHARS.replace(name, "")
            .replace(WHITESPACE_RUN, " ")
            .trim()
            .trimEnd('.', ' ')
        return folded.ifBlank { FALLBACK }
    }

    /**
     * A per-game folder name, folded the way every existing game folder already was: refused
     * characters become `_` and trailing dots stay, so folders made by earlier versions keep
     * resolving. A name that would be blank, `.` or `..` becomes [FALLBACK] instead, so a folder
     * never resolves to its parent or to itself.
     */
    fun gameFolderName(name: String): String {
        val folded = LEGACY_FOLDER_CHARS.replace(name, "_")
            .replace(WHITESPACE_RUN, " ")
            .trim()
            .take(MAX_FOLDER_NAME)
        return if (folded.isBlank() || folded.all { it == '.' }) FALLBACK else folded
    }

    private val LEGACY_FOLDER_CHARS = Regex("[\\\\/:*?\"<>|]")
    private const val MAX_FOLDER_NAME = 200

    /**
     * A platform slug folded as [sanitize] folds a name, with a blank slug returned unchanged.
     */
    fun sanitizeSlug(slug: String): String = if (slug.isBlank()) slug else sanitize(slug)

    /**
     * An archive entry path with every segment folded. Empty, `.` and `..` segments are dropped,
     * so a crafted entry cannot climb out of the directory it is being extracted into.
     */
    fun sanitizeRelativePath(path: String): String =
        path.split('/', '\\')
            .filter { it.isNotEmpty() && it != "." && it != ".." }
            .joinToString("/") { sanitize(it) }

    /**
     * Whether [file] resolves to [dir] itself or somewhere beneath it. Compares whole path
     * segments, so `../Zelda2/x` is outside `Zelda`.
     */
    fun isWithin(file: java.io.File, dir: java.io.File): Boolean {
        val root = dir.canonicalPath
        val path = file.canonicalPath
        return path == root || path.startsWith(root + java.io.File.separator)
    }

    fun isInside(file: java.io.File, dir: java.io.File): Boolean =
        file.canonicalPath.startsWith(dir.canonicalPath + java.io.File.separator)

    /**
     * The comparable form of a name, for deciding whether a file on disk is the one a server
     * reported. Folds what [sanitize] drops, reads an underscore as the space it stood in for,
     * and ignores case and spacing.
     */
    fun normalizeForMatch(name: String): String =
        INVALID_CHARS.replace(name, "")
            .replace('_', ' ')
            .replace(WHITESPACE_RUN, " ")
            .lowercase()
            .trim()
}
