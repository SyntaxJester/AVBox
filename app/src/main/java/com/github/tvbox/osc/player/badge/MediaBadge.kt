package com.github.tvbox.osc.player.badge

/**
 * 单个媒体徽章的展示数据。
 *
 * @param name     徽章文字（如 "4K Ultra HD"、"HDR10"）
 * @param imageURL 徽章图片地址（SVG/PNG），为空时回退为纯文字
 * @param type     来源类型：pack=徽章包匹配，video=视频参数探测，audio=音轨参数探测
 */
data class MediaBadge(
    val name: String,
    val imageURL: String = "",
    val type: String = TYPE_PACK,
) {
    companion object {
        const val TYPE_PACK = "pack"
        const val TYPE_VIDEO = "video"
        const val TYPE_AUDIO = "audio"
    }
}
