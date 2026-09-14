package com.duclab.streambrowser.browser

import com.duclab.streambrowser.MediaKind

object MediaDetector {
    private val segmentSuffixes = listOf(".ts", ".m4s", ".cmfv", ".cmfa", ".aac", ".vtt", ".srt")

    fun classify(url: String, headers: Map<String, String> = emptyMap()): MediaKind? {
        val u = url.trim()
        if (u.isBlank() || u.startsWith("blob:", true) || u.startsWith("data:", true)) return null
        val lower = u.lowercase()
        val pathPart = lower.substringBefore('?').substringBefore('#')
        if (segmentSuffixes.any { pathPart.endsWith(it) }) return null

        return when {
            ".m3u8" in lower || "m3u8" in lower -> MediaKind.HLS
            pathPart.endsWith(".mpd") || "manifest.mpd" in lower -> MediaKind.DASH
            pathPart.endsWith(".mp4") || pathPart.endsWith(".m4v") || pathPart.endsWith(".mov") -> MediaKind.MP4
            pathPart.endsWith(".webm") -> MediaKind.WEBM
            "mime=video" in lower || "mime%3dvideo" in lower || "videoplayback" in lower -> MediaKind.VIDEO
            headers.entries.any { it.key.equals("Accept", true) && it.value.contains("video/", true) } -> MediaKind.VIDEO
            else -> null
        }
    }
}
