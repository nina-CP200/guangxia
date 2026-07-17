package com.guangxia.filmtools.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainViewModelReadingGateTest {
    @Test
    fun unlockedMeterAcceptsCameraUpdates() {
        assertTrue(shouldAcceptCameraReading(exposureLocked = false, hasReading = true))
    }

    @Test
    fun restoredLockAcceptsTheFirstCameraReading() {
        assertTrue(shouldAcceptCameraReading(exposureLocked = true, hasReading = false))
    }

    @Test
    fun lockFreezesUpdatesAfterAReadingExists() {
        assertFalse(shouldAcceptCameraReading(exposureLocked = true, hasReading = true))
    }
}
