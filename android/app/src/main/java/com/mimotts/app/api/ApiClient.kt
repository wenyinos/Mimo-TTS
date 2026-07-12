package com.mimotts.app.api

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val MIMO_BASE = "https://api.xiaomimimo.com/"
    const val CONFUCIUS_BASE = "https://confucius4-tts.youdao.com/gradio/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    val mimoService: TtsService = Retrofit.Builder()
        .baseUrl(MIMO_BASE)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(TtsService::class.java)

    val confuciusService: TtsService = Retrofit.Builder()
        .baseUrl(CONFUCIUS_BASE)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(TtsService::class.java)
}
