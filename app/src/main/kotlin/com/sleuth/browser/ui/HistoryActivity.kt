package com.sleuth.browser.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sleuth.browser.R
import com.sleuth.browser.Sleuth
import com.sleuth.browser.SleuthIO
import com.sleuth.browser.databinding.ActivityHistoryBinding
import com.sleuth.browser.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private val adapter = RecordAdapter()
    private var showingBookmarks = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        binding.btnBack.setOnClickListener { finish() }
        binding.tabHistory.setOnClickListener { showHistory() }
        binding.tabBookmarks.setOnClickListener { showBookmarks() }
        binding.searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                refresh()
            }
        })

        showHistory()
    }

    private fun showHistory() {
        showingBookmarks = false
        binding.tabHistory.isChecked = true
        refresh()
    }

    private fun showBookmarks() {
        showingBookmarks = true
        binding.tabBookmarks.isChecked = true
        refresh()
    }

    private fun refresh() {
        val query = binding.searchField.text?.toString()?.trim().orEmpty()
        val bookmarks = showingBookmarks
        SleuthIO.submit({
            if (bookmarks) {
                Sleuth.db.bookmarks(query).map { Record(it.id, it.url, it.title, it.createdAt, false) }
            } else {
                Sleuth.db.history(query).map { Record(it.id, it.url, it.title, it.visitedAt, true) }
            }
        }) { records ->
            if (isFinishing || isDestroyed) return@submit
            adapter.submit(records)
            binding.emptyText.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private data class Record(
        val id: Long,
        val url: String,
        val title: String,
        val time: Long,
        val isHistory: Boolean
    )

    private inner class RecordAdapter : RecyclerView.Adapter<RecordHolder>() {

        private val items = ArrayList<Record>()

        fun submit(newItems: List<Record>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordHolder {
            val view = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return RecordHolder(view)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: RecordHolder, position: Int) {
            holder.bind(items[position])
        }
    }

    private inner class RecordHolder(private val view: ItemHistoryBinding) : RecyclerView.ViewHolder(view.root) {

        fun bind(item: Record) {
            view.entryTitle.text = item.title.ifBlank { item.url }
            view.entryUrl.text = item.url
            view.entryTime.text = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(item.time))
            view.root.setOnClickListener {
                if (item.url.isBlank()) return@setOnClickListener
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.url))
                intent.setPackage(packageName)
                startActivity(intent)
            }
            view.root.setOnLongClickListener {
                // the delete runs in the background, the list is refreshed when it lands
                SleuthIO.submit({
                    if (item.isHistory) {
                        Sleuth.db.deleteHistory(item.id)
                    } else {
                        Sleuth.db.deleteBookmark(item.id)
                    }
                    true
                }) { refresh() }
                true
            }
        }
    }
}
