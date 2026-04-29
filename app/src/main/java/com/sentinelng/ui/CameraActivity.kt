package com.sentinelng.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.sentinelng.R
import com.sentinelng.data.ModelType
import com.sentinelng.databinding.ActivityCameraBinding
import com.sentinelng.utils.ImagePreprocessor
import com.sentinelng.utils.LanguageManager
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODEL_TYPE = "extra_model_type"
    }

    private lateinit var binding: ActivityCameraBinding
    private val viewModel: CameraViewModel by viewModels()
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private lateinit var modelType: ModelType

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        modelType = ModelType.valueOf(
            intent.getStringExtra(EXTRA_MODEL_TYPE) ?: ModelType.CROP_DOCTOR.name
        )

        cameraExecutor = Executors.newSingleThreadExecutor()
        setupUI()
        startCamera()
        observeViewModel()

        viewModel.loadModel(modelType)
    }

    private fun setupUI() {
        val lang = LanguageManager.getLanguage(this)
        val label = if (modelType == ModelType.CROP_DOCTOR) "crop" else "patient"
        binding.tvCameraHint.text = LanguageManager.getCameraHint(lang, label)

        val titleRes = if (modelType == ModelType.CROP_DOCTOR)
            R.string.title_crop_doctor else R.string.title_health_scan
        supportActionBar?.setTitle(titleRes)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.btnCapture.setOnClickListener { capturePhoto() }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Camera failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun capturePhoto() {
        val capture = imageCapture ?: return
        binding.btnCapture.isEnabled = false

        capture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = image.toBitmap()
                    image.close()

                    val lang = LanguageManager.getLanguage(this@CameraActivity)
                    binding.tvStatusText.text = LanguageManager.getAnalysingMessage(lang)
                    binding.tvStatus.visibility = View.VISIBLE

                    viewModel.runInference(bitmap, modelType)
                }

                override fun onError(exception: ImageCaptureException) {
                    binding.btnCapture.isEnabled = true
                    Toast.makeText(
                        this@CameraActivity,
                        "Capture error: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    private fun observeViewModel() {
        viewModel.inferenceResult.observe(this) { result ->
            result ?: return@observe
            val intent = Intent(this, ResultActivity::class.java).apply {
                putExtra(ResultActivity.EXTRA_LABEL, result.label)
                putExtra(ResultActivity.EXTRA_ADVICE, result.advice)
                putExtra(ResultActivity.EXTRA_CONFIDENCE, result.confidence)
                putExtra(ResultActivity.EXTRA_MODEL_TYPE, modelType.name)
            }
            startActivity(intent)
            binding.btnCapture.isEnabled = true
            binding.tvStatus.visibility = View.GONE
        }

        viewModel.isProcessing.observe(this) { processing ->
            binding.progressBar.visibility = if (processing) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(this) { err ->
            err ?: return@observe
            Toast.makeText(this, err, Toast.LENGTH_LONG).show()
            binding.btnCapture.isEnabled = true
            binding.tvStatus.visibility = View.GONE
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
