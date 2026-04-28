package com.sentinelng.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sentinelng.R
import com.sentinelng.databinding.ActivitySplashBinding
import com.sentinelng.ml.ModelDownloadManager
import com.sentinelng.ml.ModelDownloadManager.DownloadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Entry-point activity.
 *
 * On first launch it:
 *  1. Fetches the model config JSON from the GitHub Gist.
 *  2. If a newer GGUF model is available (or none is stored), downloads it
 *     via [ModelDownloadManager] while showing progress to the user.
 *  3. On success stores the file path for [ChatActivity] / [LlamaModelManager].
 *  4. Falls back gracefully to the embedded Bonsai model on any failure.
 *
 * On subsequent launches it skips all network work if the model is already
 * on disk — the app stays fully offline after the first successful download.
 */
class SplashActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SplashActivity"
        private const val SPLASH_MIN_MILLIS = 1_500L   // show splash at least 1.5 s
    }

    private lateinit var binding: ActivitySplashBinding
    private lateinit var downloadManager: ModelDownloadManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        downloadManager = ModelDownloadManager(this)

        lifecycleScope.launch {
            val startMs = System.currentTimeMillis()
            runModelSetup()
            // Ensure the splash shows for at least SPLASH_MIN_MILLIS
            val elapsed = System.currentTimeMillis() - startMs
            if (elapsed < SPLASH_MIN_MILLIS) {
                withContext(Dispatchers.IO) {
                    kotlinx.coroutines.delay(SPLASH_MIN_MILLIS - elapsed)
                }
            }
            startMain()
        }
    }

    // ── Model setup logic ──────────────────────────────────────────────────

    private suspend fun runModelSetup() {
        // ── Step 1: Already downloaded? ────────────────────────────────────
        if (downloadManager.isModelAlreadyDownloaded()) {
            showStatus(getString(R.string.splash_model_ready))
            Log.i(TAG, "Model already on disk – skipping download")

            // Optionally check for a newer version in the background
            // (non-blocking; user proceeds immediately with the cached model)
            lifecycleScope.launch {
                checkForUpdate()
            }
            return
        }

        // ── Step 2: Fetch config from Gist ─────────────────────────────────
        showStatus(getString(R.string.splash_fetching_config))
        val config = downloadManager.fetchConfig()

        if (config == null) {
            // Config fetch failed – fall back to embedded Bonsai
            showStatus(getString(R.string.splash_config_failed))
            Log.w(TAG, "Config fetch failed – falling back to Bonsai")
            kotlinx.coroutines.delay(1_500)
            return
        }

        // ── Step 3: Download the model ──────────────────────────────────────
        showStatus(getString(R.string.splash_downloading))
        showProgressBar(indeterminate = false)

        val downloadId = downloadManager.startDownload(config)
        if (downloadId == -1L) {
            showStatus(getString(R.string.splash_download_failed))
            Log.e(TAG, "DownloadManager failed to enqueue")
            return
        }

        // ── Step 4: Observe download progress ──────────────────────────────
        downloadManager.observeDownload(downloadId).collect { state ->
            when (state) {
                is DownloadState.Idle ->
                    showStatus(getString(R.string.splash_downloading))

                is DownloadState.Downloading -> {
                    binding.tvSplashStatus.text =
                        getString(R.string.splash_progress, state.percent)
                    binding.progressDownload.progress = state.percent
                    binding.progressDownload.isIndeterminate = false
                }

                is DownloadState.Complete -> {
                    downloadManager.saveModelVersion(config.modelVersion)
                    showStatus(getString(R.string.splash_download_complete))
                    Log.i(TAG, "Model downloaded: ${state.filePath}")
                }

                is DownloadState.Failed -> {
                    // Fall back to embedded Bonsai – model path stays null
                    showStatus(getString(R.string.splash_download_failed))
                    Log.w(TAG, "Download failed: ${state.reason} – using Bonsai fallback")
                    kotlinx.coroutines.delay(2_000)
                }
            }
        }
    }

    /**
     * Background check: if the Gist config points to a newer model version,
     * queue a download silently in the background (next launch will use it).
     * Does NOT block the user.
     */
    private suspend fun checkForUpdate() {
        val config = downloadManager.fetchConfig() ?: return
        if (downloadManager.isNewerVersionAvailable(config)) {
            Log.i(TAG, "Newer model v${config.modelVersion} available – scheduling background download")
            downloadManager.startDownload(config)
            downloadManager.saveModelVersion(config.modelVersion)
        }
    }

    // ── UI helpers ─────────────────────────────────────────────────────────

    private fun showStatus(message: String) {
        binding.tvSplashStatus.text = message
    }

    private fun showProgressBar(indeterminate: Boolean) {
        binding.progressDownload.visibility    = View.VISIBLE
        binding.progressDownload.isIndeterminate = indeterminate
    }

    private fun startMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
