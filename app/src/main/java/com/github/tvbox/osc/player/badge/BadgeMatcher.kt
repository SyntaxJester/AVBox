package com.github.tvbox.osc.player.badge

object BadgeMatcher {

    const val MAX_RESULTS = 8
    const val MAX_INPUT = 1024
    const val BUDGET_NANOS = 45L * 1000L * 1000L

    private val TECHNICAL_GROUPS = setOf(
        "resolution", "video-tech", "video-codec", "audio-tech", "dv-profile",
        "bit-depth", "audio-channels", "gr", "gfr", "gc", "extra-container",
        "extra-bitrate", "extra-video-profile", "extra-source", "extra-audio-profile",
        "audio-quality", "restore-tech", "restore-tools", "picture-format",
        "picture-color", "quality-claim", "bitrate", "framerate", "codec",
    )

    private val TECHNICAL_WORDS = listOf(
        "分辨率", "resolution", "动态范围", "video-tech", "视频编码", "video-codec",
        "音频编码", "音效", "audio-tech", "色深", "bit-depth", "帧率", "framerate",
        "声道", "audio-channels", "码率", "bitrate", "画幅", "杜比视界",
        "dolby vision profile", "封装", "修复", "restore",
    )

    fun isTechnicalGroup(group: String, category: String): Boolean {
        if (group.isNotEmpty() && TECHNICAL_GROUPS.contains(group.lowercase())) return true
        val text = category.lowercase()
        for (word in TECHNICAL_WORDS) {
            if (text.contains(word)) return true
        }
        return false
    }

    fun matches(source: String?, technical: String?, rules: List<BadgeRule>): List<BadgeRule> {
        if (rules.isEmpty()) return emptyList()
        val title = source.orEmpty()
        val tech = technical.orEmpty().trim()
        val hasTech = tech.isNotEmpty()

        val usedGroups = HashSet<String>()
        val usedNames = HashSet<String>()
        val out = ArrayList<BadgeRule>()

        val deadline = System.nanoTime() + BUDGET_NANOS

        for (rule in rules) {
            if (System.nanoTime() > deadline) break
            if (usedGroups.contains(rule.group)) continue

            val isTech = isTechnicalGroup(rule.group, rule.category)
            if (isTech && !hasTech) continue

            val base = if (isTech) tech else title + " " + tech
            val text = if (base.length > MAX_INPUT) base.substring(0, MAX_INPUT) else base

            val hit = try {
                rule.matches(Guard(text, deadline))
            } catch (t: Throwable) {
                false
            }
            if (!hit) continue

            usedGroups.add(rule.group)

            if (!rule.hasImage()) {
                if (!usedNames.add(rule.name)) continue
            }

            out.add(rule)
            if (out.size >= MAX_RESULTS) break
        }
        return out
    }

    private class Guard(private val value: String, private val deadline: Long) : CharSequence {

        private var calls = 0

        override val length: Int
            get() = value.length

        override fun get(index: Int): Char {
            calls++
            if ((calls and 0xFF) == 0 && System.nanoTime() > deadline) {
                throw IllegalStateException("badge match timeout")
            }
            return value[index]
        }

        override fun subSequence(start: Int, end: Int): CharSequence = value.subSequence(start, end)

        override fun toString(): String = value
    }
}
