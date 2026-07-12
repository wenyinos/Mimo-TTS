package com.mimotts.app.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.*

interface TtsService {
    @POST("v1/chat/completions")
    @Headers("Content-Type: application/json")
    suspend fun mimoTts(
        @Header("Authorization") auth: String,
        @Body request: TtsRequest
    ): TtsResponse

    @Multipart
    @POST
    suspend fun confuciusUpload(
        @Url url: String,
        @Part file: MultipartBody.Part
    ): List<String>

    @POST
    suspend fun confuciusRef(
        @Url url: String,
        @Body request: ConfuciusRefRequest
    ): ConfuciusRefResponse

    @POST
    suspend fun confuciusPredict(
        @Url url: String,
        @Body request: ConfuciusPredictRequest
    ): ConfuciusPredictResponse

    @GET
    suspend fun downloadFile(@Url url: String): ResponseBody
}
