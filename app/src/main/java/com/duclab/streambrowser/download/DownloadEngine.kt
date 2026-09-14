package com.duclab.streambrowser.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.duclab.streambrowser.DetectedMedia
import com.duclab.streambrowser.DownloadHistoryItem
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.io.FileInputStream

class DownloadEngine(private val context: Context) {
    private val workDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "streambrowser_work").apply { mkdirs() }

    fun init() {
        YoutubeDL.getInstance().init(context)
        runCatching { FFmpeg.getInstance().init(context) }
    }

    fun download(
        media: DetectedMedia,
        onProgress: (Float, Long) -> Unit
    ): DownloadHistoryItem {
        cleanWorkDir()

        val request = YoutubeDLRequest(media.url)
        request.addOption("--no-playlist")
        request.addOption("--no-mtime")
        request.addOption("--newline")
        request.addOption("--merge-output-format", "mp4")
        request.addOption("-o", File(workDir, "%(title).120B [%(id)s].%(ext)s").absolutePath)

        media.headers.forEach { (key, value) ->
            if (value.isNotBlank()) request.addOption("--add-header", "$key:$value")
        }
        if (media.sourcePage.isNotBlank() && media.headers.keys.none { it.equals("Referer", true) }) {
            request.addOption("--referer", media.sourcePage)
        }
        
        YoutubeDL.getInstance().execute(
             request,
            "stream-${System.currentTimeMillis()}"
        ) { progress, etaInSeconds, _ ->
            onProgress(
                (progress / 100f).coerceIn(0f, 1f),
                etaInSeconds
            )
        }

        val output = workDir.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
            ?.maxByOrNull { it.lastModified() }
            ?: error("Không tìm thấy file sau khi tải.")

        val published = publishToDownloads(output)
        val title = output.nameWithoutExtension.ifBlank { "Video" }
        val kind = output.extension.ifBlank { media.kind.label }
        output.delete()

        return DownloadHistoryItem(
            title = title,
            uri = published.toString(),
            savedAt = System.currentTimeMillis(),
            kind = kind
        )
    }

    private fun publishToDownloads(file: File): Uri {
        val mime = when (file.extension.lowercase()) {
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            else -> "video/mp4"
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/StreamBrowser")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Không thể tạo file trong thư mục Download.")

        try {
            context.contentResolver.openOutputStream(uri, "w")!!.use { out ->
                FileInputStream(file).use { input -> input.copyTo(out, 1024 * 1024) }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        } catch (t: Throwable) {
            context.contentResolver.delete(uri, null, null)
            throw t
        }
        return uri
    }

    private fun cleanWorkDir() {
        workDir.listFiles()?.forEach { runCatching { it.deleteRecursively() } }
    }
}
