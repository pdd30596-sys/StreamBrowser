package com.duclab.streambrowser.ui

import android.content.Intent
import android.net.Uri
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.duclab.streambrowser.AppTab
import com.duclab.streambrowser.BrowserUiState
import com.duclab.streambrowser.DetectedMedia
import com.duclab.streambrowser.MainViewModel
import com.duclab.streambrowser.browser.BrowserFactory
import com.duclab.streambrowser.player.ExternalPlayer
import com.duclab.streambrowser.player.PlayerScreen
import java.text.DateFormat
import java.util.Date

@Composable
fun StreamBrowserApp(
    viewModel: MainViewModel,
    onPictureInPicture: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val webView = remember { BrowserFactory.create(context, viewModel) }

    DisposableEffect(webView) {
        onDispose {
            runCatching { webView.stopLoading() }
            runCatching { webView.destroy() }
        }
    }

    LaunchedEffect(state.navigationToken) {
        if (state.navigationUrl.isNotBlank()) webView.loadUrl(state.navigationUrl)
    }

    LaunchedEffect(state.desktopMode, state.popupBlockEnabled) {
        val beforeUa = webView.settings.userAgentString
        BrowserFactory.applyRuntimeSettings(webView, state.desktopMode, state.popupBlockEnabled)
        if (state.pageUrl.isNotBlank() && beforeUa != webView.settings.userAgentString) webView.reload()
    }

    BackHandler(enabled = state.currentTab != AppTab.BROWSER || webView.canGoBack()) {
        if (state.currentTab == AppTab.PLAYER) viewModel.setTab(AppTab.VIDEOS)
        else if (state.currentTab != AppTab.BROWSER) viewModel.setTab(AppTab.BROWSER)
        else if (webView.canGoBack()) webView.goBack()
    }

    if (state.currentTab == AppTab.PLAYER && state.selectedMedia != null) {
        PlayerScreen(
            media = state.selectedMedia!!,
            onBack = { viewModel.setTab(AppTab.VIDEOS) },
            onPictureInPicture = onPictureInPicture,
            onOpenMx = { ExternalPlayer.openMx(context, viewModel.currentMediaForExternal(state.selectedMedia!!)) },
            onDownload = { viewModel.download(state.selectedMedia!!) }
        )
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavItem("🌐", "Browser", state.currentTab == AppTab.BROWSER) { viewModel.setTab(AppTab.BROWSER) }
                NavItem("🎬", "Video ${if (state.detectedMedia.isNotEmpty()) "(${state.detectedMedia.size})" else ""}", state.currentTab == AppTab.VIDEOS) { viewModel.setTab(AppTab.VIDEOS) }
                NavItem("⬇", "Tải", state.currentTab == AppTab.DOWNLOADS) { viewModel.setTab(AppTab.DOWNLOADS) }
                NavItem("⚙", "Cài đặt", state.currentTab == AppTab.SETTINGS) { viewModel.setTab(AppTab.SETTINGS) }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (state.currentTab) {
                AppTab.BROWSER -> BrowserScreen(webView, state, viewModel)
                AppTab.VIDEOS -> VideoListScreen(
                    state = state,
                    onPlay = viewModel::play,
                    onMx = { ExternalPlayer.openMx(context, viewModel.currentMediaForExternal(it)) },
                    onDownload = viewModel::download,
                    onClear = viewModel::clearDetectedMedia
                )
                AppTab.DOWNLOADS -> DownloadScreen(state, viewModel)
                AppTab.SETTINGS -> SettingsScreen(state, viewModel, webView)
                AppTab.PLAYER -> Unit
            }

            state.error?.let { error ->
                Card(modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = viewModel::clearError) { Text("Đóng") }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavItem(iconText: String, label: String, selected: Boolean, onClick: () -> Unit) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Text(iconText) },
        label = { Text(label) }
    )
}

@Composable
private fun BrowserScreen(webView: WebView, state: BrowserUiState, vm: MainViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = state.addressDraft,
                onValueChange = vm::setAddressDraft,
                singleLine = true,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Nhập địa chỉ hoặc từ khóa") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { vm.navigate(state.addressDraft) })
            )
            Button(onClick = { vm.navigate(state.addressDraft) }) { Text("Đi") }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(onClick = { if (webView.canGoBack()) webView.goBack() }, enabled = state.canGoBack) { Text("←") }
            OutlinedButton(onClick = { if (webView.canGoForward()) webView.goForward() }, enabled = state.canGoForward) { Text("→") }
            OutlinedButton(onClick = { webView.reload() }) { Text("↻") }
            Column(modifier = Modifier.weight(1f)) {
                Text(state.pageTitle.take(48), maxLines = 1, style = MaterialTheme.typography.bodySmall)
                Text("Đã chặn ${state.blockedRequests} request", style = MaterialTheme.typography.labelSmall)
            }
            if (state.detectedMedia.isNotEmpty()) {
                Button(onClick = { vm.setTab(AppTab.VIDEOS) }) { Text("🎬 ${state.detectedMedia.size}") }
            } else {
                OutlinedButton(onClick = { BrowserFactory.rescan(webView) }) { Text("Quét") }
            }
        }

        if (state.isLoading) {
            LinearProgressIndicator(
                progress = { state.pageProgress / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        }

        AndroidView(
            factory = {
                (webView.parent as? android.view.ViewGroup)?.removeView(webView)
                webView
            },
            modifier = Modifier.fillMaxSize(),
            update = {
                BrowserFactory.applyRuntimeSettings(it, state.desktopMode, state.popupBlockEnabled)
            }
        )
    }
}

@Composable
private fun VideoListScreen(
    state: BrowserUiState,
    onPlay: (DetectedMedia) -> Unit,
    onMx: (DetectedMedia) -> Unit,
    onDownload: (DetectedMedia) -> Unit,
    onClear: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Video đã phát hiện", style = MaterialTheme.typography.titleLarge)
                Text("Ưu tiên manifest/direct stream; không liệt kê từng segment nhỏ.", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = onClear, enabled = state.detectedMedia.isNotEmpty()) { Text("Xóa") }
        }
        Spacer(Modifier.height(8.dp))

        if (state.detectedMedia.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Chưa thấy video. Hãy mở video trong Browser và bấm phát một lần.")
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.detectedMedia, key = { it.id }) { media ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(media.kind.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                runCatching { Uri.parse(media.url).host ?: media.url }.getOrDefault(media.url),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(media.url.take(180), style = MaterialTheme.typography.bodySmall)
                            Text("Nguồn phát hiện: ${media.discoveredBy}", style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onPlay(media) }) { Text("▶ Xem") }
                                OutlinedButton(onClick = { onMx(media) }) { Text("MX Player") }
                                OutlinedButton(onClick = { onDownload(media) }) { Text("⬇ Tải") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadScreen(state: BrowserUiState, vm: MainViewModel) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Tải xuống", style = MaterialTheme.typography.titleLarge)
                Text(state.downloadStatus, style = MaterialTheme.typography.bodyMedium)
            }
            if (state.isDownloading) CircularProgressIndicator()
        }

        if (state.isDownloading || state.downloadProgress > 0f) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { state.downloadProgress }, modifier = Modifier.fillMaxWidth())
            Text("${(state.downloadProgress * 100).toInt()}%")
        }
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Lịch sử", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = vm::clearDownloadHistory, enabled = state.downloadHistory.isNotEmpty()) { Text("Xóa lịch sử") }
        }
        HorizontalDivider(Modifier.padding(vertical = 6.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.downloadHistory) { item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall)
                        Text("${item.kind} • ${DateFormat.getDateTimeInstance().format(Date(item.savedAt))}", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(Uri.parse(item.uri), "video/*")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                )
                            }
                        }) { Text("Mở file") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(state: BrowserUiState, vm: MainViewModel, webView: WebView) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Cài đặt", style = MaterialTheme.typography.titleLarge)
        ToggleRow("Chặn quảng cáo / tracker", "Chặn theo host ở tầng request WebView.", state.adBlockEnabled, vm::setAdBlock)
        ToggleRow("Chặn popup / tab mới", "Ngăn window.open và target=_blank mở cửa sổ quảng cáo.", state.popupBlockEnabled, vm::setPopupBlock)
        ToggleRow("Chế độ trang desktop", "Đổi User-Agent và tải lại trang.", state.desktopMode, vm::setDesktopMode)

        HorizontalDivider()
        Text("Dữ liệu trình duyệt", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = {
            webView.clearHistory()
            webView.clearCache(true)
        }) { Text("Xóa cache + lịch sử WebView") }

        Text(
            "Giới hạn V1: không vượt DRM/Widevine; không thể loại bỏ quảng cáo đã được ghép trực tiếp vào cùng luồng video (SSAI); một số link ký số có thể hết hạn hoặc yêu cầu phiên đăng nhập.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
