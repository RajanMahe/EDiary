package com.aburv.kurippidu

import android.content.Intent
import android.speech.RecognizerIntent

/**
 * Languages for "Speak to write". The phone's speech service does the work;
 * [tag] is the BCP-47 language it is asked for (null = the phone's language).
 */
enum class SpeechLanguage(val tag: String?, val label: String, val nativeName: String) {
    PHONE(null, "Phone language", ""),
    ENGLISH_INDIA("en-IN", "English (India)", "English"),
    TAMIL("ta-IN", "Tamil", "தமிழ்"),
    HINDI("hi-IN", "Hindi", "हिन्दी"),
    MALAYALAM("ml-IN", "Malayalam", "മലയാളം"),
    TELUGU("te-IN", "Telugu", "తెలుగు"),
    KANNADA("kn-IN", "Kannada", "ಕನ್ನಡ"),
    MARATHI("mr-IN", "Marathi", "मराठी"),
    BENGALI("bn-IN", "Bengali", "বাংলা"),
    GUJARATI("gu-IN", "Gujarati", "ગુજરાતી"),
    PUNJABI("pa-IN", "Punjabi", "ਪੰਜਾਬੀ"),
    URDU("ur-IN", "Urdu", "اردو"),
    ENGLISH_US("en-US", "English (US)", "English");

    /** "Tamil · தமிழ்", or just the label when the names match. */
    val display: String get() = if (nativeName.isEmpty() || nativeName == label) label else "$label · $nativeName"

    fun intent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_PROMPT, if (tag == null) "Speak your entry" else "Speak your entry in $label")
        .apply {
            if (tag != null) {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag)
            }
        }

    companion object {
        fun fromKey(key: String?): SpeechLanguage? = entries.firstOrNull { it.name == key }
    }
}
