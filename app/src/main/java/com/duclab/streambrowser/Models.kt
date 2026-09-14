package com.duclab.streambrowser

enum class AppTab { BROWSER, VIDEOS, DOWNLOADS, SETTINGS, PLAYER }

enum class MediaKind(val label: String) {
    HLS("HLS / m3u8"),
    DASH("DASH / mpd"),
    MP4("MP4"),
    WEBM("WebM"),
    VIDEO("Video")
}

data class DetectedMedia(
    val url: String,
    val kind: MediaKind,
    val sourcePage: String,
    val headers: Map<String, String> = emptyMap(),
    val discoveredBy: String = "network",
    val discoveredAt: Long = System.currentTimeMillis()
) {
    val id: String get() = url
}

data class DownloadHistoryItem(
    val title: String,
    val uri: String,
    val savedAt: Long,
    val kind: String
)

data class BrowserUiState(
    val currentTab: AppTab = AppTab.BROWSER,
    val addressDraft: String = "https://www.google.com",
    val pageUrl: String = "",
    val pageTitle: String = "Stream Browser",
    val pageProgress: Int = 0,
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val navigationUrl: String = "https://www.google.com",
    val navigationToken: Long = 1L,
    val detectedMedia: List<DetectedMedia> = emptyList(),
    val selectedMedia: DetectedMedia? = null,
    val adBlockEnabled: Boolean = true,
    val popupBlockEnabled: Boolean = true,
    val desktopMode: Boolean = false,
    val blockedRequests: Int = 0,
    val downloaderReady: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadEtaSeconds: Long = 0L,
    val downloadStatus: String = "Sẵn sàng",
    val downloadHistory: List<DownloadHistoryItem> = emptyList(),
    val error: String? = null
)
