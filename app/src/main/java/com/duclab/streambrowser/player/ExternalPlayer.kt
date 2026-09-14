package com.duclab.streambrowser.player

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.duclab.streambrowser.DetectedMedia

object ExternalPlayer {
    private val mxPackages = listOf("com.mxtech.videoplayer.pro", "com.mxtech.videoplayer.ad")

    fun openMx(context: Context, media: DetectedMedia) {
        val uri = Uri.parse(media.url)
        val mime = mimeFor(media)
        val pm = context.packageManager
        val installed = mxPackages.firstOrNull { pkg ->
            runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("title", "Stream Browser")
            if (installed != null) setPackage(installed)
        }

        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, mime) },
                        "Mở video bằng…"
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Throwable) {
                Toast.makeText(context, "Không tìm thấy trình phát phù hợp.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun mimeFor(media: DetectedMedia): String = when (media.kind.name) {
        "HLS" -> "application/x-mpegURL"
        "DASH" -> "application/dash+xml"
        "WEBM" -> "video/webm"
        else -> "video/*"
    }
}
