package com.guangxia.filmtools.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReciprocityCalculatorTest {
    @Test
    fun `tri x 400 follows official table at published points`() {
        val profile = ReciprocityCalculator.profiles.first { it.id == "kodak-trix-400" }

        assertEquals(2.0, ReciprocityCalculator.calculate(profile, 1.0).correctedSeconds!!, 0.001)
        assertEquals(50.0, ReciprocityCalculator.calculate(profile, 10.0).correctedSeconds!!, 0.001)
        assertEquals(1200.0, ReciprocityCalculator.calculate(profile, 100.0).correctedSeconds!!, 0.001)
        assertEquals(1.0, ReciprocityCalculator.calculate(profile, 1.0).exposureStops!!, 0.001)
    }

    @Test
    fun `table interpolation is performed in log time`() {
        val profile = ReciprocityCalculator.profiles.first { it.id == "kodak-tmax-100" }

        val result = ReciprocityCalculator.calculate(profile, 3.16227766)

        assertEquals(4.347, result.correctedSeconds!!, 0.01)
        assertTrue(result.exposureStops!! > 0.2)
    }

    @Test
    fun `community portra profile returns estimate and warning`() {
        val profile = ReciprocityCalculator.profiles.first { it.id == "kodak-portra-400" }

        val result = ReciprocityCalculator.calculate(profile, 8.0)

        assertEquals(15.0, result.correctedSeconds!!, 0.001)
        assertEquals(ReciprocitySource.COMMUNITY_ESTIMATE, result.source)
        assertTrue(result.isEstimate)
        assertTrue(result.warning!!.contains("试拍"))
    }

    @Test
    fun `gold has community estimate within published range and refuses extrapolation`() {
        val profile = ReciprocityCalculator.profiles.first { it.id == "kodak-gold-200" }

        val result = ReciprocityCalculator.calculate(profile, 10.0)

        assertEquals(60.0, result.correctedSeconds!!, 0.001)
        assertEquals(ReciprocitySource.COMMUNITY_ESTIMATE, result.source)
        assertTrue(result.warning!!.isNotBlank())

        val extrapolated = ReciprocityCalculator.calculate(profile, 30.0)
        assertNull(extrapolated.correctedSeconds)
        assertTrue(extrapolated.warning!!.contains("超出资料建议范围"))
    }

    @Test
    fun `fuji pro 400h follows official 4 and 16 second limits`() {
        val profile = ReciprocityCalculator.profiles.first { it.id == "fujicolor-pro-400h" }

        val fourSeconds = ReciprocityCalculator.calculate(profile, 4.0)
        assertEquals(0.5, fourSeconds.exposureStops!!, 0.001)
        assertEquals(32.0, ReciprocityCalculator.calculate(profile, 16.0).correctedSeconds!!, 0.001)
        assertNull(ReciprocityCalculator.calculate(profile, 30.0).correctedSeconds)
    }

    @Test
    fun `fuji superia 200 follows official long exposure table`() {
        val profile = ReciprocityCalculator.profiles.first { it.id == "fujicolor-superia-200" }

        val result = ReciprocityCalculator.calculate(profile, 64.0)

        assertEquals(128.0, result.correctedSeconds!!, 0.001)
        assertEquals(1.0, result.exposureStops!!, 0.001)
        assertEquals(ReciprocitySource.OFFICIAL, result.source)
    }

    @Test
    fun `velvia provides color filter and exposure compensation`() {
        val profile = ReciprocityCalculator.profiles.first { it.id == "fujichrome-velvia-50" }

        val result = ReciprocityCalculator.calculate(profile, 8.0)

        assertEquals("7.5M", result.filter)
        assertEquals(0.5, result.exposureStops!!, 0.001)
        assertTrue(result.correctedSeconds!! > 8.0)
    }

    @Test
    fun `provia marks exposures beyond recommended table as unsafe`() {
        val profile = ReciprocityCalculator.profiles.first { it.id == "fujichrome-provia-100f" }

        val betweenRecommendedAndUnsafe = ReciprocityCalculator.calculate(profile, 300.0)
        assertNull(betweenRecommendedAndUnsafe.correctedSeconds)

        val result = ReciprocityCalculator.calculate(profile, 480.0)

        assertNull(result.correctedSeconds)
        assertTrue(result.warning!!.contains("超出资料建议范围"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero metered time is rejected`() {
        val profile = ReciprocityCalculator.profiles.first()

        ReciprocityCalculator.calculate(profile, 0.0)
    }

    @Test
    fun `custom power law does not compensate before onset`() {
        val profile = ReciprocityCalculator.customProfile("测试胶卷", exponent = 1.31, onsetSeconds = 1.0)

        val result = ReciprocityCalculator.calculate(profile, 0.5)

        assertEquals(0.5, result.correctedSeconds!!, 0.001)
        assertNull(result.exposureStops)
        assertEquals(ReciprocitySource.CUSTOM, result.source)
    }
}
