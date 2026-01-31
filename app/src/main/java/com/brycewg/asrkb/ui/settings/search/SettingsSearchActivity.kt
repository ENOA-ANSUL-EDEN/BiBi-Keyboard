/**
 * 设置搜索页面。
 *
 * 归属模块：ui/settings/search
 */
package com.brycewg.asrkb.ui.settings.search

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.brycewg.asrkb.R
import com.brycewg.asrkb.store.Prefs
import com.brycewg.asrkb.ui.BaseActivity
import com.brycewg.asrkb.ui.WindowInsetsHelper
import com.brycewg.asrkb.util.HapticFeedbackHelper
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import java.util.Locale

class SettingsSearchActivity : BaseActivity() {

    private data class Row(
        val entry: SettingsSearchEntry,
        val searchNormalized: String
    )

    private lateinit var prefs: Prefs
    private lateinit var etSearch: TextInputEditText
    private lateinit var rv: RecyclerView
    private lateinit var tvEmpty: TextView
    private val adapter = ResultAdapter(
        onClick = { v, entry ->
            hapticTapIfEnabled(v)
            SettingsSearchNavigator.launch(
                source = this,
                activityClass = entry.activityClass,
                targetViewId = entry.targetViewId,
                highlight = true
            )
            finish()
        },
        screenTitleProvider = { getString(it) }
    )

    private var allRows: List<Row> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_search)

        findViewById<View>(android.R.id.content).let { rootView ->
            WindowInsetsHelper.applySystemBarsInsets(rootView)
        }

        prefs = Prefs(this)

        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            setTitle(R.string.title_settings_search)
            setNavigationOnClickListener { finish() }
        }

        etSearch = findViewById(R.id.etSettingsSearch)
        rv = findViewById(R.id.rvSettingsSearchResults)
        tvEmpty = findViewById(R.id.tvSettingsSearchEmpty)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        etSearch.requestFocus()
        etSearch.post {
            val imm = getSystemService(InputMethodManager::class.java) ?: return@post
            imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT)
        }

        buildIndex()
        wireSearchBox()
        render(query = "")
    }

    private fun buildIndex() {
        val entries = SettingsSearchIndex.get(this)
        allRows = entries.map { e ->
            val screenTitle = runCatching { getString(e.screenTitleResId) }.getOrNull().orEmpty()
            val sectionTitle = e.sectionTitle.orEmpty()
            val searchText = buildString {
                append(e.title)
                if (sectionTitle.isNotBlank()) {
                    append(' ')
                    append(sectionTitle)
                }
                if (screenTitle.isNotBlank()) {
                    append(' ')
                    append(screenTitle)
                }
                for (k in e.keywords) {
                    if (k.isNotBlank()) {
                        append(' ')
                        append(k)
                    }
                }
            }
            Row(e, normalizeForSearch(searchText))
        }
    }

    private fun wireSearchBox() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                render(query = s?.toString().orEmpty())
            }
        })
    }

    private fun render(query: String) {
        val q = normalizeForSearch(query)
        val filtered = if (q.isBlank()) {
            allRows.map { it.entry }
        } else {
            allRows
                .asSequence()
                .mapNotNull { row ->
                    val score = matchScore(row.searchNormalized, q) ?: return@mapNotNull null
                    row.entry to score
                }
                .sortedWith(compareBy<Pair<SettingsSearchEntry, Int>>({ it.second }, { it.first.title }))
                .map { it.first }
                .toList()
        }

        adapter.submitList(filtered)
        tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        rv.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun normalizeForSearch(raw: String): String {
        return raw
            .lowercase(Locale.ROOT)
            .filter { it.isLetterOrDigit() }
    }

    private fun matchScore(text: String, query: String): Int? {
        if (query.isBlank()) return 0
        if (text.contains(query)) return 0
        if (query.length <= 2) return null
        if (isSubsequence(query, text)) {
            return 10 + (text.length - query.length).coerceAtLeast(0)
        }
        return null
    }

    private fun isSubsequence(needle: String, haystack: String): Boolean {
        if (needle.isEmpty()) return true
        var i = 0
        for (c in haystack) {
            if (i < needle.length && needle[i] == c) {
                i++
                if (i == needle.length) return true
            }
        }
        return false
    }

    private fun hapticTapIfEnabled(view: View?) {
        HapticFeedbackHelper.performTap(this, prefs, view)
    }

    private class ResultAdapter(
        private val onClick: (View, SettingsSearchEntry) -> Unit,
        private val screenTitleProvider: (Int) -> String
    ) : ListAdapter<SettingsSearchEntry, ResultAdapter.VH>(Diff) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_settings_search_result, parent, false)
            return VH(v, onClick, screenTitleProvider)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(getItem(position))
        }

        class VH(
            itemView: View,
            private val onClick: (View, SettingsSearchEntry) -> Unit,
            private val screenTitleProvider: (Int) -> String
        ) : RecyclerView.ViewHolder(itemView) {
            private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
            private val tvSubtitle: TextView = itemView.findViewById(R.id.tvSubtitle)

            fun bind(entry: SettingsSearchEntry) {
                tvTitle.text = entry.title
                tvSubtitle.text = entry.sectionTitle?.takeIf { it.isNotBlank() }
                    ?: screenTitleProvider(entry.screenTitleResId)
                itemView.setOnClickListener { v -> onClick(v, entry) }
            }
        }

        private companion object {
            val Diff = object : DiffUtil.ItemCallback<SettingsSearchEntry>() {
                override fun areItemsTheSame(oldItem: SettingsSearchEntry, newItem: SettingsSearchEntry): Boolean {
                    return oldItem.activityClass == newItem.activityClass &&
                        oldItem.targetViewId == newItem.targetViewId &&
                        oldItem.title == newItem.title
                }

                override fun areContentsTheSame(oldItem: SettingsSearchEntry, newItem: SettingsSearchEntry): Boolean {
                    return oldItem == newItem
                }
            }
        }
    }
}
