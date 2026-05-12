package com.hanif.textscanner.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object SessionManager {

    private const val PREF_SESSIONS = "scan_sessions"
    private const val PREF_FOLDERS  = "scan_folders"
    private const val KEY_SESSIONS  = "sessions"
    private const val KEY_FOLDERS   = "folders"
    private const val MAX_SESSIONS  = 100

    fun createSession(context: Context, title: String,
                      totalExpected: Int = 0, folderId: String = "default"): ScanSession {
        val session = ScanSession(
            id = System.currentTimeMillis(),
            title = title,
            totalExpected = totalExpected,
            folderId = folderId
        )
        saveSession(context, session)
        return session
    }

    fun savePage(context: Context, sessionId: Long, page: ScanPage) {
        val session = getSession(context, sessionId) ?: return
        val pages = session.pages.toMutableList()
        val idx = pages.indexOfFirst { it.pageNumber == page.pageNumber }
        if (idx >= 0) pages[idx] = page else pages.add(page)
        pages.sortBy { it.pageNumber }
        saveSession(context, session.copy(
            pages = pages.toList(),
            updatedAt = System.currentTimeMillis()
        ))
    }

    fun completeSession(context: Context, sessionId: Long) {
        val session = getSession(context, sessionId) ?: return
        saveSession(context, session.copy(
            status = SessionStatus.COMPLETE,
            updatedAt = System.currentTimeMillis()
        ))
    }

    fun renameSession(context: Context, sessionId: Long, newTitle: String) {
        val session = getSession(context, sessionId) ?: return
        saveSession(context, session.copy(title = newTitle,
            updatedAt = System.currentTimeMillis()))
    }

    fun moveToFolder(context: Context, sessionId: Long, folderId: String) {
        val session = getSession(context, sessionId) ?: return
        saveSession(context, session.copy(folderId = folderId,
            updatedAt = System.currentTimeMillis()))
    }

    fun deleteSession(context: Context, sessionId: Long) {
        saveAll(context, getAll(context).filter { it.id != sessionId })
    }

    fun getSession(context: Context, id: Long): ScanSession? =
        getAll(context).find { it.id == id }

    fun getAll(context: Context): List<ScanSession> {
        val prefs = context.getSharedPreferences(PREF_SESSIONS, Context.MODE_PRIVATE)
        return try {
            val arr = JSONArray(prefs.getString(KEY_SESSIONS, "[]"))
            (0 until arr.length()).map { parseSession(arr.getJSONObject(it)) }
                .sortedByDescending { it.updatedAt }
        } catch (e: Exception) { emptyList() }
    }

    fun getInProgressSessions(context: Context): List<ScanSession> =
        getAll(context).filter { it.status == SessionStatus.IN_PROGRESS && it.pages.isNotEmpty() }

    fun getByFolder(context: Context, folderId: String): List<ScanSession> =
        getAll(context).filter { it.folderId == folderId }

    fun createFolder(context: Context, name: String, icon: String = "📁"): ScanFolder {
        val folder = ScanFolder(
            id = "folder_${System.currentTimeMillis()}",
            name = name, icon = icon
        )
        val folders = getFolders(context).toMutableList()
        folders.add(folder)
        saveFolders(context, folders)
        return folder
    }

    fun getFolders(context: Context): List<ScanFolder> {
        val prefs = context.getSharedPreferences(PREF_FOLDERS, Context.MODE_PRIVATE)
        val json  = prefs.getString(KEY_FOLDERS, null) ?: return getDefaultFolders()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ScanFolder(o.getString("id"), o.getString("name"),
                    o.optString("icon","📁"), o.optLong("createdAt", 0))
            }
        } catch (e: Exception) { getDefaultFolders() }
    }

    fun deleteFolder(context: Context, folderId: String) {
        saveFolders(context, getFolders(context).filter { it.id != folderId })
        getByFolder(context, folderId).forEach { moveToFolder(context, it.id, "default") }
    }

    fun clearAll(context: Context) {
        context.getSharedPreferences(PREF_SESSIONS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun saveSession(context: Context, session: ScanSession) {
        val all = getAll(context).filter { it.id != session.id }.toMutableList()
        all.add(0, session)
        saveAll(context, if (all.size > MAX_SESSIONS) all.subList(0, MAX_SESSIONS) else all)
    }

    private fun saveAll(context: Context, sessions: List<ScanSession>) {
        val arr = JSONArray()
        sessions.forEach { arr.put(sessionToJson(it)) }
        context.getSharedPreferences(PREF_SESSIONS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SESSIONS, arr.toString()).apply()
    }

    private fun saveFolders(context: Context, folders: List<ScanFolder>) {
        val arr = JSONArray()
        folders.forEach { f ->
            arr.put(JSONObject().apply {
                put("id", f.id); put("name", f.name)
                put("icon", f.icon); put("createdAt", f.createdAt)
            })
        }
        context.getSharedPreferences(PREF_FOLDERS, Context.MODE_PRIVATE)
            .edit().putString(KEY_FOLDERS, arr.toString()).apply()
    }

    private fun sessionToJson(s: ScanSession): JSONObject {
        val pagesArr = JSONArray()
        s.pages.forEach { p ->
            pagesArr.put(JSONObject().apply {
                put("pageNumber", p.pageNumber)
                put("text", p.text)
                put("imageUri", p.imageUri)
                put("scannedAt", p.scannedAt)
                put("failed", p.failed)
            })
        }
        return JSONObject().apply {
            put("id", s.id); put("title", s.title)
            put("pages", pagesArr); put("totalExpected", s.totalExpected)
            put("status", s.status.name); put("createdAt", s.createdAt)
            put("updatedAt", s.updatedAt); put("folderId", s.folderId)
        }
    }

    private fun parseSession(o: JSONObject): ScanSession {
        val pArr = o.optJSONArray("pages") ?: JSONArray()
        val pages = (0 until pArr.length()).map { i ->
            val p = pArr.getJSONObject(i)
            ScanPage(
                pageNumber = p.optInt("pageNumber", i + 1),
                text       = p.optString("text", ""),
                imageUri   = p.optString("imageUri", ""),
                scannedAt  = p.optLong("scannedAt", 0),
                failed     = p.optBoolean("failed", false)
            )
        }.toMutableList()
        return ScanSession(
            id            = o.getLong("id"),
            title         = o.optString("title", "Untitled"),
            pages         = pages,
            totalExpected = o.optInt("totalExpected", 0),
            status        = try { SessionStatus.valueOf(o.optString("status","IN_PROGRESS")) }
                            catch (e: Exception) { SessionStatus.IN_PROGRESS },
            createdAt     = o.optLong("createdAt", 0),
            updatedAt     = o.optLong("updatedAt", 0),
            folderId      = o.optString("folderId", "default")
        )
    }

    private fun getDefaultFolders() = listOf(
        ScanFolder("default",   "সব স্ক্যান",     "📋"),
        ScanFolder("exam",      "পরীক্ষার নোট",   "📚"),
        ScanFolder("job",       "চাকরির প্রস্তুতি","💼"),
        ScanFolder("mcq",       "MCQ",             "✅"),
        ScanFolder("important", "গুরুত্বপূর্ণ",   "⭐")
    )
}
