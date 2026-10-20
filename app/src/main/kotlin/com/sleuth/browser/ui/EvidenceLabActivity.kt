package com.sleuth.browser.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.sleuth.browser.R
import com.sleuth.browser.Sleuth
import com.sleuth.browser.SleuthIO
import com.sleuth.browser.browser.MediaSaver
import com.sleuth.browser.data.CaseFile
import com.sleuth.browser.data.EvidenceType
import com.sleuth.browser.databinding.ActivityEvidenceLabBinding
import com.sleuth.browser.databinding.DialogTextInputBinding
import java.io.File
import java.util.UUID

/**
 * Where a capture is examined: mark it up, hide what should not be seen, then
 * file it or throw it away.
 */
class EvidenceLabActivity : AppCompatActivity(), AnnotationView.Listener {

    private lateinit var binding: ActivityEvidenceLabBinding
    private var sourcePath: String = ""
    private var pageTitle: String = ""
    private var pageUrl: String = ""

    companion object {
        private const val EXTRA_PATH = "path"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_URL = "url"

        fun intent(context: Context, path: String, title: String, url: String): Intent =
            Intent(context, EvidenceLabActivity::class.java)
                .putExtra(EXTRA_PATH, path)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_URL, url)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEvidenceLabBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sourcePath = intent.getStringExtra(EXTRA_PATH).orEmpty()
        pageTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        pageUrl = intent.getStringExtra(EXTRA_URL).orEmpty()

        binding.annotationView.listener = this
        binding.btnBack.setOnClickListener { finish() }
        binding.btnUndo.setOnClickListener { binding.annotationView.undo() }
        binding.btnClear.setOnClickListener { binding.annotationView.clearAll() }

        binding.toolPen.setOnClickListener { selectTool(AnnotationView.Tool.PEN) }
        binding.toolMarker.setOnClickListener { selectTool(AnnotationView.Tool.MARKER) }
        binding.toolBox.setOnClickListener { selectTool(AnnotationView.Tool.BOX) }
        binding.toolCircle.setOnClickListener { selectTool(AnnotationView.Tool.CIRCLE) }
        binding.toolArrow.setOnClickListener { selectTool(AnnotationView.Tool.ARROW) }
        binding.toolText.setOnClickListener { selectTool(AnnotationView.Tool.TEXT) }
        binding.toolRedact.setOnClickListener { selectTool(AnnotationView.Tool.REDACT) }

        binding.colorRed.setOnClickListener { binding.annotationView.strokeColor = Color.parseColor("#D1495B") }
        binding.colorAmber.setOnClickListener { binding.annotationView.strokeColor = Color.parseColor("#E0A43C") }
        binding.colorGreen.setOnClickListener { binding.annotationView.strokeColor = Color.parseColor("#3FA796") }
        binding.colorWhite.setOnClickListener { binding.annotationView.strokeColor = Color.WHITE }

        binding.btnAddToCase.setOnClickListener { addToCase() }
        binding.btnSaveGallery.setOnClickListener { saveToGallery() }
        binding.btnShare.setOnClickListener { share() }

        selectTool(AnnotationView.Tool.PEN)
        loadBitmap()
    }

    private fun loadBitmap() {
        val file = File(sourcePath)
        if (!file.exists()) {
            Toast.makeText(this, R.string.error_capture, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val bitmap = MediaSaver.decodeSampled(file.absolutePath, 2048)
        if (bitmap == null) {
            Toast.makeText(this, R.string.error_capture, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        binding.annotationView.setBitmap(bitmap)
    }

    private fun selectTool(tool: AnnotationView.Tool) {
        binding.annotationView.tool = tool
        val selected = listOf(
            binding.toolPen to AnnotationView.Tool.PEN,
            binding.toolMarker to AnnotationView.Tool.MARKER,
            binding.toolBox to AnnotationView.Tool.BOX,
            binding.toolCircle to AnnotationView.Tool.CIRCLE,
            binding.toolArrow to AnnotationView.Tool.ARROW,
            binding.toolText to AnnotationView.Tool.TEXT,
            binding.toolRedact to AnnotationView.Tool.REDACT
        )
        for ((button, value) in selected) {
            button.alpha = if (value == tool) 1f else 0.45f
        }
    }

    override fun onTextRequested(viewX: Float, viewY: Float) {
        val dialogBinding = DialogTextInputBinding.inflate(layoutInflater)
        AlertDialog.Builder(this)
            .setTitle(R.string.tool_text)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                val text = dialogBinding.inputField.text?.toString()?.trim().orEmpty()
                if (text.isNotBlank()) {
                    binding.annotationView.addLabel(text, viewX, viewY)
                }
            }
            .show()
    }

    private fun addToCase() {
        val armed = Sleuth.repository.armedCase()
        if (armed != null) {
            fileInto(armed)
            return
        }
        val cases = Sleuth.db.cases()
        if (cases.isEmpty()) {
            val id = Sleuth.db.createCase(getString(R.string.action_new_case))
            if (id > 0) {
                Sleuth.repository.arm(id)
                fileInto(Sleuth.db.caseById(id) ?: return)
            }
            return
        }
        val labels = ArrayList<String>(cases.size + 1)
        val ids = ArrayList<Long>(cases.size + 1)
        for (case in cases) {
            labels.add("${case.code} — ${case.title}")
            ids.add(case.id)
        }
        labels.add(getString(R.string.action_new_case))
        ids.add(0L)

        AlertDialog.Builder(this)
            .setTitle(R.string.tool_save_case)
            .setItems(labels.toTypedArray()) { _, which ->
                var caseId = ids[which]
                if (caseId == 0L) {
                    caseId = Sleuth.db.createCase(getString(R.string.action_new_case))
                }
                val case = Sleuth.db.caseById(caseId)
                if (case != null) {
                    Sleuth.repository.arm(case.id)
                    fileInto(case)
                }
            }
            .show()
    }

    private fun fileInto(case: CaseFile) {
        val bitmap = binding.annotationView.export() ?: return
        val title = pageTitle.ifBlank { case.code }
        val url = pageUrl
        Toast.makeText(this, R.string.status_captured, Toast.LENGTH_SHORT).show()
        finish()
        // the image is compressed and filed off the browser thread
        SleuthIO.execute {
            val file = Sleuth.repository.saveImage(case.id, bitmap, "capture")
            Sleuth.repository.addEvidence(
                case.id,
                EvidenceType.SCREENSHOT,
                title,
                url,
                "Captured on device",
                file.absolutePath
            )
        }
    }

    private fun saveToGallery() {
        val bitmap = binding.annotationView.export() ?: return
        val name = "sleuth-${System.currentTimeMillis()}.png"
        val context = applicationContext
        SleuthIO.submit({ MediaSaver.saveToGallery(context, bitmap, name) }) { uri ->
            val message = if (uri != null) R.string.status_saved else R.string.error_capture
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun share() {
        val bitmap = binding.annotationView.export() ?: return
        val name = "sleuth-${UUID.randomUUID()}.png"
        val target = File(cacheDir, name)
        val context = applicationContext
        SleuthIO.submit({ MediaSaver.writeBitmap(target, bitmap) }) { written ->
            if (!written) return@submit
            startActivity(MediaSaver.shareFile(context, target, "image/png"))
        }
    }
}
