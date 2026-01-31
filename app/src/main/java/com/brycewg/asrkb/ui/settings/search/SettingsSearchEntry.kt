/**
 * 设置搜索条目数据结构。
 *
 * 归属模块：ui/settings/search
 */
package com.brycewg.asrkb.ui.settings.search

import android.app.Activity
import androidx.annotation.IdRes
import androidx.annotation.StringRes

data class SettingsSearchEntry(
    val title: String,
    @StringRes val screenTitleResId: Int,
    val activityClass: Class<out Activity>,
    @IdRes val targetViewId: Int,
    val keywords: List<String> = emptyList()
)

