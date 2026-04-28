package com.sentinelng.ml

import android.content.Context
import android.util.Log
import com.sentinelng.data.NluIntent
import com.sentinelng.data.NluResult
import com.sentinelng.data.SupportedLanguage
import java.io.File
import java.io.FileOutputStream

/**
 * FastText-based NLU intent classifier.
 *
 * Gradle dependency (app/build.gradle):
 *   implementation 'com.github.georgenitram:fasttext-android:1.0.0'
 *
 * The `georgenitram/fasttext-android` library exposes:
 *
 *   class FastText {
 *       companion object {
 *           fun loadModel(modelPath: String): FastText
 *       }
 *       fun predict(text: String, k: Int): List<Pair<String, Float>>
 *       // Returns k (label, probability) pairs, labels prefixed "__label__"
 *   }
 *
 * The .ftz model must be trained with the following label convention:
 *   __label__HEALTH    – queries about illness, symptoms, medicine
 *   __label__CROP      – queries about farming, plant diseases, pests
 *   __label__SECURITY  – queries about danger, floods, fire, conflict
 *   __label__OTHER     – everything else
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * DEVELOPER NOTE
 * If the JitPack build of georgenitram/fasttext-android fails to resolve,
 * place a pre-built fasttext-android.aar in app/libs/ and use:
 *   implementation files('libs/fasttext-android.aar')
 * The API surface remains identical.
 * ─────────────────────────────────────────────────────────────────────────────
 */
class FastTextHelper(private val context: Context) {

    companion object {
        private const val TAG        = "FastTextHelper"
        private const val MODEL_FILE = "nigerian_nlu.ftz"
        private const val LABEL_PFX  = "__label__"
    }

    /**
     * Opaque handle to the loaded FastText model.
     *
     * Typed as [Any?] so this file compiles even before the AAR is added to
     * the classpath.  Once the AAR is resolved, replace [Any?] with
     * [com.github.georgenitram.fasttext.FastText] (or the actual package the
     * library uses) and remove the reflection-based calls below.
     */
    private var ftModel: Any? = null
    private var modelLoaded = false

    // ── Lifecycle ──────────────────────────────────────────────────────────

    /**
     * Copy the .ftz from assets to internal storage (required because the
     * native FastText loader needs a real file-system path), then initialise
     * the model handle.
     */
    fun loadModel() {
        val modelFile = getOrCopyModel() ?: run {
            Log.w(TAG, "$MODEL_FILE not found in assets – keyword fallback active")
            return
        }

        try {
            // ── Real FastText call ──────────────────────────────────────────
            // Uncomment when the AAR is on the classpath:
            //
            // ftModel = FastText.loadModel(modelFile.absolutePath)
            // modelLoaded = true
            // Log.i(TAG, "FastText model loaded from ${modelFile.absolutePath}")

            // ── Reflection-based call (compile-safe stub) ──────────────────
            // This block loads the class at runtime so the file still compiles
            // without the AAR.  Replace with the direct call above once the
            // dependency is resolved.
            val clazz  = Class.forName("com.github.georgenitram.fasttext.FastText")
            val method = clazz.getMethod("loadModel", String::class.java)
            ftModel = method.invoke(null, modelFile.absolutePath)
            modelLoaded = true
            Log.i(TAG, "FastText model loaded (reflection)")

        } catch (e: ClassNotFoundException) {
            Log.w(TAG, "FastText class not found – is the AAR in dependencies? " +
                    "Falling back to keyword classifier.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load FastText model", e)
        }
    }

    // ── Classification ─────────────────────────────────────────────────────

    /**
     * Classify [text] into one of [NluIntent].
     * Uses the FastText model when available; falls back to keyword heuristics.
     */
    fun classify(text: String, language: SupportedLanguage): NluResult =
        if (modelLoaded && ftModel != null) classifyWithModel(text)
        else classifyWithKeywords(text)

    // ── FastText model path ────────────────────────────────────────────────

    /**
     * Call `FastText.predict(text, k=1)` via the loaded model handle.
     *
     * The library returns a `List<Pair<String, Float>>` where each pair is
     * (label, probability).  Labels are formatted as `__label__INTENT`.
     */
    @Suppress("UNCHECKED_CAST")
    private fun classifyWithModel(text: String): NluResult {
        return try {
            // ── Direct call (uncomment once AAR is on classpath) ──────────
            // val results = (ftModel as FastText).predict(text.lowercase().trim(), 1)
            // val (rawLabel, prob) = results.first()

            // ── Reflection-based call ─────────────────────────────────────
            val predict = ftModel!!.javaClass.getMethod("predict", String::class.java, Int::class.java)
            val results = predict.invoke(ftModel, text.lowercase().trim(), 1) as List<Pair<String, Float>>

            if (results.isEmpty()) return classifyWithKeywords(text)

            val (rawLabel, prob) = results.first()
            val intentName = rawLabel.removePrefix(LABEL_PFX).uppercase()
            val intent = NluIntent.values().firstOrNull { it.name == intentName } ?: NluIntent.OTHER

            NluResult(intent, prob, text)
        } catch (e: Exception) {
            Log.w(TAG, "FastText predict failed: ${e.message} – falling back to keywords")
            classifyWithKeywords(text)
        }
    }

    // ── Keyword fallback ───────────────────────────────────────────────────

    private val healthKeywords = setOf(
        // English
        "sick", "fever", "pain", "cough", "malaria", "typhoid", "rash", "wound",
        "health", "hospital", "medicine", "disease", "headache", "diarrhoea", "vomit",
        "cholera", "tuberculosis", "hiv", "aids", "pregnant", "childbirth", "nurse",
        // Hausa
        "ciwon", "zazzabi", "mura", "cuta", "asibiti", "maganin", "lafiya",
        // Yoruba
        "àìsàn", "iba", "ìrora", "àrùn", "ilé-ìwòsàn", "oogun",
        // Igbo
        "ọrịa", "ụkwara", "ọgwụ", "ụlọ ọrịa", "ahụike",
        // Pidgin
        "no well", "body dey pain", "go hospital", "buy medicine"
    )

    private val cropKeywords = setOf(
        // English
        "crop", "farm", "plant", "cassava", "maize", "rice", "tomato", "yam",
        "soybean", "leaf", "harvest", "pest", "insect", "fertilizer", "soil",
        "agriculture", "groundnut", "cowpea", "banana", "disease", "fungus",
        "blight", "wilt", "rust", "mosaic", "streak", "aphid", "locust",
        // Hausa
        "gona", "noma", "amfanin gona", "shinkafa", "masara", "rogo", "doya",
        "kwari", "maganin ciyawa",
        // Yoruba
        "oko", "àgbẹ̀", "àgbàdo", "isu", "eweko", "àgbàdo",
        // Igbo
        "ọrụ ugbo", "ji", "ọka", "ihe ọkụkụ", "obere ọrịa",
        // Pidgin
        "farm work", "plant sick", "harvest time"
    )

    private val securityKeywords = setOf(
        // English
        "security", "danger", "alert", "attack", "flood", "fire", "emergency",
        "threat", "conflict", "violence", "police", "army", "report", "incident",
        "robbery", "kidnap", "herdsmen", "bandit", "hotspot",
        // Hausa
        "tsaro", "haɗari", "wuta", "ambaliyar ruwa", "rikici", "yan bindiga",
        // Yoruba
        "ààbò", "ewu", "iná", "ìkún omi", "jagun",
        // Igbo
        "nchekwa", "ihe ize ndụ", "ọkụ", "iduru ụzọ mmiri",
        // Pidgin
        "wahala", "danger", "fire dey", "flood come", "dem attack"
    )

    private fun classifyWithKeywords(text: String): NluResult {
        val tokens = text.lowercase().split(Regex("[\\s,\\.\\?!]+")).filter { it.isNotEmpty() }

        val hScore = tokens.count { it in healthKeywords }.toFloat()
        val cScore = tokens.count { it in cropKeywords }.toFloat()
        val sScore = tokens.count { it in securityKeywords }.toFloat()
        val max    = maxOf(hScore, cScore, sScore)

        return when {
            max == 0f    -> NluResult(NluIntent.OTHER,    0.90f, text)
            hScore == max -> NluResult(NluIntent.HEALTH,   hScore / tokens.size.coerceAtLeast(1), text)
            cScore == max -> NluResult(NluIntent.CROP,     cScore / tokens.size.coerceAtLeast(1), text)
            else          -> NluResult(NluIntent.SECURITY, sScore / tokens.size.coerceAtLeast(1), text)
        }
    }

    // ── Asset copy helper ──────────────────────────────────────────────────

    private fun getOrCopyModel(): File? {
        val dir    = File(context.filesDir, "models").also { it.mkdirs() }
        val target = File(dir, MODEL_FILE)
        if (target.exists() && target.length() > 0L) return target
        return try {
            context.assets.open(MODEL_FILE).use { src ->
                FileOutputStream(target).use { dst -> src.copyTo(dst) }
            }
            Log.i(TAG, "Copied $MODEL_FILE to ${target.absolutePath}")
            target
        } catch (e: Exception) {
            Log.w(TAG, "Cannot copy $MODEL_FILE from assets: ${e.message}")
            null
        }
    }
}
