package com.github.tvbox.osc.player.badge

import java.security.MessageDigest

/**
 * 一条媒体徽章订阅（对应 Arcane 的 BadgePack）。
 *
 * source 只有一种形态："https://..." 远程 JSON 徽章包，规则在线拉取并缓存，
 * 配图可选下载到本地离线目录。
 *
 * 早期版本另有 "raw:<名字>" 这种随包内置的规则包，现已全部去掉：
 * 应用里只预置在线订阅，规则和配图都不占安装包体积。
 */
data class BadgeSubscription(
    val id: String,
    val name: String,
    val source: String,
    val builtin: Boolean = false,
) {
    val isRaw: Boolean get() = source.startsWith(SOURCE_RAW_PREFIX)

    val rawName: String get() = if (isRaw) source.substring(SOURCE_RAW_PREFIX.length) else ""

    val isRemote: Boolean get() = source.startsWith("http://") || source.startsWith("https://")

    companion object {
        const val SOURCE_RAW_PREFIX = "raw:"
    }
}

/**
 * 订阅目录：只有首次使用预置的五条在线徽章包，不再带任何内置规则或内置图。
 *
 * 预置的在线包是社区在用的那几套（心月 / Streambridge / 西京 / Xperience / Nuvio Liquid），
 * 规则在线拉取、配图可整包下载到本地，解析格式是标准的 groups + filters + imageURL。
 * 这五条之外，用户自己添加的订阅照样保存。
 */
object BadgeSubscriptions {

    // 放在最前面：PRESET 初始化时就要用它算 id
    private val HEX = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

    val PRESET: List<BadgeSubscription> = listOf(
        remote("https://media-badges.app.workbuddy.host/xinyue4capyplayer.json", "心月 · xinyue4capyplayer"),
        remote("https://streambridge-studio-markers.vercel.app/badges-noframe.json", "Streambridge · noframe"),
        remote("https://raw.githubusercontent.com/zxxlxj/rex-m/main/xijing-club/pppboom.json", "西京 · pppboom"),
        remote("https://xperience-app.com/badges/ab7a447b-7858-4acf-a192-592a769426ef.json", "Xperience · elite"),
        remote("https://raw.githubusercontent.com/jyotiprakash101/nuvio-badge-kit/main/liquid.json", "Nuvio · Liquid"),
    )

    fun all(): List<BadgeSubscription> = PRESET

    /** 默认选中第一条在线订阅。 */
    fun defaultSelectedId(): String = PRESET.first().id

    /** 订阅 id 由链接推导，同一个链接重复添加会覆盖而不是重复出现。 */
    fun idOf(url: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(url.trim().toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(16)
        sb.append('u')
        for (i in 0 until 7) {
            val v = digest[i].toInt() and 0xFF
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    private fun remote(url: String, name: String) = BadgeSubscription(idOf(url), name, url)

}
