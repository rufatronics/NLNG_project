package com.sentinelng.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.sentinelng.data.InferenceResult
import com.sentinelng.data.ModelType
import com.sentinelng.ml.TFLiteHelper
import com.sentinelng.utils.LanguageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val tflite = TFLiteHelper(application)

    private val _inferenceResult = MutableLiveData<InferenceResult?>()
    val inferenceResult: LiveData<InferenceResult?> = _inferenceResult

    private val _isProcessing = MutableLiveData(false)
    val isProcessing: LiveData<Boolean> = _isProcessing

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadModel(modelType: ModelType) {
        viewModelScope.launch(Dispatchers.IO) {
            tflite.loadModel(modelType)
        }
    }

    fun runInference(bitmap: Bitmap, modelType: ModelType) {
        val language = LanguageManager.getLanguage(getApplication())
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.postValue(true)
            try {
                val result = tflite.runInference(bitmap, modelType, language)
                if (result == null) {
                    _error.postValue("Model not loaded. Please ensure model files are in assets.")
                } else {
                    _inferenceResult.postValue(result)
                }
            } catch (e: Exception) {
                _error.postValue("Inference failed: ${e.message}")
            } finally {
                _isProcessing.postValue(false)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        tflite.close()
    }
}
