package com.github.tvbox.osc.web

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.github.tvbox.osc.R
import com.github.tvbox.osc.util.LOG
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class WebViewActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "WebViewActivity"
        private const val EXTRA_URL = "url"
        private const val EXTRA_TITLE = "title"

        fun start(context: Context, url: String, title: String? = null) {
            val intent = Intent(context, WebViewActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                if (!title.isNullOrEmpty()) putExtra(EXTRA_TITLE, title)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        }
    }

    private lateinit var bridge: WebViewBridge
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 全屏模式：隐藏状态栏和导航栏，滑动显示
        supportActionBar?.hide()
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(android.view.WindowInsets.Type.systemBars())
            controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContentView(R.layout.activity_web_home)

        val url = intent.getStringExtra(EXTRA_URL) ?: ""
        val title = intent.getStringExtra(EXTRA_TITLE) ?: extractTitleFromUrl(url)

        progressBar = findViewById(R.id.progressBar)
        webView = findViewById(R.id.webView)
        bridge = WebViewBridge(this, webView)

        webView.apply {
            // 1. 硬件加速渲染层 —— 关键性能项
            setLayerType(View.LAYER_TYPE_HARDWARE, null)

            // 2. 基础设置
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true

            // 3. 关键：允许 file:// 页面加载同目录资源（JS/CSS/图片）
            @Suppress("DEPRECATION")
            settings.allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            settings.allowUniversalAccessFromFileURLs = true

            // 4. 缓存策略：优先用缓存，极大加快二次打开
            settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK

            // 5. 视口：自适应屏幕，禁用缩放减少开销
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.setSupportZoom(false)
            settings.builtInZoomControls = false
            settings.displayZoomControls = false

            // 6. 混合内容支持（http 资源在 https 页面中）
            @Suppress("DEPRECATION")
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

            // 7. 关闭无用特性减少开销
            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            settings.mediaPlaybackRequiresUserGesture = false
            settings.renderPriority = WebSettings.RenderPriority.HIGH
            settings.enableSmoothTransition = true

            // 8. JS Bridge
            addJavascriptInterface(bridge, "fm")

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    progressBar.visibility = View.VISIBLE
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    progressBar.visibility = View.GONE
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: android.webkit.WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    progressBar.visibility = View.GONE
                    LOG.e("WebView error: ${error?.description}")
                }

                // 让 WebView 自己处理所有导航，避免重复 loadUrl
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    return false
                }
            }

            // 9. 加载 URL（处理中文路径编码）
            loadUrl(encodeUrlIfNeeded(url))
        }
    }

    /** 从 URL 提取标题（文件名） */
    private fun extractTitleFromUrl(url: String): String {
        return try {
            val uri = Uri.parse(url)
            val path = uri.path ?: url
            val fileName = path.substringAfterLast('/')
            Uri.decode(fileName).takeIf { it.isNotBlank() } ?: url
        } catch (e: Exception) {
            url
        }
    }

    /** 对 file:// URL 进行编码，防止中文路径乱码 */
    private fun encodeUrlIfNeeded(url: String): String {
        if (!url.startsWith("file://")) return url
        try {
            val basePath = "file://"
            val path = url.substringAfter(basePath)
            val encodedPath = URLEncoder.encode(path, StandardCharsets.UTF_8.name())
                .replace("%2F", "/") // 保留斜杠
                .replace("%3A", ":") // 保留冒号
            return "$basePath$encodedPath"
        } catch (e: Exception) {
            LOG.e("Failed to encode URL: ${e.message}")
            return url
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}