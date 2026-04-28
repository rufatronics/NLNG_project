package com.sentinelng.ml

import android.content.Context
import android.util.Log
import com.sentinelng.data.SupportedLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Manages GGUF language model inference via llama.cpp JNI.
 *
 * ## Model priority (highest → lowest)
 *
 * 1. **Downloaded model** – fetched at first launch from the Gist config URL
 *    (stored in `filesDir/models/language_model.gguf`).  Used for ALL five
 *    languages when available.
 *
 * 2. **Embedded Bonsai** – `assets/bonsai_1.7b_q1_0.gguf`.  Used for English
 *    when the downloaded model is absent, AND as the fallback for Nigerian
 *    languages if the download has not yet completed.
 *
 * 3. **Embedded mT5** – `assets/mt5_merged.gguf`.  Legacy fallback for
 *    Nigerian languages when neither of the above is available.
 *
 * ## Offline guarantee
 * After the first successful download the app never makes another network call
 * inside this class.  [ModelDownloadManager] persists the file path in
 * SharedPreferences; [LlamaModelManager] reads it from there.
 */
class LlamaModelManager(private val context: Context) {

    companion object {
        private const val TAG              = "LlamaModelManager"
        private const val ASSET_ENGLISH    = "bonsai_1.7b_q1_0.gguf"
        private const val ASSET_NIGERIAN   = "mt5_merged.gguf"
        private const val DOWNLOADED_NAME  = "language_model.gguf"
        private const val MAX_CTX_TURNS    = 6
        private const val N_PREDICT        = 256
        private const val TEMPERATURE      = 0.7f
        private const val N_CTX            = 2048
        private const val N_BATCH          = 512
        private const val N_THREADS        = 4

        private var nativeLibLoaded = false

        private fun ensureLib() {
            if (!nativeLibLoaded) {
                try {
                    System.loadLibrary("llama")
                    nativeLibLoaded = true
                    Log.i(TAG, "libllama.so loaded")
                } catch (e: UnsatisfiedLinkError) {
                    Log.w(TAG, "libllama.so not found – stub mode active")
                }
            }
        }

        // ── JNI entry-points (llama.cpp Android demo ABI) ─────────────────
        @JvmStatic private external fun llamaLoadModel(path: String, nCtx: Int, nBatch: Int, nThreads: Int): Long
        @JvmStatic private external fun llamaFreeModel(ptr: Long)
        @JvmStatic private external fun llamaTokenize(ptr: Long, text: String, addBos: Boolean): IntArray
        @JvmStatic private external fun llamaEval(ptr: Long, tokens: IntArray, nPast: Int): Int
        @JvmStatic private external fun llamaSampleToken(ptr: Long, temperature: Float): Int
        @JvmStatic private external fun llamaTokenToPiece(ptr: Long, token: Int): String
        @JvmStatic private external fun llamaGetEosToken(ptr: Long): Int
    }

    // ── State ──────────────────────────────────────────────────────────────
    private var modelPtr: Long = 0L
    private var activeModelPath: String? = null
    private val history = mutableListOf<Pair<String, String>>()

    val isLoaded: Boolean get() = modelPtr != 0L

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Load the best available model for [language].
     *
     * Decision tree:
     *  - Downloaded model exists on disk → use it for any language.
     *  - Language == English             → use embedded Bonsai.
     *  - Nigerian language, no download  → use embedded mT5 (legacy fallback).
     */
    suspend fun loadModelForLanguage(language: SupportedLanguage) = withContext(Dispatchers.IO) {
        val targetPath = resolveModelPath(language)

        // No-op if the correct model is already loaded
        if (activeModelPath == targetPath && isLoaded) return@withContext

        unload()
        ensureLib()

        if (!nativeLibLoaded) {
            Log.w(TAG, "Native lib unavailable – stub mode")
            return@withContext
        }

        val modelFile = File(targetPath)
        if (!modelFile.exists() || modelFile.length() == 0L) {
            Log.w(TAG, "Model file missing at $targetPath – stub mode")
            return@withContext
        }

        try {
            modelPtr = llamaLoadModel(targetPath, N_CTX, N_BATCH, N_THREADS)
            if (modelPtr == 0L) throw RuntimeException("llamaLoadModel returned null")
            activeModelPath = targetPath
            Log.i(TAG, "Loaded ${modelFile.name} (ptr=0x${modelPtr.toString(16)})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model at $targetPath", e)
            modelPtr = 0L
        }
    }

    /**
     * Generate a response.  Falls back to canned stubs when no model is loaded.
     */
    suspend fun generate(
        userMessage: String,
        language: SupportedLanguage,
        systemPrompt: String = buildSystemPrompt(language)
    ): String = withContext(Dispatchers.IO) {
        if (!isLoaded) return@withContext stubResponse(userMessage, language)

        val prompt   = buildPrompt(systemPrompt, history.takeLast(MAX_CTX_TURNS), userMessage)
        val response = try {
            runInference(prompt)
        } catch (e: Exception) {
            Log.e(TAG, "Inference error", e)
            "Sorry, something went wrong. Please try again."
        }

        history.add(userMessage to response)
        if (history.size > MAX_CTX_TURNS) history.removeAt(0)
        response
    }

    fun clearHistory() = history.clear()

    fun unload() {
        if (modelPtr != 0L) {
            try { llamaFreeModel(modelPtr) } catch (_: Exception) {}
            Log.i(TAG, "Model unloaded")
        }
        modelPtr = 0L
        activeModelPath = null
    }

    // ── Model path resolution ──────────────────────────────────────────────

    /**
     * Returns the absolute path of the model that should be loaded.
     *
     * Priority:
     *  1. Downloaded model in filesDir (preferred for ALL languages).
     *  2. Bonsai from assets (English / fallback).
     *  3. mT5 from assets (Nigerian languages legacy fallback).
     */
    private fun resolveModelPath(language: SupportedLanguage): String {
        // 1. Prefer the downloaded model for any language
        val downloaded = File(context.filesDir, "models/$DOWNLOADED_NAME")
        if (downloaded.exists() && downloaded.length() > 0L) {
            Log.d(TAG, "Using downloaded model for $language")
            return downloaded.absolutePath
        }

        // 2. No download – pick embedded model
        val assetName = if (language == SupportedLanguage.ENGLISH) ASSET_ENGLISH else ASSET_NIGERIAN
        Log.d(TAG, "Downloaded model absent – using asset $assetName for $language")
        return getOrCopyAsset(assetName)?.absolutePath
            ?: File(context.filesDir, "models/$assetName").absolutePath
    }

    // ── Inference ──────────────────────────────────────────────────────────

    private fun runInference(prompt: String): String {
        val tokens = llamaTokenize(modelPtr, prompt, addBos = true)
        if (tokens.isEmpty()) return ""

        val evalCode = llamaEval(modelPtr, tokens, nPast = 0)
        if (evalCode != 0) throw RuntimeException("llamaEval failed: $evalCode")

        val eos  = llamaGetEosToken(modelPtr)
        val sb   = StringBuilder()
        var past = tokens.size

        repeat(N_PREDICT) {
            val next = llamaSampleToken(modelPtr, TEMPERATURE)
            if (next == eos) return@repeat
            sb.append(llamaTokenToPiece(modelPtr, next))
            if (llamaEval(modelPtr, intArrayOf(next), past) != 0) return@repeat
            past++
        }
        return sb.toString().trim()
    }

    // ── Prompt formatting ──────────────────────────────────────────────────

    private fun buildPrompt(
        system: String,
        history: List<Pair<String, String>>,
        userMessage: String
    ) = buildString {
        append("[INST] <<SYS>>\n$system\n<</SYS>>\n\n")
        for ((u, a) in history) append("$u [/INST] $a </s><s>[INST] ")
        append("$userMessage [/INST]")
    }

    private fun buildSystemPrompt(language: SupportedLanguage) = when (language) {
        SupportedLanguage.ENGLISH ->
            "You are Sentinel, a concise AI assistant for Nigerian farmers and communities. " +
            "Advise on crop diseases, health, and security. Respond in English."
        SupportedLanguage.HAUSA ->
            "Kai ne Sentinel, mataimakin AI don manoma da al'ummomi na Nijeriya. Ka amsa da Hausa."
        SupportedLanguage.YORUBA ->
            "Ìwọ ni Sentinel, olùrànlọ́wọ́ AI fún àwọn àgbẹ̀ Nàìjíríà. Dáhùn ní Yorùbá."
        SupportedLanguage.IGBO ->
            "Ị bụ Sentinel, onye enyemaka AI maka ndị ọrụ ugbo Nigeria. Zaghachi na Igbo."
        SupportedLanguage.PIDGIN ->
            "You be Sentinel, helpful AI for Nigeria farmers. Answer in Pidgin English."
    }

    // ── Asset copy helper ──────────────────────────────────────────────────

    private fun getOrCopyAsset(fileName: String): File? {
        val dir    = File(context.filesDir, "models").also { it.mkdirs() }
        val target = File(dir, fileName)
        if (target.exists() && target.length() > 0L) return target
        return try {
            context.assets.open(fileName).use { src ->
                FileOutputStream(target).use { dst -> src.copyTo(dst) }
            }
            Log.i(TAG, "Copied asset $fileName → ${target.absolutePath}")
            target
        } catch (e: Exception) {
            Log.w(TAG, "Cannot copy asset $fileName: ${e.message}")
            null
        }
    }

    // ── Stub responses (no model loaded) ──────────────────────────────────

    private fun stubResponse(msg: String, lang: SupportedLanguage): String {
        val lower = msg.lowercase()
        return when (lang) {
            SupportedLanguage.ENGLISH -> when {
                "cassava" in lower || "mosaic" in lower ->
                    "Cassava Mosaic Disease is spread by whiteflies. Remove infected plants " +
                    "and replant with resistant varieties such as TME 419."
                "malaria" in lower || "fever" in lower ->
                    "Fever with chills may indicate malaria. Visit a clinic for a rapid test."
                "flood" in lower ->
                    "Move to higher ground immediately. Avoid floodwater — it may be contaminated."
                else ->
                    "I can help with farming, health, and security. (AI model not loaded.)"
            }
            SupportedLanguage.HAUSA  -> "Ina nan don taimaka. (Samfurin AI bai loda ba.)"
            SupportedLanguage.YORUBA -> "Mo wà láti ràn ọ́ lọ́wọ́. (Àwòrán AI kò tíì gbà.)"
            SupportedLanguage.IGBO   -> "Anọ m ebe a inyere gị aka. (Ụzọ AI anaghị ebu.)"
            SupportedLanguage.PIDGIN -> "I dey here to help. (AI model no load.)"
        }
    }
}
