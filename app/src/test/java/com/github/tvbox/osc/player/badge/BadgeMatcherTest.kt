package com.github.tvbox.osc.player.badge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

import java.util.regex.Pattern

class BadgeMatcherTest {

    private fun rule(
        group: String,
        category: String,
        name: String,
        pattern: String,
        image: String = "",
    ) = BadgeRule(group, category, name, image, "", "", "", "filled", Pattern.compile(pattern))

    @Test
    fun technicalGroupNeedsTechnicalInput() {
        val rules = listOf(rule("resolution", "resolution resolution", "4K", "(?i)\\b4k\\b"))
        assertEquals(0, BadgeMatcher.matches("某剧 4K 高清", "", rules).size)
        assertEquals(1, BadgeMatcher.matches("某剧", "2160p 4K UHD", rules).size)
    }

    @Test
    fun nonTechnicalGroupUsesTitle() {
        val rules = listOf(rule("jp-sub", "jp-sub 字幕", "简中", "简中"))
        assertEquals(1, BadgeMatcher.matches("某剧 简中", "", rules).size)
    }

    @Test
    fun oneBadgePerGroup() {
        val rules = listOf(
            rule("edition", "edition 版本", "4K", "4K", "http://a/4k.png"),
            rule("edition", "edition 版本", "2160p", "2160", "http://a/2160.png"),
        )
        assertEquals(1, BadgeMatcher.matches("4K 2160p", "", rules).size)
    }

    @Test
    fun resultCappedAtEight() {
        val rules = (1..20).map {
            rule("g" + it, "g" + it, "n" + it, "hit", "http://a/" + it + ".png")
        }
        assertEquals(BadgeMatcher.MAX_RESULTS, BadgeMatcher.matches("hit", "", rules).size)
    }

    @Test
    fun catastrophicPatternIsBounded() {
        val rules = listOf(rule("g", "g", "bad", "(a+)+(a+)+b"))
        val input = "a".repeat(BadgeMatcher.MAX_INPUT)
        val start = System.nanoTime()
        BadgeMatcher.matches(input, "", rules)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000L
        assertTrue("elapsed=" + elapsedMs, elapsedMs < 200L)
    }
}
