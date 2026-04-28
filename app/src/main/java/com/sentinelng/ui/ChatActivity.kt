package com.sentinelng.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.sentinelng.R
import com.sentinelng.databinding.ActivityChatBinding
import com.sentinelng.ui.adapter.ChatAdapter

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var chatAdapter: ChatAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setTitle(R.string.title_chat)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupRecyclerView()
        setupInput()
        observeViewModel()

        if (savedInstanceState == null) {
            viewModel.initialise()
        }
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter()
        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
        }
    }

    private fun setupInput() {
        binding.etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else false
        }
        binding.btnSend.setOnClickListener { sendMessage() }
    }

    private fun sendMessage() {
        val text = binding.etMessage.text?.toString()?.trim() ?: return
        if (text.isEmpty()) return
        binding.etMessage.text?.clear()
        viewModel.sendMessage(text)
    }

    private fun observeViewModel() {
        viewModel.messages.observe(this) { messages ->
            chatAdapter.submitList(messages)
            if (messages.isNotEmpty()) {
                binding.rvMessages.smoothScrollToPosition(messages.size - 1)
            }
        }

        viewModel.isGenerating.observe(this) { generating ->
            binding.typingIndicator.visibility = if (generating) View.VISIBLE else View.GONE
            binding.btnSend.isEnabled = !generating
        }

        viewModel.isModelLoading.observe(this) { loading ->
            binding.progressModelLoad.visibility = if (loading) View.VISIBLE else View.GONE
            binding.tvModelLoadStatus.visibility = if (loading) View.VISIBLE else View.GONE
            binding.etMessage.isEnabled = !loading
            binding.btnSend.isEnabled = !loading
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_chat, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear_chat -> {
                viewModel.clearChat()
                true
            }
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
