package com.github.tvbox.osc.player.badge

data class BadgeParseResult(
    val rules: List<BadgeRule> = emptyList(),
    val groups: Int = 0,
    val skipped: Int = 0,
    val name: String = "",
) {
    val isEmpty: Boolean get() = rules.isEmpty()
}
