package com.github.tvbox.osc.player.badge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 离线图片的落盘命名：同一个 URL 每次都算到同一个文件（下载过就不重复下），
 * URL 不同不能撞名，svg 必须留 .svg 后缀否则 Coil 不会按矢量图解码。
 */
class BadgeOfflineNameTest {

    private val png = "https://cdn.example.com/badges/elite/1.webp"
    private val svg = "https://cdn.example.com/images/8k.svg"

    @Test
    fun sameUrlMapsToSameFile() {
        assertEquals(BadgeImageStore.offlineName(png), BadgeImageStore.offlineName(png))
        assertEquals(BadgeImageStore.offlineName(png), BadgeImageStore.offlineName("  $png  "))
        assertEquals(60 + 4, BadgeImageStore.offlineName(png).length)
    }

    @Test
    fun differentUrlsDoNotCollide() {
        assertNotEquals(BadgeImageStore.offlineName(png), BadgeImageStore.offlineName(svg))
    }

    @Test
    fun svgKeepsSvgSuffix() {
        assertTrue(BadgeImageStore.offlineName(svg).endsWith(".svg"))
        assertTrue(BadgeImageStore.offlineName("https://x/a.svg?token=1").endsWith(".svg"))
        assertTrue(BadgeImageStore.offlineName(png).endsWith(".img"))
    }

    @Test
    fun urlsWithQueryStillGetAStableName() {
        val signed = png + "?token=abc"
        assertEquals(BadgeImageStore.offlineName(signed), BadgeImageStore.offlineName(signed))
        assertTrue(BadgeImageStore.offlineName(signed).endsWith(".img"))
    }
}
