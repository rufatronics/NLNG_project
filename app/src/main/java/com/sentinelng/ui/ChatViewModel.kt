package com.sentinelng.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.sentinelng.data.ChatMessage
import com.sentinelng.ml.LlamaModelManager
import com.sentinelng.ml.ModelDownloadManager
import com.sentinelng.utils.LanguageManager
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val llamaManager     = LlamaModelManager(application)
    private val downloadManager  = ModelDownloadManager(application)

    private val _messages        = MutableLiveData<List<ChatMessage>>(emptyList())
    val messages: LiveData<List<ChatMessage>> = _messages

    private val _isGenerating    = MutableLiveData(false)
    val isGenerating: LiveData<Boolean> = _isGenerating

    private val _isModelLoading  = MutableLiveData(false)
    val isModelLoading: LiveData<Boolean> = _isModelLoading

    /**
     * Shown in the chat toolbar to tell the user which model is active.
     *
     * - "Enhanced model"  → Gist-downloaded GGUF
     * - "Bonsai (English)" → embedded Bonsai fallback
     * - "mT5 (Nigerian)"  → embedded mT5 legacy fallback
     */
    private val _activeModelLabel = MutableLiveData<String>()
    val activeModelLabel: LiveData<String> = _activeModelLabel

    private val messageList = mutableListOf<ChatMessage>()

    // ── Lifecycle ──────────────────────────────────────────────────────────

    fun initialise() {
        val language = LanguageManager.getLanguage(getApplication())
        _isModelLoading.value = true

        viewModelScope.launch {
            // Load the best available model for the current language.
            // LlamaModelManager.resolveModelPath() already prioritises:
            //   1. Downloaded model (any language)
            //   2. Bonsai (English fallback)
            //   3. mT5 (Nigerian-language legacy fallback)
            llamaManager.loadModelForLanguage(language)

            // Update toolbar label so the user knows what's running
            _activeModelLabel.postValue(resolveModelLabel())
            _isModelLoading.postValue(false)

            // Post greeting
            val greeting = LanguageManager.getGreeting(language)
            addMessage(ChatMessage(text = greeting, isUser = false))
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _isGenerating.value == true) return
        val language = LanguageManager.getLanguage(getApplication())

        addMessage(ChatMessage(text = text, isUser = true))
        _isGenerating.value = true

        viewModelScope.launch {
            val response = llamaManager.generate(text, language)
            addMessage(ChatMessage(text = response, isUser = false))
            _isGenerating.postValue(false)
        }
    }

    fun clearChat() {
        llamaManager.clearHistory()
        messageList.clear()
        _messages.value = emptyList()
        initialise()
    }

    override fun onCleared() {
        super.onCleared()
        llamaManager.unload()
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Human-readable label indicating which model is currently loaded.
     *
     * Priority mirrors [LlamaModelManager.resolveModelPath]:
     *  1. Downloaded model → "Enhanced model (downloaded)"
     *  2. Otherwise        → "Bonsai 1.7B (English)" or "mT5 (Nigerian languages)"
     */
    private fun resolveModelLabel(): String {
        return if (downloadManager.isModelAlreadyDownloaded()) {
            val version = downloadManager.getDownloadedModelVersion() ?: ""
            "Enhanced model v$version"
        } else {
            val language = LanguageManager.getLanguage(getApplication())
            if (language == com.sentinelng.data.SupportedLanguage.ENGLISH)
                "Bonsai 1.7B (English)"
            else
                "mT5 (Nigerian languages)"
        }
    }

    private fun addMessage(msg: ChatMessage) {
        messageList.add(msg)
        _messages.postValue(messageList.toList())
    }
}
