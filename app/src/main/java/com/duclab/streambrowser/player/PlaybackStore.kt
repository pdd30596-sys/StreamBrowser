package com.duclab.streambrowser.player

import android.content.Context

class PlaybackStore(context: Context) {
    private val prefs = context.getSharedPreferences("playback_positions", Context.MODE_PRIVATE)

    fun get(url: String): Long = prefs.getLong(url.hashCode().toString(), 0L)

    fun set(url: String, positionMs: Long) {
        prefs.edit().putLong(url.hashCode().toString(), positionMs.coerceAtLeast(0L)).apply()
    }
}
