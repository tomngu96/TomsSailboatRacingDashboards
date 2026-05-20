package com.sailboatracing.algorithm

import kotlin.math.sqrt

/**
 * Simple 1-D Kalman filter for smoothing GPS speed-over-ground.
 *
 * Models the boat speed as a slowly-changing quantity (random-walk process).
 * The filter separates genuine acceleration from GPS measurement jitter.
 *
 * Tuning knobs
 * ------------
 * processNoise (Q)     — how much speed can legitimately change between consecutive GPS fixes.
 *                        Higher Q → responds faster to acceleration, less smoothing.
 * measurementNoise (R) — variance of the raw GPS SOG readings.
 *                        Higher R → less trust in each measurement, more smoothing.
 *
 * At 25 Hz with Q = 0.0005 and R = 0.04 the steady-state Kalman gain is ≈ 0.11,
 * giving a smoothing time-constant of ≈ 0.4 s.  That kills 0.1-knot display flicker
 * at constant speed while still tracking a real acceleration/deceleration within ~1 s.
 */
class SogKalmanFilter(
    private val processNoise: Float     = 0.0005f,  // Q (kt²)
    private val measurementNoise: Float = 0.04f     // R (kt²)
) {
    private var estimate: Float = 0f
    private var errorCovariance: Float = 1f
    private var initialized: Boolean = false

    /** Feed in the latest raw SOG reading; returns the filtered estimate. */
    fun update(measurement: Float): Float {
        if (!initialized) {
            estimate = measurement
            initialized = true
            return measurement
        }

        // ── Predict ──────────────────────────────────────────────────────
        // Covariance grows as we're less certain about current speed.
        errorCovariance += processNoise

        // ── Update ───────────────────────────────────────────────────────
        val gain = errorCovariance / (errorCovariance + measurementNoise)
        estimate += gain * (measurement - estimate)
        errorCovariance *= (1f - gain)

        return estimate
    }

    /** Call when Bluetooth disconnects so the next session starts fresh. */
    fun reset() {
        initialized = false
        errorCovariance = 1f
        estimate = 0f
    }
}
