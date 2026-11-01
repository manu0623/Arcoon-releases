package com.nendo.argosy.domain.model

data class ChangelogEntry(
    val version: String,
    val highlights: List<String>,
    val requiredActions: List<RequiredAction> = emptyList(),
    val requiresActiveSteamAccount: Boolean = false
)

sealed class RequiredAction(
    val actionKey: String? = null
) {
    data object ReloginRomM : RequiredAction("rommConfig")
    data object ResyncLibrary : RequiredAction("syncLibrary")
    data object ClearCache : RequiredAction()
}
