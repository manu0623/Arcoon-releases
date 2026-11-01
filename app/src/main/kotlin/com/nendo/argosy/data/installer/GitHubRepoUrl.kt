package com.nendo.argosy.data.installer

object GitHubRepoUrl {

    private val SEGMENT = Regex("^[A-Za-z0-9._-]+$")

    fun parse(input: String): GitHubRepoRef? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        val withoutScheme = trimmed
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")

        val path = when {
            withoutScheme.startsWith("github.com/") -> withoutScheme.removePrefix("github.com/")
            withoutScheme.contains('/') && !withoutScheme.contains('.') -> withoutScheme
            else -> return null
        }

        val segments = path.substringBefore('?').substringBefore('#').split('/')
        if (segments.size < 2) return null

        val owner = segments[0]
        val name = segments[1].removeSuffix(".git")
        if (!SEGMENT.matches(owner) || !SEGMENT.matches(name)) return null

        return GitHubRepoRef(owner = owner, name = name)
    }
}
