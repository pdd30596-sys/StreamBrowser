package com.duclab.streambrowser

import android.app.Application
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.URLUtil
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.duclab.streambrowser.browser.MediaDetector
import com.duclab.streambrowser.download.DownloadEngine
import com.duclab.streambrowser.download.HistoryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val downloadEngine = DownloadEngine(application.applicationContext)
    private val historyStore = HistoryStore(application.applicationContext)
    private val blockedCounter = AtomicInteger(0)

    private val _state = MutableStateFlow(
        BrowserUiState(downloadHistory = historyStore.load())
    )
    val state: StateFlow<BrowserUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { downloadEngine.init() } }
                .onSuccess { _state.update { it.copy(downloaderReady = true) } }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            downloaderReady = false,
                            downloadStatus = "Bộ tải chưa sẵn sàng",
                            error = friendlyError(e)
                        )
                    }
                }
        }
    }

    fun setTab(tab: AppTab) {
        _state.update { it.copy(currentTab = tab) }
    }

    fun setAddressDraft(value: String) {
        _state.update { it.copy(addressDraft = value) }
    }

    fun navigate(raw: String) {
        val url = normalizeInput(raw)
        _state.update {
            it.copy(
                currentTab = AppTab.BROWSER,
                addressDraft = url,
                navigationUrl = url,
                navigationToken = it.navigationToken + 1,
                error = null
            )
        }
    }

    fun handleExternalText(text: String?) {
        val url = extractUrl(text.orEmpty()) ?: return
        navigate(url)
    }

    fun onPageStarted(url: String?) {
        val actual = url.orEmpty()
        blockedCounter.set(0)
        _state.update {
            it.copy(
                pageUrl = actual,
                addressDraft = actual.ifBlank { it.addressDraft },
                isLoading = true,
                pageProgress = 0,
                detectedMedia = emptyList(),
                blockedRequests = 0,
                error = null
            )
        }
    }

    fun onPageFinished(url: String?) {
        _state.update {
            it.copy(
                pageUrl = url ?: it.pageUrl,
                isLoading = false,
                pageProgress = 100
            )
        }
    }

    fun onPageProgress(progress: Int) {
        _state.update { it.copy(pageProgress = progress.coerceIn(0, 100), isLoading = progress < 100) }
    }

    fun onPageTitle(title: String?) {
        if (!title.isNullOrBlank()) _state.update { it.copy(pageTitle = title.take(120)) }
    }

    fun updateNavigationCapabilities(canBack: Boolean, canForward: Boolean) {
        _state.update { it.copy(canGoBack = canBack, canGoForward = canForward) }
    }

    fun onAdBlocked() {
        val n = blockedCounter.incrementAndGet()
        if (n == 1 || n % 5 == 0) _state.update { it.copy(blockedRequests = n) }
    }

    fun addDetectedMedia(
        url: String,
        requestHeaders: Map<String, String> = emptyMap(),
        discoveredBy: String = "network"
    ) {
        val kind = MediaDetector.classify(url, requestHeaders) ?: return
        val page = _state.value.pageUrl
        val filteredHeaders = requestHeaders
            .filterKeys { key ->
                key.equals("User-Agent", true) || key.equals("Referer", true) ||
                    key.equals("Origin", true) || key.equals("Cookie", true)
            }
        val item = DetectedMedia(
            url = url,
            kind = kind,
            sourcePage = page,
            headers = filteredHeaders,
            discoveredBy = discoveredBy
        )
        _state.update { s ->
            if (s.detectedMedia.any { it.url == url }) s
            else s.copy(detectedMedia = (listOf(item) + s.detectedMedia).take(30))
        }
    }

    fun clearDetectedMedia() {
        _state.update { it.copy(detectedMedia = emptyList()) }
    }

    fun play(media: DetectedMedia) {
        _state.update { it.copy(selectedMedia = enrichCookies(media), currentTab = AppTab.PLAYER, error = null) }
    }

    fun currentMediaForExternal(media: DetectedMedia): DetectedMedia = enrichCookies(media)

    fun download(media: DetectedMedia) {
        if (_state.value.isDownloading) return
        if (!_state.value.downloaderReady) {
            _state.update { it.copy(error = "Bộ tải video chưa khởi tạo xong.") }
            return
        }

        val enriched = enrichCookies(media)
        viewModelScope.launch {
            _state.update {
                it.copy(
                    currentTab = AppTab.DOWNLOADS,
                    isDownloading = true,
                    downloadProgress = 0f,
                    downloadEtaSeconds = 0,
                    downloadStatus = "Đang chuẩn bị tải…",
                    error = null
                )
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    downloadEngine.download(enriched) { progress, eta ->
                        _state.update {
                            it.copy(
                                downloadProgress = progress,
                                downloadEtaSeconds = eta,
                                downloadStatus = if (eta > 0) "Đang tải • còn khoảng ${eta}s" else "Đang tải…"
                            )
                        }
                    }
                }
            }.onSuccess { item ->
                val history = historyStore.add(item)
                _state.update {
                    it.copy(
                        isDownloading = false,
                        downloadProgress = 1f,
                        downloadEtaSeconds = 0,
                        downloadStatus = "Đã lưu vào Download/StreamBrowser",
                        downloadHistory = history
                    )
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        isDownloading = false,
                        downloadStatus = "Tải thất bại",
                        error = friendlyError(e)
                    )
                }
            }
        }
    }

    fun clearDownloadHistory() {
        historyStore.clear()
        _state.update { it.copy(downloadHistory = emptyList()) }
    }

    fun setAdBlock(enabled: Boolean) {
        _state.update { it.copy(adBlockEnabled = enabled) }
    }

    fun setPopupBlock(enabled: Boolean) {
        _state.update { it.copy(popupBlockEnabled = enabled) }
    }

    fun setDesktopMode(enabled: Boolean) {
        _state.update { it.copy(desktopMode = enabled) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun enrichCookies(media: DetectedMedia): DetectedMedia {
        val cookie = runCatching { CookieManager.getInstance().getCookie(media.url) }.getOrNull()
        if (cookie.isNullOrBlank()) return media
        return media.copy(headers = media.headers + ("Cookie" to cookie))
    }

    private fun normalizeInput(raw: String): String {
        val input = raw.trim()
        if (URLUtil.isValidUrl(input) && (input.startsWith("http://") || input.startsWith("https://"))) return input
        if (!input.contains(' ') && input.contains('.')) return "https://$input"
        return "https://www.google.com/search?q=${Uri.encode(input)}"
    }

    private fun extractUrl(text: String): String? {
        return Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)
            .find(text)
            ?.value
            ?.trimEnd('.', ',', ')', ']', '}')
    }

    private fun friendlyError(t: Throwable): String {
        val raw = (t.message ?: t.javaClass.simpleName).replace("ERROR:", "").trim()
        return when {
            raw.contains("DRM", true) -> "Luồng video có DRM/bảo vệ nội dung. Stream Browser không vượt DRM."
            raw.contains("403", true) || raw.contains("forbidden", true) -> "Máy chủ từ chối luồng video (403). Link có thể cần cookie/header hoặc đã hết hạn."
            raw.contains("Unsupported URL", true) -> "Bộ tải chưa hỗ trợ loại link này. Bạn vẫn có thể thử phát trực tiếp trong Player."
            else -> raw.take(300)
        }
    }
}
