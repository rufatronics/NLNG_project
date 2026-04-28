package com.sentinelng.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.sentinelng.R
import com.sentinelng.data.ModelType
import com.sentinelng.data.NluIntent
import com.sentinelng.data.SupportedLanguage
import com.sentinelng.databinding.ActivityMainBinding
import com.sentinelng.utils.LanguageManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        updateLanguageDisplay()
    }

    private fun setupUI() {
        val lang = LanguageManager.getLanguage(this)
        binding.tvGreeting.text = LanguageManager.getGreeting(lang)

        // Quick-action buttons
        binding.btnCropDoctor.setOnClickListener { openCamera(ModelType.CROP_DOCTOR) }
        binding.btnHealthScan.setOnClickListener { openCamera(ModelType.HEALTH_SCAN) }
        binding.btnChat.setOnClickListener { startActivity(Intent(this, ChatActivity::class.java)) }
        binding.btnSecurity.setOnClickListener { startActivity(Intent(this, SecurityActivity::class.java)) }
        binding.btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        // NLU input
        binding.etNluInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submitNluQuery()
                true
            } else false
        }
        binding.btnNluSend.setOnClickListener { submitNluQuery() }
    }

    private fun observeViewModel() {
        viewModel.nluResult.observe(this) { result ->
            result ?: return@observe
            binding.etNluInput.text?.clear()

            when (result.intent) {
                NluIntent.HEALTH   -> openCamera(ModelType.HEALTH_SCAN)
                NluIntent.CROP     -> openCamera(ModelType.CROP_DOCTOR)
                NluIntent.SECURITY -> startActivity(Intent(this, SecurityActivity::class.java))
                NluIntent.OTHER    -> {
                    val msg = LanguageManager.getOtherIntentMessage(viewModel.getCurrentLanguage())
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            }
            viewModel.clearNluResult()
        }

        viewModel.isLoading.observe(this) { loading ->
            binding.progressNlu.visibility = if (loading) View.VISIBLE else View.GONE
            binding.btnNluSend.isEnabled = !loading
        }
    }

    private fun submitNluQuery() {
        val text = binding.etNluInput.text?.toString()?.trim()
        if (text.isNullOrEmpty()) return
        viewModel.classifyIntent(text)
    }

    private fun openCamera(modelType: ModelType) {
        val intent = Intent(this, CameraActivity::class.java).apply {
            putExtra(CameraActivity.EXTRA_MODEL_TYPE, modelType.name)
        }
        startActivity(intent)
    }

    private fun updateLanguageDisplay() {
        val lang = LanguageManager.getLanguage(this)
        binding.tvGreeting.text = LanguageManager.getGreeting(lang)
        binding.tvCurrentLanguage.text = lang.displayName
    }
}
