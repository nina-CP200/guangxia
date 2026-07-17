package com.guangxia.filmtools.core

import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.abs
import kotlin.math.ln

enum class DistanceUnit { METERS, FEET }
enum class FlashField { GUIDE_NUMBER, APERTURE, DISTANCE, ISO }

data class FlashValues(
    val guideNumber: Double? = null,
    val aperture: Double? = null,
    val distance: Double? = null,
    val iso: Double? = null,
)

sealed interface FlashResult {
    data class Success(val field: FlashField, val value: Double) : FlashResult
    data class Error(val message: String) : FlashResult
}

object FlashCalculator {
    fun powerRatioFor(fullPowerGuideNumber: Double, aperture: Double, distance: Double, iso: Double): Double =
        (aperture * distance / (fullPowerGuideNumber * sqrt(iso / 100.0))).pow(2)

    fun apertureForPower(fullPowerGuideNumber: Double, powerRatio: Double, distance: Double, iso: Double): Double =
        fullPowerGuideNumber * sqrt(powerRatio) * sqrt(iso / 100.0) / distance

    fun closestPowerIndex(ratios: List<Double>, targetRatio: Double): Int =
        ratios.indices.minByOrNull { index -> abs(ln(ratios[index] / targetRatio)) } ?: 0

    fun solve(values: FlashValues): FlashResult {
        val entries = listOf(values.guideNumber, values.aperture, values.distance, values.iso)
        if (entries.count { it == null } != 1) return FlashResult.Error("请恰好留空一个字段")
        if (entries.filterNotNull().any { !it.isFinite() || it <= 0.0 }) return FlashResult.Error("所有已知数必须大于 0")

        val result = when {
            values.guideNumber == null -> FlashResult.Success(FlashField.GUIDE_NUMBER, values.aperture!! * values.distance!! / sqrt(values.iso!! / 100.0))
            values.aperture == null -> FlashResult.Success(FlashField.APERTURE, values.guideNumber * sqrt(values.iso!! / 100.0) / values.distance!!)
            values.distance == null -> FlashResult.Success(FlashField.DISTANCE, values.guideNumber * sqrt(values.iso!! / 100.0) / values.aperture!!)
            else -> FlashResult.Success(FlashField.ISO, 100.0 * (values.aperture!! * values.distance!! / values.guideNumber).pow(2))
        }
        val value = (result as FlashResult.Success).value
        return if (!value.isFinite() || value <= 0.0 || value > 1_000_000.0) FlashResult.Error("计算结果超出合理范围") else result
    }

    fun convertDistance(value: Double, from: DistanceUnit, to: DistanceUnit): Double = when {
        from == to -> value
        from == DistanceUnit.METERS -> value * 3.28084
        else -> value / 3.28084
    }
}
