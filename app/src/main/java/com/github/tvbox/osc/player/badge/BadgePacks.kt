package com.github.tvbox.osc.player.badge

object BadgePacks {

    const val OPTION_XINYUE = 1
    const val OPTION_STREAMBRIDGE = 2
    const val OPTION_XIJING = 3
    const val OPTION_CUSTOM = 4

    const val DEFAULT_OPTION = OPTION_XINYUE

    private const val RAW_XINYUE = "media_badges"
    private const val RAW_STREAMBRIDGE = "streambridge_badges"
    private const val RAW_XIJING = "xijing_club_badges"

    private const val URL_XINYUE = "https://media-badges.app.workbuddy.host/xinyue4capyplayer.json"
    private const val URL_STREAMBRIDGE = "https://streambridge-studio-markers.vercel.app/badges-noframe.json"
    private const val URL_XIJING = "https://raw.githubusercontent.com/zxxlxj/rex-m/main/xijing-club/pppboom-adult.json"

    fun localRaw(option: Int): String? = when (option) {
        OPTION_XINYUE -> RAW_XINYUE
        OPTION_STREAMBRIDGE -> RAW_STREAMBRIDGE
        OPTION_XIJING -> RAW_XIJING
        else -> null
    }

    fun remoteUrl(option: Int): String? = when (option) {
        OPTION_XINYUE -> URL_XINYUE
        OPTION_STREAMBRIDGE -> URL_STREAMBRIDGE
        OPTION_XIJING -> URL_XIJING
        else -> null
    }

    fun fallbackRaw(): String = RAW_XINYUE

    fun cacheFileName(option: Int, customUrl: String): String =
        if (option == OPTION_CUSTOM) {
            "pack_custom_" + Integer.toHexString(customUrl.hashCode())
        } else {
            "pack_" + option
        }
}
