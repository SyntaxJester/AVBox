package com.github.tvbox.osc.web

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.annotation.Keep
import com.github.tvbox.osc.server.ControlManager
import com.github.tvbox.osc.util.LOG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

@Keep
class WebViewBridge(
    private val context: Context,
    private val webView: WebView
) {
    companion object {
        private const val TAG = "WebViewBridge"
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * fm.req(url, opts) => Promise<{ok, status, body, headers, data}>
     */
    @JavascriptInterface
    fun req(url: String?, opts: String?): String {
        val o = JSONObject(opts ?: "{}")
        val method = o.optString("method", "GET").uppercase()
        val timeoutSec = o.optLong("timeout", 15L)
        val responseType = o.optString("responseType", "json")
        val headers = o.optJSONObject("headers") ?: JSONObject()
        val body = o.optString("body", "")

        return try {
            val requestBuilder = Request.Builder()
                .url(resolveUrl(url ?: ""))
                .header("User-Agent", "Mozilla/5.0 (Android 13; Mobile) AppleWebKit/537.36")
            headers.keys().forEach { key ->
                requestBuilder.addHeader(key, headers.getString(key))
            }

            val requestBody = if (body.isNotEmpty()) body.toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull()) else null
            val request = when (method) {
                "POST" -> requestBuilder.post(requestBody ?: "".toRequestBody(null))
                "PUT" -> requestBuilder.put(requestBody ?: "".toRequestBody(null))
                "DELETE" -> requestBuilder.delete(requestBody)
                else -> requestBuilder.get()
            }
            request.build()

            val response = client.newCall(request).execute()
            val ok = response.code in 200..299
            val status = response.code
            val respHeaders = response.headers

            val result = JSONObject()
            result.put("ok", ok)
            result.put("status", status)
            result.put("responseType", responseType)
            result.put("headers", JSONObject().also {
                for (i in 0 until respHeaders.size()) {
                    it.put(respHeaders.name(i), respHeaders.value(i))
                }
            })

            if (!ok) {
                result.put("body", null)
                result.put("error", "HTTP $status")
                return result.toString()
            }

            val responseBody = response.body?.string() ?: ""
            
            when (responseType) {
                "text" -> result.put("body", responseBody)
                "json" -> {
                    try {
                        result.put("body", JSONObject(responseBody))
                        result.put("data", JSONObject(responseBody))
                    } catch (e: Exception) {
                        result.put("body", responseBody)
                    }
                }
                else -> {
                    // arraybuffer - 返回 base64
                    val bytes = response.body?.bytes() ?: ByteArray(0)
                    result.put("arraybuffer", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
                    result.put("body", "")
                }
            }
            result.toString()
        } catch (e: Exception) {
            LOG.e(TAG, "req failed: ${e.message}")
            val result = JSONObject()
            result.put("ok", false)
            result.put("status", 0)
            result.put("error", e.message ?: "unknown")
            result.put("body", null)
            result.toString()
        }
    }

    /**
     * fm.res(url, opts) - 资源请求（同 req）
     */
    @JavascriptInterface
    fun res(url: String?, opts: String?): String = req(url, opts)

    /**
     * fm.play(url, title, opts?) - 播放媒体
     */
    @JavascriptInterface
    fun play(url: String?, title: String?, opts: String?): String {
        LOG.i(TAG, "play url=$url title=$title")
        CoroutineScope(Dispatchers.Main).launch {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url ?: ""))
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
        return "{\"ok\":true,\"type\":\"play\"}"
    }

    /**
     * fm.open(url) - 打开 URL
     */
    @JavascriptInterface
    fun open(url: String?): String {
        LOG.i(TAG, "open url=$url")
        CoroutineScope(Dispatchers.Main).launch {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url ?: ""))
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
        return "{\"ok\":true}"
    }

    /**
     * fm.ui.getViewport() => {width, height}
     */
    @JavascriptInterface
    fun getViewport(): String {
        val rect = android.graphics.Rect()
        webView.getWindowVisibleDisplayFrame(rect)
        val result = JSONObject()
        result.put("width", rect.width())
        result.put("height", rect.height())
        return result.toString()
    }

    /**
     * fm.ui.setChrome({ hidden }) - 隐藏系统 UI
     */
    @JavascriptInterface
    fun setChrome(opts: String?): String {
        val o = JSONObject(opts ?: "{}")
        val hidden = o.optBoolean("hidden", false)
        CoroutineScope(Dispatchers.Main).launch {
            val activity = context as? android.app.Activity ?: return@launch
            val window = activity.window
            if (hidden) {
                window.decorView.systemUiVisibility = (
                    android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                        or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
            } else {
                window.decorView.systemUiVisibility = 0
            }
        }
        return "{\"ok\":true}"
    }

    /**
     * fm.ui.restoreChrome() - 恢复系统 UI
     */
    @JavascriptInterface
    fun restoreChrome(): String = setChrome("{\"hidden\":false}")

    /**
     * fm.config() - 获取配置
     */
    @JavascriptInterface
    fun config(): String {
        return "{\"ok\":true,\"type\":\"none\"}"
    }

    /**
     * fm.pan.check([{type,url,password}]) => [{ok,url,valid}]
     */
    @JavascriptInterface
    fun panCheck(opts: String?): String {
        val arr = JSONArray(opts ?: "[]")
        val result = JSONArray()
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            val check = JSONObject()
            check.put("ok", true)
            check.put("url", item.optString("url", ""))
            check.put("valid", true)
            result.put(check)
        }
        return result.toString()
    }

    /**
     * fm.vodInline(payload) - 内嵌播放（占位）
     */
    @JavascriptInterface
    fun vodInline(payload: String?): String {
        LOG.i(TAG, "vodInline: $payload")
        return "{\"ok\":true}"
    }

    /**
     * fm.pan.play({type,url,password,title,name,...})
     */
    @JavascriptInterface
    fun panPlay(opts: String?): String {
        val o = JSONObject(opts ?: "{}")
        val url = o.optString("url", "")
        val title = o.optString("title", o.optString("name", ""))
        LOG.i(TAG, "panPlay url=$url title=$title")
        return play(url, title, opts)
    }

    private fun resolveUrl(url: String): String {
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        if (url.startsWith("file:///")) return url
        if (url.startsWith("//")) return "https:$url"
        if (url.startsWith("./") || url.startsWith("/")) {
            val localHost = ControlManager.get().getAddress(true)
            val path = url.trimStart('/')
            return "$localHost/html/$path"
        }
        return url
    }
}
