package com.github.tvbox.osc.player.badge

import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * 解析徽章包 JSON 字符串。
 *
 * JSON 格式示例：
 * {
 *   "id": "xinyue4capyplayer",
 *   "name": "徽章包 1 (xinyue4capyplayer)",
 *   "groups": [
 *     {
 *       "id": "resolution",
 *       "name": "分辨率",
 *       "rules": [
 *         {
 *           "id": "r-8k",
 *           "pattern": "(?i)\\b(8k|4320[pi]?)\\b",
 *           "name": "8K",
 *           "image": "https://...",
 *           "textColor": "#FFFFFF",
 *           "tagColor": "#FF333333",
 *           "borderColor": "#FF888888",
 *           "style": "filled"
 *         }
 *       ]
 *     }
 *   ]
 * }
 */
object BadgePackParser {

    private fun cleanJson(text: String): String {
        return text.trim()
    }

    /**
     * 解析徽章包 JSON，返回规则列表。
     * 任何解析错误都会返回空列表，不会抛出异常。
     */
    fun parse(text: String): BadgeParseResult {
        val json = cleanJson(text)
        if (json.isEmpty()) {
            return BadgeParseResult(rules = emptyList(), groups = 0, skipped = 0)
        }

        val rules = mutableListOf<BadgeRule>()
        var groups = 0
        var skipped = 0

        try {
            val obj = org.json.JSONObject(json)
            val packId = obj.optString("id", "")
            val packName = obj.optString("name", "")

            val groupsArray = obj.optJSONArray("groups")
            if (groupsArray != null) {
                for (i in 0 until groupsArray.length()) {
                    val groupObj = groupsArray.getJSONObject(i)
                    val groupId = groupObj.optString("id", "")
                    val groupName = groupObj.optString("name", "")
                    groups++

                    val rulesArray = groupObj.optJSONArray("rules")
                    if (rulesArray != null) {
                        for (j in 0 until rulesArray.length()) {
                            try {
                                val ruleObj = rulesArray.getJSONObject(j)
                                val ruleId = ruleObj.optString("id", "")
                                val patternStr = ruleObj.optString("pattern", "")
                                val name = ruleObj.optString("name", "")
                                val image = ruleObj.optString("image", "")
                                val textColor = ruleObj.optString("textColor", "")
                                val tagColor = ruleObj.optString("tagColor", "")
                                val borderColor = ruleObj.optString("borderColor", "")
                                val style = ruleObj.optString("style", "")

                                val pattern = if (patternStr.isNotEmpty()) {
                                    try {
                                        Pattern.compile(patternStr)
                                    } catch (_: PatternSyntaxException) {
                                        null
                                    }
                                } else {
                                    null
                                }

                                val category = "$groupId $groupName".lowercase()
                                rules.add(
                                    BadgeRule(
                                        group = groupId,
                                        category = category,
                                        name = name,
                                        imageURL = image,
                                        textColor = textColor,
                                        tagColor = tagColor,
                                        borderColor = borderColor,
                                        style = style,
                                        pattern = pattern,
                                    )
                                )
                            } catch (_: Exception) {
                                skipped++
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            return BadgeParseResult(rules = emptyList(), groups = 0, skipped = 0)
        }

        return BadgeParseResult(
            rules = rules.toList(),
            groups = groups,
            skipped = skipped,
        )
    }
}
