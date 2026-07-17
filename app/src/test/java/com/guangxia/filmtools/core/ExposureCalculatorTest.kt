package com.guangxia.filmtools.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExposureCalculatorTest {
    @Test fun `sunny sixteen is EV 15`() {
        assertEquals(15.0, ExposureCalculator.ev100(16.0, 1.0 / 125.0, 100), 0.05)
    }

    @Test fun `aperture priority returns one over 125`() {
        val result = ExposureCalculator.shutterFor(15.0, 16.0, 100)
        assertEquals("1/125", result.displayValue)
        assertTrue(kotlin.math.abs(result.deviationStops) < 0.05)
    }

    @Test fun `shutter priority returns f16`() {
        val result = ExposureCalculator.apertureFor(15.0, 1.0 / 125.0, 100)
        assertEquals("f/16", result.displayValue)
    }

    @Test fun `higher film iso shortens shutter`() {
        val iso100 = ExposureCalculator.shutterFor(10.0, 8.0, 100)
        val iso400 = ExposureCalculator.shutterFor(10.0, 8.0, 400)
        assertTrue(iso400.snappedValue < iso100.snappedValue)
    }
}
