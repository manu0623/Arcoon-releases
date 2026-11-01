package com.nendo.argosy.data.preferences

data class SyncFilterPreferences(
    val enabledRegions: List<String> = DEFAULT_REGIONS,
    val regionMode: RegionFilterMode = DEFAULT_REGION_MODE,
    val excludeBeta: Boolean = true,
    val excludePrototype: Boolean = true,
    val excludeDemo: Boolean = true,
    val excludeHack: Boolean = false,
    val excludeUnofficial: Boolean = false,
    val deleteOrphans: Boolean = true
) {
    companion object {
        val ALL_KNOWN_REGIONS = listOf(
            "USA", "World", "Europe", "Japan", "Korea",
            "China", "Taiwan", "Australia", "Brazil",
            "France", "Germany", "Italy", "Spain"
        )
        /**
         * A blacklist of nothing, so a library syncs whole until the user says otherwise. The
         * include default whitelisted [ALL_KNOWN_REGIONS] and silently dropped every rom tagged
         * with a region outside that fixed list, which reads as a broken sync rather than a filter.
         */
        val DEFAULT_REGIONS = emptyList<String>()
        val DEFAULT_REGION_MODE = RegionFilterMode.EXCLUDE
    }
}

enum class RegionFilterMode {
    INCLUDE,
    EXCLUDE
}
