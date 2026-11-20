package com.github.tvbox.osc.player.badge

data class MediaBadge(
    val name: String,
    val imageURL: String = "",
    val type: String = TYPE_PACK,
    val group: String = "",
    val technical: Boolean = false,
    val textColor: String = "",
    val tagColor: String = "",
    val borderColor: String = "",
    val style: String = "",
    /** 这条徽章来自哪个订阅，离线图按订阅目录找。 */
    val packId: String = "",
) {
    companion object {
        const val TYPE_PACK = "pack"
        const val TYPE_VIDEO = "video"
        const val TYPE_AUDIO = "audio"
    }
}
