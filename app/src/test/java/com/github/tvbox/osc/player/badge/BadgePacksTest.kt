package com.github.tvbox.osc.player.badge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgePacksTest {

    @Test
    fun knownOptionsMapToRawAndUrl() {
        assertEquals("media_badges", BadgePacks.localRaw(BadgePacks.OPTION_XINYUE))
        assertEquals("xijing_club_badges", BadgePacks.localRaw(BadgePacks.OPTION_XIJING))
        assertNull(BadgePacks.localRaw(BadgePacks.OPTION_CUSTOM))
        assertTrue(BadgePacks.remoteUrl(BadgePacks.OPTION_XINYUE)!!.startsWith("https://"))
        assertTrue(BadgePacks.remoteUrl(BadgePacks.OPTION_XIJING)!!.startsWith("https://"))
    }

    @Test
    fun cacheFileNamesAreDistinct() {
        assertEquals("pack_1", BadgePacks.cacheFileName(BadgePacks.OPTION_XINYUE, ""))
        val a = BadgePacks.cacheFileName(BadgePacks.OPTION_CUSTOM, "https://a.example/x.json")
        val b = BadgePacks.cacheFileName(BadgePacks.OPTION_CUSTOM, "https://b.example/x.json")
        assertTrue(a != b)
    }
}
