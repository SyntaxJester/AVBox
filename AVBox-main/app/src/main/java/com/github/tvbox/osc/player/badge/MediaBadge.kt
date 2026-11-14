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
) {
    companion object {
        const val TYPE_PACK = "pack"
        const val TYPE_VIDEO = "video"
        const val TYPE_AUDIO = "audio"
    }
}
