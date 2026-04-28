package com.sentinelng.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sentinelng.R
import com.sentinelng.data.ModelType
import com.sentinelng.databinding.ActivityResultBinding

class ResultActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LABEL       = "extra_label"
        const val EXTRA_ADVICE      = "extra_advice"
        const val EXTRA_CONFIDENCE  = "extra_confidence"
        const val EXTRA_MODEL_TYPE  = "extra_model_type"
    }

    private lateinit var binding: ActivityResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val label      = intent.getStringExtra(EXTRA_LABEL) ?: "Unknown"
        val advice     = intent.getStringExtra(EXTRA_ADVICE) ?: ""
        val confidence = intent.getFloatExtra(EXTRA_CONFIDENCE, 0f)
        val modelType  = ModelType.valueOf(intent.getStringExtra(EXTRA_MODEL_TYPE) ?: ModelType.CROP_DOCTOR.name)

        val titleRes = if (modelType == ModelType.CROP_DOCTOR)
            R.string.title_crop_result else R.string.title_health_result
        supportActionBar?.setTitle(titleRes)

        binding.tvDiagnosis.text  = label
        binding.tvAdvice.text     = advice
        binding.tvConfidence.text = getString(R.string.confidence_format, (confidence * 100).toInt())

        // Colour code based on confidence
        val colour = when {
            confidence > 0.75f -> getColor(R.color.confidence_high)
            confidence > 0.50f -> getColor(R.color.confidence_medium)
            else               -> getColor(R.color.confidence_low)
        }
        binding.tvConfidence.setTextColor(colour)

        binding.btnDone.setOnClickListener { finish() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
