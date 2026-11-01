package com.nendo.argosy.data.remote.ra

import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface RAApi {

    @FormUrlEncoded
    @POST("dorequest.php")
    suspend fun login(
        @Field("r") request: String = "login2",
        @Field("u") username: String,
        @Field("p") password: String
    ): Response<RALoginResponse>

    @GET("dorequest.php")
    suspend fun awardAchievement(
        @Query("r") request: String = "awardachievement",
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("a") achievementId: Long,
        @Query("h") hardcore: Int,
        @Query("v") validation: String
    ): Response<RAAwardResponse>

    /**
     * Mirrors rcheevos rc_api_init_start_session_request (src/rapi/rc_api_user.c): `h` and `m` are
     * sent together only when the ROM hash is known, and `l` carries the client version.
     */
    @FormUrlEncoded
    @POST("dorequest.php")
    suspend fun startSession(
        @Field("r") request: String = "startsession",
        @Field("u") username: String,
        @Field("t") token: String,
        @Field("g") gameId: Long,
        @Field("h") hardcore: Int? = null,
        @Field("m") gameHash: String? = null,
        @Field("l") clientVersion: String = RCHEEVOS_VERSION
    ): Response<RAStartSessionResponse>

    @FormUrlEncoded
    @POST("dorequest.php")
    suspend fun ping(
        @Field("r") request: String = "ping",
        @Field("u") username: String,
        @Field("t") token: String,
        @Field("g") gameId: Long,
        @Field("m") richPresence: String? = null
    ): Response<RABaseResponse>

    @GET("dorequest.php")
    suspend fun getGameInfo(
        @Query("r") request: String = "patch",
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("g") gameId: Long,
        @Query("f") flags: Int = 3
    ): Response<RAGameInfoResponse>

    @GET("dorequest.php")
    suspend fun resolveGameId(
        @Query("r") request: String = "gameid",
        @Query("m") hash: String
    ): Response<RAGameIdResponse>

    // Read-only unlock fetch for browsing UI. Does NOT register a play session
    // (unlike startsession which side-effects "Currently Playing" attribution).
    @GET("dorequest.php")
    suspend fun getUnlocks(
        @Query("r") request: String = "unlocks",
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("g") gameId: Long,
        @Query("h") hardcore: Int
    ): Response<RAUnlocksResponse>

    companion object {
        const val BASE_URL = "https://retroachievements.org/"
        const val RCHEEVOS_VERSION = "12.2.1"
    }
}
