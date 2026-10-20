package com.sleuth.browser.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.sleuth.browser.R
import com.sleuth.browser.Sleuth
import com.sleuth.browser.browser.MediaSaver
import com.sleuth.browser.data.EvidenceType
import com.sleuth.browser.databinding.ActivityEvidenceViewerBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EvidenceViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEvidenceViewerBinding
    private var evidenceId: Long = 0L

    companion object {
        private const val EXTRA_EVIDENCE_ID = "evidence_id"

        fun intent(context: Context, evidenceId: Long): Intent =
            Intent(context, EvidenceViewerActivity::class.java).putExtra(EXTRA_EVIDENCE_ID, evidenceId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEvidenceViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        evidenceId = intent.getLongExtra(EXTRA_EVIDENCE_ID, 0L)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnShare.setOnClickListener { share() }
        binding.btnOpen.setOnClickListener { openOriginal() }
        binding.btnDelete.setOnClickListener { delete() }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val item = Sleuth.db.evidenceById(evidenceId)
        if (item == null) {
            finish()
            return
        }
        binding.evidenceTitle.text = item.title.ifBlank { item.type }
        binding.evidenceBody.text = item.note
        binding.evidenceBody.visibility = if (item.note.isBlank()) View.GONE else View.VISIBLE

        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(item.createdAt))
        val meta = StringBuilder()
        meta.append(item.type).append("  ·  ").append(stamp)
        if (item.url.isNotBlank()) meta.append("\n").append(item.url)
        binding.evidenceMeta.text = meta.toString()

        val file = if (item.path.isBlank()) null else File(item.path)
        if (file != null && file.exists()) {
            binding.evidenceImage.visibility = View.VISIBLE
            binding.evidenceImage.setImageBitmap(MediaSaver.decodeSampled(file.absolutePath, 1600))
        } else {
            binding.evidenceImage.visibility = View.GONE
        }

        binding.btnOpen.visibility = if (item.url.isBlank()) View.GONE else View.VISIBLE
    }

    private fun share() {
        val item = Sleuth.db.evidenceById(evidenceId) ?: return
        val file = if (item.path.isBlank()) null else File(item.path)
        if (file != null && file.exists()) {
            startActivity(MediaSaver.shareFile(this, file, "image/png"))
        } else {
            val target = File(cacheDir, "evidence-${item.id}.txt")
            MediaSaver.writeText(target, item.note)
            startActivity(MediaSaver.shareFile(this, target, "text/plain"))
        }
    }

    @android.annotation.SuppressLint("UnsafeImplicitIntentLaunch")
    private fun openOriginal() {
        val item = Sleuth.db.evidenceById(evidenceId) ?: return
        if (item.url.isBlank()) return
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(item.url))
        try {
            startActivity(intent)
        } catch (error: Throwable) {
            // no browser on the device can handle it
        }
    }

    private fun delete() {
        val item = Sleuth.db.evidenceById(evidenceId) ?: return
        if (item.type == EvidenceType.SCREENSHOT || item.path.isNotBlank()) {
            Sleuth.repository.deleteEvidenceFile(item.path)
        }
        Sleuth.db.deleteEvidence(item.id)
        finish()
    }
}
