package com.github.tvbox.osc.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbPosterQualityTest {

    @Test
    fun autoKeepsCallerSize() {
        assertTrue(TmdbPosterQuality.Auto.isAuto)
        assertNull(TmdbPosterQuality.Auto.sizeToken)
    }

    @Test
    fun fixedTiersMapToTmdbSizes() {
        assertEquals("w342", TmdbPosterQuality.Standard.sizeToken)
        assertEquals("w500", TmdbPosterQuality.High.sizeToken)
        assertEquals("w780", TmdbPosterQuality.Ultra.sizeToken)
        assertEquals("original", TmdbPosterQuality.Original.sizeToken)
    }

    @Test
    fun storedOrdinalFallsBackToAuto() {
        assertEquals(TmdbPosterQuality.Auto, TmdbPosterQuality.of(0))
        assertEquals(TmdbPosterQuality.Standard, TmdbPosterQuality.of(1))
        assertEquals(TmdbPosterQuality.Original, TmdbPosterQuality.of(4))
        assertEquals(TmdbPosterQuality.Auto, TmdbPosterQuality.of(-1))
        assertEquals(TmdbPosterQuality.Auto, TmdbPosterQuality.of(99))
    }

    @Test
    fun everyTierRoundTripsThroughOrdinal() {
        for (tier in TmdbPosterQuality.entries) {
            assertEquals(tier, TmdbPosterQuality.of(tier.ordinal))
        }
    }
}
