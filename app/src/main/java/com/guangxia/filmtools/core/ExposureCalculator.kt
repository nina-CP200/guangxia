package com.guangxia.filmtools.core

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class MeteringMode { AVERAGE, CENTER }
enum class ExposurePriority { APERTURE, SHUTTER }

data class MeterReading(
    val aperture: Double,
    val exposureSeconds: Double,
    val sensorIso: Int,
    val ev100: Double,
    val status: ReadingStatus = ReadingStatus.NORMAL,
)

enum class ReadingStatus { NORMAL, TOO_DARK, TOO_BRIGHT, UNAVAILABLE }

data class ExposureResult(
    val displayValue: String,
    val exactValue: Double,
    val snappedValue: Double,
    val deviationStops: Double,
)

object ExposureCalculator {
    val apertures = listOf(1.0, 1.1, 1.2, 1.4, 1.6, 1.8, 2.0, 2.2, 2.5, 2.8, 3.2, 3.5, 4.0, 4.5, 5.0, 5.6, 6.3, 7.1, 8.0, 9.0, 10.0, 11.0, 13.0, 14.0, 16.0, 18.0, 20.0, 22.0, 25.0, 29.0, 32.0)
    val shutters = listOf(
        30.0, 25.0, 20.0, 15.0, 13.0, 10.0, 8.0, 6.0, 5.0, 4.0, 3.2, 2.5, 2.0, 1.6, 1.3, 1.0,
        0.8, 0.6, 0.5, 0.4, 1.0 / 3.0, 0.25, 0.2, 1.0 / 6.0, 0.125, 0.1, 1.0 / 13.0,
        1.0 / 15.0, 1.0 / 20.0, 1.0 / 25.0, 1.0 / 30.0, 1.0 / 40.0, 1.0 / 50.0,
        1.0 / 60.0, 1.0 / 80.0, 1.0 / 100.0, 1.0 / 125.0, 1.0 / 160.0, 1.0 / 200.0,
        1.0 / 250.0, 1.0 / 320.0, 1.0 / 400.0, 1.0 / 500.0, 1.0 / 640.0, 1.0 / 800.0,
        1.0 / 1000.0, 1.0 / 1250.0, 1.0 / 1600.0, 1.0 / 2000.0, 1.0 / 2500.0,
        1.0 / 3200.0, 1.0 / 4000.0, 1.0 / 5000.0, 1.0 / 6400.0, 1.0 / 8000.0,
    )

    fun ev100(aperture: Double, exposureSeconds: Double, iso: Int): Double =
        log2(aperture * aperture / exposureSeconds * 100.0 / iso)

    fun shutterFor(ev100: Double, aperture: Double, filmIso: Int): ExposureResult {
        val exact = aperture * aperture / 2.0.pow(ev100 + log2(filmIso / 100.0))
        val snapped = nearestLog(exact, shutters)
        return ExposureResult(formatShutter(snapped), exact, snapped, log2(exact / snapped))
    }

    fun apertureFor(ev100: Double, shutterSeconds: Double, filmIso: Int): ExposureResult {
        val exact = sqrt(shutterSeconds * 2.0.pow(ev100 + log2(filmIso / 100.0)))
        val snapped = nearestLog(exact, apertures)
        return ExposureResult("f/${formatDecimal(snapped)}", exact, snapped, 2.0 * log2(snapped / exact))
    }

    fun formatShutter(seconds: Double): String = when {
        seconds >= 1.0 -> if (seconds % 1.0 == 0.0) "${seconds.toInt()}s" else "${formatDecimal(seconds)}s"
        else -> "1/${(1.0 / seconds).roundToInt()}"
    }

    private fun nearestLog(value: Double, values: List<Double>): Double =
        values.minBy { abs(log2(it / value)) }

    private fun formatDecimal(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
    private fun log2(value: Double) = ln(value) / ln(2.0)
}
