package com.github.tvbox.osc.player.badge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 守住订阅目录这条线：
 * 内置离线包必须在最前面且默认被选中（不然新装机器一片空白），
 * 预置的 5 条在线订阅要都在，id 由链接决定、同一链接不会重复出现。
 */
class BadgeSubscriptionsTest {

    @Test
    fun catalogIsOnlyTheFiveOnlinePresets() {
        val all = BadgeSubscriptions.all()
        // 应用里不再内置任何规则包/配图，只有这五条在线订阅
        assertEquals(5, all.size)
        assertEquals(BadgeSubscriptions.PRESET, all)
        assertTrue(all.all { it.isRemote && !it.isRaw && !it.builtin })
        // 默认选中的是第一条在线订阅
        assertEquals(BadgeSubscriptions.PRESET.first().id, BadgeSubscriptions.defaultSelectedId())
        assertTrue(all.any { it.id == BadgeSubscriptions.defaultSelectedId() })
    }

    @Test
    fun presetsCoverRequestedSources() {
        val urls = BadgeSubscriptions.PRESET.map { it.source }
        assertEquals(BadgeSubscriptions.PRESET.size, urls.distinct().size)
        assertTrue(urls.any { it.contains("xinyue4capyplayer") })
        assertTrue(urls.any { it.contains("streambridge") })
        assertTrue(urls.any { it.contains("pppboom.json") })
        assertTrue(urls.any { it.contains("xperience-app.com") })
        assertTrue(urls.any { it.contains("nuvio-badge-kit") })
        assertTrue(BadgeSubscriptions.PRESET.all { it.isRemote && !it.builtin })
    }

    @Test
    fun idIsStableAndDistinct() {
        val url = "https://example.com/a.json"
        assertEquals(BadgeSubscriptions.idOf(url), BadgeSubscriptions.idOf(url))
        assertEquals(BadgeSubscriptions.idOf(url), BadgeSubscriptions.idOf("  $url  "))
        assertTrue(BadgeSubscriptions.idOf(url) != BadgeSubscriptions.idOf("https://example.com/b.json"))
        assertTrue(BadgeSubscriptions.idOf(url).startsWith("u"))
        assertTrue(BadgeSubscriptions.idOf(url).matches(Regex("u[0-9a-f]{14}")))
    }

    @Test
    fun presetSourcesAreRemote() {
        val remote = BadgeSubscriptions.PRESET.first()
        assertFalse(remote.isRaw)
        assertTrue(remote.isRemote)
        assertEquals("", remote.rawName)
    }

    @Test
    fun downloadProgressMath() {
        assertEquals(0, BadgeDownloadState(total = 0, processed = 0).percent)
        assertEquals(50, BadgeDownloadState(total = 10, processed = 5).percent)
        assertEquals(100, BadgeDownloadState(total = 10, processed = 10).percent)
    }

    @Test
    fun stateResolvesSelectedSubscription() {
        val subs = BadgeSubscriptions.all()
        val state = BadgePackState(subscriptions = subs, selectedId = subs[2].id)
        assertEquals(subs[2].id, state.selected()?.id)
        assertEquals(null, BadgePackState(subscriptions = subs, selectedId = "none").selected())
    }
}
