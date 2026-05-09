package com.claudevoice.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.claudevoice.R
import com.claudevoice.api.ClaudeApiClient
import com.claudevoice.data.SettingsRepository
import com.claudevoice.databinding.ActivityVoiceSettingsBinding
import com.claudevoice.voice.VoiceManager
import kotlinx.coroutines.launch

class VoiceSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVoiceSettingsBinding
    private lateinit var repository: SettingsRepository
    private lateinit var voiceManager: VoiceManager
    private val apiClient = ClaudeApiClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVoiceSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        repository = SettingsRepository(this)
        voiceManager = VoiceManager(this)
        voiceManager.applySettings(repository.voiceSettings)

        binding.apiKeyInput.setText(repository.apiKey)
        binding.guidelinesInput.setText(repository.voiceGuidelines)
        refreshSettingsDisplay()

        binding.saveApiKeyButton.setOnClickListener {
            repository.apiKey = binding.apiKeyInput.text?.toString()?.trim() ?: ""
            toast(getString(R.string.toast_key_saved))
        }

        binding.applyButton.setOnClickListener { applyGuidelines() }

        binding.testVoiceButton.setOnClickListener {
            voiceManager.speak(getString(R.string.test_sentence))
        }
    }

    private fun applyGuidelines() {
        val apiKey = binding.apiKeyInput.text?.toString()?.trim() ?: ""
        val guidelines = binding.guidelinesInput.text?.toString()?.trim() ?: ""

        if (apiKey.isEmpty()) { toast(getString(R.string.toast_enter_key)); return }
        if (guidelines.isEmpty()) { toast(getString(R.string.toast_enter_guidelines)); return }

        setApplyButtonEnabled(false)

        lifecycleScope.launch {
            apiClient.parseVoiceGuidelines(apiKey, guidelines)
                .onSuccess { settings ->
                    repository.apiKey = apiKey
                    repository.voiceGuidelines = guidelines
                    repository.voiceSettings = settings
                    voiceManager.applySettings(settings)
                    runOnUiThread {
                        refreshSettingsDisplay()
                        toast(getString(R.string.toast_applied))
                    }
                }
                .onFailure { error ->
                    runOnUiThread { toast("Failed: ${error.message}") }
                }
            runOnUiThread { setApplyButtonEnabled(true) }
        }
    }

    private fun refreshSettingsDisplay() {
        val s = repository.voiceSettings
        binding.settingsDisplay.text = buildString {
            appendLine("Speed:  ${"%.1f".format(s.speechRate)}x")
            appendLine("Pitch:  ${"%.1f".format(s.pitch)}x")
            appendLine("Volume: ${(s.volume * 100).toInt()}%")
            appendLine("Locale: ${s.voiceLocale}")
            append("Gender: ${s.voiceGender}")
        }
    }

    private fun setApplyButtonEnabled(enabled: Boolean) {
        binding.applyButton.isEnabled = enabled
        binding.applyButton.text = getString(
            if (enabled) R.string.btn_apply else R.string.btn_applying
        )
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    override fun onDestroy() {
        super.onDestroy()
        voiceManager.shutdown()
    }
}
