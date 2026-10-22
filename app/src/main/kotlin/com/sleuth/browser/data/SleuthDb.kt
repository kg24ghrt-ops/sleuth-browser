package com.sleuth.browser.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class SleuthDb(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "sleuth.db"
        private const val DB_VERSION = 1
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS history (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "url TEXT NOT NULL, title TEXT NOT NULL DEFAULT '', visited_at INTEGER NOT NULL, visits INTEGER NOT NULL DEFAULT 1)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS bookmarks (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "url TEXT NOT NULL UNIQUE, title TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS cases (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "code TEXT NOT NULL, title TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '', " +
                "created_at INTEGER NOT NULL, stakeout INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS evidence (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "case_id INTEGER NOT NULL, type TEXT NOT NULL, title TEXT NOT NULL DEFAULT '', " +
                "url TEXT NOT NULL DEFAULT '', note TEXT NOT NULL DEFAULT '', path TEXT NOT NULL DEFAULT '', " +
                "created_at INTEGER NOT NULL, meta TEXT NOT NULL DEFAULT '')"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS events (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "case_id INTEGER NOT NULL, kind TEXT NOT NULL, detail TEXT NOT NULL DEFAULT '', " +
                "url TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_evidence_case ON evidence (case_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_events_case ON events (case_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS history")
        db.execSQL("DROP TABLE IF EXISTS bookmarks")
        db.execSQL("DROP TABLE IF EXISTS cases")
        db.execSQL("DROP TABLE IF EXISTS evidence")
        db.execSQL("DROP TABLE IF EXISTS events")
        onCreate(db)
    }

    // ---------------------------------------------------------------- history

    fun recordVisit(url: String, title: String) {
        if (url.isBlank()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            var id = 0L
            var visits = 0
            db.rawQuery("SELECT id, visits FROM history WHERE url = ? LIMIT 1", arrayOf(url)).use { cursor ->
                if (cursor.moveToFirst()) {
                    id = cursor.getLong(0)
                    visits = cursor.getInt(1)
                }
            }
            val values = ContentValues().apply {
                put("title", title)
                put("visited_at", System.currentTimeMillis())
                put("visits", visits + 1)
            }
            if (id > 0L) {
                db.update("history", values, "id = ?", arrayOf(id.toString()))
            } else {
                values.put("url", url)
                db.insert("history", null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun history(query: String, limit: Int = 300): List<HistoryEntry> {
        val sql = StringBuilder("SELECT id, url, title, visited_at, visits FROM history")
        val args = ArrayList<String>()
        if (query.isNotBlank()) {
            sql.append(" WHERE url LIKE ? OR title LIKE ?")
            args.add("%$query%")
            args.add("%$query%")
        }
        sql.append(" ORDER BY visited_at DESC LIMIT ?")
        args.add(limit.toString())

        val out = ArrayList<HistoryEntry>()
        readableDatabase.rawQuery(sql.toString(), args.toTypedArray()).use { cursor ->
            while (cursor.moveToNext()) {
                out.add(
                    HistoryEntry(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getLong(3),
                        cursor.getInt(4)
                    )
                )
            }
        }
        return out
    }

    fun deleteHistory(id: Long) {
        writableDatabase.delete("history", "id = ?", arrayOf(id.toString()))
    }

    fun clearHistory() {
        writableDatabase.delete("history", null, null)
    }

    // -------------------------------------------------------------- bookmarks

    fun isBookmarked(url: String): Boolean {
        var found = false
        readableDatabase.rawQuery("SELECT id FROM bookmarks WHERE url = ? LIMIT 1", arrayOf(url)).use { cursor ->
            found = cursor.moveToFirst()
        }
        return found
    }

    fun setBookmark(url: String, title: String, on: Boolean) {
        val db = writableDatabase
        if (on) {
            val values = ContentValues().apply {
                put("url", url)
                put("title", title)
                put("created_at", System.currentTimeMillis())
            }
            db.insertWithOnConflict("bookmarks", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        } else {
            db.delete("bookmarks", "url = ?", arrayOf(url))
        }
    }

    fun bookmarks(query: String, limit: Int = 300): List<BookmarkEntry> {
        val sql = StringBuilder("SELECT id, url, title, created_at FROM bookmarks")
        val args = ArrayList<String>()
        if (query.isNotBlank()) {
            sql.append(" WHERE url LIKE ? OR title LIKE ?")
            args.add("%$query%")
            args.add("%$query%")
        }
        sql.append(" ORDER BY created_at DESC LIMIT ?")
        args.add(limit.toString())

        val out = ArrayList<BookmarkEntry>()
        readableDatabase.rawQuery(sql.toString(), args.toTypedArray()).use { cursor ->
            while (cursor.moveToNext()) {
                out.add(BookmarkEntry(cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getLong(3)))
            }
        }
        return out
    }

    fun deleteBookmark(id: Long) {
        writableDatabase.delete("bookmarks", "id = ?", arrayOf(id.toString()))
    }

    // ------------------------------------------------------------------ cases

    fun createCase(title: String): Long {
        val db = writableDatabase
        val code = "CASE-" + "%04d".format(countCases() + 1)
        val values = ContentValues().apply {
            put("code", code)
            put("title", title)
            put("notes", "")
            put("created_at", System.currentTimeMillis())
            put("stakeout", 0)
        }
        return db.insert("cases", null, values)
    }

    fun countCases(): Int {
        var count = 0
        readableDatabase.rawQuery("SELECT COUNT(*) FROM cases", null).use { cursor ->
            if (cursor.moveToFirst()) count = cursor.getInt(0)
        }
        return count
    }

    fun cases(): List<CaseFile> {
        val out = ArrayList<CaseFile>()
        readableDatabase.rawQuery(
            "SELECT id, code, title, notes, created_at, stakeout FROM cases ORDER BY created_at DESC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                out.add(
                    CaseFile(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getLong(4),
                        cursor.getInt(5) == 1
                    )
                )
            }
        }
        return out
    }

    /** One query: evidence count per case, instead of one COUNT per case. */
    fun caseCounts(): Map<Long, Int> {
        val out = HashMap<Long, Int>()
        readableDatabase.rawQuery(
            "SELECT case_id, COUNT(*) FROM evidence GROUP BY case_id",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                out[cursor.getLong(0)] = cursor.getInt(1)
            }
        }
        return out
    }

    fun caseById(id: Long): CaseFile? {
        var found: CaseFile? = null
        readableDatabase.rawQuery(
            "SELECT id, code, title, notes, created_at, stakeout FROM cases WHERE id = ? LIMIT 1",
            arrayOf(id.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                found = CaseFile(
                    cursor.getLong(0),
                    cursor.getString(1),
                    cursor.getString(2),
                    cursor.getString(3),
                    cursor.getLong(4),
                    cursor.getInt(5) == 1
                )
            }
        }
        return found
    }

    fun updateCase(id: Long, title: String, notes: String) {
        val values = ContentValues().apply {
            put("title", title)
            put("notes", notes)
        }
        writableDatabase.update("cases", values, "id = ?", arrayOf(id.toString()))
    }

    fun setCaseStakeout(id: Long, on: Boolean) {
        val values = ContentValues().apply { put("stakeout", if (on) 1 else 0) }
        writableDatabase.update("cases", values, "id = ?", arrayOf(id.toString()))
    }

    fun deleteCase(id: Long) {
        val db = writableDatabase
        db.delete("evidence", "case_id = ?", arrayOf(id.toString()))
        db.delete("events", "case_id = ?", arrayOf(id.toString()))
        db.delete("cases", "id = ?", arrayOf(id.toString()))
    }

    // --------------------------------------------------------------- evidence

    fun addEvidence(
        caseId: Long,
        type: String,
        title: String,
        url: String,
        note: String,
        path: String,
        meta: String
    ): Long {
        val values = ContentValues().apply {
            put("case_id", caseId)
            put("type", type)
            put("title", title)
            put("url", url)
            put("note", note)
            put("path", path)
            put("created_at", System.currentTimeMillis())
            put("meta", meta)
        }
        return writableDatabase.insert("evidence", null, values)
    }

    fun evidenceForCase(caseId: Long): List<Evidence> {
        val out = ArrayList<Evidence>()
        readableDatabase.rawQuery(
            "SELECT id, case_id, type, title, url, note, path, created_at, meta FROM evidence " +
                "WHERE case_id = ? ORDER BY created_at DESC",
            arrayOf(caseId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                out.add(
                    Evidence(
                        cursor.getLong(0),
                        cursor.getLong(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getString(4),
                        cursor.getString(5),
                        cursor.getString(6),
                        cursor.getLong(7),
                        cursor.getString(8)
                    )
                )
            }
        }
        return out
    }

    fun evidenceById(id: Long): Evidence? {
        var found: Evidence? = null
        readableDatabase.rawQuery(
            "SELECT id, case_id, type, title, url, note, path, created_at, meta FROM evidence WHERE id = ? LIMIT 1",
            arrayOf(id.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                found = Evidence(
                    cursor.getLong(0),
                    cursor.getLong(1),
                    cursor.getString(2),
                    cursor.getString(3),
                    cursor.getString(4),
                    cursor.getString(5),
                    cursor.getString(6),
                    cursor.getLong(7),
                    cursor.getString(8)
                )
            }
        }
        return found
    }

    fun deleteEvidence(id: Long) {
        writableDatabase.delete("evidence", "id = ?", arrayOf(id.toString()))
    }

    fun evidenceCount(caseId: Long): Int {
        var count = 0
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM evidence WHERE case_id = ?",
            arrayOf(caseId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) count = cursor.getInt(0)
        }
        return count
    }

    // ----------------------------------------------------------------- events

    fun addEvent(caseId: Long, kind: String, detail: String, url: String): Long {
        val values = ContentValues().apply {
            put("case_id", caseId)
            put("kind", kind)
            put("detail", detail)
            put("url", url)
            put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insert("events", null, values)
    }

    fun eventsForCase(caseId: Long, limit: Int = 200): List<CaseEvent> {
        val out = ArrayList<CaseEvent>()
        readableDatabase.rawQuery(
            "SELECT id, case_id, kind, detail, url, created_at FROM events WHERE case_id = ? " +
                "ORDER BY created_at DESC LIMIT ?",
            arrayOf(caseId.toString(), limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                out.add(
                    CaseEvent(
                        cursor.getLong(0),
                        cursor.getLong(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getString(4),
                        cursor.getLong(5)
                    )
                )
            }
        }
        return out
    }
}
