package com.sailboatracing.location

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Wraps Android LocationManager to provide phone-GPS position updates.
 * All phone-GPS logic lives here — nothing leaks into the ViewModel.
 *
 * Phone GPS is typically 1 Hz (vs 25 Hz from the Teensy), so it is used
 * only as a fallback when the Teensy GPS has no fix.
 */
class PhoneGpsService(context: Context) {

    data class PhoneLocation(
        val lat: Double,
        val lon: Double,
        val sogKts: Float,   // speed over ground converted from m/s
        val cogDeg: Float,   // course over ground (bearing), degrees true
        val accuracyM: Float
    )

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _location = MutableSharedFlow<PhoneLocation>(extraBufferCapacity = 16)
    val location: SharedFlow<PhoneLocation> = _location

    private var listener: LocationListener? = null

    /**
     * Start receiving updates. Throws [SecurityException] if ACCESS_FINE_LOCATION
     * has not been granted — catch in the caller.
     */
    @Throws(SecurityException::class)
    fun start() {
        if (listener != null) return   // already running

        val l = LocationListener { loc -> onLocation(loc) }
        listener = l

        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)     -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return   // no provider available
        }

        locationManager.requestLocationUpdates(
            provider,
            500L,              // minimum 500 ms between updates
            0f,                // no minimum distance filter
            l,
            Looper.getMainLooper()
        )
    }

    fun stop() {
        listener?.let { locationManager.removeUpdates(it) }
        listener = null
    }

    fun isRunning() = listener != null

    private fun onLocation(loc: Location) {
        _location.tryEmit(
            PhoneLocation(
                lat       = loc.latitude,
                lon       = loc.longitude,
                sogKts    = if (loc.hasSpeed())    loc.speed * 1.94384f  else 0f,
                cogDeg    = if (loc.hasBearing())  loc.bearing           else 0f,
                accuracyM = if (loc.hasAccuracy()) loc.accuracy          else Float.MAX_VALUE
            )
        )
    }
}
