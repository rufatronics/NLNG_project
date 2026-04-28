package com.sentinelng.ml

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Manages fetching the remote model config from a GitHub Gist and downloading
 * the GGUF model file via Android's DownloadManager (supports pause/resume).
 *
 * Flow:
 *  1. [fetchConfig] → GET the Gist JSON → parse [ModelConfig].
 *  2. [isModelAlreadyDownloaded] → check SharedPreferences + file existence.
 *  3. [downloadModel] → DownloadManager queues the file, [observeDownload]
 *     emits [DownloadState] updates (progress %, complete, failed).
 *  4. On completion the model path is stored in SharedPreferences.
 *  5. [getDownloadedModelPath] returns the path for [LlamaModelManager].
 *
 * Fallback:  If any step fails, callers use the embedded Bonsai model.
 */
class ModelDownloadManager(private val context: Context) {

    // ── Constants ──────────────────────────────────────────────────────────
    companion object {
        private const val TAG = "ModelDownloadManager"

        /** GitHub Gist raw URL that returns the model config JSON. */
        private const val CONFIG_URL =
            "https://gist.githubusercontent.com/" +
            "abdul123456789umar-stack/" +
            "d50b2fa5dd3ed789fe7e71bde14e3de2/raw/model-config.json"

        /** Keys for SharedPreferences persistence. */
        private const val PREFS_NAME          = "sentinel_model_prefs"
        private const val KEY_MODEL_PATH      = "downloaded_model_path"
        private const val KEY_MODEL_VERSION   = "downloaded_model_version"
        private const val KEY_DOWNLOAD_ID     = "download_manager_id"

        /** Filename saved to internal storage. */
        private const val MODEL_FILENAME = "language_model.gguf"

        private const val CONNECT_TIMEOUT_SEC = 15L
        private const val READ_TIMEOUT_SEC    = 30L
        private const val CONFIG_RETRY_COUNT  = 3
        private const val CONFIG_RETRY_DELAY  = 2_000L  // ms between retries
    }

    // ── SharedPreferences ──────────────────────────────────────────────────
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ── OkHttp (config fetch only; model download uses DownloadManager) ────
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
            .build()
    }

    // ── Data classes ───────────────────────────────────────────────────────

    data class ModelConfig(
        val modelUrl: String,
        val modelVersion: String,
        val modelName: String
    )

    sealed class DownloadState {
        object Idle                            : DownloadState()
        data class Downloading(val percent: Int) : DownloadState()
        data class Complete(val filePath: String): DownloadState()
        data class Failed(val reason: String)  : DownloadState()
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Returns true if the model file is already on disk and its path is stored.
     * Call this before attempting a download to skip unnecessary work.
     */
    fun isModelAlreadyDownloaded(): Boolean {
        val path = prefs.getString(KEY_MODEL_PATH, null) ?: return false
        val file = File(path)
        val exists = file.exists() && file.length() > 0L
        if (!exists) {
            // File deleted externally – clear stale pref
            prefs.edit().remove(KEY_MODEL_PATH).apply()
        }
        return exists
    }

    /**
     * Returns the absolute path of the previously downloaded model, or null.
     */
    fun getDownloadedModelPath(): String? {
        if (!isModelAlreadyDownloaded()) return null
        return prefs.getString(KEY_MODEL_PATH, null)
    }

    /**
     * Returns the version string of the previously downloaded model, or null.
     */
    fun getDownloadedModelVersion(): String? =
        prefs.getString(KEY_MODEL_VERSION, null)

    /**
     * Fetch the model config JSON from the Gist URL.
     * Retries up to [CONFIG_RETRY_COUNT] times on failure.
     *
     * @return [ModelConfig] on success, null on failure.
     */
    suspend fun fetchConfig(): ModelConfig? = withContext(Dispatchers.IO) {
        repeat(CONFIG_RETRY_COUNT) { attempt ->
            try {
                Log.i(TAG, "Fetching config (attempt ${attempt + 1})…")
                val request = Request.Builder().url(CONFIG_URL).build()
                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    Log.w(TAG, "Config fetch HTTP ${response.code}")
                    delay(CONFIG_RETRY_DELAY)
                    return@repeat
                }

                val body = response.body?.string()
                    ?: run { Log.w(TAG, "Empty config body"); return@repeat }

                val config = parseConfig(body)
                if (config != null) {
                    Log.i(TAG, "Config fetched: url=${config.modelUrl} v=${config.modelVersion}")
                    return@withContext config
                }
            } catch (e: Exception) {
                Log.w(TAG, "Config fetch attempt ${attempt + 1} failed: ${e.message}")
                if (attempt < CONFIG_RETRY_COUNT - 1) delay(CONFIG_RETRY_DELAY)
            }
        }
        Log.e(TAG, "All config fetch attempts failed")
        null
    }

    /**
     * Queue the model file for download using Android's [DownloadManager].
     * Returns the download ID, or -1L on failure.
     *
     * The DownloadManager handles:
     *  - WiFi / mobile switching
     *  - Resume on connectivity restored
     *  - System notification progress
     */
    fun startDownload(config: ModelConfig): Long {
        val destFile = File(context.filesDir, MODEL_FILENAME)

        val request = DownloadManager.Request(Uri.parse(config.modelUrl)).apply {
            setTitle("Sentinel-NG: Downloading language model")
            setDescription("${config.modelName} v${config.modelVersion}")
            setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            setDestinationUri(Uri.fromFile(destFile))
            // Allow download on both WiFi and mobile data
            setAllowedNetworkTypes(
                DownloadManager.Request.NETWORK_WIFI or
                DownloadManager.Request.NETWORK_MOBILE
            )
            setAllowedOverRoaming(false)
        }

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)
        prefs.edit().putLong(KEY_DOWNLOAD_ID, downloadId).apply()
        Log.i(TAG, "Download enqueued id=$downloadId → ${destFile.absolutePath}")
        return downloadId
    }

    /**
     * Returns a [Flow] that polls [DownloadManager] every second and emits
     * [DownloadState] until the download completes or fails.
     *
     * Collect this in a coroutine; cancel the collection to stop polling.
     */
    fun observeDownload(downloadId: Long): Flow<DownloadState> = flow {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        emit(DownloadState.Idle)

        while (true) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor: Cursor = dm.query(query) ?: break

            if (!cursor.moveToFirst()) {
                cursor.close()
                emit(DownloadState.Failed("Download record not found"))
                break
            }

            val status = cursor.getInt(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            )
            val bytesDownloaded = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            )
            val bytesTotal = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            )
            cursor.close()

            when (status) {
                DownloadManager.STATUS_RUNNING,
                DownloadManager.STATUS_PENDING,
                DownloadManager.STATUS_PAUSED -> {
                    val pct = if (bytesTotal > 0)
                        (bytesDownloaded * 100 / bytesTotal).toInt() else 0
                    emit(DownloadState.Downloading(pct))
                    delay(1_000)
                }

                DownloadManager.STATUS_SUCCESSFUL -> {
                    val path = File(context.filesDir, MODEL_FILENAME).absolutePath
                    // Persist path so future launches skip the download
                    prefs.edit()
                        .putString(KEY_MODEL_PATH, path)
                        .apply()
                    Log.i(TAG, "Download complete → $path")
                    emit(DownloadState.Complete(path))
                    break
                }

                DownloadManager.STATUS_FAILED -> {
                    val reason = cursor.runCatching {
                        getInt(getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)).toString()
                    }.getOrDefault("unknown")
                    Log.e(TAG, "Download failed: reason=$reason")
                    emit(DownloadState.Failed("Download failed (reason $reason)"))
                    break
                }

                else -> {
                    delay(1_000)
                }
            }
        }
    }

    /**
     * Save the model version string after a successful download so we can
     * detect when the Gist config points to a newer model in a future run.
     */
    fun saveModelVersion(version: String) {
        prefs.edit().putString(KEY_MODEL_VERSION, version).apply()
    }

    /**
     * Returns true if the Gist config's version is newer than what's on disk.
     * Uses simple string comparison — use semver if you need numeric ordering.
     */
    fun isNewerVersionAvailable(config: ModelConfig): Boolean {
        val stored = getDownloadedModelVersion() ?: return true
        return config.modelVersion != stored
    }

    // ── Private helpers ────────────────────────────────────────────────────

    /**
     * Parse the Gist JSON.  Accepts flexible shapes:
     *
     *   { "model_url": "...", "model_version": "1.0", "model_name": "..." }
     *   { "modelUrl":  "...", "version": "1.0" }
     *
     * Falls back to sensible defaults for optional fields.
     */
    private fun parseConfig(json: String): ModelConfig? {
        return try {
            val obj = JSONObject(json)

            // Accept both snake_case and camelCase keys
            val url = obj.optString("model_url")
                .ifBlank { obj.optString("modelUrl") }
                .ifBlank { return null }   // url is required

            val version = obj.optString("model_version")
                .ifBlank { obj.optString("version") }
                .ifBlank { "1.0" }

            val name = obj.optString("model_name")
                .ifBlank { obj.optString("modelName") }
                .ifBlank { MODEL_FILENAME }

            ModelConfig(modelUrl = url, modelVersion = version, modelName = name)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse config JSON: ${e.message}\nRaw: $json")
            null
        }
    }
}
