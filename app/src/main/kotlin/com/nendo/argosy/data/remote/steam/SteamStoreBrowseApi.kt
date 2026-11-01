package com.nendo.argosy.data.remote.steam

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface SteamStoreBrowseApi {

    @GET("IStoreBrowseService/GetItems/v1")
    suspend fun getItems(@Query("input_json") inputJson: String): Response<SteamBrowseEnvelope>
}

@JsonClass(generateAdapter = true)
data class SteamBrowseEnvelope(
    @Json(name = "response") val response: SteamBrowseResponse? = null
)

@JsonClass(generateAdapter = true)
data class SteamBrowseResponse(
    @Json(name = "store_items") val storeItems: List<SteamBrowseItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class SteamBrowseItem(
    @Json(name = "appid") val appId: Long? = null,
    @Json(name = "assets") val assets: SteamBrowseAssets? = null
)

@JsonClass(generateAdapter = true)
data class SteamBrowseAssets(
    @Json(name = "asset_url_format") val assetUrlFormat: String? = null,
    @Json(name = "library_capsule") val libraryCapsule: String? = null,
    @Json(name = "library_hero") val libraryHero: String? = null
)
