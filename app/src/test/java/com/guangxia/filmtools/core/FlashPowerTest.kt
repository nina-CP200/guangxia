package com.guangxia.filmtools.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FlashPowerTest {
    @Test fun `one over 256 is eight stops below full power`() {
        val power = FlashPowerSettings.options(0.3).last()
        assertEquals(8.0, power.stopsBelowFull, 0.0)
        assertEquals(1.0 / 256.0, power.ratio, 0.0000001)
        assertEquals(1.0 / 16.0, power.guideNumberFactor, 0.0000001)
    }

    @Test fun `point one EV dial includes every tenth stop`() {
        assertEquals(81, FlashPowerSettings.options(0.1).size)
    }

    @Test fun `third stop labels use Godox base fraction notation`() {
        val oneThirdBelowFull = FlashPowerSettings.options(0.3)[1]
        val twoThirdsBelowFull = FlashPowerSettings.options(0.3)[2]
        assertEquals("1/2+0.7", FlashPowerSettings.displayLabel(oneThirdBelowFull))
        assertEquals("1/2+0.3", FlashPowerSettings.displayLabel(twoThirdsBelowFull))
    }
}
