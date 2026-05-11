package com.hanif.textscanner.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object HistoryManager {

    private const val PREF_NAME = "scan_history"
    private const val KEY_LIST = "history_list"
    private const val MAX_HISTORY = 50

    fun addHistory(context: Context, item: ScanHistory) {
        val list = getAll(context).toMutableList()
        list.add(0, item)
        // Keep only latest MAX_HISTORY
        val trimmed = if (list.size > MAX_HISTORY) list.subList(0, MAX_HISTORY) else list
        saveAll(context, trimmed)
    }

    fun getAll(context: Context): List<ScanHistory> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_LIST, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                ScanHistory(
                    id = obj.getLong("id"),
                    text = obj.getString("text"),
                    sourceUri = obj.optString("sourceUri", ""),
                    timestamp = obj.getLong("timestamp"),
                    wordCount = obj.optInt("wordCount", 0)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearAll(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_LIST).apply()
    }

    fun delete(context: Context, id: Long) {
        val list = getAll(context).filter { it.id != id }
        saveAll(context, list)
    }

    private fun saveAll(context: Context, list: List<ScanHistory>) {
        val arr = JSONArray()
        list.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("text", item.text)
                put("sourceUri", item.sourceUri)
                put("timestamp", item.timestamp)
                put("wordCount", item.wordCount)
            }
            arr.put(obj)
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LIST, arr.toString()).apply()
    }
}
