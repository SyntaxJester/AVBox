package com.github.tvbox.osc.web

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.github.tvbox.osc.server.ControlManager
import com.github.tvbox.osc.util.LOG
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

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

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * fm.req(url, opts) => Promise<{ok, status, body, headers, data}>
     */
    @JavascriptInterface
    fun req(url: String?, opts: String?): String {
        val o = JSONObject(opts ?: "{}")
        val method = o.optString("method", "GET").uppercase()
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
                "POST" -> requestBuilder.post(requestBody ?: "".toRequestBody(null)).build()
                "PUT" -> requestBuilder.put(requestBody ?: "".toRequestBody(null)).build()
                "DELETE" -> requestBuilder.delete(requestBody).build()
                else -> requestBuilder.get().build()
            }

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
                        val jsonObj = JSONObject(responseBody)
                        result.put("body", jsonObj)
                        result.put("data", jsonObj)
                    } catch (e: Exception) {
                        result.put("body", responseBody)
                    }
                }
                else -> {
                    val bytes = response.body?.bytes() ?: ByteArray(0)
                    result.put("arraybuffer", Base64.encodeToString(bytes, Base64.NO_WRAP))
                    result.put("body", "")
                }
            }
            result.toString()
        } catch (e: Exception) {
            LOG.e("req failed: ${e.message}")
            val result = JSONObject()
            result.put("ok", false)
            result.put("status", 0)
            result.put("error", e.message ?: "unknown")
            result.put("body", null)
            result.toString()
        }
    }

    /**
     * fm.res(url, opts) - resource request (alias for req)
     */
    @JavascriptInterface
    fun res(url: String?, opts: String?): String = req(url, opts)

    /**
     * fm.play(url, title, opts?) - play media
     */
    @JavascriptInterface
    fun play(url: String?, title: String?, opts: String?): String {
        LOG.i("play url=$url title=$title")
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url ?: ""))
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        mainHandler.post { context.startActivity(intent) }
        return "{\"ok\":true,\"type\":\"play\"}"
    }

    /**
     * fm.open(url) - open URL in browser/app
     */
    @JavascriptInterface
    fun open(url: String?): String {
        LOG.i("open url=$url")
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url ?: ""))
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        mainHandler.post { context.startActivity(intent) }
        return "{\"ok\":true}"
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
     * fm.vodInline(payload) - inline playback
     */
    @JavascriptInterface
    fun vodInline(payload: String?): String {
        LOG.i("vodInline: $payload")
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
        LOG.i("panPlay url=$url title=$title")
        return play(url, title, opts)
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
     * fm.ui.setChrome({ hidden }) - hide/show system UI
     */
    @JavascriptInterface
    fun setChrome(opts: String?): String {
        val o = JSONObject(opts ?: "{}")
        val hidden = o.optBoolean("hidden", false)
        mainHandler.post {
            val activity = context as? android.app.Activity ?: return@post
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
     * fm.ui.restoreChrome() - restore system UI
     */
    @JavascriptInterface
    fun restoreChrome(): String = setChrome("{\"hidden\":false}")

    /**
     * fm.ui.openUrl(url) - open URL in browser
     */
    @JavascriptInterface
    fun openUrl(url: String?): String {
        LOG.i("ui.openUrl: $url")
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url ?: ""))
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        mainHandler.post { context.startActivity(intent) }
        return "{\"ok\":true}"
    }

    /**
     * fm.config() - get config (placeholder)
     */
    @JavascriptInterface
    fun config(): String = "{\"ok\":true,\"type\":\"none\"}"

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
