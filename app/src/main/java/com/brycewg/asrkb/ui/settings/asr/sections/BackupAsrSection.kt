package com.brycewg.asrkb.ui.settings.asr.sections

import android.view.View
import android.widget.TextView
import com.brycewg.asrkb.R
import com.brycewg.asrkb.asr.AsrVendor
import com.brycewg.asrkb.ui.AsrVendorUi
import com.brycewg.asrkb.ui.SettingsOptionSheet
import com.brycewg.asrkb.ui.settings.asr.AsrSettingsBinding
import com.brycewg.asrkb.ui.settings.asr.AsrSettingsSection
import com.google.android.material.materialswitch.MaterialSwitch

internal class BackupAsrSection : AsrSettingsSection {
    override fun bind(binding: AsrSettingsBinding) {
        val switchBackupAsrEnabled = binding.view<MaterialSwitch>(R.id.switchBackupAsrEnabled)
        val groupBackupAsr = binding.view<View>(R.id.groupBackupAsr)
        val tvBackupAsrVendor = binding.view<TextView>(R.id.tvBackupAsrVendorValue)

        fun updateVendorSummary() {
            val vendorOrder = AsrVendorUi.ordered()
            val vendorItems = AsrVendorUi.names(binding.activity)
            val idx = vendorOrder.indexOf(binding.prefs.backupAsrVendor).coerceAtLeast(0)
            tvBackupAsrVendor.text = vendorItems[idx]
        }

        fun updateBackupUi() {
            val enabled = binding.prefs.backupAsrEnabled
            groupBackupAsr.visibility = if (enabled) View.VISIBLE else View.GONE
            updateVendorSummary()
        }

        switchBackupAsrEnabled.isChecked = binding.prefs.backupAsrEnabled
        updateBackupUi()

        switchBackupAsrEnabled.setOnCheckedChangeListener { _, isChecked ->
            binding.prefs.backupAsrEnabled = isChecked
            updateBackupUi()
            binding.hapticTapIfEnabled(switchBackupAsrEnabled)
        }

        tvBackupAsrVendor.setOnClickListener { v ->
            binding.hapticTapIfEnabled(v)
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
            val curIdx = vendorOrder.indexOf(binding.prefs.backupAsrVendor).coerceAtLeast(0)
            SettingsOptionSheet.showSingleChoiceTagged(
                context = binding.activity,
                titleResId = R.string.label_backup_asr_vendor,
                items = vendorItems,
                selectedIndex = curIdx
            ) { selectedIdx ->
                val vendor = vendorOrder.getOrNull(selectedIdx) ?: AsrVendor.SiliconFlow
                binding.prefs.backupAsrVendor = vendor
                updateVendorSummary()
            }
        }
    }
}

