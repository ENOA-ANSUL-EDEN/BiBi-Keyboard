package com.brycewg.asrkb.ui.settings.asr.sections

import android.widget.TextView
import com.brycewg.asrkb.R
import com.brycewg.asrkb.asr.AsrVendor
import com.brycewg.asrkb.ui.AsrVendorUi
import com.brycewg.asrkb.ui.SettingsOptionSheet
import com.brycewg.asrkb.ui.settings.asr.AsrSettingsBinding
import com.brycewg.asrkb.ui.settings.asr.AsrSettingsSection

internal class AsrVendorSelectionSection : AsrSettingsSection {
    override fun bind(binding: AsrSettingsBinding) {
        val tvAsrVendor = binding.view<TextView>(R.id.tvAsrVendorValue)
        val vendorOrder = AsrVendorUi.ordered()
        val vendorItems = vendorOrder.map { vendor ->
            SettingsOptionSheet.TaggedItem(
                title = AsrVendorUi.name(binding.activity, vendor),
                tags = AsrVendorUi.tags(vendor).map { tag ->
                    SettingsOptionSheet.Tag(
                        label = binding.activity.getString(tag.labelResId),
                        bgColorResId = tag.bgColorResId,
                        textColorResId = tag.textColorResId
                    )
                }
            )
        }

        tvAsrVendor.setOnClickListener { v ->
            binding.hapticTapIfEnabled(v)
            val curIdx = vendorOrder.indexOf(binding.prefs.asrVendor).coerceAtLeast(0)
            SettingsOptionSheet.showSingleChoiceTagged(
                context = binding.activity,
                titleResId = R.string.label_asr_vendor,
                items = vendorItems,
                selectedIndex = curIdx
            ) { selectedIdx ->
                val vendor = vendorOrder.getOrNull(selectedIdx) ?: AsrVendor.Volc
                binding.viewModel.updateVendor(vendor)
            }
        }
    }
}

