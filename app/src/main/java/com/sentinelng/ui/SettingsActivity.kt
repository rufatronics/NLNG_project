package com.sentinelng.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sentinelng.R
import com.sentinelng.data.SupportedLanguage
import com.sentinelng.databinding.ActivitySettingsBinding
import com.sentinelng.utils.LanguageManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setTitle(R.string.title_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupLanguagePicker()
        setupAbout()
    }

    private fun setupLanguagePicker() {
        val current = LanguageManager.getLanguage(this)

        // Set current selection in RadioGroup
        val radioId = when (current) {
            SupportedLanguage.ENGLISH -> R.id.rb_english
            SupportedLanguage.HAUSA   -> R.id.rb_hausa
            SupportedLanguage.YORUBA  -> R.id.rb_yoruba
            SupportedLanguage.IGBO    -> R.id.rb_igbo
            SupportedLanguage.PIDGIN  -> R.id.rb_pidgin
        }
        binding.rgLanguage.check(radioId)

        binding.rgLanguage.setOnCheckedChangeListener { _, checkedId ->
            val selected = when (checkedId) {
                R.id.rb_hausa   -> SupportedLanguage.HAUSA
                R.id.rb_yoruba  -> SupportedLanguage.YORUBA
                R.id.rb_igbo    -> SupportedLanguage.IGBO
                R.id.rb_pidgin  -> SupportedLanguage.PIDGIN
                else            -> SupportedLanguage.ENGLISH
            }
            LanguageManager.setLanguage(this, selected)
            Toast.makeText(
                this,
                getString(R.string.language_changed, selected.displayName),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun setupAbout() {
        binding.tvVersion.text = getString(R.string.app_version, "1.0.0")
        binding.tvAbout.text   = getString(R.string.about_text)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
