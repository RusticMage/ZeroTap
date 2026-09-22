package com.zerotap.ai.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import com.zerotap.util.Logger
import java.util.Locale

/**
 * On-device speech synthesizer using Android's native TextToSpeech engine.
 * 100% offline, zero cloud calls, zero external heavy ML dependencies.
 */
class OnDeviceDeterrentSpeaker(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.US)
            }
            isInitialized = true
            Logger.alert("DeterrentSpeaker", "[ZeroTap][Speech] Native on-device TextToSpeech initialized")
        } else {
            Logger.alert("DeterrentSpeaker", "[ZeroTap][Speech] TextToSpeech initialization failed (Status: $status)")
        }
    }

    /**
     * Speaks emergency safety check prompt during accident confirmation.
     */
    fun speakPrompt(text: String) {
        if (!isInitialized) {
            Logger.alert("DeterrentSpeaker", "[ZeroTap][Speech] TTS not initialized yet, skipping speech: $text")
            return
        }
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ZeroTapPrompt")
            Logger.alert("DeterrentSpeaker", "[ZeroTap][Speech] Spoken prompt: \"$text\"")
        } catch (e: Exception) {
            Logger.alert("DeterrentSpeaker", "[ZeroTap][Speech] Failed to speak: ${e.message}")
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            // Ignored
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        } catch (e: Exception) {
            // Ignored
        }
    }
}
