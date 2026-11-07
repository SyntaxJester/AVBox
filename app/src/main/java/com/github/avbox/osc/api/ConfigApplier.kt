package com.github.avbox.osc.api

import com.github.avbox.osc.ad.audio.AdAudioFingerprintRuntime
import com.github.avbox.osc.bean.ParseBean
import com.github.avbox.osc.net.OkGoHelper
import com.github.avbox.osc.util.AdBlocker
import com.github.avbox.osc.util.DefaultConfig
import com.github.avbox.osc.util.AppLog
import com.github.avbox.osc.util.M3u8
import com.github.avbox.osc.util.VideoParseRuler
import com.google.gson.JsonObject

import java.util.ArrayList

object ConfigApplier {

    @JvmStatic
    fun applyHostRules(infoJson: JsonObject) {
        if (infoJson.has("rules")) {
            VideoParseRuler.clearRule()
            for (oneHostRule in infoJson.getAsJsonArray("rules")) {
                val obj = oneHostRule as JsonObject
                if (obj.has("host")) {
                    val host = obj.get("host").asString
                    if (obj.has("rule")) {
                        val ruleJsonArr = obj.getAsJsonArray("rule")
                        val rule = ArrayList<String>()
                        for (one in ruleJsonArr) {
                            val oneRule = one.asString
                            rule.add(oneRule)
                        }
                        if (rule.size > 0) {
                            VideoParseRuler.addHostRule(host, rule)
                        }
                    }
                    if (obj.has("filter")) {
                        val filterJsonArr = obj.getAsJsonArray("filter")
                        val filter = ArrayList<String>()
                        for (one in filterJsonArr) {
                            val oneFilter = one.asString
                            filter.add(oneFilter)
                        }
                        if (filter.size > 0) {
                            VideoParseRuler.addHostFilter(host, filter)
                        }
                    }
                }
                if (obj.has("hosts") && obj.has("regex")) {
                    val rule = ArrayList<String>()
                    val ads = ArrayList<String>()
                    val regexArray = obj.getAsJsonArray("regex")
                    for (one in regexArray) {
                        val regex = one.asString
                        if (M3u8.isAd(regex)) ads.add(regex) else rule.add(regex)
                    }
                    val array = obj.getAsJsonArray("hosts")
                    for (one in array) {
                        val host = one.asString
                        VideoParseRuler.addHostRule(host, rule)
                        VideoParseRuler.addHostRegex(host, ads)
                    }
                }
                if (obj.has("hosts") && obj.has("script")) {
                    val scripts = ArrayList<String>()
                    val scriptArray = obj.getAsJsonArray("script")
                    for (one in scriptArray) {
                        val script = one.asString
                        scripts.add(script)
                    }
                    val array = obj.getAsJsonArray("hosts")
                    for (one in array) {
                        val host = one.asString
                        VideoParseRuler.addHostScript(host, scripts)
                    }
                }
                if (obj.has("hosts") && obj.has("exclude")
                        && obj.get("hosts").isJsonArray && obj.get("exclude").isJsonArray) {
                    val excludes = ArrayList<String>()
                    for (one in obj.getAsJsonArray("exclude")) {
                        excludes.add(one.asString)
                    }
                    if (!excludes.isEmpty()) {
                        for (one in obj.getAsJsonArray("hosts")) {
                            VideoParseRuler.addHostExclude(one.asString, excludes)
                        }
                    }
                }
            }
        }
    }

    @JvmStatic
    fun applyDoh(infoJson: JsonObject) {
        var dohJson = ""
        if (infoJson.has("doh")) {
            try {
                dohJson = infoJson.getAsJsonArray("doh").toString()
            } catch (e: Exception) {
                AppLog.e("ApiConfig", e)
            }
        }
        OkGoHelper.applyDohConfig(dohJson)
    }

    @JvmStatic
    fun applyAds(infoJson: JsonObject) {
        if (infoJson.has("ads")) {
            for (host in infoJson.getAsJsonArray("ads")) {
                if (!AdBlocker.hasHost(host.asString)) {
                    AdBlocker.addAdHost(host.asString)
                }
            }
        }
        applyAudioFingerprintRules(infoJson)
    }

    /**
     * 解析站点配置中的音频指纹广告规则。
     *
     * 支持两种写法：
     * 1. `adRules` 为对象：{"schemaVersion":2,"algorithm":"spectral-sequence-v2","rules":[...]}
     * 2. `adRules` 为数组：直接是 rules 数组，内部包装成标准结构
     */
    @JvmStatic
    fun applyAudioFingerprintRules(infoJson: JsonObject) {
        try {
            if (!infoJson.has("adRules")) return
            val element = infoJson.get("adRules")
            if (element.isJsonNull) {
                AdAudioFingerprintRuntime.setRules(null)
                return
            }
            val payload: JsonObject = if (element.isJsonObject) {
                element.asJsonObject
            } else if (element.isJsonArray) {
                val wrapper = JsonObject()
                wrapper.addProperty("schemaVersion", 2)
                wrapper.addProperty("algorithm", "spectral-sequence-v2")
                wrapper.add("rules", element.asJsonArray)
                wrapper
            } else {
                return
            }
            if (!payload.has("rules")) return
            AdAudioFingerprintRuntime.setRules(payload.toString())
        } catch (e: Throwable) {
            AppLog.e("ApiConfig", e)
        }
    }

    @JvmStatic
    fun parseParseBeans(infoJson: JsonObject): List<ParseBean> {
        val parseBeans: MutableList<ParseBean> = ArrayList()
        if (infoJson.has("parses")) {
            val parses = infoJson.get("parses").asJsonArray
            for (opt in parses) {
                val obj = opt as JsonObject
                val pb = ParseBean()
                pb.name = obj.get("name").asString.trim { it <= ' ' }
                pb.url = obj.get("url").asString.trim { it <= ' ' }
                val ext = if (obj.has("ext")) obj.get("ext").asJsonObject.toString() else ""
                pb.ext = ext
                pb.type = DefaultConfig.safeJsonInt(obj, "type", 0)
                parseBeans.add(pb)
            }
        }
        return parseBeans
    }

}
