package com.sleuth.browser.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import com.sleuth.browser.browser.BrowserSettings
import com.sleuth.browser.ui.ReportEvidence
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * The heart of the detective system: cases, the evidence attached to them and
 * the single case that can be armed to record the browsing session silently.
 */
class CaseRepository(
    private val context: Context,
    private val db: SleuthDb,
    private val settings: BrowserSettings
) {

    fun evidenceDir(caseId: Long): File {
        val dir = File(File(context.filesDir, "evidence"), caseId.toString())
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun saveImage(caseId: Long, bitmap: Bitmap, prefix: String): File {
        val dir = evidenceDir(caseId)
        val file = File(dir, "$prefix-${UUID.randomUUID()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file
    }

    fun addEvidence(
        caseId: Long,
        type: String,
        title: String,
        url: String,
        note: String,
        path: String,
        meta: String = ""
    ): Long {
        val id = db.addEvidence(caseId, type, title, url, note, path, meta)
        db.addEvent(caseId, type, title, url)
        return id
    }

    fun armedCase(): CaseFile? {
        val id = settings.armedCaseId
        if (id <= 0L) return null
        return db.caseById(id)
    }

    fun arm(caseId: Long) {
        settings.armedCaseId = caseId
        db.setCaseStakeout(caseId, true)
    }

    fun disarm() {
        val id = settings.armedCaseId
        if (id > 0L) db.setCaseStakeout(id, false)
        settings.armedCaseId = 0L
    }

    fun log(kind: String, detail: String, url: String) {
        val caseId = settings.armedCaseId
        if (caseId > 0L) db.addEvent(caseId, kind, detail, url)
    }

    fun reportEvidence(caseId: Long): List<ReportEvidence> {
        val items = db.evidenceForCase(caseId)
        val out = ArrayList<ReportEvidence>(items.size)
        for (item in items) {
            val image = if (item.path.isBlank()) null else encodeImage(item.path)
            out.add(
                ReportEvidence(
                    id = item.id,
                    kind = item.type,
                    title = item.title,
                    url = item.url,
                    note = item.note,
                    createdAt = item.createdAt,
                    imageBase64 = image
                )
            )
        }
        return out
    }

    fun deleteEvidenceFile(path: String) {
        if (path.isBlank()) return
        val file = File(path)
        if (file.exists() && file.parentFile?.absolutePath?.startsWith(context.filesDir.absolutePath) == true) {
            file.delete()
        }
    }

    private fun encodeImage(path: String): String? {
        return try {
            val file = File(path)
            if (!file.exists()) null else Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        } catch (error: Throwable) {
            null
        }
    }
}
