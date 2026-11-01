package com.nendo.argosy.data.remote.gitea

import com.nendo.argosy.data.remote.github.GitHubRelease
import com.nendo.argosy.data.remote.github.GitHubTag
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface GiteaApi {

    @GET("api/v1/repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<GitHubRelease>

    @GET("api/v1/repos/{owner}/{repo}/tags")
    suspend fun getTags(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("limit") limit: Int = 30
    ): Response<List<GitHubTag>>
}
