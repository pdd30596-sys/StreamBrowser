package com.duclab.streambrowser.download

import android.content.Context
import com.duclab.streambrowser.DownloadHistoryItem
import org.json.JSONArray
import org.json.JSONObject

class HistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("download_history", Context.MODE_PRIVATE)

    fun load(): List<DownloadHistoryItem> {
        val raw = prefs.getString("items", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        DownloadHistoryItem(
                            title = o.optString("title"),
                            uri = o.optString("uri"),
                            savedAt = o.optLong("savedAt"),
                            kind = o.optString("kind")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun add(item: DownloadHistoryItem): List<DownloadHistoryItem> {
        val updated = (listOf(item) + load()).distinctBy { it.uri }.take(30)
        save(updated)
        return updated
    }

    fun clear() {
        prefs.edit().remove("items").apply()
    }

    private fun save(items: List<DownloadHistoryItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject()
                    .put("title", item.title)
                    .put("uri", item.uri)
                    .put("savedAt", item.savedAt)
                    .put("kind", item.kind)
            )
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }
}
