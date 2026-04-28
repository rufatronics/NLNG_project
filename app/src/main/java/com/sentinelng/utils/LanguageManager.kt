package com.sentinelng.utils

import android.content.Context
import android.content.SharedPreferences
import com.sentinelng.data.SupportedLanguage

object LanguageManager {

    private const val PREF_NAME = "sentinel_prefs"
    private const val KEY_LANGUAGE = "selected_language"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getLanguage(context: Context): SupportedLanguage {
        val code = prefs(context).getString(KEY_LANGUAGE, SupportedLanguage.ENGLISH.code)
            ?: SupportedLanguage.ENGLISH.code
        return SupportedLanguage.fromCode(code)
    }

    fun setLanguage(context: Context, language: SupportedLanguage) {
        prefs(context).edit().putString(KEY_LANGUAGE, language.code).apply()
    }

    fun isEnglish(context: Context): Boolean =
        getLanguage(context) == SupportedLanguage.ENGLISH

    // ── Localised string maps ───────────────────────────────────────────────

    fun getGreeting(language: SupportedLanguage): String = when (language) {
        SupportedLanguage.ENGLISH -> "Hello! How can I help you today?"
        SupportedLanguage.HAUSA   -> "Sannu! Yaya zan taimake ku yau?"
        SupportedLanguage.YORUBA  -> "Ẹ káàbọ̀! Báwo ni mo ṣe lè ràn yín lọ́wọ́ lónì?"
        SupportedLanguage.IGBO    -> "Nnọọ! Kedu ka m ga-enyere gị aka taa?"
        SupportedLanguage.PIDGIN  -> "How you dey! Wetin I go do for you today?"
    }

    fun getNoModelMessage(language: SupportedLanguage): String = when (language) {
        SupportedLanguage.ENGLISH -> "AI model is loading. Please wait a moment."
        SupportedLanguage.HAUSA   -> "Ana lodawa samfurin AI. Da fatan za a jira ɗan lokaci."
        SupportedLanguage.YORUBA  -> "Àwòrán AI ń gba àkókò. Jọ̀wọ́ dúró ìṣẹ́jú díẹ̀."
        SupportedLanguage.IGBO    -> "Ụzọ AI na-ebu. Biko chere obere oge."
        SupportedLanguage.PIDGIN  -> "AI model dey load. Abeg wait small."
    }

    fun getOtherIntentMessage(language: SupportedLanguage): String = when (language) {
        SupportedLanguage.ENGLISH -> "Please ask about health, farming, or security."
        SupportedLanguage.HAUSA   -> "Da fatan za a tambaya game da lafiya, noma, ko tsaro."
        SupportedLanguage.YORUBA  -> "Jọ̀wọ́ béèrè nípa ìlera, iṣẹ́ àgbẹ̀, tàbí ààbò."
        SupportedLanguage.IGBO    -> "Biko jụọ maka ahụike, ọrụ ugbo, ma ọ bụ nchekwa."
        SupportedLanguage.PIDGIN  -> "Abeg ask about health, farming, or security matters."
    }

    fun getCameraHint(language: SupportedLanguage, modelType: String): String = when (language) {
        SupportedLanguage.ENGLISH -> "Point camera at $modelType and tap capture"
        SupportedLanguage.HAUSA   -> "Duba kyamara zuwa $modelType sannan danna ɗauka"
        SupportedLanguage.YORUBA  -> "Tọ́ka kámẹ́rà sí $modelType kí o sì tẹ gbígba"
        SupportedLanguage.IGBO    -> "Tụọ camera na $modelType wee pịa nwụchie"
        SupportedLanguage.PIDGIN  -> "Point camera to $modelType then press capture"
    }

    fun getAnalysingMessage(language: SupportedLanguage): String = when (language) {
        SupportedLanguage.ENGLISH -> "Analysing image…"
        SupportedLanguage.HAUSA   -> "Ana nazarin hoto…"
        SupportedLanguage.YORUBA  -> "Ń ṣàyẹ̀wò àwòrán…"
        SupportedLanguage.IGBO    -> "Na-enyocha foto…"
        SupportedLanguage.PIDGIN  -> "Dey check di picture…"
    }

    fun getReportSuccessMessage(language: SupportedLanguage): String = when (language) {
        SupportedLanguage.ENGLISH -> "Incident reported successfully. Thank you."
        SupportedLanguage.HAUSA   -> "An ba da rahoton lamarin da nasara. Na gode."
        SupportedLanguage.YORUBA  -> "Ìṣẹ̀lẹ̀ ti ròyìn pẹ̀lú àṣeyọrí. Ẹ ṣé."
        SupportedLanguage.IGBO    -> "Emekọrịtara ihe omume nke ọma. Daalụ."
        SupportedLanguage.PIDGIN  -> "You don report di matter. Thank you."
    }
}
