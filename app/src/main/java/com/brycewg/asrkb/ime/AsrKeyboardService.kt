package com.brycewg.asrkb.ime

import android.Manifest
import android.content.Context
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.Intent
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.view.LayoutInflater
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.Toast
import android.content.ClipboardManager
import android.content.ClipData
import android.widget.ImageButton
import android.widget.TextView
import android.view.inputmethod.InputMethodManager
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.brycewg.asrkb.R
import com.brycewg.asrkb.asr.AsrVendor
import com.brycewg.asrkb.asr.AudioCaptureManager
import com.brycewg.asrkb.asr.BluetoothRouteManager
import com.brycewg.asrkb.asr.LlmPostProcessor
import com.brycewg.asrkb.asr.VadDetector
import com.brycewg.asrkb.store.Prefs
import com.brycewg.asrkb.util.HapticFeedbackHelper
import com.brycewg.asrkb.ui.SettingsActivity
import com.brycewg.asrkb.ui.AsrVendorUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.brycewg.asrkb.LocaleHelper
import com.brycewg.asrkb.UiColors
import com.brycewg.asrkb.clipboard.SyncClipboardManager
import com.brycewg.asrkb.clipboard.ClipboardHistoryStore
import com.brycewg.asrkb.store.debug.DebugLogManager
import androidx.recyclerview.widget.RecyclerView
import android.text.TextUtils
import androidx.appcompat.widget.PopupMenu

/**
 * ASR 键盘服务
 *
 * 职责：
 * - 管理键盘视图的生命周期
 * - 绑定视图事件到 KeyboardActionHandler
 * - 响应 UI 更新通知
 * - 管理系统回调（onStartInputView, onFinishInputView 等）
 * - 协调剪贴板同步等辅助功能
 *
 * 复杂的业务逻辑已拆分到：
 * - KeyboardActionHandler: 键盘动作处理和状态管理
 * - AsrSessionManager: ASR 引擎生命周期管理
 * - InputConnectionHelper: 输入连接操作封装
 * - BackspaceGestureHandler: 退格手势处理
 */
class AsrKeyboardService : InputMethodService(), KeyboardActionHandler.UiListener {

    companion object {
        const val ACTION_REFRESH_IME_UI = "com.brycewg.asrkb.action.REFRESH_IME_UI"
    }

    override fun attachBaseContext(newBase: Context?) {
        val wrapped = newBase?.let { LocaleHelper.wrap(it) }
        super.attachBaseContext(wrapped ?: newBase)
    }

    // ========== 组件实例 ==========
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var prefs: Prefs
    private lateinit var inputHelper: InputConnectionHelper
    private lateinit var asrManager: AsrSessionManager
    private lateinit var actionHandler: KeyboardActionHandler
    private lateinit var backspaceGestureHandler: BackspaceGestureHandler

    // ========== 视图与控制器 ==========
    private var rootView: View? = null
    private var viewRefs: ImeViewRefs? = null
    private val themeStyler = ImeThemeStyler()
    private var aiEditPanelController: AiEditPanelController? = null
    private var numpadPanelController: NumpadPanelController? = null
    private var clipboardPanelController: ClipboardPanelController? = null
    private var micGestureController: MicGestureController? = null
    private var imeViewVisible: Boolean = false

    private val layoutMainKeyboard: View?
        get() = viewRefs?.layoutMainKeyboard
    private val layoutAiEditPanel: View?
        get() = viewRefs?.layoutAiEditPanel
    private val layoutNumpadPanel: View?
        get() = viewRefs?.layoutNumpadPanel
    private val layoutClipboardPanel: View?
        get() = viewRefs?.layoutClipboardPanel

    private val btnMic: FloatingActionButton?
        get() = viewRefs?.btnMic
    private val btnSettings: ImageButton?
        get() = viewRefs?.btnSettings
    private val btnEnter: ImageButton?
        get() = viewRefs?.btnEnter
    private val btnPostproc: ImageButton?
        get() = viewRefs?.btnPostproc
    private val btnAiEdit: ImageButton?
        get() = viewRefs?.btnAiEdit
    private val btnBackspace: ImageButton?
        get() = viewRefs?.btnBackspace
    private val btnPromptPicker: ImageButton?
        get() = viewRefs?.btnPromptPicker
    private val btnHide: ImageButton?
        get() = viewRefs?.btnHide
    private val btnImeSwitcher: ImageButton?
        get() = viewRefs?.btnImeSwitcher

    private val btnPunct1: ImageButton?
        get() = viewRefs?.btnPunct1
    private val btnPunct2: com.brycewg.asrkb.ui.widgets.PunctKeyView?
        get() = viewRefs?.btnPunct2
    private val btnPunct3: com.brycewg.asrkb.ui.widgets.PunctKeyView?
        get() = viewRefs?.btnPunct3
    private val btnPunct4: ImageButton?
        get() = viewRefs?.btnPunct4

    private val rowRecordingGestures: ConstraintLayout?
        get() = viewRefs?.rowRecordingGestures
    private val btnGestureCancel: TextView?
        get() = viewRefs?.btnGestureCancel
    private val btnGestureSend: TextView?
        get() = viewRefs?.btnGestureSend

    private val btnExt1: ImageButton?
        get() = viewRefs?.btnExt1
    private val btnExt2: ImageButton?
        get() = viewRefs?.btnExt2
    private val btnExt3: ImageButton?
        get() = viewRefs?.btnExt3
    private val btnExt4: ImageButton?
        get() = viewRefs?.btnExt4
    private val btnExtCenter1: View?
        get() = viewRefs?.btnExtCenter1
    private val btnExtCenter2: Button?
        get() = viewRefs?.btnExtCenter2

    private val txtStatusText: TextView?
        get() = viewRefs?.txtStatusText
    private val txtAiEditInfo: TextView?
        get() = viewRefs?.txtAiEditInfo
    private val waveformView: com.brycewg.asrkb.ui.widgets.WaveformView?
        get() = viewRefs?.waveformView
    private val groupMicStatus: View?
        get() = viewRefs?.groupMicStatus

    private val clipBtnDelete: ImageButton?
        get() = viewRefs?.clipBtnDelete
    private val clipList: RecyclerView?
        get() = viewRefs?.clipList

    private val isAiEditPanelVisible: Boolean
        get() = aiEditPanelController?.isVisible == true
    private val isNumpadPanelVisible: Boolean
        get() = numpadPanelController?.isVisible == true
    private val isClipboardPanelVisible: Boolean
        get() = clipboardPanelController?.isVisible == true
    private val clipStore: ClipboardHistoryStore?
        get() = clipboardPanelController?.store
    // 记录麦克风容器基线高度与上次应用的缩放，避免缩放后沿用旧高度造成偏移
    private var micBaseGroupHeight: Int = -1
    private var lastAppliedHeightScale: Float = 1.0f

    // ========== 剪贴板和其他辅助功能 ==========
    private var clipboardPreviewTimeout: Runnable? = null
    private var clipboardManager: ClipboardManager? = null
    private var clipboardChangeListener: ClipboardManager.OnPrimaryClipChangedListener? = null
    @Volatile private var lastShownClipboardHash: String? = null
    private var prefsReceiver: BroadcastReceiver? = null
    private var syncClipboardManager: SyncClipboardManager? = null
    // 本地模型首次出现预热仅触发一次
    private var localPreloadTriggered: Boolean = false
    private var suppressReturnPrevImeOnHideOnce: Boolean = false
    // 系统导航栏底部高度（用于适配 Android 15 边缘到边缘显示）
    private var systemNavBarBottomInset: Int = 0
    // 记录最近一次在 IME 内弹出菜单的时间，用于限制“防误收起”逻辑的作用窗口
    private var lastPopupMenuShownAt: Long = 0L

    // ========== 生命周期 ==========

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)

        // 初始化组件
        inputHelper = InputConnectionHelper("AsrKeyboardService")
        asrManager = AsrSessionManager(this, serviceScope, prefs)
        actionHandler = KeyboardActionHandler(
            this,
            serviceScope,
            prefs,
            asrManager,
            inputHelper,
            LlmPostProcessor()
        )
        backspaceGestureHandler = BackspaceGestureHandler(inputHelper)

        // 设置监听器
        asrManager.setListener(actionHandler)
        actionHandler.setUiListener(this)
        actionHandler.setInputConnectionProvider { currentInputConnection }
        actionHandler.setEditorInfoProvider { currentInputEditorInfo }

        // 构建初始 ASR 引擎
        asrManager.rebuildEngine()

        // 监听设置变化以即时刷新键盘 UI
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    ACTION_REFRESH_IME_UI -> {
                        val v = rootView
                        if (v != null) {
                            applyKeyboardHeightScale(v)
                            applyExtensionButtonConfig()
                            // 更新波形灵敏度
                            waveformView?.sensitivity = prefs.waveformSensitivity
                            // 更新 AI 后处理按钮状态
                            btnPostproc?.setImageResource(
                                if (prefs.postProcessEnabled) R.drawable.magic_wand_fill else R.drawable.magic_wand
                            )
                            v.requestLayout()
                            // 第二次异步重算，确保尺寸变化与父容器测量完成后 padding/overlay 位置也被同步
                            v.post {
                                applyKeyboardHeightScale(v)
                                v.requestLayout()
                            }
                        }
                    }
                }
            }
        }
        prefsReceiver = r
        try {
            androidx.core.content.ContextCompat.registerReceiver(
                /* context = */ this,
                /* receiver = */ r,
                /* filter = */ IntentFilter().apply {
                    addAction(ACTION_REFRESH_IME_UI)
                },
                /* flags = */ androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (e: Throwable) {
            android.util.Log.e("AsrKeyboardService", "Failed to register prefsReceiver", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        asrManager.cleanup()
        serviceScope.cancel()
        stopClipboardSyncSafely()
        try {
            prefsReceiver?.let { unregisterReceiver(it) }
        } catch (e: Throwable) {
            android.util.Log.e("AsrKeyboardService", "Failed to unregister prefsReceiver", e)
        }
        prefsReceiver = null
    }

    @SuppressLint("InflateParams")
    override fun onCreateInputView(): View {
        return createKeyboardView()
    }

  private fun createKeyboardView(): View {
    val themedContext = ContextThemeWrapper(this, R.style.Theme_ASRKeyboard_Ime)
    val dynamicContext = com.google.android.material.color.DynamicColors.wrapContextIfAvailable(themedContext)
    val view = LayoutInflater.from(dynamicContext).inflate(R.layout.keyboard_view, null, false)
    return setupKeyboardView(view)
  }

  private fun setupKeyboardView(view: View): View {
    rootView = view

    // 根据主题动态调整键盘背景色，使其略浅于当前容器色但仍明显深于普通按键与麦克风按钮
    themeStyler.applyKeyboardBackgroundColor(view)

        // 应用 Window Insets 以适配 Android 15 边缘到边缘显示
        applyKeyboardInsets(view)

        // 查找所有视图
        bindViews(view)

        // 设置监听器
        setupListeners()

        // 应用偏好设置
        applyKeyboardHeightScale(view)
        applyPunctuationLabels()
        applyExtensionButtonConfig()

        // 更新初始 UI 状态
        refreshPermissionUi()
        onStateChanged(actionHandler.getCurrentState())

        // 同步系统导航栏颜色
        view.post { syncSystemBarsToKeyboardBackground(view) }

        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        imeViewVisible = true
        // 每次键盘视图启动时应用一次高度/底部间距等缩放
        applyKeyboardHeightScale(rootView)
        rootView?.requestLayout()
        // 冷启动首帧偶现 system insets 迟到/不稳定：主动触发一次重新分发，降低高度异常概率
        rootView?.let { androidx.core.view.ViewCompat.requestApplyInsets(it) }
        DebugLogManager.log(
            category = "ime",
            event = "start_input_view",
            data = mapOf(
                "pkg" to (info?.packageName ?: ""),
                "inputType" to (info?.inputType ?: 0),
                "imeOptions" to (info?.imeOptions ?: 0),
                "icNull" to (currentInputConnection == null),
                "isMultiLine" to ((info?.inputType ?: 0) and android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0),
                "actionId" to ((info?.imeOptions ?: 0) and android.view.inputmethod.EditorInfo.IME_MASK_ACTION)
            )
        )

        // 键盘面板首次出现时，按需异步预加载本地模型（SenseVoice/FunASR Nano/Paraformer）
        tryPreloadLocalModel()


        // 刷新 UI
        btnImeSwitcher?.visibility = View.VISIBLE
        applyPunctuationLabels()
        applyExtensionButtonConfig()
        refreshPermissionUi()
        resetPanelsToMainKeyboard()
        // 如果此时引擎仍在运行（键盘收起期间继续录音），需要把 UI 恢复为 Listening
        if (asrManager.isRunning()) {
            onStateChanged(actionHandler.getCurrentState())
        }


        // 同步系统栏颜色
        rootView?.post { syncSystemBarsToKeyboardBackground(rootView) }

        // 若正在录音，恢复中间结果为 composing
        if (asrManager.isRunning()) {
            actionHandler.restorePartialAsComposing(currentInputConnection)
        }

        // 启动剪贴板同步
        startClipboardSync()

        // 监听系统剪贴板变更，IME 可见期间弹出预览
        startClipboardPreviewListener()

        // 预热耳机路由（键盘显示）
        try { BluetoothRouteManager.setImeActive(this, true) } catch (t: Throwable) { android.util.Log.w("AsrKeyboardService", "BluetoothRouteManager setImeActive(true)", t) }

        // 自动启动录音（如果开启了设置）
        if (prefs.autoStartRecordingOnShow) {
            // 与手动开始保持一致的就绪性校验，避免在缺少 Key/模型时进入 Listening 状态
            if (!checkAsrReady()) {
                // refreshPermissionUi() 已在校验中处理，这里直接返回
            } else {
                // 延迟一小段时间再启动，确保键盘 UI 已完全显示
                rootView?.postDelayed({
                    // 再次确认仍然就绪（期间用户可能改了设置/权限）
                    if (!checkAsrReady()) return@postDelayed
                    if (!asrManager.isRunning()) {
                        actionHandler.startAutoRecording()
                    }
                }, 100)
            }
        }

    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        aiEditPanelController?.onSelectionChanged(newSelStart, newSelEnd)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        imeViewVisible = false
        DebugLogManager.log("ime", "finish_input_view")
        stopClipboardSyncSafely()

        // 停止剪贴板预览监听
        stopClipboardPreviewListener()

        resetPanelsToMainKeyboard()

        // 键盘收起，解除预热（若未在录音）
        try { BluetoothRouteManager.setImeActive(this, false) } catch (t: Throwable) { android.util.Log.w("AsrKeyboardService", "BluetoothRouteManager setImeActive(false)", t) }

        // 如开启：键盘收起后自动切回上一个输入法
        if (prefs.returnPrevImeOnHide) {
            if (suppressReturnPrevImeOnHideOnce) {
                // 清除一次性抑制标记，避免连环切换
                suppressReturnPrevImeOnHideOnce = false
            } else {
                val switched = switchToConfiguredImeOrPrevious()
                if (!switched) {
                    // 若系统未允许切回，不做额外操作
                }
            }
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)

        // 若正在录音，同步中间结果为 composing
        if (asrManager.isRunning()) {
            actionHandler.restorePartialAsComposing(currentInputConnection)
        }
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        // 避免全屏候选，保持紧凑的麦克风键盘
        return false
    }

    override fun onComputeInsets(outInsets: InputMethodService.Insets) {
        super.onComputeInsets(outInsets)
        // 冷启动偶现：系统拿到错误的 Insets（contentTopInsets=0），导致宿主被过度 adjustResize，
        // 表现为输入框被顶到接近屏幕顶端、键盘仍在底部、两者之间出现大块空白区域（IME 背景色）。
        // 这里按实际输入视图高度/位置兜底修正，避免首次 insets 失真后卡住直到下一次收起/唤出。
        fixImeInsetsIfNeeded(outInsets)
    }

    private fun fixImeInsetsIfNeeded(outInsets: InputMethodService.Insets) {
        if (!imeViewVisible) return
        val input = rootView ?: return
        val decor = window?.window?.decorView ?: return

        val decorH = decor.height
        val decorW = decor.width
        if (decorH <= 0 || decorW <= 0) return

        var inputH = input.height
        if (inputH <= 0) {
            // 视图尚未 layout 时，使用一次 measure 获取 wrap_content 目标高度
            try {
                val wSpec = View.MeasureSpec.makeMeasureSpec(decorW, View.MeasureSpec.EXACTLY)
                val hSpec = View.MeasureSpec.makeMeasureSpec(decorH, View.MeasureSpec.AT_MOST)
                input.measure(wSpec, hSpec)
                inputH = input.measuredHeight
            } catch (t: Throwable) {
                android.util.Log.w("AsrKeyboardService", "fixImeInsets measure failed", t)
                return
            }
        }
        if (inputH <= 0) return

        val beforeContentTop = outInsets.contentTopInsets
        val beforeVisibleTop = outInsets.visibleTopInsets
        if (beforeContentTop > 0) return

        var top = 0
        run {
            try {
                val loc = IntArray(2)
                input.getLocationInWindow(loc)
                if (loc[1] > 0) top = loc[1]
            } catch (t: Throwable) {
                android.util.Log.w("AsrKeyboardService", "fixImeInsets getLocationInWindow failed", t)
            }
        }
        if (top <= 0) top = decorH - inputH
        top = top.coerceIn(0, decorH)

        // window 明显高于 inputView，但系统仍认为 contentTopInsets=0 -> 典型异常场景
        val needsFix = top > 0 && decorH > inputH
        if (!needsFix) return

        outInsets.contentTopInsets = top
        outInsets.visibleTopInsets = top
        outInsets.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_REGION
        // 触摸区域限定为键盘区域，避免空白区域吞触摸
        outInsets.touchableRegion.set(0, top, decorW, decorH)

        DebugLogManager.log(
            category = "ime",
            event = "compute_insets_fix",
            data = mapOf(
                "decorH" to decorH,
                "decorW" to decorW,
                "inputH" to inputH,
                "beforeContentTop" to beforeContentTop,
                "beforeVisibleTop" to beforeVisibleTop,
                "afterTop" to top
            )
        )
    }

    // ========== KeyboardActionHandler.UiListener 实现 ==========

    override fun onStateChanged(state: KeyboardState) {
        render(state)
    }

    private fun render(state: KeyboardState) {
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
            currentInputConnection?.let { ic ->
                inputHelper.setComposingText(ic, state.partialText)
            }
        }

        updateAiEditInfoBar(state)
    }

    override fun onStatusMessage(message: String) {
        clearStatusTextStyle()
        txtStatusText?.text = message
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
            try {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("ASR Error", message))
                Toast.makeText(this, getString(R.string.error_auto_copied), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("AsrKeyboardService", "Failed to copy error message", e)
            }
        }

        if (isAiEditPanelVisible) {
            val tv = txtAiEditInfo
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

    override fun onVibrate() {
        vibrateTick()
    }

    override fun onAmplitude(amplitude: Float) {
        waveformView?.updateAmplitude(amplitude)
    }

    override fun onShowClipboardPreview(preview: ClipboardPreview) {
        val tv = txtStatusText ?: return
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
                actionHandler.handleClipboardPreviewClick(currentInputConnection)
            }
        }

        // 若当前处于录音波形显示，临时切换为文本以展示预览
        txtStatusText?.visibility = View.VISIBLE
        waveformView?.visibility = View.GONE
        waveformView?.stop()

        // 标记最近一次展示的剪贴板内容，避免重复触发
        lastShownClipboardHash = sha256Hex(preview.fullText)

        // 超时自动恢复
        clipboardPreviewTimeout?.let { tv.removeCallbacks(it) }
        val r = Runnable { actionHandler.hideClipboardPreview() }
        clipboardPreviewTimeout = r
        tv.postDelayed(r, 10_000)
    }

    override fun onHideClipboardPreview() {
        val tv = txtStatusText ?: return
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

    // ========== 视图绑定和监听器设置 ==========

    private fun bindViews(view: View) {
        val refs = ImeViewRefs.bind(view)
        viewRefs = refs

        // 为波形视图应用动态颜色（通过 UiColors 统一获取主色）
        refs.waveformView?.setWaveformColor(UiColors.primary(view))
        // 应用波形灵敏度设置
        refs.waveformView?.sensitivity = prefs.waveformSensitivity
        // 修复麦克风垂直位置
        micBaseGroupHeight = -1
        refs.groupMicStatus?.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val h = v.height
            if (h <= 0) return@addOnLayoutChangeListener
            if (micBaseGroupHeight < 0) {
                micBaseGroupHeight = h
                refs.btnMic?.translationY = 0f
            } else {
                val delta = h - micBaseGroupHeight
                refs.btnMic?.translationY = (delta / 2f)
            }
        }

        aiEditPanelController = AiEditPanelController(
            context = this,
            prefs = prefs,
            views = refs,
            inputHelper = inputHelper,
            actionHandler = actionHandler,
            backspaceGestureHandler = backspaceGestureHandler,
            performKeyHaptic = ::performKeyHaptic,
            showPopupMenuKeepingIme = ::showPopupMenuKeepingIme,
            inputConnectionProvider = { currentInputConnection },
            onRequestShowNumpad = { returnToAiPanel -> showNumpadPanel(returnToAiPanel) },
        )
        numpadPanelController = NumpadPanelController(
            prefs = prefs,
            views = refs,
            inputHelper = inputHelper,
            actionHandler = actionHandler,
            backspaceGestureHandler = backspaceGestureHandler,
            performKeyHaptic = ::performKeyHaptic,
            inputConnectionProvider = { currentInputConnection },
            editorInfoProvider = { currentInputEditorInfo },
            onRequestShowAiEditPanel = { showAiEditPanel() },
        )
        clipboardPanelController = ClipboardPanelController(
            context = this,
            prefs = prefs,
            views = refs,
            themeStyler = themeStyler,
            performKeyHaptic = ::performKeyHaptic,
            inputConnectionProvider = { currentInputConnection },
            showPopupMenuKeepingIme = ::showPopupMenuKeepingIme,
            onOpenFile = ::openFile,
            onDownloadFile = ::downloadClipboardFile,
        )
        micGestureController = MicGestureController(
            prefs = prefs,
            views = refs,
            actionHandler = actionHandler,
            performKeyHaptic = ::performKeyHaptic,
            checkAsrReady = ::checkAsrReady,
            inputConnectionProvider = { currentInputConnection },
            isAiEditPanelVisible = { isAiEditPanelVisible },
            onLockedBySwipeChanged = { onStateChanged(actionHandler.getCurrentState()) },
        )
    }

    private fun setupListeners() {
        aiEditPanelController?.bindListeners()
        numpadPanelController?.bindListeners()
        clipboardPanelController?.bindListeners()
        micGestureController?.bindMicButton()
        micGestureController?.bindOverlayButtons()

        setupTopRowListeners()
        setupBackspaceListeners()
        setupMainKeyboardListeners()
        setupPunctuationListeners()
        setupExtensionButtonListeners()
    }

    private fun setupTopRowListeners() {
        // 顶部左侧按钮（原 Prompt 切换）改为：进入 AI 编辑面板
        btnPromptPicker?.setOnClickListener { v ->
            performKeyHaptic(v)
            if (!hasRecordAudioPermission()) {
                refreshPermissionUi()
                return@setOnClickListener
            }
            if (!prefs.hasAsrKeys()) {
                clearStatusTextStyle()
                txtStatusText?.text = getString(R.string.hint_need_keys)
                return@setOnClickListener
            }
            if (!prefs.hasLlmKeys()) {
                clearStatusTextStyle()
                txtStatusText?.text = getString(R.string.hint_need_llm_keys)
                return@setOnClickListener
            }
            showAiEditPanel()
        }

        // 顶部行：后处理开关（魔杖）
        btnPostproc?.apply {
            setImageResource(if (prefs.postProcessEnabled) R.drawable.magic_wand_fill else R.drawable.magic_wand)
            setOnClickListener { v ->
                performKeyHaptic(v)
                actionHandler.handlePostprocessToggle()
                setImageResource(if (prefs.postProcessEnabled) R.drawable.magic_wand_fill else R.drawable.magic_wand)
            }
        }
    }

    private fun setupBackspaceListeners() {
        // 退格按钮（委托给手势处理器）
        btnBackspace?.setOnClickListener { v ->
            performKeyHaptic(v)
            inputHelper.sendBackspace(currentInputConnection)
        }

        btnBackspace?.setOnTouchListener { v, event ->
            backspaceGestureHandler.handleTouchEvent(v, event, currentInputConnection)
        }

        // 设置退格手势监听器
        backspaceGestureHandler.setListener(object : BackspaceGestureHandler.Listener {
            override fun onSingleDelete() {
                actionHandler.saveUndoSnapshot(currentInputConnection)
                inputHelper.sendBackspace(currentInputConnection)
            }

            override fun onClearAll() {
                // 强制以清空前的文本作为撤销快照
                actionHandler.saveUndoSnapshot(currentInputConnection, force = true)
            }

            override fun onUndo() {
                actionHandler.handleUndo(currentInputConnection)
            }

            override fun onVibrateRequest() {
                vibrateTick()
            }
        })
    }

    private fun setupMainKeyboardListeners() {
        btnSettings?.setOnClickListener { v ->
            performKeyHaptic(v)
            openSettings()
        }

        btnEnter?.setOnClickListener { v ->
            performKeyHaptic(v)
            inputHelper.sendEnter(currentInputConnection, currentInputEditorInfo)
        }

        btnHide?.setOnClickListener { v ->
            performKeyHaptic(v)
            showClipboardPanel()
        }

        // 覆盖行按钮：Prompt 选择（article）
        btnImeSwitcher?.setOnClickListener { v ->
            performKeyHaptic(v)
            showPromptPicker(v)
        }

        // 中间功能行按钮（现为键盘切换）
        btnAiEdit?.setOnClickListener { v ->
            performKeyHaptic(v)
            if (prefs.fcitx5ReturnOnImeSwitch) {
                if (asrManager.isRunning()) asrManager.stopRecording()
                suppressReturnPrevImeOnHideOnce = true
                val switched = switchToConfiguredImeOrPrevious()
                if (!switched) {
                    showImePicker()
                }
            } else {
                showImePicker()
            }
        }
    }

    private fun setupPunctuationListeners() {
        // 第一个标点按钮替换为数字/符号键盘入口（普通按钮）
        btnPunct1?.setOnClickListener { v ->
            performKeyHaptic(v)
            showNumpadPanel(returnToAiPanel = false)
        }

        // 自定义标点（合并为两个：btnPunct2 -> 第1/2，btnPunct3 -> 第3/4）
        // 点按：输入主符号；上滑：输入次符号
        // 左右两侧按钮（btnPunct1/btnPunct4）还原为常规按钮类型，本步骤不绑定标点功能

        // 左侧合并标点键（1/2）
        btnPunct2?.setOnClickListener { v ->
            performKeyHaptic(v)
            actionHandler.commitText(currentInputConnection, prefs.punct1)
        }
        btnPunct2?.setOnTouchListener(
            createSwipeUpToAltListener(
                primary = { prefs.punct1 },
                secondary = { prefs.punct2 }
            )
        )
        // 右侧合并标点键（3/4）
        btnPunct3?.setOnClickListener { v ->
            performKeyHaptic(v)
            actionHandler.commitText(currentInputConnection, prefs.punct3)
        }
        btnPunct3?.setOnTouchListener(
            createSwipeUpToAltListener(
                primary = { prefs.punct3 },
                secondary = { prefs.punct4 }
            )
        )

        // 第四个按键：供应商切换按钮（样式与 Prompt 选择类似）
        btnPunct4?.setOnClickListener { v ->
            performKeyHaptic(v)
            showVendorPicker(v)
        }
    }

    private fun setupExtensionButtonListeners() {
        // 扩展按钮（可自定义功能）
        setupExtensionButton(btnExt1, prefs.extBtn1)
        setupExtensionButton(btnExt2, prefs.extBtn2)
        setupExtensionButton(btnExt3, prefs.extBtn3)
        setupExtensionButton(btnExt4, prefs.extBtn4)

        // 中央扩展按钮（占位，暂无功能）
        btnExtCenter1?.setOnClickListener { v ->
            performKeyHaptic(v)
            // TODO: 添加具体功能
        }
        btnExtCenter2?.setOnClickListener { v ->
            performKeyHaptic(v)
            if (actionHandler.getCurrentState() !is KeyboardState.Listening) {
                actionHandler.commitText(currentInputConnection, " ")
            }
        }
    }

    private fun showAiEditPanel() {
        if (isAiEditPanelVisible) return
        hideClipboardPanel()
        hideNumpadPanel()
        aiEditPanelController?.show()
        render(actionHandler.getCurrentState())
    }

    private fun showNumpadPanel(returnToAiPanel: Boolean = false) {
        if (isNumpadPanelVisible) return
        hideClipboardPanel()
        aiEditPanelController?.hide()
        numpadPanelController?.show(returnToAiPanel)
    }

    private fun hideNumpadPanel() {
        numpadPanelController?.hide()
    }

    private fun showClipboardPanel() {
        if (isClipboardPanelVisible) return
        hideNumpadPanel()
        aiEditPanelController?.hide()
        clipboardPanelController?.show()
    }

    private fun hideClipboardPanel() {
        clipboardPanelController?.hide()
    }

    private fun resetPanelsToMainKeyboard() {
        clipboardPanelController?.hide()
        numpadPanelController?.hide()
        aiEditPanelController?.hide()
        aiEditPanelController?.resetSelectionState()

        layoutClipboardPanel?.visibility = View.GONE
        layoutNumpadPanel?.visibility = View.GONE
        layoutAiEditPanel?.visibility = View.GONE
        layoutMainKeyboard?.visibility = View.VISIBLE
        groupMicStatus?.visibility = View.VISIBLE
    }

    /**
     * 在 IME 窗口内展示 PopupMenu，并在异常情况下尝试保持键盘不被收起。
     *
     * 部分机型上，在输入法窗口里弹出菜单偶现触发系统收起软键盘；
     * 这里在菜单消失时检测输入视图是否已被隐藏，如已隐藏则请求重新显示。
     */
    private fun showPopupMenuKeepingIme(popup: PopupMenu) {
        popup.setOnDismissListener {
            // 仅在弹出后短时间内发生收起时尝试恢复，避免干扰用户主动收起键盘
            val now = System.currentTimeMillis()
            if (now - lastPopupMenuShownAt > 2000L) return@setOnDismissListener
            if (!isInputViewShown && currentInputEditorInfo != null) {
                try {
                    requestShowSelf(0)
                } catch (t: Throwable) {
                    android.util.Log.w("AsrKeyboardService", "Failed to re-show IME after popup dismiss", t)
                }
            }
        }
        lastPopupMenuShownAt = System.currentTimeMillis()
        popup.show()
    }

    // ========== UI 更新方法 ==========

    private fun updateUiIdle() {
        hideRecordingGesturesOverlay()
        clearStatusTextStyle()
        // 显示文字，隐藏波形
        txtStatusText?.visibility = View.VISIBLE
        txtStatusText?.text = getString(R.string.status_idle)
        waveformView?.visibility = View.GONE
        waveformView?.stop()

        btnMic?.isSelected = false
        btnMic?.setImageResource(R.drawable.microphone)
        btnPromptPicker?.setImageResource(R.drawable.pencil_simple_line)
        currentInputConnection?.let { inputHelper.finishComposingText(it) }
    }

    private fun updateUiListening(state: KeyboardState.Listening? = null) {
        clearStatusTextStyle()
        // 隐藏文字，显示波形动画
        txtStatusText?.visibility = View.GONE
        waveformView?.visibility = View.VISIBLE
        waveformView?.start()

        btnMic?.isSelected = true
        btnMic?.setImageResource(R.drawable.microphone_fill)
        btnPromptPicker?.setImageResource(R.drawable.pencil_simple_line)
        showRecordingGesturesOverlay(state)
    }

    private fun updateUiProcessing() {
        hideRecordingGesturesOverlay()
        clearStatusTextStyle()
        // 显示文字，隐藏波形
        txtStatusText?.visibility = View.VISIBLE
        txtStatusText?.text = getString(R.string.status_recognizing)
        waveformView?.visibility = View.GONE
        waveformView?.stop()

        btnMic?.isSelected = false
        btnMic?.setImageResource(R.drawable.microphone)
        btnPromptPicker?.setImageResource(R.drawable.pencil_simple_line)
    }

    private fun updateUiAiProcessing() {
        hideRecordingGesturesOverlay()
        clearStatusTextStyle()
        // 显示文字，隐藏波形
        txtStatusText?.visibility = View.VISIBLE
        txtStatusText?.text = getString(R.string.status_ai_processing)
        waveformView?.visibility = View.GONE
        waveformView?.stop()

        btnMic?.isSelected = false
        btnMic?.setImageResource(R.drawable.microphone)
        btnPromptPicker?.setImageResource(R.drawable.pencil_simple_line)
    }

    private fun updateUiAiEditListening() {
        hideRecordingGesturesOverlay()
        clearStatusTextStyle()
        // AI Edit 录音状态也使用文字显示（避免与普通录音混淆）
        txtStatusText?.visibility = View.VISIBLE
        txtStatusText?.text = getString(R.string.status_ai_edit_listening)
        waveformView?.visibility = View.GONE
        waveformView?.stop()

        btnMic?.isSelected = false
        btnMic?.setImageResource(R.drawable.microphone_fill)
        btnPromptPicker?.setImageResource(R.drawable.pencil_simple_line_fill)
    }

    private fun updateUiAiEditProcessing() {
        hideRecordingGesturesOverlay()
        clearStatusTextStyle()
        // 显示文字，隐藏波形
        txtStatusText?.visibility = View.VISIBLE
        txtStatusText?.text = getString(R.string.status_ai_editing)
        waveformView?.visibility = View.GONE
        waveformView?.stop()

        btnMic?.isSelected = false
        btnMic?.setImageResource(R.drawable.microphone)
        btnPromptPicker?.setImageResource(R.drawable.pencil_simple_line_fill)
    }

    // ========== 辅助方法 ==========

    private fun showRecordingGesturesOverlay(state: KeyboardState.Listening?) {
        rowRecordingGestures?.visibility = View.VISIBLE
        // 根据点按/长按模式设置按钮文案
        if (prefs.micTapToggleEnabled) {
            btnGestureCancel?.text = getString(R.string.label_recording_tap_cancel)
            btnGestureSend?.text = getString(R.string.label_recording_tap_send)
        } else {
            btnGestureCancel?.text = getString(R.string.label_recording_gesture_cancel)
            btnGestureSend?.text = getString(R.string.label_recording_gesture_send)
        }
        applyLockZoneUi(state)
    }

    private fun hideRecordingGesturesOverlay() {
        rowRecordingGestures?.visibility = View.GONE
        resetLockZoneUi()
        micGestureController?.resetPressedState()
    }

    private fun applyLockZoneUi(state: KeyboardState.Listening?) {
        val spaceKey = btnExtCenter2 ?: return
        if (prefs.micTapToggleEnabled || state == null) {
            resetLockZoneUi()
            return
        }
        spaceKey.isEnabled = false
        spaceKey.text = getString(if (state.lockedBySwipe) R.string.hint_tap_to_stop_recording else R.string.hint_swipe_down_lock)
    }

    private fun resetLockZoneUi() {
        btnExtCenter2?.isEnabled = true
        btnExtCenter2?.text = getString(R.string.cd_space)
    }

    /**
     * 清除状态文本的粘贴板预览样式（背景遮罩、内边距、点击监听器、单行限制）
     * 确保普通状态文本不会显示粘贴板预览的样式
     */
    private fun clearStatusTextStyle() {
        val tv = txtStatusText ?: return
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

    private fun enableStatusMarquee() {
        val tv = txtStatusText ?: return
        tv.ellipsize = TextUtils.TruncateAt.MARQUEE
        tv.marqueeRepeatLimit = -1
        tv.isSelected = true
    }

    private fun disableStatusMarquee() {
        val tv = txtStatusText ?: return
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
        return getString(if (prefs.micTapToggleEnabled) R.string.ime_ai_edit_guide_tap else R.string.ime_ai_edit_guide_hold)
    }

    private fun updateAiEditInfoBar(state: KeyboardState) {
        if (!isAiEditPanelVisible) return
        val tv = txtAiEditInfo ?: return
        applyInfoBarMarquee(tv, enabled = true)
        tv.text = when (state) {
            is KeyboardState.AiEditListening -> state.instruction?.takeIf { it.isNotBlank() }
                ?: getString(R.string.status_ai_edit_listening)

            is KeyboardState.AiEditProcessing -> getString(R.string.status_ai_editing)
            else -> getAiEditGuideText()
        }
    }

    // ========== AI 编辑面板：协调入口 ==========

    private fun moveCursorBy(delta: Int) {
        aiEditPanelController?.moveCursorBy(delta)
    }

    private fun toggleSelectionMode() {
        aiEditPanelController?.toggleSelectionMode()
    }

    private fun updateSelectExtButtonsUi() {
        aiEditPanelController?.applySelectExtButtonsUi()
    }

    /**
     * 同步主界面扩展按钮（配置为静音判停开关的按钮）图标为开启/关闭态。
     */
    private fun updateSilenceAutoStopExtButtonsUi() {
        val enabled = prefs.autoStopOnSilenceEnabled
        fun updateBtn(btn: ImageButton?, action: ExtensionButtonAction) {
            if (action == ExtensionButtonAction.SILENCE_AUTOSTOP_TOGGLE) {
                btn?.setImageResource(if (enabled) R.drawable.hand_palm_fill else R.drawable.hand_palm)
                btn?.isSelected = enabled
            }
        }
        updateBtn(btnExt1, prefs.extBtn1)
        updateBtn(btnExt2, prefs.extBtn2)
        updateBtn(btnExt3, prefs.extBtn3)
        updateBtn(btnExt4, prefs.extBtn4)
    }

    override fun onShowRetryChip(label: String) {
        val tv = txtStatusText ?: return
        tv.text = label
        // 移除芯片样式，仅保持可点击
        tv.background = null
        tv.setPaddingRelative(0, 0, 0, 0)
        // 在中心信息栏展示，并临时隐藏波形
        txtStatusText?.visibility = View.VISIBLE
        waveformView?.visibility = View.GONE
        waveformView?.stop()
        tv.isClickable = true
        tv.isFocusable = true
        tv.setOnClickListener { v ->
            performKeyHaptic(v)
            actionHandler.handleRetryClick()
        }
    }

    override fun onHideRetryChip() {
        clearStatusTextStyle()
    }

    internal fun checkAsrReady(): Boolean {
        if (!hasRecordAudioPermission()) {
            refreshPermissionUi()
            DebugLogManager.log("ime", "asr_not_ready", mapOf("reason" to "perm"))
            return false
        }
        if (!prefs.hasAsrKeys()) {
            refreshPermissionUi()
            DebugLogManager.log("ime", "asr_not_ready", mapOf("reason" to "keys"))
            return false
        }
        if (prefs.asrVendor == AsrVendor.SenseVoice) {
            val prepared = com.brycewg.asrkb.asr.isSenseVoicePrepared()
            if (!prepared) {
                val base = getExternalFilesDir(null) ?: filesDir
                val probeRoot = java.io.File(base, "sensevoice")
                val rawVariant = prefs.svModelVariant
                val variant = if (rawVariant == "small-full") "small-full" else "small-int8"
                val variantDir = if (variant == "small-full") {
                    java.io.File(probeRoot, "small-full")
                } else {
                    java.io.File(probeRoot, "small-int8")
                }
                val found = com.brycewg.asrkb.asr.findSvModelDir(variantDir)
                    ?: com.brycewg.asrkb.asr.findSvModelDir(probeRoot)
                if (found == null) {
                    clearStatusTextStyle()
                    txtStatusText?.text = getString(R.string.error_sensevoice_model_missing)
                    return false
                }
            }
        } else if (prefs.asrVendor == AsrVendor.FunAsrNano) {
            val prepared = com.brycewg.asrkb.asr.isFunAsrNanoPrepared()
            if (!prepared) {
                val base = getExternalFilesDir(null) ?: filesDir
                val probeRoot = java.io.File(base, "funasr_nano")
                val variantDir = java.io.File(probeRoot, "nano-int8")
                val found = com.brycewg.asrkb.asr.findFnModelDir(variantDir)
                    ?: com.brycewg.asrkb.asr.findFnModelDir(probeRoot)
                if (found == null) {
                    clearStatusTextStyle()
                    txtStatusText?.text = getString(R.string.error_funasr_model_missing)
                    return false
                }
            }
        }
        // 确保引擎匹配当前模式
        asrManager.ensureEngineMatchesMode()
        return true
    }

    private fun refreshPermissionUi() {
        clearStatusTextStyle()
        val granted = hasRecordAudioPermission()
        val hasKeys = prefs.hasAsrKeys()
        if (!granted) {
            btnMic?.isEnabled = false
            txtStatusText?.text = getString(R.string.hint_need_permission)
        } else if (!hasKeys) {
            btnMic?.isEnabled = false
            txtStatusText?.text = getString(R.string.hint_need_keys)
        } else {
            btnMic?.isEnabled = true
            txtStatusText?.text = getString(R.string.status_idle)
        }
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }


    private fun applyPunctuationLabels() {
        // 新布局：中间两个按键显示两行标签，由自定义视图绘制
        btnPunct2?.setTexts(prefs.punct1, prefs.punct2)
        btnPunct3?.setTexts(prefs.punct3, prefs.punct4)
    }

    /**
     * 创建“上滑触发次符号”的触摸监听：
     * - ACTION_UP 时根据位移决定输入主/次符号
     */
    private fun createSwipeUpToAltListener(
        primary: () -> String,
        secondary: () -> String
    ): View.OnTouchListener {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        val thresholdPx = (24f * resources.displayMetrics.density).toInt().coerceAtLeast(touchSlop)
        var downY = 0f
        return View.OnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = ev.y
                    false
                }
                MotionEvent.ACTION_UP -> {
                    val dy = downY - ev.y
                    if (dy >= thresholdPx) {
                        // 上滑：输入次符号
                        performKeyHaptic(v)
                        actionHandler.commitText(currentInputConnection, secondary())
                        v.isPressed = false
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_CANCEL -> false
                else -> false
            }
        }
    }

    /**
     * 设置单个扩展按钮的功能
     */
    private fun setupExtensionButton(btn: ImageButton?, action: ExtensionButtonAction) {
        if (btn == null) return

        // 设置图标
        btn.setImageResource(action.iconResId)

        // 清理旧监听，避免切换功能后残留触摸/点击逻辑导致误触发
        btn.setOnClickListener(null)
        btn.setOnTouchListener(null)

        // 根据动作类型设置行为
        when (action) {
            ExtensionButtonAction.NONE -> {
                btn.visibility = View.GONE
            }
            ExtensionButtonAction.CURSOR_LEFT, ExtensionButtonAction.CURSOR_RIGHT -> {
                // 光标移动需要长按连发
                btn.visibility = View.VISIBLE
                setupCursorButtonRepeat(btn, action)
            }
            else -> {
                // 普通按钮：点击即可
                btn.visibility = View.VISIBLE
                btn.setOnClickListener { v ->
                    performKeyHaptic(v)
                    handleExtensionButtonAction(action)
                }
            }
        }
    }

    /**
     * 处理扩展按钮动作
     */
    private fun handleExtensionButtonAction(action: ExtensionButtonAction) {
        val result = actionHandler.handleExtensionButtonClick(action, currentInputConnection)

        when (result) {
            KeyboardActionHandler.ExtensionButtonActionResult.SUCCESS -> {
                // 成功，不需要额外处理
            }
            KeyboardActionHandler.ExtensionButtonActionResult.FAILED -> {
                // 失败，已在 actionHandler 中处理
            }
            KeyboardActionHandler.ExtensionButtonActionResult.NEED_TOGGLE_SELECTION -> {
                // 在主界面直接切换选择模式（不进入 AI 编辑面板）
                toggleSelectionMode()
                // 同步扩展按钮（若配置为 SELECT）
                updateSelectExtButtonsUi()
            }
            KeyboardActionHandler.ExtensionButtonActionResult.NEED_SHOW_NUMPAD -> {
                // 从主界面进入数字/符号面板
                showNumpadPanel(returnToAiPanel = false)
            }
            KeyboardActionHandler.ExtensionButtonActionResult.NEED_SHOW_CLIPBOARD -> {
                showClipboardPanel()
            }
            KeyboardActionHandler.ExtensionButtonActionResult.NEED_CURSOR_LEFT,
            KeyboardActionHandler.ExtensionButtonActionResult.NEED_CURSOR_RIGHT -> {
                // 光标移动已在长按处理中完成
            }
            KeyboardActionHandler.ExtensionButtonActionResult.NEED_HIDE_KEYBOARD -> {
                hideKeyboardPanel()
            }
            KeyboardActionHandler.ExtensionButtonActionResult.NEED_TOGGLE_CONTINUOUS_TALK -> {
                applyExtensionButtonConfig()
            }
        }

        if (action == ExtensionButtonAction.SILENCE_AUTOSTOP_TOGGLE &&
            result == KeyboardActionHandler.ExtensionButtonActionResult.SUCCESS
        ) {
            updateSilenceAutoStopExtButtonsUi()
        }
    }

    /**
     * 设置光标移动按钮的长按连发
     */
    private fun setupCursorButtonRepeat(btn: ImageButton, action: ExtensionButtonAction) {
        val initialDelay = 350L
        val repeatInterval = 50L
        var repeatRunnable: Runnable? = null

        btn.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    performKeyHaptic(v)
                    // 立即移动一次
                    val delta = if (action == ExtensionButtonAction.CURSOR_LEFT) -1 else 1
                    moveCursorBy(delta)

                    // 设置连发
                    repeatRunnable?.let { v.removeCallbacks(it) }
                    val r = Runnable {
                        moveCursorBy(delta)
                        repeatRunnable?.let { v.postDelayed(it, repeatInterval) }
                    }
                    repeatRunnable = r
                    v.postDelayed(r, initialDelay)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    repeatRunnable?.let { v.removeCallbacks(it) }
                    repeatRunnable = null
                    v.performClick()
                    true
                }
                else -> false
            }
        }
    }

    /**
     * 应用扩展按钮配置（更新图标和可见性）
     */
    private fun applyExtensionButtonConfig() {
        setupExtensionButton(btnExt1, prefs.extBtn1)
        setupExtensionButton(btnExt2, prefs.extBtn2)
        setupExtensionButton(btnExt3, prefs.extBtn3)
        setupExtensionButton(btnExt4, prefs.extBtn4)
        updateSelectExtButtonsUi()
        updateSilenceAutoStopExtButtonsUi()
    }


    private fun vibrateTick() {
        HapticFeedbackHelper.performTap(this, prefs, rootView)
    }

    private fun performKeyHaptic(view: View?) {
        HapticFeedbackHelper.performTap(this, prefs, view)
    }

    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun hideKeyboardPanel() {
        if (asrManager.isRunning()) {
            asrManager.stopRecording()
        }
        updateUiIdle()
        try {
            requestHideSelf(0)
        } catch (e: Exception) {
            android.util.Log.w("AsrKeyboardService", "requestHideSelf failed", e)
        }
    }

    private fun showImePicker() {
        val imm = getSystemService(InputMethodManager::class.java)
        imm?.showInputMethodPicker()
    }

    private fun switchToConfiguredImeOrPrevious(): Boolean {
        val targetId = prefs.imeSwitchTargetId
        return if (targetId.isNotBlank()) {
            switchToTargetInputMethod(targetId)
        } else {
            safeSwitchToPreviousInputMethod()
        }
    }

    private fun switchToTargetInputMethod(targetId: String): Boolean {
        if (targetId.isBlank()) return false
        val imm = getSystemService(InputMethodManager::class.java) ?: return false
        val enabled = imm.enabledInputMethodList.any { it.id == targetId }
        if (!enabled) return false
        val token = window?.window?.attributes?.token ?: return false
        imm.setInputMethod(token, targetId)
        return true
    }

    private fun safeSwitchToPreviousInputMethod(): Boolean {
        return try {
            switchToPreviousInputMethod()
        } catch (t: Throwable) {
            android.util.Log.w("AsrKeyboardService", "Failed to switch to previous input method", t)
            false
        }
    }

    private fun showPromptPicker(anchor: View) {
        val presets = prefs.getPromptPresets()
        if (presets.isEmpty()) return
        val popup = PopupMenu(anchor.context, anchor)
        presets.forEachIndexed { idx, p ->
            val item = popup.menu.add(0, idx, idx, p.title)
            item.isCheckable = true
            if (p.id == prefs.activePromptId) item.isChecked = true
        }
        popup.menu.setGroupCheckable(0, true, true)
        popup.setOnMenuItemClickListener { mi ->
            val position = mi.itemId
            val preset = presets.getOrNull(position) ?: return@setOnMenuItemClickListener false
            prefs.activePromptId = preset.id
            clearStatusTextStyle()
            txtStatusText?.text = getString(R.string.switched_preset, preset.title)
            true
        }
        showPopupMenuKeepingIme(popup)
    }

    private fun showVendorPicker(anchor: View) {
        val vendors = AsrVendorUi.ordered()
        val names = AsrVendorUi.names(this)
        val popup = PopupMenu(anchor.context, anchor)
        val cur = prefs.asrVendor
        vendors.forEachIndexed { idx, v ->
            val item = popup.menu.add(0, idx, idx, names[idx])
            item.isCheckable = true
            if (v == cur) item.isChecked = true
        }
        popup.menu.setGroupCheckable(0, true, true)
        popup.setOnMenuItemClickListener { mi ->
            val position = mi.itemId
            val vendor = vendors.getOrNull(position)
            if (vendor != null && vendor != prefs.asrVendor) {
                val old = prefs.asrVendor
                prefs.asrVendor = vendor

                // 离开本地引擎时卸载缓存识别器，释放内存
                try {
                    if (old == com.brycewg.asrkb.asr.AsrVendor.SenseVoice && vendor != com.brycewg.asrkb.asr.AsrVendor.SenseVoice) {
                        com.brycewg.asrkb.asr.unloadSenseVoiceRecognizer()
                    }
                    if (old == com.brycewg.asrkb.asr.AsrVendor.FunAsrNano && vendor != com.brycewg.asrkb.asr.AsrVendor.FunAsrNano) {
                        com.brycewg.asrkb.asr.unloadFunAsrNanoRecognizer()
                    }
                    if (old == com.brycewg.asrkb.asr.AsrVendor.Telespeech && vendor != com.brycewg.asrkb.asr.AsrVendor.Telespeech) {
                        com.brycewg.asrkb.asr.unloadTelespeechRecognizer()
                    }
                    if (old == com.brycewg.asrkb.asr.AsrVendor.Paraformer && vendor != com.brycewg.asrkb.asr.AsrVendor.Paraformer) {
                        com.brycewg.asrkb.asr.unloadParaformerRecognizer()
                    }
                } catch (t: Throwable) {
                    android.util.Log.e("AsrKeyboardService", "Failed to unload local recognizer", t)
                }

                // 空闲时立即重建引擎
                if (actionHandler.getCurrentState() is KeyboardState.Idle) {
                    asrManager.rebuildEngine()
                }

                // 切换到本地引擎且启用预加载时，尝试预加载
                try {
                    when (vendor) {
                        com.brycewg.asrkb.asr.AsrVendor.SenseVoice -> if (prefs.svPreloadEnabled) com.brycewg.asrkb.asr.preloadSenseVoiceIfConfigured(this, prefs)
                        com.brycewg.asrkb.asr.AsrVendor.FunAsrNano -> if (prefs.fnPreloadEnabled) com.brycewg.asrkb.asr.preloadFunAsrNanoIfConfigured(this, prefs)
                        com.brycewg.asrkb.asr.AsrVendor.Telespeech -> if (prefs.tsPreloadEnabled) com.brycewg.asrkb.asr.preloadTelespeechIfConfigured(this, prefs)
                        com.brycewg.asrkb.asr.AsrVendor.Paraformer -> if (prefs.pfPreloadEnabled) com.brycewg.asrkb.asr.preloadParaformerIfConfigured(this, prefs)
                        else -> {}
                    }
                } catch (t: Throwable) {
                    android.util.Log.e("AsrKeyboardService", "Failed to preload local recognizer", t)
                }

                // 状态栏提示
                clearStatusTextStyle()
                val name = try {
                    AsrVendorUi.name(this, vendor)
                } catch (t: Throwable) {
                    android.util.Log.w("AsrKeyboardService", "Failed to resolve AsrVendorUi name: $vendor", t)
                    vendor.name
                }
                txtStatusText?.text = getString(R.string.switched_preset, name)
            }
            true
        }
        showPopupMenuKeepingIme(popup)
    }

    private fun tryPreloadLocalModel() {
        if (localPreloadTriggered) return
        val p = prefs
        val enabled = when (p.asrVendor) {
            AsrVendor.SenseVoice -> p.svPreloadEnabled
            AsrVendor.FunAsrNano -> p.fnPreloadEnabled
            AsrVendor.Telespeech -> p.tsPreloadEnabled
            AsrVendor.Paraformer -> p.pfPreloadEnabled
            else -> false
        }
        if (!enabled) return
        if (com.brycewg.asrkb.asr.isLocalAsrPrepared(p)) { localPreloadTriggered = true; return }

        // 信息栏显示"加载中…"，完成后回退状态
        rootView?.post {
            clearStatusTextStyle()
            txtStatusText?.text = getString(R.string.sv_loading_model)
        }
        localPreloadTriggered = true

        serviceScope.launch(Dispatchers.Default) {
            val t0 = android.os.SystemClock.uptimeMillis()
            com.brycewg.asrkb.asr.preloadLocalAsrIfConfigured(
                this@AsrKeyboardService,
                p,
                onLoadStart = null,
                onLoadDone = {
                    val dt = (android.os.SystemClock.uptimeMillis() - t0).coerceAtLeast(0)
                    rootView?.post {
                        clearStatusTextStyle()
                        txtStatusText?.text = getString(R.string.sv_model_ready_with_ms, dt)
                        rootView?.postDelayed({
                            clearStatusTextStyle()
                            txtStatusText?.text = if (asrManager.isRunning()) getString(R.string.status_listening) else getString(R.string.status_idle)
                        }, 1200)
                    }
                },
                suppressToastOnStart = true
            )
        }
    }

    private fun startClipboardSync() {
        if (prefs.syncClipboardEnabled) {
            if (syncClipboardManager == null) {
                syncClipboardManager = SyncClipboardManager(
                    this,
                    prefs,
                    serviceScope,
                    object : SyncClipboardManager.Listener {
                        override fun onPulledNewContent(text: String) {
                            rootView?.post { actionHandler.showClipboardPreview(text) }
                        }

                        override fun onUploadSuccess() {
                            // 成功时不提示
                        }

                        override fun onUploadFailed(reason: String?) {
                            rootView?.post {
                                // 失败时短暂提示，然后恢复到剪贴板预览，方便点击粘贴
                                onStatusMessage(getString(R.string.sc_status_upload_failed))
                                txtStatusText?.postDelayed({ actionHandler.reShowClipboardPreviewIfAny() }, 900)
                            }
                        }

                        override fun onFilePulled(type: com.brycewg.asrkb.clipboard.EntryType, fileName: String, serverFileName: String) {
                            rootView?.post {
                                // 刷新剪贴板列表显示新文件
                                if (isClipboardPanelVisible) {
                                    clipboardPanelController?.refreshList()
                                }
                                // 在键盘信息栏展示文件预览（文件名 + 格式）
                                val store = clipStore
                                if (store != null) {
                                    val all = store.getAll()
                                    val entry = all.firstOrNull {
                                        it.type != com.brycewg.asrkb.clipboard.EntryType.TEXT &&
                                            (it.serverFileName == serverFileName || it.fileName == fileName)
                                    }
                                    if (entry != null) {
                                        actionHandler.showClipboardFilePreview(entry)
                                    }
                                }
                            }
                        }
                    },
                    clipStore
                )
            }
            syncClipboardManager?.start()
            serviceScope.launch(Dispatchers.IO) {
                syncClipboardManager?.proactiveUploadIfChanged()
                syncClipboardManager?.pullNow(true)
            }
        } else {
            stopClipboardSyncSafely()
        }
    }

    private fun stopClipboardSyncSafely() {
        try {
            syncClipboardManager?.stop()
        } catch (t: Throwable) {
            android.util.Log.e("AsrKeyboardService", "Failed to stop SyncClipboardManager", t)
        }
    }

    /**
     * 下载剪贴板文件（通过条目引用）
     */
    private fun downloadClipboardFile(entry: com.brycewg.asrkb.clipboard.ClipboardHistoryStore.Entry) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val success = syncClipboardManager?.downloadFile(entry.id) ?: false
                rootView?.post {
                    if (success) {
                        android.widget.Toast.makeText(
                            this@AsrKeyboardService,
                            getString(R.string.clip_file_download_success),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        // 刷新列表显示下载完成状态
                        if (isClipboardPanelVisible) {
                            clipboardPanelController?.refreshList()
                        }
                    } else {
                        android.widget.Toast.makeText(
                            this@AsrKeyboardService,
                            getString(R.string.clip_file_download_failed),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        // 刷新列表显示失败状态
                        if (isClipboardPanelVisible) {
                            clipboardPanelController?.refreshList()
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AsrKeyboardService", "Failed to download file", e)
                rootView?.post {
                    android.widget.Toast.makeText(
                        this@AsrKeyboardService,
                        getString(R.string.clip_file_download_error, e.message ?: ""),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /**
     * 通过剪贴板条目 ID 下载文件（用于信息栏预览点击）。
     */
    private fun downloadClipboardFileById(entryId: String) {
        val store = clipStore ?: return
        val entry = store.getEntryById(entryId) ?: return
        downloadClipboardFile(entry)
    }

    /**
     * 打开已下载的文件
     */
    private fun openFile(filePath: String) {
        try {
            val file = java.io.File(filePath)
            if (!file.exists()) {
                android.widget.Toast.makeText(
                    this,
                    getString(R.string.clip_file_not_found),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                return
            }

            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )

            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, getMimeType(file))
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                startActivity(intent)
            } catch (e: android.content.ActivityNotFoundException) {
                // 如果没有应用可以打开，则使用系统分享
                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = getMimeType(file)
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(android.content.Intent.createChooser(shareIntent, getString(R.string.clip_file_open_chooser_title)).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        } catch (e: Exception) {
            android.util.Log.e("AsrKeyboardService", "Failed to open file: $filePath", e)
            android.widget.Toast.makeText(
                this,
                getString(R.string.clip_file_open_failed, e.message ?: ""),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * 根据文件扩展名获取 MIME 类型
     */
    private fun getMimeType(file: java.io.File): String {
        val extension = file.extension.lowercase()
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "ppt", "pptx" -> "application/vnd.ms-powerpoint"
            "zip" -> "application/zip"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            else -> "*/*"
        }
    }

    /**
     * 应用 Window Insets 以适配 Android 15 边缘到边缘显示
     */
    private fun applyKeyboardInsets(view: View) {
        themeStyler.installKeyboardInsetsListener(view) { bottom ->
            systemNavBarBottomInset = bottom
            // 重新应用键盘高度缩放以更新底部 padding
            applyKeyboardHeightScale(view)
        }
    }

    private fun applyKeyboardHeightScale(view: View?) {
        if (view == null) return
        val tier = prefs.keyboardHeightTier
        val scale = when (tier) {
            2 -> 1.15f
            3 -> 1.30f
            else -> 1.0f
        }

        fun dp(v: Float): Int {
            val d = view.resources.displayMetrics.density
            return (v * d + 0.5f).toInt()
        }

        // 若缩放等级发生变化，重置麦克风位移基线，避免基于旧高度的下移造成底部截断
        if (kotlin.math.abs(lastAppliedHeightScale - scale) > 1e-3f) {
            lastAppliedHeightScale = scale
            micBaseGroupHeight = -1
            btnMic?.translationY = 0f
        }

        // 同步一次当前 RootWindowInsets，避免首次缩放时 bottom inset 尚未写入导致底部裁剪
        run {
            val rw = androidx.core.view.ViewCompat.getRootWindowInsets(view) ?: return@run
            val b = rw.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).bottom
            if (b > 0) systemNavBarBottomInset = b
        }

        // 应用底部间距（无论是否缩放都需要）
        val fl = view as? android.widget.FrameLayout
        if (fl != null) {
            val ps = fl.paddingStart
            val pe = fl.paddingEnd
            val pt = dp(8f * scale)
            val basePb = dp(12f * scale)
            // 添加用户设置的底部间距
            val extraPadding = dp(prefs.keyboardBottomPaddingDp.toFloat())
            // 添加系统导航栏高度以适配 Android 15 边缘到边缘显示
            val pb = basePb + extraPadding + systemNavBarBottomInset
            fl.setPaddingRelative(ps, pt, pe, pb)
        }

        // 顶部主行高度（无论是否缩放都需要重设，避免从大/中切回小时残留）
        run {
            val topRow = view.findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.rowTop)
            if (topRow != null) {
                val lp = topRow.layoutParams
                lp.height = dp(80f * scale)
                topRow.layoutParams = lp
            }
        }

        // 扩展按钮行高度（同样需要在 scale==1 时恢复）
        run {
            val extRow = view.findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.rowExtension)
            if (extRow != null) {
                val lp = extRow.layoutParams
                lp.height = dp(50f * scale)
                extRow.layoutParams = lp
            }
        }

        // 使主键盘功能行（overlay）从顶部锚定，避免垂直居中导致的像素舍入抖动
        // 计算规则：rowExtension 完整高度 + rowTop 高度的一半 + 固定偏移
        // = 50s(rowExtension完整) + 40s(rowTop的一半) + 6 = 90s + 6
        run {
            val overlay = view.findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.rowOverlay)
            if (overlay != null) {
                val lp = overlay.layoutParams as? android.widget.FrameLayout.LayoutParams
                if (lp != null) {
                    lp.topMargin = dp(90f * scale + 6f)
                    lp.gravity = android.view.Gravity.TOP
                    overlay.layoutParams = lp
                }
            }
        }
        // 手势按钮覆盖层：定位到第二排第三排按钮的位置
        // 计算：rowExtension 高度 (50dp) 作为顶部偏移，使手势按钮与第二排顶部对齐
        run {
            val overlay = view.findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.rowRecordingGestures)
            if (overlay != null) {
                val lp = overlay.layoutParams as? android.widget.FrameLayout.LayoutParams
                if (lp != null) {
                    lp.topMargin = dp(50f * scale)
                    lp.gravity = android.view.Gravity.TOP
                    overlay.layoutParams = lp
                }
            }
        }

        fun scaleSquareButton(id: Int) {
            val v = view.findViewById<View>(id) ?: return
            val lp = v.layoutParams
            lp.width = dp(40f * scale)
            lp.height = dp(40f * scale)
            v.layoutParams = lp
        }
        fun scaleGestureButton(v: View?) {
            val lp = v?.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams ?: return
            val baseSize = 86f * scale
            lp.width = dp(baseSize)
            lp.height = dp(baseSize)
            v.layoutParams = lp
        }
        fun scaleRectButton(id: Int, widthDp: Float, heightDp: Float) {
            val v = view.findViewById<View>(id) ?: return
            val lp = v.layoutParams
            lp.width = dp(widthDp * scale)
            lp.height = dp(heightDp * scale)
            v.layoutParams = lp
        }
        fun scaleChildrenByTag(root: View?, tag: String) {
            if (root == null) return
            if (root is android.view.ViewGroup) {
                for (i in 0 until root.childCount) {
                    scaleChildrenByTag(root.getChildAt(i), tag)
                }
            }
            val t = root.tag as? String
            if (t == tag) {
                val lp = root.layoutParams
                lp.height = dp(40f * scale)
                // 宽度可能由权重控制，不强制写入
                root.layoutParams = lp
            }
        }

        val ids40 = intArrayOf(
            // 主键盘按钮
            R.id.btnHide, R.id.btnPostproc, R.id.btnBackspace, R.id.btnPromptPicker,
            R.id.btnSettings, R.id.btnImeSwitcher, R.id.btnEnter, R.id.btnAiEdit,
            R.id.btnPunct1, R.id.btnPunct2, R.id.btnPunct3, R.id.btnPunct4,
            // 扩展按钮
            R.id.btnExt1, R.id.btnExt2, R.id.btnExt3, R.id.btnExt4,
            // AI 编辑面板按钮
            R.id.btnAiPanelBack, R.id.btnAiPanelApplyPreset,
            R.id.btnAiPanelCursorLeft, R.id.btnAiPanelCursorRight,
            R.id.btnAiPanelNumpad, R.id.btnAiPanelSelect,
            R.id.btnAiPanelSelectAll, R.id.btnAiPanelCopy,
            R.id.btnAiPanelUndo, R.id.btnAiPanelPaste,
            R.id.btnAiPanelMoveStart, R.id.btnAiPanelMoveEnd,
            // 剪贴板面板按钮
            R.id.clip_btnBack, R.id.clip_btnDelete
        )
        ids40.forEach { scaleSquareButton(it) }
        scaleGestureButton(btnGestureCancel)
        scaleGestureButton(btnGestureSend)

        // 缩放中央按钮（仅高度，宽度由约束控制）
        run {
            val v1 = view.findViewById<View>(R.id.btnExtCenter1)
            if (v1 != null) {
                val lp = v1.layoutParams
                lp.height = dp(40f * scale)
                // 宽度由约束控制，不设置
                v1.layoutParams = lp
            }
        }

        run {
            val v2 = view.findViewById<View>(R.id.btnExtCenter2)
            if (v2 != null) {
                val lp = v2.layoutParams
                lp.height = dp(40f * scale)
                // 宽度由约束控制，不设置
                v2.layoutParams = lp
            }
        }

        // 数字/标点小键盘的方形按键（通过 tag="key40" 统一缩放高度）
        scaleChildrenByTag(layoutNumpadPanel, "key40")

        // AI 编辑面板：按主键盘按钮行对齐（避免切换时按钮上下跳变）
        run {
            fun updateTopMargin(id: Int, topPx: Int) {
                val v = view.findViewById<View>(id) ?: return
                val lp = v.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams ?: return
                if (lp.topMargin == topPx) return
                lp.topMargin = topPx
                v.layoutParams = lp
            }

            val infoTop = dp(5f * scale)
            val row1Top = dp(50f * scale)
            val row2Top = dp(90f * scale + 6f)
            val row3Top = dp(130f * scale + 12f)

            // 信息栏：与主键盘第一行按钮对齐，且高度随缩放同步
            val info = view.findViewById<View>(R.id.aiEditInfoBar)
            val infoLp = info?.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            if (info != null && infoLp != null) {
                val h = dp(40f * scale)
                var changed = false
                if (infoLp.topMargin != infoTop) {
                    infoLp.topMargin = infoTop
                    changed = true
                }
                if (infoLp.height != h) {
                    infoLp.height = h
                    changed = true
                }
                if (changed) info.layoutParams = infoLp
            }

            // 空格键：与 AI 编辑面板第三行按钮对齐（对应主键盘第四行），且高度随缩放同步
            val space = view.findViewById<View>(R.id.btnAiPanelSpace)
            val spaceLp = space?.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            if (space != null && spaceLp != null) {
                val h = dp(40f * scale)
                var changed = false
                if (spaceLp.topMargin != row3Top) {
                    spaceLp.topMargin = row3Top
                    changed = true
                }
                if (spaceLp.height != h) {
                    spaceLp.height = h
                    changed = true
                }
                if (changed) space.layoutParams = spaceLp
            }

            // 三行按钮：分别与主键盘第 2/3/4 行按钮对齐
            updateTopMargin(R.id.aiEditRow1Left, row1Top)
            updateTopMargin(R.id.aiEditRow1Right, row1Top)
            updateTopMargin(R.id.aiEditRow2Left, row2Top)
            updateTopMargin(R.id.aiEditRow2Right, row2Top)
            updateTopMargin(R.id.aiEditRow3Left, row3Top)
            updateTopMargin(R.id.aiEditRow3Right, row3Top)
        }

        btnMic?.customSize = dp(72f * scale)

        // 调整麦克风容器的 translationY：使用常量位移，避免大比例时向下偏移过多导致底部裁剪
        groupMicStatus?.translationY = dp(3f).toFloat()
        // 确保麦克风容器在最上层，避免被其它 overlay 遮挡
        groupMicStatus?.bringToFront()

        // txtStatus 已移除，状态文本现在显示在 btnExtCenter1 中
    }

    // ========== 剪贴板预览监听 ==========

    private fun startClipboardPreviewListener() {
        if (clipboardManager == null) {
            clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        }
        if (clipboardChangeListener == null) {
            clipboardChangeListener = ClipboardManager.OnPrimaryClipChangedListener {
                val text = readClipboardText() ?: return@OnPrimaryClipChangedListener
                val h = sha256Hex(text)
                if (h == lastShownClipboardHash) return@OnPrimaryClipChangedListener
                lastShownClipboardHash = h
                // 写入历史
                clipStore?.addFromClipboard(text)
                // 若当前面板打开，同步刷新
                if (isClipboardPanelVisible) clipboardPanelController?.refreshList()
                rootView?.post { actionHandler.showClipboardPreview(text) }
            }
        }
        clipboardManager?.addPrimaryClipChangedListener(clipboardChangeListener!!)
    }

    private fun stopClipboardPreviewListener() {
        clipboardManager?.removePrimaryClipChangedListener(clipboardChangeListener)
    }

    private fun readClipboardText(): String? {
        val cm = clipboardManager ?: return null
        val clip = cm.primaryClip ?: return null
        if (clip.itemCount <= 0) return null
        val item = clip.getItemAt(0)
        return item.coerceToText(this)?.toString()?.takeIf { it.isNotEmpty() }
    }

    private fun sha256Hex(s: String): String {
        return try {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val bytes = md.digest(s.toByteArray(Charsets.UTF_8))
            val sb = StringBuilder(bytes.size * 2)
            for (b in bytes) sb.append(String.format("%02x", b))
            sb.toString()
        } catch (t: Throwable) {
            android.util.Log.w("AsrKeyboardService", "sha256 failed", t)
            s // fallback: use raw text as hash key
        }
    }

    private fun syncSystemBarsToKeyboardBackground(anchorView: View? = null) {
        val w = window?.window ?: return
        themeStyler.syncSystemBarsToKeyboardBackground(w, anchorView, anchorView?.context ?: this)
    }
}
