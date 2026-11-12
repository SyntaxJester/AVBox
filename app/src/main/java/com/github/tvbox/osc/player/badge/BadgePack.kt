package com.github.tvbox.osc.player.badge

/** 一个徽章包（订阅项）。 */
data class BadgePack(
    val id: String,
    val name: String,
    /** 来源：https 地址，或 content:// 本地文件，导入后留作"更新"用 */
    val source: String,
) {
    init {
        val n = name
        this.name = if (n == null) "" else n
        val s = source
        this.source = if (s == null) "" else s
    }
}
