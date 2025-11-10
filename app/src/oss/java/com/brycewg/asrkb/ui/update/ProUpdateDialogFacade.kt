package com.brycewg.asrkb.ui.update

import android.app.Activity

/**
 * Pro 版本更新对话框门面（OSS 占位符）
 *
 * OSS 版本不提供 Pro 更新对话框功能
 */
object ProUpdateDialogFacade {
    /**
     * 是否应该显示 Pro 版本的更新对话框
     */
    fun shouldShowProDialog(): Boolean = false

    /**
     * 显示 Pro 版本的更新对话框（OSS 空实现）
     */
    fun showProUpdateDialog(
        activity: Activity,
        result: UpdateChecker.UpdateCheckResult
    ) {
        // OSS 版本不实现此功能
    }
}
