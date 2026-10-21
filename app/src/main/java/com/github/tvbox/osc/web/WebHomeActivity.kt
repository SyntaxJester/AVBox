package com.github.tvbox.osc.web

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import com.github.catvod.crawler.SpiderDebug
import java.io.ByteArrayInputStream

/**
 * WebHome Activity - 加载 eclipse.html HTML 主题页
 *
 * 加载方式说明：
 * eclipse.html 依赖 4 个相对路径的 JS 文件，并且内容里含 `#`（CSS 颜色）。
 * 因此不能用 loadData()（会截断 `#` 之后的内容，且无法解析相对资源），
 * 也不能直接用 file:///android_asset（file 域下 XHR/部分 API 受限）。
 * 这里用一个虚拟 https 域，配上 shouldInterceptRequest 从 assets 读文件，
 * 页面拿到的是正常的网页上下文，相对路径 JS 也能被正确解析。
 *
 * 桥接注入：bootstrap 脚本直接插进 HTML 的 <head> 顶部，
 * 保证在任何页面脚本执行之前 window.fm 就已就绪。
 */
class WebHomeActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var bridge: WebHomeBridge

    companion object {
        const val THEME_NAME = "eclipse.html"

        /** 虚拟域，仅用于 WebHome 内部资源，拦截时严格校验 host。 */
        private const val VIRTUAL_HOST = "webhome.local"
        private const val BASE_URL = "https://$VIRTUAL_HOST/"
        private const val ASSET_ROOT = "webhome"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        webView = WebView(this)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            builtInZoomControls = false
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            mediaPlaybackRequiresUserGesture = false
        }

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url ?: return null
                // 只接管自己这个虚拟域，其它请求一律放行
                if (!VIRTUAL_HOST.equals(url.host, ignoreCase = true)) return null

                val path = url.path?.removePrefix("/")?.takeIf { it.isNotEmpty() } ?: THEME_NAME
                return serveAsset(path)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                SpiderDebug.log("webhome: page finished: $url")
                // 双保险：页面加载完再注入一次（bootstrap 内部有幂等守卫）
                injectBootstrapScript()
            }
        }

        bridge = WebHomeBridge(this, webView)

        setContentView(webView)

        setupBridge()
        loadPage()
    }

    private fun setupBridge() {
        webView.addJavascriptInterface(bridge, WebHomeProtocol.NATIVE_BRIDGE_NAME)
    }

    /** 从 assets/webhome 读取文件并返回给 WebView；HTML 会顺带注入 bootstrap。 */
    private fun serveAsset(path: String): WebResourceResponse? {
        return try {
            val isHtml = path.endsWith(".html", ignoreCase = true)
            var data = assets.open("$ASSET_ROOT/$path").use { it.readBytes() }
            if (isHtml) {
                val html = String(data, Charsets.UTF_8)
                val tag = "<script>${WebHomeProtocol.bootstrapScript()}</script>"
                val injected = html.replaceFirst(Regex("<head>", RegexOption.IGNORE_CASE), "<head>$tag")
                data = injected.toByteArray(Charsets.UTF_8)
            }
            WebResourceResponse(mimeOf(path), "UTF-8", ByteArrayInputStream(data))
        } catch (e: Exception) {
            SpiderDebug.log("webhome: asset miss -> $path (${e.message})")
            WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
        }
    }

    private fun mimeOf(path: String): String = when {
        path.endsWith(".html", true) -> "text/html"
        path.endsWith(".js", true) -> "application/javascript"
        path.endsWith(".css", true) -> "text/css"
        path.endsWith(".json", true) -> "application/json"
        path.endsWith(".png", true) -> "image/png"
        path.endsWith(".jpg", true) || path.endsWith(".jpeg", true) -> "image/jpeg"
        path.endsWith(".svg", true) -> "image/svg+xml"
        path.endsWith(".woff2", true) -> "font/woff2"
        else -> "application/octet-stream"
    }

    private fun loadPage() {
        // 走真实 URL，让相对路径的 JS/CSS 正常解析（由 shouldInterceptRequest 提供内容）
        webView.loadUrl(BASE_URL + THEME_NAME)
    }

    private fun injectBootstrapScript() {
        webView.evaluateJavascript(WebHomeProtocol.bootstrapScript(), null)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onStop() {
        super.onStop()
        webView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.destroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
