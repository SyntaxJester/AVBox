package com.github.tvbox.osc.web

import android.content.Intent
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.View
import android.view.WindowInsets
import androidx.activity.viewModels
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.base.BaseActivity
import com.github.tvbox.osc.util.LOG
import org.json.JSONObject

/**
 * WebView 载体，用于展示 webhome 主题皮肤（如 eclipse.html）
 *
 * 功能：
 *   - 加载本地 assets/webhome/ 下的 HTML
 *   - 注入 WebHomeBridge（桥接 Java ↔ JS）
 *   - 处理回退/导航
 */
class WebHomeActivity : BaseActivity() {

    companion object {
        private const val TAG = "WebHomeActivity"
        private const val PAGE_INDEX = "index.html"
        private const val PAGE_ECLIPSE = "webhome/eclipse.html"
    }

    private lateinit var webView: WebView
    private val apiConfig by lazy { ApiConfig.get() }

    override fun onCreate(savedInstanceState: Bundle?) {
        supportRequestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_home)

        setupWebView()
    }

    override fun init() {
        // No-op for webhome activity
    }

    private fun setupWebView() {
        webView = findViewById(R.id.webview)
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.databaseEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        // 注入 Bridge
        val bridge = WebHomeBridge(apiConfig)
        webView.addJavascriptInterface(bridge, WebHomeBridge.BRIDGE_NAME)

        // 注入 JS Promise 包装器（让同步 bridge 方法支持 .then()）
        injectPromisedBridge()

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                LOG.i("page finished: $url")
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    android.content.Intent(Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))).also {
                        startActivity(it)
                    }
                    return true
                }
                return false
            }
        }

        // 加载 eclipse 主题首页
        webView.loadUrl("file:///android_asset/webhome/eclipse.html")
    }

    /**
     * 注入 JS 代码，将同步 bridge 方法包装成 Promise，支持 .then() / .catch()
     */
    private fun injectPromisedBridge() {
        val js = """
            (function() {
                if (typeof window.__fmPromised !== 'undefined') return;
                window.__fmPromised = true;
                
                // 获取原生 bridge 对象
                var nativeFm = window.fm;
                if (!nativeFm) { console.error('Bridge fm not found'); return; }
                
                // 代理函数，返回 Promise
                function proxyMethod(name) {
                    return function() {
                        return new Promise(function(resolve, reject) {
                            try {
                                var args = Array.prototype.slice.call(arguments);
                                // 构造参数字符串
                                var paramStr;
                                if (args.length === 0) {
                                    paramStr = '{}';
                                } else if (typeof args[0] === 'string') {
                                    paramStr = args[0];
                                } else {
                                    paramStr = JSON.stringify(args[0] || {});
                                }
                                var result;
                                if (args.length === 1 && typeof args[0] === 'string') {
                                    // 单个字符串参数（如 toast, back, res）
                                    result = nativeFm[name](paramStr);
                                } else if (args.length >= 2 && typeof args[0] === 'string' && typeof args[1] === 'string') {
                                    // 多字符串参数（如 vod(tid, page, params)）
                                    result = nativeFm[name].apply(nativeFm, args);
                                } else {
                                    result = nativeFm[name](paramStr);
                                }
                                if (result && result !== 'null' && result !== 'undefined') {
                                    var parsed = JSON.parse(result);
                                    if (parsed.code === 1 || parsed.code === true) {
                                        resolve(parsed.data !== undefined ? parsed.data : parsed);
                                    } else {
                                        reject(parsed.msg || parsed.error || 'unknown error');
                                    }
                                } else {
                                    resolve(null);
                                }
                            } catch (e) {
                                reject(e.message || String(e));
                            }
                        });
                    };
                }
                
                // 包装所有桥接方法
                var methods = ['vodHome','vodCategory','vod','search','play','res','toast',
                               'ui_getViewport','openDetail','back','reload'];
                var promisedFm = Object.create(nativeFm);
                methods.forEach(function(m) {
                    if (typeof nativeFm[m] === 'function') {
                        promisedFm[m] = proxyMethod(m);
                    }
                });
                
                // 同时暴露为 window.fm（保持兼容性）
                Object.assign(window.fm, promisedFm);
                console.log('WebHomeBridge Promise wrapper injected');
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
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