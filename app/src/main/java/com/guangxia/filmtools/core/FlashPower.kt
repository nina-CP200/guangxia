package com.guangxia.filmtools.core

import kotlin.math.pow
import kotlin.math.ceil
import kotlin.math.floor

/** A manual-flash output setting expressed as stops below full power. */
data class FlashPower(val stopsBelowFull: Double) {
    val ratio: Double get() = 2.0.pow(-stopsBelowFull)
    val guideNumberFactor: Double get() = ratio.pow(0.5)
}

object FlashPowerSettings {
    const val MAX_STOPS_BELOW_FULL = 8.0

    fun options(stepEv: Double): List<FlashPower> {
        require(stepEv == 0.1 || stepEv == 0.3) { "仅支持 0.1 或 0.3 EV 步长" }
        val divisionsPerStop = if (stepEv == 0.3) 3 else 10
        return (0..(MAX_STOPS_BELOW_FULL.toInt() * divisionsPerStop)).map { tick ->
            FlashPower(tick.toDouble() / divisionsPerStop)
        }
    }

    /** Godox-style manual output notation, e.g. 1/2+0.3 rather than 1/1.6. */
    fun displayLabel(power: FlashPower): String {
        val stops = power.stopsBelowFull
        val lowerWholeStop = ceil(stops - 1e-8).toInt()
        if (kotlin.math.abs(stops - floor(stops)) < 1e-8) return "1/${1 shl lowerWholeStop}"
        val plusEv = lowerWholeStop - stops
        return "1/${1 shl lowerWholeStop}+${formatGodoxEv(plusEv)}"
    }

    fun displayEv(stops: Double): String = formatGodoxEv(stops)

    private fun formatGodoxEv(value: Double): String = when {
        kotlin.math.abs(value - 1.0 / 3.0) < 0.02 -> "0.3"
        kotlin.math.abs(value - 2.0 / 3.0) < 0.02 -> "0.7"
        else -> "%.1f".format(java.util.Locale.US, value)
    }
}
