package com.github.tvbox.osc.player.badge

import java.util.regex.Pattern

/**
 * 徽章匹配器 —— 原 APK 里 MediaBadgeStore.matches() 的等价实现。
 *
 * 规则要点（一条都别改，改了效果就不一样）：
 *   1. 整个匹配过程共用 45 毫秒预算，超时就停手，避免恶意正则把界面拖死。
 *   2. 同一分组（group）只出一个徽章，先命中的拿走。
 *   3. 结果最多 8 个。
 *   4. 参与匹配的文本最长只取前 1024 个字符。
 *   5. 「技术类」规则（分辨率 / 动态范围 / 视频编码这些组）优先只用 technical 字段匹配，
 *      其它规则用 "片名 + 技术信息" 拼起来匹配。
 *   6. 没有图片的规则按名字去重，重名的只留第一个。
 */
object BadgeMatcher {

    /** 技术类分组的特征词，命中这些的组只拿 technical 去匹配。 */
    private val TECH_WORDS = listOf(
        "分辨率", "resolution", "动态范围", "video-tech", "视频编码", "video-codec"
    )

    const val MAX_RESULTS = 8
    const val MAX_INPUT = 1024
    const val BUDGET_NANOS = 45L * 1000 * 1000   // 45ms

    /**
     * 匹配给定文本中的徽章。
     *
     * @param source 源文本（如片名）
     * @param technical 技术信息（如 "4K HDR"）
     * @param rules 规则列表
     * @return 匹配到的徽章列表
     */
    fun matches(source: String?, technical: String?, rules: List<BadgeRule>): List<BadgeRule> {
        if (rules.isEmpty()) return emptyList()
        val src = source ?: ""
        val tech = technical ?: ""
        val hasTech = tech.trim().isNotEmpty()

        val usedGroups = mutableSetOf<String>()
        val usedNames = mutableSetOf<String>()
        val out = mutableListOf<BadgeRule>()

        val deadline = System.nanoTime() + BUDGET_NANOS

        for (rule in rules) {
            if (System.nanoTime() > deadline) break
            if (usedGroups.contains(rule.group)) continue

            val text: String = if (isTech(rule) && hasTech) {
                tech
            } else {
                src + " " + tech
            }
            val truncated = if (text.length > MAX_INPUT) text.substring(0, MAX_INPUT) else text

            val hit: Boolean = try {
                rule.matches(Guard(truncated, deadline))
            } catch (t: Throwable) {
                false   // 匹配超时 / 正则异常一律当作不命中
            }
            if (!hit) continue

            usedGroups.add(rule.group)

            if (!rule.hasImage()) {
                // 文字徽章按名字去重；重名的那条直接丢掉（但组已经被占用，跟原实现一致）
                if (!usedNames.add(rule.name)) continue
            }

            out.add(rule)
            if (out.size >= MAX_RESULTS) break
        }
        return out
    }

    private fun isTech(rule: BadgeRule): Boolean {
        for (w in TECH_WORDS) {
            if (rule.category.contains(w)) return true
        }
        return false
    }

    /**
     * 包一层 CharSequence：正则引擎每取 256 个字符检查一次时间，
     * 超时直接抛异常中断灾难性回溯。原实现就是这么干的。
     */
    private class Guard(private val value: String, private val deadline: Long) : CharSequence {

        private var calls = 0

        override val length: Int
            get() = value.length

        override fun get(index: Int): Char {
            calls++
            if ((calls and 0xFF) == 0 && System.nanoTime() > deadline) {
                throw IllegalStateException("徽章表达式匹配超时")
            }
            return value[index]
        }

        override fun subSequence(start: Int, end: Int): CharSequence {
            return value.subSequence(start, end)
        }

        override fun toString(): String {
            return value
        }
    }
}
