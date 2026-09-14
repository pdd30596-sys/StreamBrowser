package com.duclab.streambrowser.browser

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

class AdBlocker(context: Context) {
    private val blockedHosts: Set<String> = context.assets.open("ad_hosts.txt").bufferedReader().useLines { lines ->
        lines.map { it.trim().lowercase() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .toSet()
    }

    fun shouldBlock(url: String): Boolean {
        val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull() ?: return false
        return blockedHosts.any { blocked -> host == blocked || host.endsWith(".$blocked") }
    }

    fun emptyResponse(): WebResourceResponse = WebResourceResponse(
        "text/plain",
        "utf-8",
        204,
        "No Content",
        emptyMap(),
        ByteArrayInputStream(ByteArray(0))
    )
}
