package com.claudevoice.model

import com.google.gson.annotations.SerializedName

data class VoiceSettings(
    @SerializedName("speechRate") val speechRate: Float = 1.0f,
    @SerializedName("pitch") val pitch: Float = 1.0f,
    @SerializedName("volume") val volume: Float = 1.0f,
    @SerializedName("voiceLocale") val voiceLocale: String = "en-US",
    @SerializedName("voiceGender") val voiceGender: String = "default"
)
