package com.nendo.argosy.data.remote.gitlab

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface GitLabApi {

    @GET("api/v4/projects/{projectPath}/releases")
    suspend fun getReleases(
        @Path("projectPath", encoded = true) projectPath: String,
        @Query("per_page") perPage: Int = 1
    ): Response<List<GitLabRelease>>
}

@JsonClass(generateAdapter = true)
data class GitLabRelease(
    @Json(name = "tag_name") val tagName: String,
    @Json(name = "name") val name: String,
    @Json(name = "description") val description: String?,
    @Json(name = "released_at") val releasedAt: String?,
    @Json(name = "assets") val assets: GitLabAssets?
)

@JsonClass(generateAdapter = true)
data class GitLabAssets(
    @Json(name = "links") val links: List<GitLabAssetLink> = emptyList()
)

@JsonClass(generateAdapter = true)
data class GitLabAssetLink(
    @Json(name = "name") val name: String,
    @Json(name = "direct_asset_url") val directAssetUrl: String,
    @Json(name = "url") val url: String,
    @Json(name = "link_type") val linkType: String
)
