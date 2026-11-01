package com.nendo.argosy.data.download

object ServerRomLayout {

    fun relativeDir(
        filePath: String,
        siblingPaths: List<String>,
        romFolderNames: List<String> = emptyList()
    ): String? {
        val segments = segmentsOf(filePath)
        val rootIndex = segments.indexOfLast { it in romFolderNames }
        if (rootIndex >= 0) {
            return segments.drop(rootIndex + 1).joinToString("/").takeIf { it.isNotEmpty() }
        }
        if (siblingPaths.isEmpty()) return null
        val root = siblingPaths
            .map { segmentsOf(it) }
            .reduce { common, segments ->
                common.zip(segments).takeWhile { it.first == it.second }.map { it.first }
            }
        return segmentsOf(filePath)
            .drop(root.size)
            .joinToString("/")
            .takeIf { it.isNotEmpty() }
    }

    private fun segmentsOf(path: String): List<String> =
        path.trim('/').split('/').filter { it.isNotEmpty() && it != "." && it != ".." }
}
