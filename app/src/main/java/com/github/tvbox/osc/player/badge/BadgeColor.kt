package com.github.tvbox.osc.player.badge

/**
 * 颜色解析，跟原实现一致：
 *   #RRGGBB    -> 不透明
 *   #RRGGBBAA  -> 最后两位是 alpha（CSS 习惯），内部转成 ARGB
 * 解析失败返回 null，调用方用默认色兜底。
 */
object BadgeColor {

    /** 默认文字色（深色界面上用白色） */
    val DEFAULT_TEXT_LIGHT_BG: Int = java.lang.Long.parseLong("FF111111", 16).toInt()
    /** 默认文字色（亮色界面上用黑色） */
    val DEFAULT_TEXT_DARK_BG: Int = java.lang.Long.parseLong("FFFFFFFF", 16).toInt()
    /** 默认背景色 */
    val DEFAULT_BG: Int = java.lang.Long.parseLong("FF333333", 16).toInt()
    /** 默认边框色 */
    val DEFAULT_BORDER: Int = java.lang.Long.parseLong("FF888888", 16).toInt()

    fun parse(value: String?): Int? {
        if (value == null) return null
        val s = value
        if (!s.startsWith("#")) return null
        val len = s.length
        if (len != 7 && len != 9) return null
        return try {
            val str: String = if (len == 9) {
                val alpha = s.substring(7, 9)
                val rgb = s.substring(1, 7)
                "#" + alpha + rgb
            } else {
                s
            }
            val hex = str.substring(1)
            val v: Long = java.lang.Long.parseLong(hex, 16)
            val result: Int = if (len == 9) {
                v.toInt()
            } else {
                val combined: Long = 0xFF000000L or v
                combined.toInt()
            }
            result
        } catch (e: Exception) {
            null
        }
    }

    fun text(value: String?, lightTheme: Boolean): Int {
        val c: Int? = parse(value)
        if (c != null) return c
        return if (lightTheme) DEFAULT_TEXT_LIGHT_BG else DEFAULT_TEXT_DARK_BG
    }

    fun background(value: String?): Int {
        val c: Int? = parse(value)
        return if (c != null) c else DEFAULT_BG
    }

    fun border(value: String?): Int {
        val c: Int? = parse(value)
        return if (c != null) c else DEFAULT_BORDER
    }
}
