package com.guangxia.filmtools.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraMeterControllerTest {
    @Test
    fun smoothRangePrefersFixedThirtyForCrossDeviceStability() {
        val selected = selectSmoothPreviewFpsRange(
            listOf(5..60, 15..60, 30..30, 30..60, 60..60),
        )

        assertEquals(30..30, selected)
    }

    @Test
    fun smoothRangeUsesThirtyToSixtyWhenFixedThirtyIsUnavailable() {
        val selected = selectSmoothPreviewFpsRange(listOf(5..60, 30..60, 60..60))

        assertEquals(30..60, selected)
    }

    @Test
    fun smoothRangeRejectsRangesThatCanDropBelowThirty() {
        val selected = selectSmoothPreviewFpsRange(listOf(5..60, 15..30, 24..60))

        assertNull(selected)
    }
}
