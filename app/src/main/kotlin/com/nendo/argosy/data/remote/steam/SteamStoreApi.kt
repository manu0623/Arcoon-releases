package com.nendo.argosy.data.remote.steam

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface SteamStoreApi {

    @GET("api/appdetails")
    suspend fun getAppDetails(
        @Query("appids") appId: Long,
        @Query("l") language: String = "english"
    ): Response<Map<String, SteamAppDetailsResponse>>
}
