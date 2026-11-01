package com.nendo.argosy.data.iconpack

data class AppFilterEntry(val component: String?, val drawable: String?)

/**
 * An icon pack's appfilter: which drawable in the pack replaces which app component. Keyed by
 * the ADW/Nova `ComponentInfo{package/class}` form; an entry whose component or drawable cannot
 * be read is dropped.
 */
class AppFilter private constructor(
    private val byComponent: Map<String, String>,
    private val byPackage: Map<String, String>
) {

    val isEmpty: Boolean get() = byComponent.isEmpty()

    /**
     * The resource id of the pack drawable for [packageName], preferring the entry for its launch
     * activity [className] and falling back to the first entry for the package. [idOf] maps a
     * drawable name to a resource id, 0 when the pack has no such drawable; null when nothing maps.
     */
    fun resolve(packageName: String, className: String?, idOf: (String) -> Int): Int? =
        candidateNames(packageName, className)
            .map(idOf)
            .firstOrNull { it != 0 }

    private fun candidateNames(packageName: String, className: String?): Sequence<String> =
        sequenceOf(
            className?.let { byComponent[componentKey(packageName, qualify(packageName, it))] },
            byPackage[packageName]
        ).filterNotNull().distinct()

    companion object {
        private const val PREFIX = "ComponentInfo{"
        private const val SUFFIX = "}"

        val EMPTY = AppFilter(emptyMap(), emptyMap())

        fun fromEntries(entries: Iterable<AppFilterEntry>): AppFilter {
            val byComponent = LinkedHashMap<String, String>()
            val byPackage = LinkedHashMap<String, String>()
            for (entry in entries) {
                val drawable = entry.drawable?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                val (packageName, className) = parseComponent(entry.component) ?: continue
                byComponent.getOrPut(componentKey(packageName, className)) { drawable }
                byPackage.getOrPut(packageName) { drawable }
            }
            return AppFilter(byComponent, byPackage)
        }

        fun parseComponent(raw: String?): Pair<String, String>? {
            val value = raw?.trim() ?: return null
            if (!value.startsWith(PREFIX) || !value.endsWith(SUFFIX)) return null
            val inner = value.substring(PREFIX.length, value.length - SUFFIX.length)
            val slash = inner.indexOf('/')
            if (slash <= 0) return null
            val packageName = inner.substring(0, slash).trim()
            val className = inner.substring(slash + 1).trim()
            if (packageName.isEmpty() || className.isEmpty() || className == ".") return null
            return packageName to qualify(packageName, className)
        }

        private fun qualify(packageName: String, className: String): String =
            if (className.startsWith(".")) packageName + className else className

        private fun componentKey(packageName: String, className: String): String =
            "$packageName/$className"
    }
}
