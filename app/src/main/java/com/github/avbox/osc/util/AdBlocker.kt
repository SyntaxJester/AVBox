package com.github.avbox.osc.util

import android.webkit.WebResourceResponse

import java.io.ByteArrayInputStream
import java.nio.charset.Charset

object AdBlocker {
    private val AD_HOSTS = ArrayList<String>()

    /**
     * 去广告总开关。关闭后 [isAd] 一律返回 false，WebView 嗅探不再拦截广告请求。
     */
    private var enabled: Boolean = KV.get(HawkConfig.AD_BLOCK_ENABLED, true)

    @JvmStatic
    fun setEnabled(value: Boolean) {
        enabled = value
        KV.put(HawkConfig.AD_BLOCK_ENABLED, value)
    }

    @JvmStatic
    fun isEnabled(): Boolean = enabled

    @JvmStatic
    fun clear() {
        AD_HOSTS.clear()
    }

    @JvmStatic
    fun isEmpty(): Boolean {
        return AD_HOSTS.isEmpty()
    }

    @JvmStatic
    fun addAdHost(host: String) {
        AD_HOSTS.add(host)
    }

    @JvmStatic
    fun hasHost(host: String): Boolean {
        return AD_HOSTS.contains(host)
    }

    @JvmStatic
    fun isAd(url: String): Boolean {
        if (!enabled) return false
        val lower = url.lowercase()
        for (adHost in AD_HOSTS) {
            if (lower.contains(adHost)) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun createEmptyResource(): WebResourceResponse {
        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray(Charset.defaultCharset())))
    }

}
