package com.claudevoice.ui

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.claudevoice.R
import com.claudevoice.api.ClaudeApiClient
import com.claudevoice.data.SettingsRepository
import com.claudevoice.databinding.ActivityMainBinding
import com.claudevoice.voice.VoiceManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var voiceManager: VoiceManager
    private lateinit var repository: SettingsRepository
    private val apiClient = ClaudeApiClient()
    private val adapter = ChatAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = SettingsRepository(this)
        voiceManager = VoiceManager(this)
        voiceManager.applySettings(repository.voiceSettings)

        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }

        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_settings) {
                startActivity(Intent(this, VoiceSettingsActivity::class.java))
                true
            } else false
        }

        binding.sendButton.setOnClickListener { sendMessage() }
        binding.messageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendMessage(); true } else false
        }
    }

    override fun onResume() {
        super.onResume()
        voiceManager.applySettings(repository.voiceSettings)
    }

    private fun sendMessage() {
        val text = binding.messageInput.text?.toString()?.trim() ?: return
        if (text.isEmpty()) return

        val apiKey = repository.apiKey
        if (apiKey.isEmpty()) {
            startActivity(Intent(this, VoiceSettingsActivity::class.java))
            return
        }

        binding.messageInput.setText("")
        adapter.addMessage(ChatMessage(text, isUser = true))
        adapter.addMessage(ChatMessage("…", isUser = false))
        scrollToBottom()

        val response = StringBuilder()
        lifecycleScope.launch {
            apiClient.sendMessage(
                apiKey = apiKey,
                userMessage = text,
                onChunk = { chunk ->
                    response.append(chunk)
                    runOnUiThread {
                        adapter.updateLastMessage(response.toString())
                        scrollToBottom()
                    }
                }
            ).onSuccess {
                voiceManager.speak(response.toString())
            }.onFailure { error ->
                runOnUiThread {
                    adapter.updateLastMessage("Error: ${error.message}")
                }
            }
        }
    }

    private fun scrollToBottom() {
        binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceManager.shutdown()
    }
}
