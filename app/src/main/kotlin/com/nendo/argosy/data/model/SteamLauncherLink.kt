package com.nendo.argosy.data.model

data class SteamLauncherLinkPrompt(
    val gameId: Long,
    val title: String,
    val coverPath: String? = null
)

data class SteamMarkOption(
    val launcherPackage: String,
    val displayName: String
)
