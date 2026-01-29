package com.brycewg.asrkb.ui.settings.asr.sections

import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.brycewg.asrkb.R
import com.brycewg.asrkb.ui.settings.asr.bindString
import com.brycewg.asrkb.ui.installExplainedSwitch
import com.brycewg.asrkb.ui.settings.asr.AsrSettingsBinding
import com.brycewg.asrkb.ui.settings.asr.AsrSettingsSection
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch

internal class SonioxAsrSettingsSection : AsrSettingsSection {
    override fun bind(binding: AsrSettingsBinding) {
        binding.view<EditText>(R.id.etSonioxApiKey).apply {
            setText(binding.prefs.sonioxApiKey)
            bindString { binding.prefs.sonioxApiKey = it }
        }

        binding.view<MaterialSwitch>(R.id.switchSonioxStreaming).apply {
            isChecked = binding.prefs.sonioxStreamingEnabled
            installExplainedSwitch(
                context = binding.activity,
                titleRes = R.string.label_soniox_streaming,
                offDescRes = R.string.feature_soniox_streaming_off_desc,
                onDescRes = R.string.feature_soniox_streaming_on_desc,
                preferenceKey = "soniox_streaming_explained",
                readPref = { binding.prefs.sonioxStreamingEnabled },
                writePref = { v -> binding.viewModel.updateSonioxStreaming(v) },
                hapticFeedback = { binding.hapticTapIfEnabled(it) }
            )
        }

        bindLanguageSelection(binding)

        binding.view<MaterialSwitch>(R.id.switchSonioxLanguageStrict).apply {
            isChecked = binding.prefs.sonioxLanguageHintsStrict
            installExplainedSwitch(
                context = binding.activity,
                titleRes = R.string.label_soniox_language_strict,
                offDescRes = R.string.feature_soniox_language_strict_off_desc,
                onDescRes = R.string.feature_soniox_language_strict_on_desc,
                preferenceKey = "soniox_language_strict_explained",
                readPref = { binding.prefs.sonioxLanguageHintsStrict },
                writePref = { v -> binding.prefs.sonioxLanguageHintsStrict = v },
                hapticFeedback = { binding.hapticTapIfEnabled(it) }
            )
        }

        binding.view<MaterialButton>(R.id.btnSonioxGetKey).setOnClickListener { v ->
            binding.hapticTapIfEnabled(v)
            binding.openUrlSafely("https://brycewg.notion.site/bibi-keyboard-providers-guide")
        }
    }

    private fun bindLanguageSelection(binding: AsrSettingsBinding) {
        val sonioxLangLabels = listOf(
            binding.activity.getString(R.string.soniox_lang_auto),
            binding.activity.getString(R.string.soniox_lang_en),
            binding.activity.getString(R.string.soniox_lang_zh),
            binding.activity.getString(R.string.soniox_lang_ja),
            binding.activity.getString(R.string.soniox_lang_ko),
            binding.activity.getString(R.string.soniox_lang_es),
            binding.activity.getString(R.string.soniox_lang_pt),
            binding.activity.getString(R.string.soniox_lang_de),
            binding.activity.getString(R.string.soniox_lang_fr),
            binding.activity.getString(R.string.soniox_lang_id),
            binding.activity.getString(R.string.soniox_lang_ru),
            binding.activity.getString(R.string.soniox_lang_ar),
            binding.activity.getString(R.string.soniox_lang_hi),
            binding.activity.getString(R.string.soniox_lang_vi),
            binding.activity.getString(R.string.soniox_lang_th),
            binding.activity.getString(R.string.soniox_lang_ms),
            binding.activity.getString(R.string.soniox_lang_fil)
        )
        val sonioxLangCodes = listOf(
            "",
            "en",
            "zh",
            "ja",
            "ko",
            "es",
            "pt",
            "de",
            "fr",
            "id",
            "ru",
            "ar",
            "hi",
            "vi",
            "th",
            "ms",
            "fil"
        )
        val tvSonioxLanguage = binding.view<TextView>(R.id.tvSonioxLanguageValue)

        fun updateSonioxLangSummary() {
            val selected = binding.prefs.getSonioxLanguages()
            if (selected.isEmpty()) {
                tvSonioxLanguage.text = binding.activity.getString(R.string.soniox_lang_auto)
                return
            }
            val names = selected.mapNotNull { code ->
                val idx = sonioxLangCodes.indexOf(code)
                if (idx >= 0) sonioxLangLabels[idx] else null
            }
            tvSonioxLanguage.text = if (names.isEmpty()) {
                binding.activity.getString(R.string.soniox_lang_auto)
            } else {
                names.joinToString(separator = "、")
            }
        }

        updateSonioxLangSummary()
        tvSonioxLanguage.setOnClickListener { v ->
            binding.hapticTapIfEnabled(v)
            val saved = binding.prefs.getSonioxLanguages()
            val checked = BooleanArray(sonioxLangCodes.size) { idx ->
                if (idx == 0) saved.isEmpty() else sonioxLangCodes[idx] in saved
            }
            AlertDialog.Builder(binding.activity)
                .setTitle(R.string.label_soniox_language)
                .setMultiChoiceItems(sonioxLangLabels.toTypedArray(), checked) { _, which, isChecked ->
                    if (which == 0) {
                        if (isChecked) {
                            for (i in 1 until checked.size) checked[i] = false
                        }
                    } else if (isChecked) {
                        checked[0] = false
                    }
                }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val codes = mutableListOf<String>()
                    for (i in checked.indices) {
                        if (checked[i]) {
                            val code = sonioxLangCodes[i]
                            if (code.isNotEmpty()) codes.add(code)
                        }
                    }
                    binding.viewModel.updateSonioxLanguages(codes)
                    updateSonioxLangSummary()
                }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
        }
    }
}
