package com.github.tvbox.osc.player.badge

import java.util.regex.Pattern

/**
 * 媒体信息匹配器：根据标题/描述文本匹配徽章规则。
 *
 * 对应原实现里的 BadgeMatcher，支持：
 *   - 按规则正则匹配（规则优先于分组）
 *   - 同一组只保留第一个匹配的规则
 *   - 支持忽略大小写
 */
class BadgeMatcher(private val rules: List<BadgeRule>) {

    private val rulesByGroup: Map<String, List<BadgeRule>> = rules.groupBy { it.group }

    /**
     * 匹配给定文本中的徽章。
     *
     * @param input 待匹配文本（如视频标题、描述、媒体信息）
     * @return 匹配到的徽章列表（同一组最多一个）
     */
    fun match(input: String?): List<BadgeRule> {
        if (input == null || input.isEmpty() || rules.isEmpty()) return emptyList()

        val matched = mutableListOf<BadgeRule>()
        val matchedGroups = mutableSetOf<String>()

        // 先按规则顺序匹配，同一组只取第一个
        for (rule in rules) {
            if (rule.matches(input)) {
                if (rule.group.isEmpty() || matchedGroups.contains(rule.group)) {
                    matched.add(rule)
                } else {
                    matchedGroups.add(rule.group)
                    matched.add(rule)
                }
            }
        }

        return matched.toList()
    }

    /**
     * 匹配多个文本源并合并结果。
     */
    fun matchAll(vararg inputs: String?): List<BadgeRule> {
        val all = mutableListOf<String>()
        for (input in inputs) {
            if (input.isNotEmpty()) all.add(input)
        }
        return match(all.joinToString(" "))
    }

    companion object {
        /**
         * 从规则列表创建匹配器，过滤掉没有 pattern 的规则。
         */
        fun create(rules: List<BadgeRule>): BadgeMatcher {
            val validRules = rules.filter { it.pattern != null }
            return BadgeMatcher(validRules)
        }
    }
}
