package com.sailboatracing.algorithm

import com.sailboatracing.model.HeadingTrend
import com.sailboatracing.model.SensorData
import com.sailboatracing.model.Tack
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class HeadedLiftedDetector(
    private val defaultShortWindowMs: Long = 15_000L,
    private val defaultLongWindowMs: Long = 90_000L,
    private val liftThresholdDeg: Float = 2f,
    private val strongThresholdDeg: Float = 5f,
    // A new trend must hold for this many ms before it is committed to the UI.
    private val holdMs: Long = 3_000L
) {

    data class Result(val trend: HeadingTrend, val degrees: Float)

    private var committedTrend = HeadingTrend.NEUTRAL
    private var pendingTrend = HeadingTrend.NEUTRAL
    private var pendingSinceMs = 0L

    fun evaluate(
        history: List<SensorData>,
        tack: Tack,
        shortWindowMs: Long = defaultShortWindowMs,
        longWindowMs: Long = defaultLongWindowMs
    ): Result {
        if (history.isEmpty()) return Result(committedTrend, 0f)

        val now = history.last().timestampMs
        val shortCutoff = now - shortWindowMs
        val longCutoff = now - longWindowMs

        val shortWindow = history.filter { it.timestampMs >= shortCutoff }
        val longWindow = history.filter { it.timestampMs >= longCutoff }

        if (shortWindow.isEmpty() || longWindow.isEmpty()) return Result(committedTrend, 0f)

        val shortMean = circularMean(shortWindow.map { it.heading })
        val longMean = circularMean(longWindow.map { it.heading })

        var diff = shortMean - longMean
        while (diff > 180f) diff -= 360f
        while (diff < -180f) diff += 360f

        if (tack == Tack.PORT) diff = -diff

        val rawTrend = when {
            diff >= strongThresholdDeg  -> HeadingTrend.LIFTED_STRONG
            diff >= liftThresholdDeg    -> HeadingTrend.LIFTED
            diff <= -strongThresholdDeg -> HeadingTrend.HEADED_STRONG
            diff <= -liftThresholdDeg   -> HeadingTrend.HEADED
            else                        -> HeadingTrend.NEUTRAL
        }

        // Hysteresis: only commit a trend change after it holds for holdMs.
        when {
            rawTrend == committedTrend -> {
                // Consistent with committed — reset any pending candidate.
                pendingTrend = rawTrend
                pendingSinceMs = 0L
            }
            rawTrend != pendingTrend -> {
                // New candidate — start hold timer.
                pendingTrend = rawTrend
                pendingSinceMs = now
            }
            pendingSinceMs > 0L && (now - pendingSinceMs) >= holdMs -> {
                // Pending has held long enough — commit.
                committedTrend = pendingTrend
                pendingSinceMs = 0L
            }
            // else: same pending, still within hold period — do nothing
        }

        return Result(committedTrend, diff)
    }

    private fun circularMean(headings: List<Float>): Float {
        if (headings.isEmpty()) return 0f
        var sinSum = 0.0
        var cosSum = 0.0
        for (h in headings) {
            val rad = Math.toRadians(h.toDouble())
            sinSum += sin(rad)
            cosSum += cos(rad)
        }
        val meanRad = atan2(sinSum / headings.size, cosSum / headings.size)
        var meanDeg = Math.toDegrees(meanRad).toFloat()
        if (meanDeg < 0f) meanDeg += 360f
        return meanDeg
    }
}
