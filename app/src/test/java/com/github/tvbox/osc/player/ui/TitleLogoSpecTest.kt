package com.github.tvbox.osc.player.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleLogoSpecTest {

    @Test
    fun defaultIsLeftBottom() {
        assertEquals("左下", TitleLogoSpec.normalize(null))
        assertEquals("左下", TitleLogoSpec.normalize(""))
        assertEquals("左下", TitleLogoSpec.normalize("左上上"))
    }

    @Test
    fun knownPositionsSurvive() {
        for (pos in TitleLogoSpec.POSITIONS) {
            assertEquals(pos, TitleLogoSpec.normalize(pos))
        }
    }

    @Test
    fun cornersMapToAlignments() {
        assertTrue(TitleLogoSpec.isTop("左上"))
        assertTrue(TitleLogoSpec.isTop("右上"))
        assertFalse(TitleLogoSpec.isTop("左下"))
        assertTrue(TitleLogoSpec.isStart("左上"))
        assertTrue(TitleLogoSpec.isStart("左下"))
        assertFalse(TitleLogoSpec.isStart("右下"))
    }

    @Test
    fun offMeansNoLogo() {
        assertTrue(TitleLogoSpec.isOff("关闭"))
        assertFalse(TitleLogoSpec.shouldShow("关闭", true))
        assertTrue(TitleLogoSpec.shouldShow("左下", true))
    }

    @Test
    fun tmdbTitleArtSwitchDrivesPlayerLogo() {
        // 「使用 TMDB 片名图标」关掉时，播放器片名标识一起收起
        assertFalse(TitleLogoSpec.shouldShow("左下", false))
        assertFalse(TitleLogoSpec.shouldShow("左上", false))
    }

    @Test
    fun alwaysOffOnlyShowsWithControls() {
        assertTrue(TitleLogoSpec.visibleNow("左下", true, always = true, controlsVisible = false))
        assertFalse(TitleLogoSpec.visibleNow("左下", true, always = false, controlsVisible = false))
        assertTrue(TitleLogoSpec.visibleNow("左下", true, always = false, controlsVisible = true))
        assertFalse(TitleLogoSpec.visibleNow("关闭", true, always = true, controlsVisible = true))
        assertFalse(TitleLogoSpec.visibleNow("左下", false, always = true, controlsVisible = true))
    }
}
