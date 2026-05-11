package com.sailboatracing.algorithm

import com.sailboatracing.model.LatLng
import com.sailboatracing.model.SensorData
import com.sailboatracing.model.StartLine
import com.sailboatracing.model.StartLineStatus
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object StartLineCalculator {

    private const val NM_PER_DEG_LAT = 60.0

    /**
     * Evaluate start line (or gate) status using flat-earth approximation (fine for < 1 nm).
     *
     * trueTimeToLine: how long at current COG and SOG until you cross the start line (extended
     *   as an infinite line through pin and boat — intersection outside the segment still counts,
     *   because "the gate" means any crossing of that line).
     *
     * optimalTimeToLine: perpendicular distance to the line / SOG — how long if you turned and
     *   drove straight for the nearest point.
     *
     * earlyOrLate: positive = arriving after the gun (running late),
     *              negative = arriving before the gun (risk of OCS).
     */
    fun evaluate(data: SensorData, line: StartLine, timerRemainingMs: Long): StartLineStatus {
        val cosLat = cos(Math.toRadians(data.lat))

        // Flat-earth coordinates in nautical miles, origin = boat position
        fun toFlat(pos: LatLng): Pair<Double, Double> {
            val dx = (pos.longitude - data.lon) * cosLat * NM_PER_DEG_LAT
            val dy = (pos.latitude - data.lat) * NM_PER_DEG_LAT
            return Pair(dx, dy)
        }

        val (px, py) = toFlat(line.pin)
        val (bx, by) = toFlat(line.boat)

        // ── Perpendicular distance to the infinite line through pin and boat ──
        val lineLen = sqrt((bx - px) * (bx - px) + (by - py) * (by - py))
        val distToLine: Float = if (lineLen < 1e-10) 0f
        else (abs(px * by - py * bx) / lineLen).toFloat()

        // ── COG direction vector (east, north) ──
        val cogRad = Math.toRadians(data.cogDeg.toDouble())
        val cogDx = sin(cogRad)
        val cogDy = cos(cogRad)

        // ── Parametric intersection of COG ray with the EXTENDED start line ──
        // Ray:        (0,0) + t*(cogDx, cogDy)
        // Line:       (px,py) + s*(dx, dy)   where dx = bx-px, dy = by-py
        // Solve:      t*cogDx - s*dx = px
        //             t*cogDy - s*dy = py
        val dx = bx - px
        val dy = by - py
        val denom = cogDx * (-dy) - cogDy * (-dx)

        var trueTimeToLine: Float? = null
        if (abs(denom) > 1e-10) {
            val t = (px * (-dy) - py * (-dx)) / denom
            // t > 0 means intersection is ahead of the boat on its current course.
            // We deliberately do NOT restrict s to [0,1] — crossing the extended line
            // (even outside the pin–boat segment) still counts as crossing the gate.
            if (t > 0.0 && data.sogKts > 0.01f) {
                trueTimeToLine = (t / data.sogKts * 3600f).toFloat()
            }
        }

        // ── Optimal time: straight-line charge at the nearest point on the line ──
        val optimalTimeToLine: Float? = if (data.sogKts > 0.01f && distToLine > 0f) {
            distToLine / data.sogKts * 3600f
        } else null

        // ── earlyOrLate: positive = late (arrive after gun), negative = early (OCS risk) ──
        val earlyOrLate = if (trueTimeToLine != null) {
            trueTimeToLine - timerRemainingMs / 1000f
        } else 0f

        return StartLineStatus(
            trueTimeToLineSeconds = trueTimeToLine,
            optimalTimeToLineSeconds = optimalTimeToLine,
            distanceToLineNm = distToLine,
            earlyOrLate = earlyOrLate
        )
    }
}
