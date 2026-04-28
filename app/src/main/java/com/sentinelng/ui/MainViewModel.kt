package com.sentinelng.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.sentinelng.data.NluIntent
import com.sentinelng.data.NluResult
import com.sentinelng.data.SupportedLanguage
import com.sentinelng.ml.FastTextHelper
import com.sentinelng.utils.LanguageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val fastText = FastTextHelper(application)

    private val _nluResult = MutableLiveData<NluResult?>()
    val nluResult: LiveData<NluResult?> = _nluResult

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    init {
        viewModelScope.launch(Dispatchers.IO) {
            fastText.loadModel()
        }
    }

    fun classifyIntent(text: String) {
        val language = LanguageManager.getLanguage(getApplication())
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            val result = fastText.classify(text, language)
            _nluResult.postValue(result)
            _isLoading.postValue(false)
        }
    }

    fun clearNluResult() {
        _nluResult.value = null
    }

    fun getCurrentLanguage(): SupportedLanguage =
        LanguageManager.getLanguage(getApplication())
}
