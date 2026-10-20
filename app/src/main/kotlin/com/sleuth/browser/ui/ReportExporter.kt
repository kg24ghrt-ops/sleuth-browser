package com.sleuth.browser.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ReportEvidence(
    val id: Long,
    val kind: String,
    val title: String,
    val url: String,
    val note: String,
    val createdAt: Long,
    val imageBase64: String? = null
)

/**
 * Builds a self contained case report. Kept free of Android types so it can be
 * exercised by plain JVM tests.
 */
object ReportExporter {

    fun stamp(millis: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date(millis))
    }

    fun escapeHtml(text: String): String {
        val builder = StringBuilder(text.length)
        for (char in text) {
            when (char) {
                '&' -> builder.append("&amp;")
                '<' -> builder.append("&lt;")
                '>' -> builder.append("&gt;")
                '"' -> builder.append("&quot;")
                '\'' -> builder.append("&#39;")
                else -> builder.append(char)
            }
        }
        return builder.toString()
    }

    fun escapeJson(text: String): String {
        val builder = StringBuilder(text.length + 8)
        for (char in text) {
            when (char) {
                '"' -> builder.append("\\\"")
                '\\' -> builder.append("\\\\")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                else -> if (char < ' ') builder.append("\\u%04x".format(char.code)) else builder.append(char)
            }
        }
        return builder.toString()
    }

    fun buildHtml(
        caseTitle: String,
        caseCode: String,
        caseNotes: String,
        createdAt: Long,
        evidences: List<ReportEvidence>
    ): String {
        val builder = StringBuilder()
        builder.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n")
        builder.append("<meta charset=\"utf-8\">\n")
        builder.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
        builder.append("<title>Case ").append(escapeHtml(caseCode)).append("</title>\n")
        builder.append("<style>\n")
        builder.append("body{background:#0D1117;color:#E7EAF0;font-family:system-ui,Roboto,sans-serif;margin:0;padding:24px;}\n")
        builder.append("h1{font-size:22px;margin:0 0 4px 0;}\n")
        builder.append(".meta{color:#93A0B4;font-size:13px;margin-bottom:18px;}\n")
        builder.append(".notes{background:#151B23;border-left:3px solid #E0A43C;padding:12px;margin:0 0 22px 0;white-space:pre-wrap;}\n")
        builder.append(".item{background:#151B23;border:1px solid #2C3644;border-radius:10px;margin-bottom:16px;padding:14px;}\n")
        builder.append(".item h2{font-size:16px;margin:0 0 6px 0;}\n")
        builder.append(".item .url{color:#4A90D9;font-size:12px;word-break:break-all;}\n")
        builder.append(".item .time{color:#8A6524;font-size:11px;margin-top:6px;}\n")
        builder.append(".item img{max-width:100%;border-radius:6px;margin-top:10px;}\n")
        builder.append(".item .note{white-space:pre-wrap;color:#E7EAF0;margin-top:8px;font-size:14px;}\n")
        builder.append("footer{color:#93A0B4;font-size:11px;margin-top:28px;}\n")
        builder.append("</style>\n</head>\n<body>\n")

        builder.append("<h1>").append(escapeHtml(caseTitle)).append("</h1>\n")
        builder.append("<div class=\"meta\">").append(escapeHtml(caseCode))
            .append(" &middot; opened ").append(stamp(createdAt))
            .append(" &middot; ").append(evidences.size).append(" items</div>\n")

        if (caseNotes.isNotBlank()) {
            builder.append("<div class=\"notes\">").append(escapeHtml(caseNotes)).append("</div>\n")
        }

        for (item in evidences) {
            builder.append("<div class=\"item\">\n")
            builder.append("<h2>[").append(escapeHtml(item.kind)).append("] ")
                .append(escapeHtml(item.title)).append("</h2>\n")
            if (item.url.isNotBlank()) {
                builder.append("<div class=\"url\">").append(escapeHtml(item.url)).append("</div>\n")
            }
            if (item.note.isNotBlank()) {
                builder.append("<div class=\"note\">").append(escapeHtml(item.note)).append("</div>\n")
            }
            if (!item.imageBase64.isNullOrBlank()) {
                builder.append("<img alt=\"evidence\" src=\"data:image/png;base64,")
                    .append(item.imageBase64).append("\">\n")
            }
            builder.append("<div class=\"time\">").append(stamp(item.createdAt)).append("</div>\n")
            builder.append("</div>\n")
        }

        builder.append("<footer>Compiled locally by Sleuth. Nothing left this device.</footer>\n")
        builder.append("</body>\n</html>\n")
        return builder.toString()
    }

    fun buildJson(
        caseTitle: String,
        caseCode: String,
        caseNotes: String,
        createdAt: Long,
        evidences: List<ReportEvidence>
    ): String {
        val builder = StringBuilder()
        builder.append("{\n")
        builder.append("  \"code\": \"").append(escapeJson(caseCode)).append("\",\n")
        builder.append("  \"title\": \"").append(escapeJson(caseTitle)).append("\",\n")
        builder.append("  \"notes\": \"").append(escapeJson(caseNotes)).append("\",\n")
        builder.append("  \"opened_at\": \"").append(stamp(createdAt)).append("\",\n")
        builder.append("  \"evidence\": [\n")
        evidences.forEachIndexed { index, item ->
            builder.append("    {")
            builder.append("\"kind\": \"").append(escapeJson(item.kind)).append("\", ")
            builder.append("\"title\": \"").append(escapeJson(item.title)).append("\", ")
            builder.append("\"url\": \"").append(escapeJson(item.url)).append("\", ")
            builder.append("\"note\": \"").append(escapeJson(item.note)).append("\", ")
            builder.append("\"captured_at\": \"").append(stamp(item.createdAt)).append("\"")
            if (!item.imageBase64.isNullOrBlank()) {
                builder.append(", \"image_base64\": \"").append(escapeJson(item.imageBase64)).append("\"")
            }
            builder.append("}")
            if (index < evidences.lastIndex) builder.append(",")
            builder.append("\n")
        }
        builder.append("  ]\n")
        builder.append("}\n")
        return builder.toString()
    }
}
