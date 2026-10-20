package com.sleuth.browser.ui

import android.content.Context
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
import com.sleuth.browser.browser.BrowserState
import com.sleuth.browser.browser.MediaSaver
import com.sleuth.browser.data.Evidence
import com.sleuth.browser.data.EvidenceType
import com.sleuth.browser.databinding.ActivityCaseDetailBinding
import com.sleuth.browser.databinding.DialogTextInputBinding
import com.sleuth.browser.databinding.ItemEvidenceBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CaseDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCaseDetailBinding
    private val adapter = EvidenceAdapter()
    private var caseId: Long = 0L

    companion object {
        private const val EXTRA_CASE_ID = "case_id"

        fun intent(context: Context, caseId: Long): Intent =
            Intent(context, CaseDetailActivity::class.java).putExtra(EXTRA_CASE_ID, caseId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCaseDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        caseId = intent.getLongExtra(EXTRA_CASE_ID, 0L)

        binding.evidenceList.layoutManager = LinearLayoutManager(this)
        binding.evidenceList.adapter = adapter

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAddNote.setOnClickListener { addNote() }
        binding.btnAddPage.setOnClickListener { addCurrentPage() }
        binding.btnStakeout.setOnClickListener { toggleStakeout() }
        binding.btnExport.setOnClickListener { exportReport() }
        binding.btnExportJson.setOnClickListener { exportJson() }
        binding.btnDelete.setOnClickListener { deleteCase() }
        binding.caseTitle.setOnClickListener { renameCase() }
        binding.caseNotes.setOnClickListener { editNotes() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val file = Sleuth.db.caseById(caseId)
        if (file == null) {
            finish()
            return
        }
        binding.caseTitle.text = file.title.ifBlank { getString(R.string.action_new_case) }
        binding.caseCode.text = file.code
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(file.createdAt))
        binding.caseMeta.text = "$stamp  ·  ${Sleuth.db.evidenceCount(file.id)} items"
        binding.caseNotes.text = file.notes
        binding.caseNotes.visibility = if (file.notes.isBlank()) View.GONE else View.VISIBLE
        binding.btnStakeout.text = if (file.stakeout) R.string.action_disarm else R.string.action_stakeout

        val items = Sleuth.db.evidenceForCase(caseId)
        adapter.submit(items)
        binding.emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun addNote() {
        showTextDialog(getString(R.string.action_add_note), getString(R.string.note_hint), "") { text ->
            if (text.isBlank()) return@showTextDialog
            Sleuth.repository.addEvidence(
                caseId,
                EvidenceType.NOTE,
                text.take(60),
                "",
                text,
                ""
            )
            refresh()
        }
    }

    private fun addCurrentPage() {
        val url = BrowserState.currentUrl
        if (url.isBlank()) {
            binding.emptyText.text = getString(R.string.status_no_case)
            return
        }
        val title = BrowserState.currentTitle.ifBlank { url }
        val excerpt = BrowserState.currentExcerpt.take(1200)
        Sleuth.repository.addEvidence(
            caseId,
            EvidenceType.PAGE,
            title,
            url,
            excerpt,
            ""
        )
        refresh()
    }

    private fun toggleStakeout() {
        val file = Sleuth.db.caseById(caseId) ?: return
        if (file.stakeout) {
            Sleuth.repository.disarm()
        } else {
            Sleuth.repository.arm(caseId)
        }
        refresh()
    }

    private fun renameCase() {
        val file = Sleuth.db.caseById(caseId) ?: return
        showTextDialog(getString(R.string.case_title_hint), getString(R.string.case_title_hint), file.title) { text ->
            if (text.isBlank()) return@showTextDialog
            Sleuth.db.updateCase(caseId, text, file.notes)
            refresh()
        }
    }

    private fun editNotes() {
        val file = Sleuth.db.caseById(caseId) ?: return
        showTextDialog(getString(R.string.case_notes_hint), getString(R.string.case_notes_hint), file.notes) { text ->
            Sleuth.db.updateCase(caseId, file.title, text)
            refresh()
        }
    }

    private fun exportReport() {
        val file = Sleuth.db.caseById(caseId) ?: return
        val items = Sleuth.repository.reportEvidence(caseId)
        val html = ReportExporter.buildHtml(file.title, file.code, file.notes, file.createdAt, items)
        val target = File(cacheDir, "${file.code}.html")
        if (!MediaSaver.writeText(target, html)) return
        startActivity(MediaSaver.shareFile(this, target, "text/html"))
    }

    private fun exportJson() {
        val file = Sleuth.db.caseById(caseId) ?: return
        val items = Sleuth.repository.reportEvidence(caseId)
        val json = ReportExporter.buildJson(file.title, file.code, file.notes, file.createdAt, items)
        val target = File(cacheDir, "${file.code}.json")
        if (!MediaSaver.writeText(target, json)) return
        startActivity(MediaSaver.shareFile(this, target, "application/json"))
    }

    private fun deleteCase() {
        val file = Sleuth.db.caseById(caseId) ?: return
        AlertDialog.Builder(this)
            .setTitle(file.code)
            .setMessage(getString(R.string.action_delete_case))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                if (Sleuth.settings.armedCaseId == caseId) {
                    Sleuth.repository.disarm()
                }
                Sleuth.db.deleteCase(caseId)
                finish()
            }
            .show()
    }

    private fun showTextDialog(title: String, hint: String, value: String, onDone: (String) -> Unit) {
        val dialogBinding = DialogTextInputBinding.inflate(layoutInflater)
        dialogBinding.inputField.setText(value)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save) { _, _ ->
                onDone(dialogBinding.inputField.text?.toString()?.trim().orEmpty())
            }
            .show()
    }

    private inner class EvidenceAdapter : RecyclerView.Adapter<EvidenceHolder>() {

        private val items = ArrayList<Evidence>()

        fun submit(newItems: List<Evidence>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EvidenceHolder {
            val view = ItemEvidenceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return EvidenceHolder(view)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: EvidenceHolder, position: Int) {
            holder.bind(items[position])
        }
    }

    private inner class EvidenceHolder(private val view: ItemEvidenceBinding) : RecyclerView.ViewHolder(view.root) {

        fun bind(item: Evidence) {
            view.evidenceTitle.text = item.title.ifBlank { item.type }
            view.evidenceSub.text = item.url.ifBlank { item.note.take(80) }
            view.evidenceTime.text = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(item.createdAt))
            view.evidenceIcon.setImageResource(
                when (item.type) {
                    EvidenceType.SCREENSHOT -> R.drawable.ic_camera
                    EvidenceType.PAGE -> R.drawable.ic_document
                    EvidenceType.EXCERPT -> R.drawable.ic_text
                    else -> R.drawable.ic_note
                }
            )
            if (item.path.isNotBlank() && File(item.path).exists()) {
                view.evidenceThumb.visibility = View.VISIBLE
                view.evidenceThumb.setImageBitmap(MediaSaver.decodeSampled(item.path, 160))
            } else {
                view.evidenceThumb.visibility = View.GONE
            }
            view.root.setOnClickListener {
                startActivity(EvidenceViewerActivity.intent(this@CaseDetailActivity, item.id))
            }
        }
    }
}
