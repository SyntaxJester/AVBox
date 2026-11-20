package com.github.tvbox.osc.player.badge

/**
 * 离线图片下载进度（对应 Arcane 的 BadgeDownload）。
 *
 * processed / total 是整包图片的进度；failures 是本次失败张数；
 * remaining 留着让用户「继续下载」，下次接着没下完的那些来。
 */
data class BadgeDownloadState(
    val packId: String = "",
    val refresh: Boolean = false,
    val processed: Int = 0,
    val total: Int = 0,
    val failures: Int = 0,
    val running: Boolean = false,
    val remaining: List<String> = emptyList(),
) {
    val percent: Int get() = if (total <= 0) 0 else (processed * 100 / total)
}

data class BadgePackState(
    val version: Int = 0,
    val rules: List<BadgeRule> = emptyList(),
    val packName: String = "",
    val source: String = "",
    val loading: Boolean = false,
    val failed: Boolean = false,
    /** 当前订阅列表（预置的五条在线订阅 + 用户自己添加的）。 */
    val subscriptions: List<BadgeSubscription> = emptyList(),
    /** 当前选中的订阅 id。 */
    val selectedId: String = "",
    /** 当前订阅已下载的离线图片数 / 总图片数。 */
    val offlineCount: Int = 0,
    val offlineTotal: Int = 0,
    val download: BadgeDownloadState? = null,
    val error: String = "",
) {
    val isEmpty: Boolean get() = rules.isEmpty()

    fun selected(): BadgeSubscription? = subscriptions.firstOrNull { it.id == selectedId }
}
