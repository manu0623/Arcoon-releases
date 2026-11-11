package com.nendo.argosy.data.model

/** Every provider a RomM server can serve art from, through its store or its cover search. */
enum class ArtProvider { ROMM, SCREENSCRAPER, LAUNCHBOX, STEAMGRIDDB, STEAM }

data class ServerArt(val url: String, val provider: ArtProvider)
