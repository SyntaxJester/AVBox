package com.github.tvbox.osc.player.ui

import com.github.tvbox.osc.util.HawkConfig

/**
 * 播放器片名标识的纯逻辑部分（不碰 Android 环境，方便单测）。
 *
 * 行为照 Arcane「外观与设置 → 播放器片名标识」：位置四选一 + 关闭，
 * 并且与 TMDB 设置里的「使用 TMDB 片名图标」联动——后者关闭时这里也不显示。
 */
object TitleLogoSpec {

    const val POS_LEFT_BOTTOM = "左下"
    const val POS_RIGHT_BOTTOM = "右下"
    const val POS_LEFT_TOP = "左上"
    const val POS_RIGHT_TOP = "右上"
    const val POS_OFF = "关闭"

    val POSITIONS: List<String> = listOf(
        POS_LEFT_BOTTOM,
        POS_RIGHT_BOTTOM,
        POS_LEFT_TOP,
        POS_RIGHT_TOP,
        POS_OFF,
    )

    /** 旧配置、手改配置或空值一律回落到默认位置，避免整块标识消失得莫名其妙。 */
    fun normalize(value: String?): String =
        if (value != null && POSITIONS.contains(value)) value else HawkConfig.PLAYER_TITLE_LOGO_POSITION_DEFAULT

    fun isOff(position: String): Boolean = normalize(position) == POS_OFF

    fun isTop(position: String): Boolean {
        val p = normalize(position)
        return p == POS_LEFT_TOP || p == POS_RIGHT_TOP
    }

    fun isStart(position: String): Boolean {
        val p = normalize(position)
        return p == POS_LEFT_TOP || p == POS_LEFT_BOTTOM
    }

    /**
     * 要不要显示：位置不是「关闭」，且「使用 TMDB 片名图标」开着。
     * 这两者是联动的——用户在 TMDB 页关掉片名图标，播放器上的片名标识也一起收起来。
     */
    fun shouldShow(position: String, titleArtEnabled: Boolean): Boolean =
        !isOff(position) && titleArtEnabled

    /**
     * 「始终显示」关闭时只在控制条出现时亮出来（Arcane 的 logoAlways 就是这个意思）。
     */
    fun visibleNow(position: String, titleArtEnabled: Boolean, always: Boolean, controlsVisible: Boolean): Boolean =
        shouldShow(position, titleArtEnabled) && (always || controlsVisible)
}
