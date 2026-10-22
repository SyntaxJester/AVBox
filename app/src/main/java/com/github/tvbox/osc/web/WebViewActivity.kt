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

        val url = intent.getStringExtra(EXTRA_URL) ?: "file:///android_asset/html/index融合.html"
        val title = intent.getStringExtra(EXTRA_TITLE) ?: url
        window.title = title

        webView = findViewById(R.id.webView)
        bridge = WebViewBridge(this, webView)

        webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.loadWithOverviewMode = true
            settings.useWideViewSafeArea = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addJavascriptInterface(bridge, "fm")

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val reqUrl = request?.url?.toString() ?: return false
                    LOG.i(TAG, "shouldOverrideUrlLoading: $reqUrl")
                    if (reqUrl.startsWith("http://") || reqUrl.startsWith("https://")) {
                        view?.loadUrl(reqUrl)
                        return false
                    }
                    if (reqUrl.startsWith("file:///android_asset/")) {
                        view?.loadUrl(reqUrl)
                        return false
                    }
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
