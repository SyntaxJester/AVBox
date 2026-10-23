package com.github.tvbox.osc.web

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.github.tvbox.osc.R
import com.github.tvbox.osc.util.LOG

class WebViewActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "WebViewActivity"
        private const val EXTRA_URL = "url"
        private const val EXTRA_TITLE = "title"

        fun start(context: Context, url: String, title: String? = null) {
            val intent = Intent(context, WebViewActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                if (!title.isNullOrEmpty()) putExtra(EXTRA_TITLE, title)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
            }
            context.startActivity(intent)
        }
    }

    private lateinit var bridge: WebViewBridge
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        val title = intent.getStringExtra(EXTRA_TITLE) ?: url
        supportActionBar?.title = title

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
            settings.setAppCacheEnabled(true)  // deprecated but still effective on many devices

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
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    // 让 WebView 自己处理所有导航，避免重复 loadUrl 导致的循环
                    return false
                }
            }

            loadUrl(url)
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
