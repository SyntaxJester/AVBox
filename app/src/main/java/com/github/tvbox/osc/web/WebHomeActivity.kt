package com.github.tvbox.osc.web

import android.app.Activity
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import com.github.tvbox.osc.R
import com.github.catvod.crawler.SpiderDebug

/**
 * WebHome Activity - 加载 eclipse.html HTML 主题页
 * 参考 webhtv 实现，集成 V1 Theme Bridge 协议
 */
class WebHomeActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var bridge: WebHomeBridge

    companion object {
        const val THEME_NAME = "eclipse.html"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 让内容显示在沉浸式状态栏下方
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
        }

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // 页面开始加载时注入引导脚本（必须在任何页面脚本执行前）
                injectBootstrapScript()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                SpiderDebug.log("webhome", "page finished: $url")
                // 页面加载完成后再次注入（防覆盖）
                injectBootstrapScript()
            }
        }

        bridge = WebHomeBridge(this, webView)

        setContentView(webView)

        // 注入桥接对象
        setupBridge()

        // 加载 HTML 页面
        loadPage()
    }

    private fun setupBridge() {
        webView.addJavascriptInterface(bridge, WebHomeProtocol.NATIVE_BRIDGE_NAME)
    }

    private fun loadPage() {
        val url = "file:///android_asset/webhome/$THEME_NAME"
        webView.loadUrl(url)
    }

    /**
     * 注入引导脚本，建立 window.fm 门面
     * 必须在页面任何脚本执行前注入（onPageStarted 时）
     */
    private fun injectBootstrapScript() {
        val script = WebHomeProtocol.bootstrapScript()
        webView.evaluateJavascript(script, null)
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

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}