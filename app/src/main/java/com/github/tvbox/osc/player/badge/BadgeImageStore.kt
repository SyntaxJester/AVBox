package com.github.tvbox.osc.player.badge

import android.content.Context

import java.io.File
import java.security.MessageDigest

/**
 * 徽章配图的本地仓库：只负责订阅下载下来的离线图。
 *
 * 早先版本把 3206 张徽章图直接打进 APK（17 MB）。现在订阅本身能拉规则、
 * 能把整包配图下载到本地，随包带图就纯属浪费安装包体积，所以那批图已经清掉；
 * 播放时按这个顺序取图：本地离线图 → 订阅里的网络地址（GitHub raw 走 jsDelivr 镜像）→ 文字徽章。
 *
 * 本地布局：files/badge_packs/<订阅id>-images/<URL 的 SHA-256 前 30 字节>.[svg|img]
 */
object BadgeImageStore {

    private const val OFFLINE_DIR = "badge_packs"

    private val HEX = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

    /** 某个订阅的离线图目录：files/badge_packs/<订阅id>-images */
    fun offlineDir(context: Context, packId: String): File =
        File(File(context.filesDir, OFFLINE_DIR), packId + "-images")

    /** 离线文件名 = URL 的 SHA-256 前 30 字节；svg 保留后缀交给 Coil 解码。 */
    fun offlineName(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.trim().toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(64)
        for (i in 0 until 30) {
            val v = digest[i].toInt() and 0xFF
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        val path = url.substringBefore('?')
        return sb.append(if (path.endsWith(".svg", true)) ".svg" else ".img").toString()
    }

    fun offlineFile(context: Context, packId: String, url: String): File? {
        if (url.isBlank() || packId.isBlank()) return null
        val file = File(offlineDir(context, packId), offlineName(url))
        return if (file.isFile && file.length() > 0L) file else null
    }

    fun writeOffline(context: Context, packId: String, url: String, bytes: ByteArray) {
        val dir = offlineDir(context, packId)
        if (!dir.exists()) dir.mkdirs()
        val target = File(dir, offlineName(url))
        val tmp = File(dir, target.name + ".tmp")
        tmp.writeBytes(bytes)
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            target.writeBytes(bytes)
            tmp.delete()
        }
    }

    fun clearOffline(context: Context, packId: String) {
        val dir = offlineDir(context, packId)
        if (dir.exists()) dir.deleteRecursively()
    }

    /** 网络地址的备选镜像：GitHub raw 走 jsDelivr，国内命中率高一些。 */
    fun mirrorCandidates(url: String): List<String> {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return emptyList()
        val out = ArrayList<String>(2)
        out.add(trimmed)
        val prefix = "https://raw.githubusercontent.com/"
        if (trimmed.startsWith(prefix)) {
            val parts = trimmed.substring(prefix.length).split("/", limit = 4)
            if (parts.size == 4) {
                out.add(
                    "https://cdn.jsdelivr.net/gh/" + parts[0] + "/" + parts[1] + "@" +
                        parts[2] + "/" + parts[3]
                )
            }
        }
        return out
    }
}
