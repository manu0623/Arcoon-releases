package com.nendo.argosy.data.social.discord

sealed class DiscordPresenceState {
    data object Unavailable : DiscordPresenceState()
    data object Disconnected : DiscordPresenceState()
    data object Connected : DiscordPresenceState()
    data class Active(val title: String) : DiscordPresenceState()
    data class Error(val message: String) : DiscordPresenceState()
}
