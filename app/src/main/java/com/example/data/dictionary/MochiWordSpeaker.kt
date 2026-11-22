package com.example.data.dictionary

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import java.util.Locale

class MochiWordSpeaker(context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private val appContext = context.applicationContext

    fun speak(text: String, languageTag: String = "en", onError: ((String) -> Unit)? = null) {
        val clean = text.trim()
        if (clean.isBlank()) return
        if (tts == null) {
            tts = TextToSpeech(appContext) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (ready) {
                    applyLocaleAndSpeak(clean, languageTag, onError)
                } else {
                    onError?.invoke("System Text-to-Speech engine unavailable on this device.")
                }
            }
        } else if (ready) {
            applyLocaleAndSpeak(clean, languageTag, onError)
        } else {
            onError?.invoke("Initializing device speech voice…")
        }
    }

    private fun applyLocaleAndSpeak(text: String, languageTag: String, onError: ((String) -> Unit)?) {
        val engine = tts ?: return
        val locale = Locale.forLanguageTag(languageTag.ifBlank { "en" })
        val langResult = engine.setLanguage(locale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.US)
        }
        val res = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mochi_dict_${System.currentTimeMillis()}")
        if (res != TextToSpeech.SUCCESS) {
            onError?.invoke("Could not speak pronunciation offline.")
        }
    }

    fun shutdown() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        ready = false
    }
}

object SystemTranslateIntentHelper {
    /**
     * Opens an opt-in system intent for external translation apps. Never uses network inside Mochi.
     */
    fun launchSystemTranslate(context: Context, text: String): Boolean {
        val clean = text.trim()
        if (clean.isBlank()) return false

        // Try Android ACTION_TRANSLATE first if supported by an installed app
        val translateIntent = Intent("android.intent.action.TRANSLATE").apply {
            putExtra(Intent.EXTRA_TEXT, clean)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (translateIntent.resolveActivity(context.packageManager) != null) {
            return runCatching {
                context.startActivity(Intent.createChooser(translateIntent, "Translate passage"))
                true
            }.getOrDefault(false)
        }

        // Fallback to standard ACTION_SEND share chooser for offline translator apps
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, clean)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(Intent.createChooser(sendIntent, "Send to translator app"))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
