package com.sleuth.browser.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sleuth.browser.R
import com.sleuth.browser.Sleuth
import com.sleuth.browser.data.CaseFile
import com.sleuth.browser.databinding.ActivityCasesBinding
import com.sleuth.browser.databinding.ItemCaseBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CasesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCasesBinding
    private val adapter = CaseAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCasesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.caseList.layoutManager = LinearLayoutManager(this)
        binding.caseList.adapter = adapter

        binding.btnBack.setOnClickListener { finish() }
        binding.btnNewCase.setOnClickListener { createCase() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val cases = Sleuth.db.cases()
        adapter.submit(cases)
        binding.emptyText.visibility = if (cases.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun createCase() {
        val id = Sleuth.db.createCase(getString(R.string.action_new_case))
        refresh()
        if (id > 0) {
            startActivity(CaseDetailActivity.intent(this, id))
        }
    }

    private inner class CaseAdapter : RecyclerView.Adapter<CaseHolder>() {

        private val items = ArrayList<CaseFile>()

        fun submit(newItems: List<CaseFile>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CaseHolder {
            val view = ItemCaseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return CaseHolder(view)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: CaseHolder, position: Int) {
            holder.bind(items[position])
        }
    }

    private inner class CaseHolder(private val view: ItemCaseBinding) : RecyclerView.ViewHolder(view.root) {

        fun bind(item: CaseFile) {
            view.caseCode.text = item.code
            view.caseTitle.text = item.title.ifBlank { getString(R.string.action_new_case) }
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(item.createdAt))
            val count = Sleuth.db.evidenceCount(item.id)
            view.caseMeta.text = "$stamp  ·  $count items"
            view.caseArmed.visibility = if (item.stakeout) View.VISIBLE else View.GONE
            view.root.setOnClickListener {
                startActivity(CaseDetailActivity.intent(this@CasesActivity, item.id))
            }
            view.root.setOnLongClickListener {
                confirmDelete(item)
                true
            }
        }
    }

    private fun confirmDelete(item: CaseFile) {
        AlertDialog.Builder(this)
            .setTitle(item.code)
            .setMessage(getString(R.string.action_delete_case))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                Sleuth.db.deleteCase(item.id)
                refresh()
            }
            .show()
    }
}
