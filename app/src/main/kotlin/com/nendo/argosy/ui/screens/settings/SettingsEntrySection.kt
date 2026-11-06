package com.nendo.argosy.ui.screens.settings

/**
 * Resolves the `section` route argument a settings entry receives.
 *
 * A caller that navigates with the route template (`settings?section={section}...`) instead of a
 * built route hands the destination its literal placeholder. That is not a section, and treating
 * it as one made [SettingsScreen] believe it had opened straight into a page, so it covered the
 * whole list until its fallback timeout elapsed.
 */
internal fun settingsEntrySection(raw: String?): SettingsSection? {
    val name = raw?.trim().orEmpty()
    if (name.isEmpty()) return null
    if (name.startsWith("{") && name.endsWith("}")) return null
    return SettingsSection.entries.find { it.name.equals(name, ignoreCase = true) }
}

/** True when the entry opens on a deeper page and must cover the list while that page comes up. */
internal fun settingsEntryCovered(raw: String?): Boolean {
    val section = settingsEntrySection(raw) ?: return false
    return section != SettingsSection.MAIN
}
