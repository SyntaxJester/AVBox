package com.github.tvbox.osc.player.badge

data class BadgePackState(
    val version: Int = 0,
    val rules: List<BadgeRule> = emptyList(),
    val packName: String = "",
    val source: String = "",
    val loading: Boolean = false,
    val failed: Boolean = false,
) {
    val isEmpty: Boolean get() = rules.isEmpty()
}
