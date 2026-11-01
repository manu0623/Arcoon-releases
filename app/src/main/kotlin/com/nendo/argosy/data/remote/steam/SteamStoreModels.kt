package com.nendo.argosy.data.remote.steam

import android.text.Html
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SteamAppDetailsResponse(
    val success: Boolean,
    val data: SteamAppData?
)

@JsonClass(generateAdapter = true)
data class SteamAppData(
    val type: String?,
    val name: String,
    @Json(name = "steam_appid") val steamAppId: Long,
    @Json(name = "required_age") val requiredAge: Int?,
    @Json(name = "is_free") val isFree: Boolean?,
    @Json(name = "detailed_description") val detailedDescription: String?,
    @Json(name = "about_the_game") val aboutTheGame: String?,
    @Json(name = "short_description") val shortDescription: String?,
    @Json(name = "header_image") val headerImage: String?,
    @Json(name = "capsule_image") val capsuleImage: String?,
    @Json(name = "capsule_imagev5") val capsuleImageV5: String?,
    val website: String?,
    val developers: List<String>?,
    val publishers: List<String>?,
    val platforms: SteamPlatforms?,
    val metacritic: SteamMetacritic?,
    val categories: List<SteamCategory>?,
    val genres: List<SteamGenre>?,
    val screenshots: List<SteamScreenshot>?,
    val movies: List<SteamMovie>?,
    @Json(name = "release_date") val releaseDate: SteamReleaseDate?,
    @Json(name = "background") val background: String?,
    @Json(name = "background_raw") val backgroundRaw: String?
) {
    val plainShortDescription: String?
        get() = shortDescription
            ?.let { Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString().trim() }
            ?.takeIf { it.isNotEmpty() }
}

@JsonClass(generateAdapter = true)
data class SteamPlatforms(
    val windows: Boolean?,
    val mac: Boolean?,
    val linux: Boolean?
)

@JsonClass(generateAdapter = true)
data class SteamMetacritic(
    val score: Int?,
    val url: String?
)

@JsonClass(generateAdapter = true)
data class SteamCategory(
    val id: Int,
    val description: String?
)

@JsonClass(generateAdapter = true)
data class SteamGenre(
    val id: String,
    val description: String?
)

@JsonClass(generateAdapter = true)
data class SteamScreenshot(
    val id: Int,
    @Json(name = "path_thumbnail") val pathThumbnail: String?,
    @Json(name = "path_full") val pathFull: String?
)

@JsonClass(generateAdapter = true)
data class SteamMovie(
    val id: Int,
    val name: String?,
    val thumbnail: String?,
    val webm: SteamMovieFormat?,
    val mp4: SteamMovieFormat?,
    val highlight: Boolean?
)

@JsonClass(generateAdapter = true)
data class SteamMovieFormat(
    @Json(name = "480") val quality480: String?,
    val max: String?
)

@JsonClass(generateAdapter = true)
data class SteamReleaseDate(
    @Json(name = "coming_soon") val comingSoon: Boolean?,
    val date: String?
)
