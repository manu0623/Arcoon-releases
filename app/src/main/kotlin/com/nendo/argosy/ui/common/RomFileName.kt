package com.nendo.argosy.ui.common

data class RomFileNameParts(val title: String, val tags: List<String>)

private val TAG_GROUP = Regex("""[(\[]([^)\]]*)[)\]]""")
private val KNOWN_EXTENSIONS_MAX_LENGTH = 5

fun parseRomFileName(fileName: String): RomFileNameParts {
    val withoutExtension = fileName.substringBeforeLast('.', "")
        .takeIf { it.isNotBlank() && fileName.length - it.length <= KNOWN_EXTENSIONS_MAX_LENGTH + 1 }
        ?: fileName

    val tags = TAG_GROUP.findAll(withoutExtension)
        .flatMap { match -> match.groupValues[1].split(',') }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toList()

    val title = TAG_GROUP.replace(withoutExtension, " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .trimEnd('-', '_', ' ')
        .trim()

    return RomFileNameParts(
        title = title.ifEmpty { withoutExtension },
        tags = tags
    )
}
