package com.guangxia.filmtools.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlashCalculatorTest {
    @Test fun `GN40 at five meters is f8`() {
        val result = FlashCalculator.solve(FlashValues(guideNumber = 40.0, distance = 5.0, iso = 100.0)) as FlashResult.Success
        assertEquals(FlashField.APERTURE, result.field)
        assertEquals(8.0, result.value, 0.001)
    }

    @Test fun `solves guide number`() {
        val result = FlashCalculator.solve(FlashValues(aperture = 8.0, distance = 5.0, iso = 100.0)) as FlashResult.Success
        assertEquals(40.0, result.value, 0.001)
    }

    @Test fun `solves distance`() {
        val result = FlashCalculator.solve(FlashValues(guideNumber = 40.0, aperture = 8.0, iso = 400.0)) as FlashResult.Success
        assertEquals(10.0, result.value, 0.001)
    }

    @Test fun `solves ISO`() {
        val result = FlashCalculator.solve(FlashValues(guideNumber = 40.0, aperture = 8.0, distance = 10.0)) as FlashResult.Success
        assertEquals(400.0, result.value, 0.001)
    }

    @Test fun `requires exactly one missing field`() {
        assertTrue(FlashCalculator.solve(FlashValues(guideNumber = 40.0)) is FlashResult.Error)
        assertTrue(FlashCalculator.solve(FlashValues(40.0, 8.0, 5.0, 100.0)) is FlashResult.Error)
    }

    @Test fun `converts meters and feet round trip`() {
        val feet = FlashCalculator.convertDistance(10.0, DistanceUnit.METERS, DistanceUnit.FEET)
        assertEquals(10.0, FlashCalculator.convertDistance(feet, DistanceUnit.FEET, DistanceUnit.METERS), 0.0001)
    }

    @Test fun `power follows aperture and distance`() {
        val ratio = FlashCalculator.powerRatioFor(fullPowerGuideNumber = 40.0, aperture = 8.0, distance = 5.0, iso = 100.0)
        assertEquals(1.0, ratio, 0.0001)
        assertEquals(0.25, FlashCalculator.powerRatioFor(40.0, 4.0, 5.0, 100.0), 0.0001)
    }

    @Test fun `aperture follows power while distance remains a separate input`() {
        assertEquals(4.0, FlashCalculator.apertureForPower(40.0, 0.25, 5.0, 100.0), 0.0001)
    }
}
