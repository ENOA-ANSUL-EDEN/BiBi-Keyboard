package com.brycewg.asrkb.ime

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.brycewg.asrkb.R
import com.brycewg.asrkb.clipboard.ClipboardHistoryStore

class ClipboardPanelAdapter(
    private val onItemClick: (ClipboardHistoryStore.Entry) -> Unit
) : ListAdapter<ClipboardHistoryStore.Entry, ClipboardPanelAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ClipboardHistoryStore.Entry>() {
            override fun areItemsTheSame(oldItem: ClipboardHistoryStore.Entry, newItem: ClipboardHistoryStore.Entry): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: ClipboardHistoryStore.Entry, newItem: ClipboardHistoryStore.Entry): Boolean =
                oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_clipboard_entry, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = getItem(position)
        holder.bind(e, onItemClick)
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tv: TextView = itemView.findViewById(R.id.tvEntry)
        private val pin: View? = itemView.findViewById(R.id.viewPinned)
        fun bind(e: ClipboardHistoryStore.Entry, onClick: (ClipboardHistoryStore.Entry) -> Unit) {
            tv.text = e.text
            pin?.visibility = if (e.pinned) View.VISIBLE else View.GONE
            itemView.setOnClickListener { onClick(e) }
        }
    }
}

