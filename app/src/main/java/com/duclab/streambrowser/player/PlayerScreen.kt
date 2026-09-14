package com.duclab.streambrowser.player

import android.webkit.CookieManager
import android.webkit.WebSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.duclab.streambrowser.DetectedMedia
import com.duclab.streambrowser.MediaKind

@Composable
fun PlayerScreen(
    media: DetectedMedia,
    onBack: () -> Unit,
    onPictureInPicture: () -> Unit,
    onOpenMx: () -> Unit,
    onDownload: () -> Unit
) {
    val context = LocalContext.current
    val playbackStore = remember { PlaybackStore(context) }
    var error by remember(media.url) { mutableStateOf<String?>(null) }

    val player = remember(media.url) {
        val headers = media.headers.toMutableMap().apply {
            putIfAbsent("User-Agent", WebSettings.getDefaultUserAgent(context))
            val cookie = runCatching { CookieManager.getInstance().getCookie(media.url) }.getOrNull()
            if (!cookie.isNullOrBlank()) put("Cookie", cookie)
            if (media.sourcePage.isNotBlank()) putIfAbsent("Referer", media.sourcePage)
        }
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(headers)
        val mediaSourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(httpFactory)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                val item = MediaItem.Builder()
                    .setUri(media.url)
                    .setMimeType(mimeFor(media.kind))
                    .build()
                setMediaItem(item)
                seekTo(playbackStore.get(media.url))
                addListener(object : Player.Listener {
                    override fun onPlayerError(playerError: PlaybackException) {
                        error = playerError.message ?: "Không phát được luồng video này."
                    }
                })
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(player) {
        onDispose {
            playbackStore.set(media.url, player.currentPosition)
            player.release()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onBack) { Text("← Quay lại") }
            Button(onClick = onPictureInPicture) { Text("PiP") }
            Button(onClick = onOpenMx) { Text("MX Player") }
            Button(onClick = onDownload) { Text("Tải") }
        }

        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = true
                    keepScreenOn = true
                    this.player = player
                }
            },
            update = { it.player = player }
        )

        if (error != null) {
            Text(
                text = error!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(12.dp)
            )
        }
        Text(
            text = media.kind.label + " • " + media.url.take(120),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(10.dp)
        )
    }
}

private fun mimeFor(kind: MediaKind): String? = when (kind) {
    MediaKind.HLS -> MimeTypes.APPLICATION_M3U8
    MediaKind.DASH -> MimeTypes.APPLICATION_MPD
    MediaKind.MP4 -> MimeTypes.VIDEO_MP4
    MediaKind.WEBM -> MimeTypes.VIDEO_WEBM
    MediaKind.VIDEO -> null
}
