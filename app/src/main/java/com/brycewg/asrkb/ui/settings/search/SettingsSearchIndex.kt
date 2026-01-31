/**
 * 设置搜索索引构建与缓存。
 *
 * 归属模块：ui/settings/search
 */
package com.brycewg.asrkb.ui.settings.search

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import com.brycewg.asrkb.R
import com.brycewg.asrkb.ui.settings.ai.AiPostSettingsActivity
import com.brycewg.asrkb.ui.settings.asr.AsrSettingsActivity
import com.brycewg.asrkb.ui.settings.backup.BackupSettingsActivity
import com.brycewg.asrkb.ui.settings.floating.FloatingSettingsActivity
import com.brycewg.asrkb.ui.settings.input.InputSettingsActivity
import com.brycewg.asrkb.ui.settings.other.OtherSettingsActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputLayout
import java.util.Locale

object SettingsSearchIndex {

    @Volatile
    private var cachedLocaleTag: String? = null

    @Volatile
    private var cachedEntries: List<SettingsSearchEntry>? = null

    fun get(context: Context): List<SettingsSearchEntry> {
        val localeTag = context.resources.configuration.locales[0]?.toLanguageTag() ?: ""
        val existing = cachedEntries
        if (existing != null && cachedLocaleTag == localeTag) {
            return existing
        }
        synchronized(this) {
            val existing2 = cachedEntries
            if (existing2 != null && cachedLocaleTag == localeTag) {
                return existing2
            }
            val built = buildIndex(context)
            cachedLocaleTag = localeTag
            cachedEntries = built
            return built
        }
    }

    private data class ScreenSpec(
        @LayoutRes val layoutResId: Int,
        @StringRes val screenTitleResId: Int,
        val activityClass: Class<out Activity>,
        val manualMappings: List<ManualMapping>
    )

    private data class ManualMapping(
        @StringRes val labelResId: Int,
        @IdRes val targetViewId: Int
    )

    private fun buildIndex(context: Context): List<SettingsSearchEntry> {
        val minCardTitlePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            18f,
            context.resources.displayMetrics
        )
        val specs = listOf(
            ScreenSpec(
                layoutResId = R.layout.activity_input_settings,
                screenTitleResId = R.string.title_input_settings,
                activityClass = InputSettingsActivity::class.java,
                manualMappings = listOf(
                    ManualMapping(R.string.label_ime_switch_target, R.id.tvImeSwitchTargetValue),
                    ManualMapping(R.string.label_keyboard_height, R.id.toggleKeyboardHeight),
                    ManualMapping(R.string.label_haptic_feedback_strength, R.id.sliderHapticFeedbackStrength),
                    ManualMapping(R.string.label_keyboard_bottom_padding, R.id.sliderBottomPadding),
                    ManualMapping(R.string.label_waveform_sensitivity, R.id.sliderWaveformSensitivity),
                    ManualMapping(R.string.label_language, R.id.tvLanguageValue),
                    ManualMapping(R.string.label_extension_buttons, R.id.tvExtensionButtonsValue),
                )
            ),
            ScreenSpec(
                layoutResId = R.layout.activity_asr_settings,
                screenTitleResId = R.string.title_asr_settings,
                activityClass = AsrSettingsActivity::class.java,
                manualMappings = listOf(
                    ManualMapping(R.string.label_asr_vendor, R.id.tvAsrVendorValue),
                    ManualMapping(R.string.label_silence_window_ms, R.id.sliderSilenceWindow),
                    ManualMapping(R.string.label_silence_sensitivity, R.id.sliderSilenceSensitivity),
                )
            ),
            ScreenSpec(
                layoutResId = R.layout.activity_ai_post_settings,
                screenTitleResId = R.string.title_ai_settings,
                activityClass = AiPostSettingsActivity::class.java,
                manualMappings = listOf(
                    ManualMapping(R.string.label_llm_vendor, R.id.tvLlmVendor),
                    ManualMapping(R.string.title_ai_skip_under, R.id.sliderSkipAiUnderChars),
                )
            ),
            ScreenSpec(
                layoutResId = R.layout.activity_floating_settings,
                screenTitleResId = R.string.title_floating_settings,
                activityClass = FloatingSettingsActivity::class.java,
                manualMappings = listOf(
                    ManualMapping(R.string.label_floating_alpha, R.id.sliderFloatingAlpha),
                    ManualMapping(R.string.label_floating_size, R.id.sliderFloatingSize),
                )
            ),
            ScreenSpec(
                layoutResId = R.layout.activity_other_settings,
                screenTitleResId = R.string.title_other_settings,
                activityClass = OtherSettingsActivity::class.java,
                manualMappings = listOf(
                    ManualMapping(R.string.label_speech_preset_section, R.id.tvSpeechPresetsValue),
                )
            ),
            ScreenSpec(
                layoutResId = R.layout.activity_backup_settings,
                screenTitleResId = R.string.title_backup_settings,
                activityClass = BackupSettingsActivity::class.java,
                manualMappings = emptyList()
            ),
        )

        val unique = LinkedHashMap<String, SettingsSearchEntry>()
        for (spec in specs) {
            val root = LayoutInflater.from(context).inflate(spec.layoutResId, null, false)
            val auto = collectAutoEntries(spec, root, minCardTitlePx)
            for (entry in auto) {
                unique.putIfAbsent(entry.uniqueKey(), entry)
            }
            for (mapping in spec.manualMappings) {
                val title = runCatching { context.getString(mapping.labelResId) }.getOrNull().orEmpty()
                if (title.isBlank()) continue
                val sectionTitle = resolveSectionTitle(root, mapping.targetViewId, minCardTitlePx)
                val manual = SettingsSearchEntry(
                    title = title,
                    sectionTitle = sectionTitle,
                    screenTitleResId = spec.screenTitleResId,
                    activityClass = spec.activityClass,
                    targetViewId = mapping.targetViewId,
                    keywords = emptyList()
                )
                unique.putIfAbsent(manual.uniqueKey(), manual)
            }
        }

        return unique.values.toList()
    }

    private fun SettingsSearchEntry.uniqueKey(): String {
        return activityClass.name + "#" + targetViewId + "#" + title.lowercase(Locale.ROOT)
    }

    private fun collectAutoEntries(
        spec: ScreenSpec,
        root: View,
        minCardTitlePx: Float
    ): List<SettingsSearchEntry> {
        val results = mutableListOf<SettingsSearchEntry>()
        collectFromView(spec, root, isVisibleSoFar = true, currentSectionTitle = null, minCardTitlePx = minCardTitlePx, out = results)
        return results
    }

    private fun collectFromView(
        spec: ScreenSpec,
        view: View,
        isVisibleSoFar: Boolean,
        currentSectionTitle: String?,
        minCardTitlePx: Float,
        out: MutableList<SettingsSearchEntry>
    ) {
        val isVisible = isVisibleSoFar && view.visibility == View.VISIBLE
        if (!isVisible) return

        when (view) {
            is MaterialSwitch -> {
                val title = view.text?.toString()?.trim().orEmpty()
                if (title.isNotBlank() && view.id != View.NO_ID) {
                    out.add(
                        SettingsSearchEntry(
                            title = title,
                            sectionTitle = currentSectionTitle,
                            screenTitleResId = spec.screenTitleResId,
                            activityClass = spec.activityClass,
                            targetViewId = view.id,
                            keywords = emptyList()
                        )
                    )
                }
            }

            is MaterialButton -> {
                val title = view.text?.toString()?.trim().orEmpty()
                if (title.isNotBlank() && view.id != View.NO_ID) {
                    out.add(
                        SettingsSearchEntry(
                            title = title,
                            sectionTitle = currentSectionTitle,
                            screenTitleResId = spec.screenTitleResId,
                            activityClass = spec.activityClass,
                            targetViewId = view.id,
                            keywords = emptyList()
                        )
                    )
                }
            }

            is TextInputLayout -> {
                val title = view.hint?.toString()?.trim().orEmpty()
                val editTextId = view.editText?.id ?: View.NO_ID
                if (title.isNotBlank() && editTextId != View.NO_ID) {
                    out.add(
                        SettingsSearchEntry(
                            title = title,
                            sectionTitle = currentSectionTitle,
                            screenTitleResId = spec.screenTitleResId,
                            activityClass = spec.activityClass,
                            targetViewId = editTextId,
                            keywords = emptyList()
                        )
                    )
                }
            }
        }

        if (view is ViewGroup) {
            val nextSectionTitle = extractCardTitleText(view, minCardTitlePx) ?: currentSectionTitle
            for (i in 0 until view.childCount) {
                collectFromView(
                    spec = spec,
                    view = view.getChildAt(i),
                    isVisibleSoFar = isVisible,
                    currentSectionTitle = nextSectionTitle,
                    minCardTitlePx = minCardTitlePx,
                    out = out
                )
            }
        }
    }

    private fun extractCardTitleText(group: ViewGroup, minCardTitlePx: Float): String? {
        val linear = group as? LinearLayout ?: return null
        if (linear.orientation != LinearLayout.VERTICAL) return null

        // SettingsSectionCardTitle：bold + 20sp。使用字号与粗体做启发式识别。
        val titleView = (0 until linear.childCount)
            .asSequence()
            .map { linear.getChildAt(it) }
            .filterIsInstance<TextView>()
            .firstOrNull()
            ?: return null

        val title = titleView.text?.toString()?.trim().orEmpty()
        if (title.isBlank()) return null
        if (titleView.textSize < minCardTitlePx) return null
        val typefaceStyle = titleView.typeface?.style ?: 0
        val isBold = (typefaceStyle and Typeface.BOLD) == Typeface.BOLD || titleView.paint.isFakeBoldText
        if (!isBold) return null
        return title
    }

    private fun resolveSectionTitle(root: View, @IdRes targetViewId: Int, minCardTitlePx: Float): String? {
        val target = root.findViewById<View>(targetViewId) ?: return null
        return findSectionTitleForTarget(target, minCardTitlePx)
    }

    private fun findSectionTitleForTarget(target: View, minCardTitlePx: Float): String? {
        var p = target.parent
        while (p is ViewGroup) {
            val title = extractCardTitleText(p, minCardTitlePx)
            if (!title.isNullOrBlank()) return title
            p = p.parent
        }
        return null
    }
}
