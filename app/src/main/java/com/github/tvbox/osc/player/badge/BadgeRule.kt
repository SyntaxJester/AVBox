package com.github.tvbox.osc.player.badge

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * 一条徽章规则：正则 + 名称 + 样式。
 *
 * 对应原 APK 里的 com.arcane.android.data.BadgeRule，字段语义完全一致：
 *   group       分组 id（同一组只会出现一个徽章）
 *   category    (groupId + " " + 分组名) 的小写形式，用于判断是不是"技术类"规则
 *   name        徽章显示的文字
 *   image       图片地址（空串表示没有图，走文字徽章）
 *   textColor   前景色/文字色（#RRGGBB 或 #RRGGBBAA）
 *   tagColor    背景色
 *   borderColor 边框色
 *   style       样式标记，包含 "filled" 则填色，包含 "bordered" 则描边
 */
class BadgeRule(
    val group: String,
    val category: String,
    val name: String,
    val image: String,
    val textColor: String,
    val tagColor: String,
    val borderColor: String,
    val style: String,
    private val pattern: Pattern?,
) {
    /** 等价于原实现里的 Regex.containsMatchIn(...)，即 Java 的 find()。 */
    fun matches(input: CharSequence): Boolean {
        if (pattern == null) return false
        val m: Matcher = pattern.matcher(input)
        return m.find()
    }

    fun hasImage(): Boolean = image.isNotEmpty()

    override fun toString(): String =
        "BadgeRule{$group/$category $name${if (hasImage()) " [img]" else ""}}"
}
