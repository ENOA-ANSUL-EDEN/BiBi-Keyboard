package com.brycewg.asrkb.ime

import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.brycewg.asrkb.R
import com.brycewg.asrkb.ui.widgets.PunctKeyView
import com.brycewg.asrkb.ui.widgets.WaveformView
import com.google.android.material.floatingactionbutton.FloatingActionButton

internal class ImeViewRefs private constructor(
    val rootView: View,

    // Panels
    val layoutMainKeyboard: View?,
    val layoutAiEditPanel: View?,
    val layoutNumpadPanel: View?,
    val layoutClipboardPanel: View?,

    // Main controls
    val btnMic: FloatingActionButton?,
    val btnSettings: ImageButton?,
    val btnEnter: ImageButton?,
    val btnPostproc: ImageButton?,
    val btnAiEdit: ImageButton?,
    val btnBackspace: ImageButton?,
    val btnPromptPicker: ImageButton?,
    val btnHide: ImageButton?,
    val btnImeSwitcher: ImageButton?,

    // Punctuation
    val btnPunct1: ImageButton?,
    val btnPunct2: PunctKeyView?,
    val btnPunct3: PunctKeyView?,
    val btnPunct4: ImageButton?,

    // Rows
    val rowTop: ConstraintLayout?,
    val rowOverlay: ConstraintLayout?,
    val rowRecordingGestures: ConstraintLayout?,
    val rowExtension: ConstraintLayout?,

    // Recording gesture overlay
    val btnGestureCancel: TextView?,
    val btnGestureSend: TextView?,

    // Extension buttons
    val btnExt1: ImageButton?,
    val btnExt2: ImageButton?,
    val btnExt3: ImageButton?,
    val btnExt4: ImageButton?,
    val btnExtCenter1: View?,
    val btnExtCenter2: Button?,

    // Status
    val txtStatusText: TextView?,
    val waveformView: WaveformView?,
    val txtStatus: TextView?,
    val groupMicStatus: View?,

    // AI edit panel
    val btnAiEditPanelBack: ImageButton?,
    val btnAiPanelApplyPreset: ImageButton?,
    val btnAiPanelCursorLeft: ImageButton?,
    val btnAiPanelCursorRight: ImageButton?,
    val btnAiPanelMoveStart: ImageButton?,
    val btnAiPanelMoveEnd: ImageButton?,
    val btnAiPanelSelect: ImageButton?,
    val btnAiPanelSelectAll: ImageButton?,
    val btnAiPanelCopy: ImageButton?,
    val btnAiPanelPaste: ImageButton?,
    val btnAiPanelUndo: ImageButton?,
    val btnAiPanelNumpad: ImageButton?,

    // Numpad panel
    val btnNumpadBack: ImageButton?,
    val btnNumpadEnter: ImageButton?,
    val btnNumpadBackspace: ImageButton?,
    val btnNumpadPunctToggle: ImageButton?,

    // Clipboard panel
    val clipBtnBack: ImageButton?,
    val clipBtnDelete: ImageButton?,
    val clipTxtCount: TextView?,
    val clipList: RecyclerView?,
) {
    companion object {
        fun bind(rootView: View): ImeViewRefs {
            return ImeViewRefs(
                rootView = rootView,

                layoutMainKeyboard = rootView.findViewById(R.id.layoutMainKeyboard),
                layoutAiEditPanel = rootView.findViewById(R.id.layoutAiEditPanel),
                layoutNumpadPanel = rootView.findViewById(R.id.layoutNumpadPanel),
                layoutClipboardPanel = rootView.findViewById(R.id.layoutClipboardPanel),

                btnMic = rootView.findViewById(R.id.btnMic),
                btnSettings = rootView.findViewById(R.id.btnSettings),
                btnEnter = rootView.findViewById(R.id.btnEnter),
                btnPostproc = rootView.findViewById(R.id.btnPostproc),
                btnAiEdit = rootView.findViewById(R.id.btnAiEdit),
                btnBackspace = rootView.findViewById(R.id.btnBackspace),
                btnPromptPicker = rootView.findViewById(R.id.btnPromptPicker),
                btnHide = rootView.findViewById(R.id.btnHide),
                btnImeSwitcher = rootView.findViewById(R.id.btnImeSwitcher),

                btnPunct1 = rootView.findViewById(R.id.btnPunct1),
                btnPunct2 = rootView.findViewById(R.id.btnPunct2),
                btnPunct3 = rootView.findViewById(R.id.btnPunct3),
                btnPunct4 = rootView.findViewById(R.id.btnPunct4),

                rowTop = rootView.findViewById(R.id.rowTop),
                rowOverlay = rootView.findViewById(R.id.rowOverlay),
                rowRecordingGestures = rootView.findViewById(R.id.rowRecordingGestures),
                rowExtension = rootView.findViewById(R.id.rowExtension),

                btnGestureCancel = rootView.findViewById(R.id.btnGestureCancel),
                btnGestureSend = rootView.findViewById(R.id.btnGestureSend),

                btnExt1 = rootView.findViewById(R.id.btnExt1),
                btnExt2 = rootView.findViewById(R.id.btnExt2),
                btnExt3 = rootView.findViewById(R.id.btnExt3),
                btnExt4 = rootView.findViewById(R.id.btnExt4),
                btnExtCenter1 = rootView.findViewById(R.id.btnExtCenter1),
                btnExtCenter2 = rootView.findViewById(R.id.btnExtCenter2),

                txtStatusText = rootView.findViewById(R.id.txtStatusText),
                waveformView = rootView.findViewById(R.id.waveformView),
                txtStatus = rootView.findViewById(R.id.txtStatus),
                groupMicStatus = rootView.findViewById(R.id.groupMicStatus),

                btnAiEditPanelBack = rootView.findViewById(R.id.btnAiPanelBack),
                btnAiPanelApplyPreset = rootView.findViewById(R.id.btnAiPanelApplyPreset),
                btnAiPanelCursorLeft = rootView.findViewById(R.id.btnAiPanelCursorLeft),
                btnAiPanelCursorRight = rootView.findViewById(R.id.btnAiPanelCursorRight),
                btnAiPanelMoveStart = rootView.findViewById(R.id.btnAiPanelMoveStart),
                btnAiPanelMoveEnd = rootView.findViewById(R.id.btnAiPanelMoveEnd),
                btnAiPanelSelect = rootView.findViewById(R.id.btnAiPanelSelect),
                btnAiPanelSelectAll = rootView.findViewById(R.id.btnAiPanelSelectAll),
                btnAiPanelCopy = rootView.findViewById(R.id.btnAiPanelCopy),
                btnAiPanelPaste = rootView.findViewById(R.id.btnAiPanelPaste),
                btnAiPanelUndo = rootView.findViewById(R.id.btnAiPanelUndo),
                btnAiPanelNumpad = rootView.findViewById(R.id.btnAiPanelNumpad),

                btnNumpadBack = rootView.findViewById(R.id.np_btnBack),
                btnNumpadEnter = rootView.findViewById(R.id.np_btnEnter),
                btnNumpadBackspace = rootView.findViewById(R.id.np_btnBackspace),
                btnNumpadPunctToggle = rootView.findViewById(R.id.np_btnPunctToggle),

                clipBtnBack = rootView.findViewById(R.id.clip_btnBack),
                clipBtnDelete = rootView.findViewById(R.id.clip_btnDelete),
                clipTxtCount = rootView.findViewById(R.id.clip_txtCount),
                clipList = rootView.findViewById(R.id.clip_list),
            )
        }
    }
}

