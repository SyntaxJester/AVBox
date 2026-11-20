package com.github.tvbox.osc.player.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaBadgeSpecTest {

    @Test
    fun invalidValuesFallBackToDefault() {
        assertEquals(MediaBadgeSpec.DEFAULT, MediaBadgeSpec.normalize(0))
        assertEquals(MediaBadgeSpec.DEFAULT, MediaBadgeSpec.normalize(-30))
        assertEquals(MediaBadgeSpec.DEFAULT, MediaBadgeSpec.normalize(999))
        assertEquals(MediaBadgeSpec.DEFAULT, MediaBadgeSpec.normalize(MediaBadgeSpec.DEFAULT))
    }

    @Test
    fun defaultIsBiggerThanTheOldLook() {
        assertTrue(MediaBadgeSpec.DEFAULT > 100)
        assertTrue(MediaBadgeSpec.heightDp(MediaBadgeSpec.DEFAULT) > MediaBadgeSpec.heightDp(100))
    }

    @Test
    fun rangeIsKept() {
        for (percent in MediaBadgeSpec.MIN..MediaBadgeSpec.MAX step 10) {
            assertEquals(percent, MediaBadgeSpec.normalize(percent))
        }
    }

    @Test
    fun hundredPercentMatchesOriginalLook() {
        assertEquals(1f, MediaBadgeSpec.scale(100), 0.0001f)
        assertEquals(26f, MediaBadgeSpec.heightDp(100), 0.0001f)
        assertEquals(112f, MediaBadgeSpec.maxWidthDp(100), 0.0001f)
        assertEquals(5f, MediaBadgeSpec.cornerDp(100), 0.0001f)
        assertEquals(6f, MediaBadgeSpec.padHorizontalDp(100), 0.0001f)
        assertEquals(3f, MediaBadgeSpec.padVerticalDp(100), 0.0001f)
        assertEquals(12f, MediaBadgeSpec.fontSizeSp(100), 0.0001f)
        assertEquals(15f, MediaBadgeSpec.lineHeightSp(100), 0.0001f)
        assertEquals(8f, MediaBadgeSpec.gapDp(100), 0.0001f)
    }

    @Test
    fun largerPercentScalesEverythingUp() {
        val small = 60
        val large = 140
        assertTrue(MediaBadgeSpec.heightDp(large) > MediaBadgeSpec.heightDp(100))
        assertTrue(MediaBadgeSpec.heightDp(100) > MediaBadgeSpec.heightDp(small))
        assertEquals(26f * 1.4f, MediaBadgeSpec.heightDp(large), 0.0001f)
        assertEquals(112f * 0.6f, MediaBadgeSpec.maxWidthDp(small), 0.0001f)
        assertEquals(12f * 1.4f, MediaBadgeSpec.fontSizeSp(large), 0.0001f)
        assertEquals(8f * 1.4f, MediaBadgeSpec.gapDp(large), 0.0001f)
    }

    @Test
    fun dirtyPercentFallsBackToDefaultSizes() {
        assertEquals(MediaBadgeSpec.heightDp(MediaBadgeSpec.DEFAULT), MediaBadgeSpec.heightDp(0), 0.0001f)
        assertEquals(MediaBadgeSpec.heightDp(MediaBadgeSpec.DEFAULT), MediaBadgeSpec.heightDp(500), 0.0001f)
    }

    @Test
    fun stripShrinksOnlyWhenItOverflows() {
        assertEquals(1f, MediaBadgeSpec.fitScale(300, 300), 0.0001f)
        assertEquals(1f, MediaBadgeSpec.fitScale(200, 300), 0.0001f)
        assertEquals(0.5f, MediaBadgeSpec.fitScale(600, 300), 0.0001f)
        assertEquals(1f, MediaBadgeSpec.fitScale(0, 300), 0.0001f)
        assertEquals(1f, MediaBadgeSpec.fitScale(600, 0), 0.0001f)
    }
}
