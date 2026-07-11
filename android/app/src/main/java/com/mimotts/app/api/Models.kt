package com.mimotts.app.api

import com.google.gson.annotations.SerializedName

// ── MiMo TTS ──

data class TtsRequest(
    val model: String,
    val messages: List<Message>,
    val audio: AudioConfig,
    @SerializedName("stream") val stream: Boolean = false
)

data class Message(val role: String, val content: String)

data class AudioConfig(
    val format: String = "wav",
    val voice: String? = null,
    @SerializedName("optimize_text_preview") val optimizeTextPreview: Boolean? = null
)

data class TtsResponse(val choices: List<Choice>)
data class Choice(val message: ResponseMessage)
data class ResponseMessage(val audio: AudioData?)
data class AudioData(val data: String)

// ── Confucius4-TTS ──

data class ConfuciusUploadResponse(val path: String)

data class ConfuciusRefRequest(val data: List<Any>)
data class ConfuciusRefResponse(val data: List<Any>)

data class ConfuciusPredictRequest(val data: List<Any>)
data class ConfuciusPredictResponse(val data: List<Any>)
