package com.claudevoice.data

import android.content.Context
import com.claudevoice.model.VoiceSettings
import com.google.gson.Gson

class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("claude_voice_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    var voiceSettings: VoiceSettings
        get() {
            val json = prefs.getString(KEY_VOICE_SETTINGS, null)
            return if (json != null) gson.fromJson(json, VoiceSettings::class.java) else VoiceSettings()
        }
        set(value) = prefs.edit().putString(KEY_VOICE_SETTINGS, gson.toJson(value)).apply()

    var voiceGuidelines: String
        get() = prefs.getString(KEY_GUIDELINES, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GUIDELINES, value).apply()

    companion object {
        private const val KEY_API_KEY = "api_key"
        private const val KEY_VOICE_SETTINGS = "voice_settings"
        private const val KEY_GUIDELINES = "voice_guidelines"
    }
}
