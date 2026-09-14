package com.duclab.streambrowser.browser

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.ServiceWorkerClientCompat
import androidx.webkit.ServiceWorkerControllerCompat
import androidx.webkit.WebViewFeature
import com.duclab.streambrowser.MainViewModel
import org.json.JSONObject

object BrowserFactory {
    private const val MEDIA_PREFIX = "__SB_MEDIA__"

    @SuppressLint("SetJavaScriptEnabled")
    fun create(context: Context, viewModel: MainViewModel): WebView {
        val blocker = AdBlocker(context)
        installServiceWorkerHook(viewModel, blocker)

        return WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = false
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.allowFileAccess = false
            settings.allowContentAccess = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            settings.setSupportMultipleWindows(true)

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    viewModel.onPageStarted(url)
                    updateNav(view, viewModel)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    viewModel.onPageFinished(url)
                    updateNav(view, viewModel)
                    view?.evaluateJavascript(detectorScript(), null)
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    if (viewModel.state.value.adBlockEnabled && blocker.shouldBlock(url)) {
                        viewModel.onAdBlocked()
                        return true
                    }
                    val scheme = request.url.scheme?.lowercase()
                    if (scheme == "http" || scheme == "https") return false
                    if (request.hasGesture()) {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, request.url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                    return true
                }

                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
                    val url = request.url.toString()
                    if (viewModel.state.value.adBlockEnabled && blocker.shouldBlock(url)) {
                        viewModel.onAdBlocked()
                        return blocker.emptyResponse()
                    }
                    viewModel.addDetectedMedia(url, request.requestHeaders, "network")
                    return null
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    viewModel.onPageProgress(newProgress)
                    updateNav(view, viewModel)
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    viewModel.onPageTitle(title)
                }

                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?
                ): Boolean {
                    // supportMultipleWindows=true + returning false blocks popup/new-window creation.
                    return false
                }

                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                    val msg = consoleMessage.message()
                    if (msg.startsWith(MEDIA_PREFIX)) {
                        runCatching {
                            val json = JSONObject(msg.removePrefix(MEDIA_PREFIX))
                            val src = json.optString("src")
                            if (src.startsWith("http://") || src.startsWith("https://")) {
                                viewModel.addDetectedMedia(src, emptyMap(), "DOM")
                            }
                        }
                        return true
                    }
                    return super.onConsoleMessage(consoleMessage)
                }
            }
        }
    }

    fun applyRuntimeSettings(webView: WebView, desktopMode: Boolean, popupBlock: Boolean) {
        webView.settings.setSupportMultipleWindows(popupBlock)
        val mobileUa = WebSettings.getDefaultUserAgent(webView.context)
        webView.settings.userAgentString = if (desktopMode) {
            mobileUa
                .replace("; wv", "")
                .replace(Regex("Android [^;\\)]+;? ?"), "")
                .replace("Mobile", "")
                .trim()
        } else mobileUa
    }

    fun rescan(webView: WebView) {
        webView.evaluateJavascript(detectorScript(), null)
    }

    private fun updateNav(view: WebView?, vm: MainViewModel) {
        if (view != null) vm.updateNavigationCapabilities(view.canGoBack(), view.canGoForward())
    }

    private fun installServiceWorkerHook(vm: MainViewModel, blocker: AdBlocker) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_SHOULD_INTERCEPT_REQUEST)
        ) return

        runCatching {
            ServiceWorkerControllerCompat.getInstance().setServiceWorkerClient(
                object : ServiceWorkerClientCompat() {
                    override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
                        val url = request.url.toString()
                        if (vm.state.value.adBlockEnabled && blocker.shouldBlock(url)) {
                            vm.onAdBlocked()
                            return blocker.emptyResponse()
                        }
                        vm.addDetectedMedia(url, request.requestHeaders, "service-worker")
                        return null
                    }
                }
            )
        }
    }

    private fun detectorScript(): String = """
        (function() {
          if (window.__streamBrowserDetectorInstalled) {
            if (window.__streamBrowserScan) window.__streamBrowserScan();
            return;
          }
          window.__streamBrowserDetectorInstalled = true;
          const PREFIX = '$MEDIA_PREFIX';
          const seen = new Set();
          function report(src) {
            try {
              if (!src || typeof src !== 'string') return;
              if (!(src.startsWith('http://') || src.startsWith('https://'))) return;
              if (seen.has(src)) return;
              seen.add(src);
              console.log(PREFIX + JSON.stringify({src: src}));
            } catch(e) {}
          }
          function scan() {
            try {
              document.querySelectorAll('video').forEach(v => {
                report(v.currentSrc); report(v.src);
                v.querySelectorAll('source').forEach(s => report(s.src));
              });
              document.querySelectorAll('source[type^="video"], source[type*="mpegurl"]').forEach(s => report(s.src));
              if (window.performance && performance.getEntriesByType) {
                performance.getEntriesByType('resource').forEach(e => {
                  const u = e.name || '';
                  const l = u.toLowerCase();
                  if (l.includes('.m3u8') || l.includes('.mpd') || l.includes('.mp4') || l.includes('.webm')) report(u);
                });
              }
            } catch(e) {}
          }
          window.__streamBrowserScan = scan;
          new MutationObserver(scan).observe(document.documentElement || document, {subtree:true, childList:true, attributes:true, attributeFilter:['src']});
          document.addEventListener('loadedmetadata', scan, true);
          document.addEventListener('play', scan, true);
          scan();
          setInterval(scan, 2500);
        })();
    """.trimIndent()
}
