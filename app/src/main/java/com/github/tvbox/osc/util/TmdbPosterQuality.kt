package com.github.tvbox.osc.util

/**
 * TMDB 封面取图的清晰度档位。
 *
 * [sizeToken] 是 TMDB 图片服务 URL 里的尺寸段，形如
 * `https://image.tmdb.org/t/p/<sizeToken><path>`。
 * `null` 表示「自动」：跟随调用场景（列表缩略图 w342 / 详情大图 w780 / 全屏 original），
 * 与未加该设置时的行为完全一致。
 */
enum class TmdbPosterQuality(val sizeToken: String?) {
    Auto(null),
    Standard("w342"),
    High("w500"),
    Ultra("w780"),
    Original("original"),
    ;

    /** 「自动」时不干预调用方选择的尺寸。 */
    val isAuto: Boolean get() = sizeToken == null

    companion object {
        fun of(value: Int): TmdbPosterQuality = entries.getOrElse(value) { Auto }
    }
}
