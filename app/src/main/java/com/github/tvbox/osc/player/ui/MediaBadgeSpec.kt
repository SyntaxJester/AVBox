package com.github.tvbox.osc.player.ui

import com.github.tvbox.osc.util.HawkConfig

/**
 * 媒体徽章的尺寸规格（纯逻辑，不碰 Android 环境，方便单测）。
 *
 * 行为照 Arcane「媒体徽章 → 徽章大小」：一个 60%~140% 的百分比，
 * 默认 120%（比改动前略大一点，正好对上「徽章太小」这个反馈；100% 就是改动前的原尺寸），
 * 按比例同时放大这几样：
 * 徽章高度、字号、行高、内边距、圆角、徽章之间间距、单个徽章的最大宽度。
 *
 * Arcane 那边的原始数值（scale = sizePercent / 100）：
 * height 26 * scale、maxWidth 112 * scale、cornerRadius 5 * scale、
 * padding 6*scale / 3*scale、fontSize 12*scale、lineHeight 15*scale、间距 8*scale。
 */
object MediaBadgeSpec {

    const val MIN = HawkConfig.MEDIA_BADGES_SIZE_MIN
    const val MAX = HawkConfig.MEDIA_BADGES_SIZE_MAX
    const val DEFAULT = HawkConfig.MEDIA_BADGES_SIZE_DEFAULT
    const val STEP = 10

    // 100% 时的基准尺寸
    const val BASE_HEIGHT_DP = 26f
    const val BASE_MAX_WIDTH_DP = 112f
    const val BASE_CORNER_DP = 5f
    const val BASE_PAD_H_DP = 6f
    const val BASE_PAD_V_DP = 3f
    const val BASE_FONT_SP = 12f
    const val BASE_LINE_HEIGHT_SP = 15f
    const val BASE_GAP_DP = 8f

    /** 空值、0、脏数据一律回落到默认档位，不会让徽章突然消失或变没。 */
    fun normalize(percent: Int): Int =
        if (percent < MIN || percent > MAX) DEFAULT else percent

    fun scale(percent: Int): Float = normalize(percent) / 100f

    fun heightDp(percent: Int): Float = BASE_HEIGHT_DP * scale(percent)

    fun maxWidthDp(percent: Int): Float = BASE_MAX_WIDTH_DP * scale(percent)

    fun cornerDp(percent: Int): Float = BASE_CORNER_DP * scale(percent)

    fun padHorizontalDp(percent: Int): Float = BASE_PAD_H_DP * scale(percent)

    fun padVerticalDp(percent: Int): Float = BASE_PAD_V_DP * scale(percent)

    fun fontSizeSp(percent: Int): Float = BASE_FONT_SP * scale(percent)

    fun lineHeightSp(percent: Int): Float = BASE_LINE_HEIGHT_SP * scale(percent)

    fun gapDp(percent: Int): Float = BASE_GAP_DP * scale(percent)

    /**
     * 一整条徽章放不下时，整条等比缩小（Arcane 的做法是整行统一缩，不换行、不裁切）。
     */
    fun fitScale(contentWidthPx: Int, availableWidthPx: Int): Float = when {
        contentWidthPx <= 0 || availableWidthPx <= 0 -> 1f
        contentWidthPx <= availableWidthPx -> 1f
        else -> availableWidthPx.toFloat() / contentWidthPx.toFloat()
    }
}
