package com.brycewg.asrkb.ui

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckedTextView
import android.widget.ListView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.brycewg.asrkb.R
import com.brycewg.asrkb.store.Prefs
import com.brycewg.asrkb.util.HapticFeedbackHelper
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlin.math.roundToInt

object SettingsOptionSheet {
  data class Tag(
    val label: String,
    val bgColorResId: Int,
    val textColorResId: Int
  )

  data class TaggedItem(
    val title: String,
    val tags: List<Tag>
  )

  fun showSingleChoice(
    context: Context,
    @StringRes titleResId: Int,
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit
  ) {
    if (items.isEmpty()) {
      return
    }

    val dialog = BottomSheetDialog(context, R.style.SettingsBottomSheetDialog)
    val contentView = LayoutInflater.from(context)
      .inflate(R.layout.bottom_sheet_single_choice, null, false)
    val titleView = contentView.findViewById<TextView>(R.id.tvBottomSheetTitle)
    val listView = contentView.findViewById<ListView>(R.id.listBottomSheetOptions)
    val prefs = Prefs(context)

    titleView.setText(titleResId)
    val adapter = ArrayAdapter(context, R.layout.item_settings_bottom_sheet_single_choice, items)
    listView.adapter = adapter
    listView.choiceMode = ListView.CHOICE_MODE_SINGLE

    val safeIndex = selectedIndex.takeIf { it in items.indices } ?: -1
    if (safeIndex >= 0) {
      listView.setItemChecked(safeIndex, true)
      listView.setSelection(safeIndex)
    }

    listView.setOnItemClickListener { view, _, position, _ ->
      HapticFeedbackHelper.performTap(context, prefs, view)
      onSelected(position)
      dialog.dismiss()
    }

    dialog.setContentView(contentView)
    dialog.setOnShowListener {
      contentView.post {
        // Cap sheet height to 3/4 screen while keeping short lists compact.
        adjustBottomSheetHeight(dialog, contentView, listView)
      }
    }
    dialog.show()
  }

  fun showSingleChoiceTagged(
    context: Context,
    @StringRes titleResId: Int,
    items: List<TaggedItem>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit
  ) {
    if (items.isEmpty()) {
      return
    }

    val dialog = BottomSheetDialog(context, R.style.SettingsBottomSheetDialog)
    val contentView = LayoutInflater.from(context)
      .inflate(R.layout.bottom_sheet_single_choice, null, false)
    val titleView = contentView.findViewById<TextView>(R.id.tvBottomSheetTitle)
    val listView = contentView.findViewById<ListView>(R.id.listBottomSheetOptions)
    val prefs = Prefs(context)

    titleView.setText(titleResId)
    val adapter = TaggedChoiceAdapter(context, items, selectedIndex)
    listView.adapter = adapter
    listView.choiceMode = ListView.CHOICE_MODE_SINGLE

    val safeIndex = selectedIndex.takeIf { it in items.indices } ?: -1
    if (safeIndex >= 0) {
      listView.setItemChecked(safeIndex, true)
      listView.setSelection(safeIndex)
    }

    listView.setOnItemClickListener { view, _, position, _ ->
      HapticFeedbackHelper.performTap(context, prefs, view)
      onSelected(position)
      dialog.dismiss()
    }

    dialog.setContentView(contentView)
    dialog.setOnShowListener {
      contentView.post {
        adjustBottomSheetHeight(dialog, contentView, listView)
      }
    }
    dialog.show()
  }

  private class TaggedChoiceAdapter(
    context: Context,
    private val items: List<TaggedItem>,
    private val selectedIndex: Int
  ) : ArrayAdapter<TaggedItem>(context, 0, items) {
    private val inflater = LayoutInflater.from(context)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
      val row = convertView
        ?: inflater.inflate(R.layout.item_settings_bottom_sheet_single_choice_tagged, parent, false)
      val titleView = row.findViewById<CheckedTextView>(android.R.id.text1)
      val tagGroup = row.findViewById<ChipGroup>(R.id.cgOptionTags)
      val item = items[position]

      titleView.text = item.title
      titleView.isChecked = position == selectedIndex

      val tags = item.tags.filter { it.label.isNotBlank() }
      if (tags.isEmpty()) {
        tagGroup.visibility = View.GONE
        tagGroup.removeAllViews()
      } else {
        tagGroup.visibility = View.VISIBLE
        tagGroup.removeAllViews()
        tags.forEach { tag ->
          val chip = inflater.inflate(R.layout.item_settings_tag_chip, tagGroup, false) as Chip
          chip.text = tag.label
          applyTagColors(chip, tag)
          tagGroup.addView(chip)
        }
      }
      return row
    }

    private fun applyTagColors(chip: Chip, tag: Tag) {
      val bg = ContextCompat.getColor(chip.context, tag.bgColorResId)
      val fg = ContextCompat.getColor(chip.context, tag.textColorResId)
      chip.chipBackgroundColor = ColorStateList.valueOf(bg)
      chip.setTextColor(fg)
    }
  }

  private fun adjustBottomSheetHeight(
    dialog: BottomSheetDialog,
    contentView: View,
    listView: ListView
  ) {
    val bottomSheet =
      dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        ?: return
    val maxHeight = (contentView.resources.displayMetrics.heightPixels * 0.75f).roundToInt()

    val headerView = contentView.findViewById<View>(R.id.layoutBottomSheetHeader)
    val sheetWidth = bottomSheet.width
      .takeIf { it > 0 }
      ?: contentView.resources.displayMetrics.widthPixels
    val headerHeight = measureViewHeight(headerView, sheetWidth)
    val verticalPadding = contentView.paddingTop + contentView.paddingBottom
    val listContentHeight = measureListContentHeight(listView, sheetWidth)
    val maxListHeight = (maxHeight - headerHeight - verticalPadding).coerceAtLeast(0)
    val targetListHeight = listContentHeight.coerceAtMost(maxListHeight)

    val listLayoutParams = listView.layoutParams
    if (listLayoutParams.height != targetListHeight) {
      listLayoutParams.height = targetListHeight
      listView.layoutParams = listLayoutParams
    }

    val targetHeight = (headerHeight + verticalPadding + targetListHeight)
      .coerceAtMost(maxHeight)
    val sheetLayoutParams = bottomSheet.layoutParams
    if (sheetLayoutParams.height != targetHeight) {
      sheetLayoutParams.height = targetHeight
      bottomSheet.layoutParams = sheetLayoutParams
    }
    bottomSheet.requestLayout()

    val behavior = BottomSheetBehavior.from(bottomSheet)
    behavior.isHideable = true
    behavior.skipCollapsed = true
    behavior.state = BottomSheetBehavior.STATE_EXPANDED
  }

  private fun measureViewHeight(view: View?, maxWidth: Int): Int {
    if (view == null) {
      return 0
    }
    if (view.height > 0) {
      return view.height
    }
    val widthSpec = View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST)
    val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    view.measure(widthSpec, heightSpec)
    return view.measuredHeight
  }

  private fun measureListContentHeight(listView: ListView, maxWidth: Int): Int {
    val adapter = listView.adapter ?: return 0
    val widthSpec = View.MeasureSpec.makeMeasureSpec(
      (maxWidth - listView.paddingLeft - listView.paddingRight).coerceAtLeast(0),
      View.MeasureSpec.AT_MOST
    )
    val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    var totalHeight = listView.paddingTop + listView.paddingBottom

    for (index in 0 until adapter.count) {
      val itemView = adapter.getView(index, null, listView)
      itemView.measure(widthSpec, heightSpec)
      totalHeight += itemView.measuredHeight
    }

    val dividerCount = (adapter.count - 1).coerceAtLeast(0)
    totalHeight += listView.dividerHeight * dividerCount
    return totalHeight
  }
}
