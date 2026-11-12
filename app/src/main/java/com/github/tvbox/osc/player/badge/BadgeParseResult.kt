package com.github.tvbox.osc.player.badge

/** 解析徽章包的结果：规则列表 + 分组数 + 被跳过的条数。 */
data class BadgeParseResult(
    val rules: List<BadgeRule> = emptyList(),
    val groups: Int = 0,
    val skipped: Int = 0,
) {
    val isEmpty: Boolean get() = rules.isEmpty()
}
