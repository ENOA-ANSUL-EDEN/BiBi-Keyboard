package com.brycewg.asrkb.ime

import android.content.Context
import android.text.TextUtils
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.brycewg.asrkb.R
import com.brycewg.asrkb.store.Prefs

internal class ImeUiRenderer(
    private val context: Context,
    private val prefs: Prefs,
    private val views: ImeViewRefs,
    private val inputHelper: InputConnectionHelper,
    private val actionHandler: KeyboardActionHandler,
    private val inputConnectionProvider: () -> android.view.inputmethod.InputConnection?,
    private val performKeyHaptic: (View?) -> Unit,
    private val isAiEditPanelVisible: () -> Boolean,
    private val micGestureController: () -> MicGestureController?,
    private val downloadClipboardFileById: (String) -> Unit,
    private val markShownClipboardText: (String) -> Unit,
    private val copyTextToSystemClipboard: (label: String, text: String) -> Boolean,
) {
    private var clipboardPreviewTimeout: Runnable? = null

    fun render(state: KeyboardState) {
        when (state) {
            is KeyboardState.Idle -> updateUiIdle()
            is KeyboardState.Listening -> updateUiListening(state)
            is KeyboardState.Processing -> updateUiProcessing()
            is KeyboardState.AiProcessing -> updateUiAiProcessing()
            is KeyboardState.AiEditListening -> updateUiAiEditListening()
            is KeyboardState.AiEditProcessing -> updateUiAiEditProcessing()
        }

        // 更新中间结果到 composing
        if (state is KeyboardState.Listening && state.partialText != null) {
            inputConnectionProvider()?.let { ic ->
                inputHelper.setComposingText(ic, state.partialText)
            }
        }

        updateAiEditInfoBar(state)
    }

    fun showStatusMessage(message: String) {
        clearStatusTextStyle()
        views.txtStatusText?.text = message
        enableStatusMarquee()

        val isError = message.contains("错误", ignoreCase = true) ||
            message.contains("失败", ignoreCase = true) ||
            message.contains("异常", ignoreCase = true) ||
            message.contains("error", ignoreCase = true) ||
            message.contains("failed", ignoreCase = true) ||
            message.contains("failure", ignoreCase = true) ||
            message.contains("exception", ignoreCase = true) ||
            message.contains("invalid", ignoreCase = true) ||
            message.contains(Regex("\\b(401|403|404|500|502|503)\\b"))

        if (isError) {
            val copied = try {
                copyTextToSystemClipboard("ASR Error", message)
            } catch (e: Exception) {
                android.util.Log.e("AsrKeyboardService", "Failed to copy error message", e)
                false
            }
            if (copied) {
                Toast.makeText(context, context.getString(R.string.error_auto_copied), Toast.LENGTH_SHORT).show()
            }
        }

        if (isAiEditPanelVisible()) {
            val tv = views.txtAiEditInfo
            if (tv != null) {
                val state = actionHandler.getCurrentState()
                val allowOverride = when (state) {
                    is KeyboardState.AiEditListening -> state.instruction.isNullOrBlank() || isError
                    else -> true
                }
                if (allowOverride) {
                    applyInfoBarMarquee(tv, enabled = true)
                    tv.text = message
                }
            }
        }
    }

    fun updateWaveformSensitivity() {
        views.waveformView?.sensitivity = prefs.waveformSensitivity
    }

    fun updatePostprocIcon() {
        views.btnPostproc?.setImageResource(if (prefs.postProcessEnabled) R.drawable.magic_wand_fill else R.drawable.magic_wand)
    }

    fun showClipboardPreview(preview: ClipboardPreview) {
        val tv = views.txtStatusText ?: return
        disableStatusMarquee()
        tv.text = preview.displaySnippet

        // 限制粘贴板内容为单行显示，避免破坏 UI 布局（txtStatusText 默认已单行，这里冗余保证）
        tv.maxLines = 1
        tv.isSingleLine = true

        // 取消圆角遮罩与额外内边距：使用中心按钮原生背景
        tv.background = null
        tv.setPaddingRelative(0, 0, 0, 0)

        // 启用点击：文本类型为粘贴，文件类型为拉取
        tv.isClickable = true
        tv.isFocusable = true
        tv.setOnClickListener { v ->
            performKeyHaptic(v)
            if (preview.type == ClipboardPreviewType.FILE) {
                val entryId = preview.fileEntryId
                if (!entryId.isNullOrEmpty()) {
                    downloadClipboardFileById(entryId)
                }
            } else {
                actionHandler.handleClipboardPreviewClick(inputConnectionProvider())
            }
        }

        // 若当前处于录音波形显示，临时切换为文本以展示预览
        views.txtStatusText?.visibility = View.VISIBLE
        views.waveformView?.visibility = View.GONE
        views.waveformView?.stop()

        // 标记最近一次展示的剪贴板内容，避免重复触发
        markShownClipboardText(preview.fullText)

        // 超时自动恢复
        clipboardPreviewTimeout?.let { tv.removeCallbacks(it) }
        val r = Runnable { actionHandler.hideClipboardPreview() }
        clipboardPreviewTimeout = r
        tv.postDelayed(r, 10_000)
    }

    fun hideClipboardPreview() {
        val tv = views.txtStatusText ?: return
        clipboardPreviewTimeout?.let { tv.removeCallbacks(it) }
        clipboardPreviewTimeout = null

        tv.isClickable = false
        tv.isFocusable = false
        tv.setOnClickListener(null)
        tv.background = null
        tv.setPaddingRelative(0, 0, 0, 0)
        // 保持单行显示以匹配中心信息栏设计
        tv.maxLines = 1
        tv.isSingleLine = true

        render(actionHandler.getCurrentState())
    }

    fun showRetryChip(label: String) {
        val tv = views.txtStatusText ?: return
        tv.text = label
        // 移除芯片样式，仅保持可点击
        tv.background = null
        tv.setPaddingRelative(0, 0, 0, 0)
        // 在中心信息栏展示，并临时隐藏波形
        views.txtStatusText?.visibility = View.VISIBLE
        views.waveformView?.visibility = View.GONE
        views.waveformView?.stop()
        tv.isClickable = true
        tv.isFocusable = true
        tv.setOnClickListener { v ->
            performKeyHaptic(v)
            actionHandler.handleRetryClick()
        }
    }

    fun hideRetryChip() {
        clearStatusTextStyle()
    }

    fun clearStatusTextStyle() {
        val tv = views.txtStatusText ?: return
        enableStatusMarquee()
        tv.isClickable = false
        tv.isFocusable = false
        tv.setOnClickListener(null)
        tv.background = null
        tv.setPaddingRelative(0, 0, 0, 0)
        // 中心信息栏保持单行，以避免布局跳动
        tv.maxLines = 1
        tv.isSingleLine = true
    }

    fun enableStatusMarquee() {
        val tv = views.txtStatusText ?: return
        tv.ellipsize = TextUtils.TruncateAt.MARQUEE
        tv.marqueeRepeatLimit = -1
        tv.isSelected = true
    }

    fun disableStatusMarquee() {
        val tv = views.txtStatusText ?: return
        tv.ellipsize = TextUtils.TruncateAt.END
        tv.isSelected = false
    }

    private fun applyInfoBarMarquee(tv: TextView?, enabled: Boolean) {
        if (tv == null) return
        if (enabled) {
            tv.ellipsize = TextUtils.TruncateAt.MARQUEE
            tv.marqueeRepeatLimit = -1
            tv.isSelected = true
        } else {
            tv.ellipsize = TextUtils.TruncateAt.END
            tv.isSelected = false
        }
    }

    private fun getAiEditGuideText(): String {
        return context.getString(if (prefs.micTapToggleEnabled) R.string.ime_ai_edit_guide_tap else R.string.ime_ai_edit_guide_hold)
    }

    private fun updateAiEditInfoBar(state: KeyboardState) {
        if (!isAiEditPanelVisible()) return
        val tv = views.txtAiEditInfo ?: return
        applyInfoBarMarquee(tv, enabled = true)
        tv.text = when (state) {
            is KeyboardState.AiEditListening -> state.instruction?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.status_ai_edit_listening)

            is KeyboardState.AiEditProcessing -> context.getString(R.string.status_ai_editing)
            else -> getAiEditGuideText()
        }
    }

    private fun updateUiIdle() {
        hideRecordingGesturesOverlay()
        clearStatusTextStyle()
        // 显示文字，隐藏波形
        views.txtStatusText?.visibility = View.VISIBLE
        views.txtStatusText?.text = context.getString(R.string.status_idle)
        views.waveformView?.visibility = View.GONE
        views.waveformView?.stop()

        views.btnMic?.isSelected = false
        views.btnMic?.setImageResource(R.drawable.microphone)
        views.btnPromptPicker?.setImageResource(R.drawable.pencil_simple_line)
        inputConnectionProvider()?.let { inputHelper.finishComposingText(it) }
    }

    private fun updateUiListening(state: KeyboardState.Listening? = null) {
        clearStatusTextStyle()
        // 隐藏文字，显示波形动画
        views.txtStatusText?.visibility = View.GONE
        views.waveformView?.visibility = View.VISIBLE
        views.waveformView?.start()

        views.btnMic?.isSelected = true
        views.btnMic?.setImageResource(R.drawable.microphone_fill)
        views.btnPromptPicker?.setImageResource(R.drawable.pencil_simple_line)
        showRecordingGesturesOverlay(state)
    }

    private fun updateUiProcessing() {
        hideRecordingGesturesOverlay()
        clearStatusTextStyle()
        // 显示文字，隐藏波形
        views.txtStatusText?.visibility = View.VISIBLE
        views.txtStatusText?.text = context.getString(R.string.status_recognizing)
        views.waveformView?.visibility = View.GONE
        views.waveformView?.stop()

        views.btnMic?.isSelected = false
        views.btnMic?.setImageResource(R.drawable.microphone)
        views.btnPromptPicker?.setImageResource(R.drawable.pencil_simple_line)
    }

    private fun updateUiAiProcessing() {
        hideRecordingGesturesOverlay()
        clearStatusTextStyle()
        // 显示文字，隐藏波形
        views.txtStatusText?.visibility = View.VISIBLE
        views.txtStatusText?.text = context.getString(R.string.status_ai_processing)
        views.waveformView?.visibility = View.GONE
        views.waveformView?.stop()

        views.btnMic?.isSelected = false
        views.btnMic?.setImageResource(R.drawable.microphone)
        views.btnPromptPicker?.setImageResource(R.drawable.pencil_simple_line)
    }

    private fun updateUiAiEditListening() {
        hideRecordingGesturesOverlay()
        clearStatusTextStyle()
        // AI Edit 录音状态也使用文字显示（避免与普通录音混淆）
        views.txtStatusText?.visibility = View.VISIBLE
        views.txtStatusText?.text = context.getString(R.string.status_ai_edit_listening)
        views.waveformView?.visibility = View.GONE
        views.waveformView?.stop()

        views.btnMic?.isSelected = false
        views.btnMic?.setImageResource(R.drawable.microphone_fill)
        views.btnPromptPicker?.setImageResource(R.drawable.pencil_simple_line_fill)
    }

    private fun updateUiAiEditProcessing() {
        hideRecordingGesturesOverlay()
        clearStatusTextStyle()
        // 显示文字，隐藏波形
        views.txtStatusText?.visibility = View.VISIBLE
        views.txtStatusText?.text = context.getString(R.string.status_ai_editing)
        views.waveformView?.visibility = View.GONE
        views.waveformView?.stop()

        views.btnMic?.isSelected = false
        views.btnMic?.setImageResource(R.drawable.microphone)
        views.btnPromptPicker?.setImageResource(R.drawable.pencil_simple_line_fill)
    }

    private fun showRecordingGesturesOverlay(state: KeyboardState.Listening?) {
        views.rowRecordingGestures?.visibility = View.VISIBLE
        // 根据点按/长按模式设置按钮文案
        if (prefs.micTapToggleEnabled) {
            views.btnGestureCancel?.text = context.getString(R.string.label_recording_tap_cancel)
            views.btnGestureSend?.text = context.getString(R.string.label_recording_tap_send)
        } else {
            views.btnGestureCancel?.text = context.getString(R.string.label_recording_gesture_cancel)
            views.btnGestureSend?.text = context.getString(R.string.label_recording_gesture_send)
        }
        applyLockZoneUi(state)
    }

    private fun hideRecordingGesturesOverlay() {
        views.rowRecordingGestures?.visibility = View.GONE
        resetLockZoneUi()
        micGestureController()?.resetPressedState()
    }

    private fun applyLockZoneUi(state: KeyboardState.Listening?) {
        val spaceKey = views.btnExtCenter2 ?: return
        if (prefs.micTapToggleEnabled || state == null) {
            resetLockZoneUi()
            return
        }
        spaceKey.isEnabled = false
        spaceKey.text = context.getString(if (state.lockedBySwipe) R.string.hint_tap_to_stop_recording else R.string.hint_swipe_down_lock)
    }

    private fun resetLockZoneUi() {
        views.btnExtCenter2?.isEnabled = true
        views.btnExtCenter2?.text = context.getString(R.string.cd_space)
    }
}
