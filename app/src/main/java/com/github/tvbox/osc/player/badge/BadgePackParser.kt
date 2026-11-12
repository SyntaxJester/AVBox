package com.github.tvbox.osc.player.badge

import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.Charset
import java.util.Locale
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * 徽章包解析器。
 *
 * 包格式（和原 APK 完全一致）：
 * {
 *   "groups":  [ { "id": "resolution", "name": "分辨率" }, ... ],
 *   "filters": [ {
 *       "isEnabled": true,
 *       "id": "4k",
 *       "name": "4K",
 *       "groupId": "resolution",
 *       "pattern": "2160[Pp]|\\b4K\\b",
 *       "imageURL": "https://example.com/4k.png",
 *       "textColor": "#FFFFFF",
 *       "tagColor": "#CC000000",
 *       "borderColor": "#88FFFFFF",
 *       "tagStyle": "filled"
 *   }, ... ]
 * }
 *
 * 硬限制（跟原实现一致，防止恶意包把播放器卡死）：
 *   - 包文本 UTF-8 不超过 8 MB
 *   - filters 不超过 10000 条
 *   - 单条 pattern 非空且不超过 64000 字符
 *   - 正则编译失败的条目直接跳过
 *   - id 重复的条目直接跳过（id 为空则自动生成 rule-<下标>）
 */
object BadgePackParser {

    const val MAX_BYTES = 8 * 1024 * 1024
    const val MAX_FILTERS = 10000
    const val MAX_PATTERN = 64000

    private val UTF8 = Charset.forName("UTF-8")

    /**
     * 解析徽章包 JSON，返回规则列表。
     * 任何解析错误都会抛出 IllegalArgumentException。
     */
    fun parse(text: String?): BadgeParseResult {
        if (text == null) throw IllegalArgumentException("徽章包为空")
        val bytes = text.toByteArray(UTF8).size
        if (bytes > MAX_BYTES) throw IllegalArgumentException("徽章包超过 8 MB")

        // 去掉 UTF-8 BOM，原实现也是这样处理的
        var body = text
        if (body.isNotEmpty() && body[0] == '\uFEFF') body = body.substring(1)

        val root: JSONObject = try {
            JSONObject(body)
        } catch (e: Exception) {
            throw IllegalArgumentException("徽章包不是合法 JSON：" + e.message)
        }

        val groupArray: JSONArray? = root.optJSONArray("groups")
        val filterArray: JSONArray? = root.optJSONArray("filters")
        if (filterArray == null) throw IllegalArgumentException("徽章包缺少 filters")
        if (filterArray.length() > MAX_FILTERS) throw IllegalArgumentException("徽章规则过多")

        // ---- 分组：id -> 名称，以及声明顺序 ----
        val groupNames = LinkedHashMap<String, String>()
        val groupOrder = HashMap<String, Int>()
        for (g in objects(groupArray)) {
            val id = text(g, "id")
            if (groupNames.containsKey(id)) continue
            groupNames[id] = text(g, "name")
            groupOrder[id] = groupNames.size - 1
        }

        // ---- 规则 ----
        val rules = mutableListOf<BadgeRule>()
        val seenIds = HashSet<String>()
        var enabled = 0

        for (i in 0 until filterArray.length()) {
            val f: JSONObject? = filterArray.optJSONObject(i)
            if (f == null) continue
            if (!f.optBoolean("isEnabled", true)) continue
            enabled++

            val raw = text(f, "pattern")
            if (raw.trim().isEmpty() || raw.length > MAX_PATTERN) continue

            val regex: Pattern = try {
                Pattern.compile(raw)
            } catch (e: PatternSyntaxException) {
                continue   // 坏正则跳过，跟原实现一样
            }

            val name = text(f, "name")
            var id = text(f, "id")
            if (id.trim().isEmpty()) id = "rule-$i"
            if (!seenIds.add(id)) continue   // id 重复跳过

            var groupId = text(f, "groupId")
            if (groupId.trim().isEmpty()) groupId = id

            val groupName = groupNames[groupId] ?: ""
            val category = (groupId + " " + groupName).lowercase(Locale.ROOT)

            // 图片只接受绝对 http(s) 地址，其它一律当作没有图
            var imageUrl = text(f, "imageURL")
            if (!isHttpUrl(imageUrl)) imageUrl = ""

            rules.add(
                BadgeRule(
                    groupId,
                    category,
                    name,
                    imageUrl,
                    text(f, "textColor"),
                    text(f, "tagColor"),
                    text(f, "borderColor"),
                    text(f, "tagStyle"),
                    regex,
                )
            )
        }

        // ---- 按 groups 里声明的顺序排序（没声明的排最后），稳定排序 ----
        val order = groupOrder
        val tail = groupNames.size
        rules.sortWith(Comparator { a, b ->
            val oa = order[a.group]
            val ob = order[b.group]
            val ia = oa ?: tail
            val ib = ob ?: tail
            when {
                ia < ib -> -1
                ia > ib -> 1
                else -> 0
            }
        })

        if (rules.isEmpty()) throw IllegalArgumentException("包中没有可用且已启用的规则")

        return BadgeParseResult(rules, groupNames.size, enabled - rules.size)
    }

    private fun objects(array: JSONArray?): List<JSONObject> {
        if (array == null) return emptyList()
        val out = mutableListOf<JSONObject>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i)
            if (o != null) out.add(o)
        }
        return out
    }

    /** 对应原实现里的 ModelsKt.text()：optString，且把字面量 "null" 当空串。 */
    private fun text(o: JSONObject?, key: String): String {
        if (o == null) return ""
        val v = try {
            o.optString(key)
        } catch (e: Exception) {
            return ""
        }
        if (v == null || "null" == v) return ""
        return v
    }

    private fun isHttpUrl(s: String?): Boolean {
        if (s == null || s.isEmpty()) return false
        val lower = s.lowercase(Locale.ROOT)
        if (!(lower.startsWith("https://") || lower.startsWith("http://"))) return false
        val rest = if (lower.startsWith("https://")) s.substring(8) else s.substring(7)
        val slash = rest.indexOf('/')
        val host = if (slash >= 0) rest.substring(0, slash) else rest
        return host.isNotEmpty() && host.indexOf(' ') < 0
    }
}
