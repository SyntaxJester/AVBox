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
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
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
    private lateinit var backBtn: ImageButton
    private lateinit var titleText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorText: TextView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 全屏模式
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

        // 初始化 UI
        backBtn = findViewById(R.id.backBtn)
        titleText = findViewById(R.id.titleText)
        progressBar = findViewById(R.id.progressBar)
        errorText = findViewById(R.id.errorText)

        titleText.text = title

        // 返回按钮
        backBtn.setOnClickListener {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                finish()
            }
        }

        webView = findViewById(R.id.webView)
        bridge = WebViewBridge(this, webView)

        webView.apply {
            // 性能优化：开启硬件加速渲染层
            setLayerType(View.LAYER_TYPE_HARDWARE, null)

            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true

            // 本地文件访问权限（关键：允许 file:// 页面加载同目录资源）
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            @Suppress("DEPRECATION")
            settings.allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            settings.allowUniversalAccessFromFileURLs = true

            // 缓存策略：优先用缓存，加快二次打开
            settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK

            // 视口与缩放
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.setSupportZoom(false)
            settings.builtInZoomControls = false
            settings.displayZoomControls = false

            // 混合内容（http 资源在 https 页面中）
            @Suppress("DEPRECATION")
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

            // 关闭不必要的特性，减少开销
            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            settings.mediaPlaybackRequiresUserGesture = false

            addJavascriptInterface(bridge, "fm")

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    progressBar.visibility = View.VISIBLE
                    errorText.visibility = View.GONE
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
                    errorText.visibility = View.VISIBLE
                    errorText.text = "加载失败：${error?.description}"
                    LOG.e("WebView error: ${error?.description}")
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    // 让 WebView 自己处理所有导航
                    return false
                }
            }

            // 加载 URL（处理中文路径编码）
            val encodedUrl = encodeUrlIfNeeded(url)
            loadUrl(encodedUrl)
        }
    }

    /**
     * 从 URL 中提取标题（文件名）
     */
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

    /**
     * 对 file:// URL 进行编码，防止中文路径乱码
     */
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
