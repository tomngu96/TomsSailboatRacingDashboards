package com.sailboatracing.algorithm

import com.sailboatracing.model.LatLng
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object VMGCalculator {

    private const val EARTH_RADIUS_NM = 3440.065

    /**
     * Velocity Made Good toward mark.
     * VMG = SOG * cos(angle between COG and bearing to mark)
     */
    fun vmg(currentPos: LatLng, markPos: LatLng, cogDeg: Float, sogKts: Float): Float? {
        if (sogKts <= 0f) return null
        val bearingToMark = bearing(currentPos, markPos)
        var angleDiff = bearingToMark - cogDeg
        while (angleDiff > 180f) angleDiff -= 360f
        while (angleDiff < -180f) angleDiff += 360f
        return sogKts * cos(Math.toRadians(angleDiff.toDouble())).toFloat()
    }

    /**
     * Haversine distance in nautical miles.
     */
    fun distanceNm(a: LatLng, b: LatLng): Float {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)

        val sinDLat = sin(dLat / 2)
        val sinDLon = sin(dLon / 2)
        val h = sinDLat * sinDLat + cos(lat1) * cos(lat2) * sinDLon * sinDLon
        val c = 2 * atan2(sqrt(h), sqrt(1 - h))
        return (EARTH_RADIUS_NM * c).toFloat()
    }

    /**
     * Great-circle initial bearing from [from] to [to], 0-360 degrees.
     */
    fun bearing(from: LatLng, to: LatLng): Float {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)

        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        var bearing = Math.toDegrees(atan2(y, x)).toFloat()
        if (bearing < 0f) bearing += 360f
        return bearing
    }
}
