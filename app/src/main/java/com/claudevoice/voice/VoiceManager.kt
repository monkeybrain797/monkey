package com.claudevoice.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.claudevoice.model.VoiceSettings
import java.util.Locale
import java.util.UUID

class VoiceManager(context: Context) : TextToSpeech.OnInitListener {

    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var pendingSettings: VoiceSettings? = null
    private var currentVolume: Float = 1.0f

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ready = true
            pendingSettings?.let { applySettings(it) }
        }
    }

    fun applySettings(settings: VoiceSettings) {
        currentVolume = settings.volume
        if (!ready) {
            pendingSettings = settings
            return
        }
        tts.setSpeechRate(settings.speechRate.coerceIn(0.5f, 2.0f))
        tts.setPitch(settings.pitch.coerceIn(0.5f, 2.0f))

        val locale = Locale.forLanguageTag(settings.voiceLocale)
        val voices = tts.voices
        if (!voices.isNullOrEmpty()) {
            val voice = when (settings.voiceGender) {
                "male" ->
                    voices.firstOrNull { it.locale == locale && "male" in it.name.lowercase() }
                        ?: voices.firstOrNull { it.locale == locale }
                "female" ->
                    voices.firstOrNull { it.locale == locale && "female" in it.name.lowercase() }
                        ?: voices.firstOrNull { it.locale == locale }
                else -> voices.firstOrNull { it.locale == locale }
            }
            if (voice != null) tts.voice = voice else tts.setLanguage(locale)
        } else {
            tts.setLanguage(locale)
        }
        pendingSettings = null
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!ready) return
        val id = UUID.randomUUID().toString()
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, currentVolume.coerceIn(0.0f, 1.0f))
        }
        if (onDone != null) {
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == id) onDone()
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {}
            })
        }
        tts.speak(text, TextToSpeech.QUEUE_ADD, params, id)
    }

    fun stop() = tts.stop()

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
