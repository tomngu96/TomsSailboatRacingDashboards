package com.sailboatracing.model

data class LatLng(val latitude: Double, val longitude: Double)

// ── Session replay ─────────────────────────────────────────────────────────

data class ReplayFrame(
    val timestampMs: Long,
    val heading:     Float,
    val pitch:       Float,
    val roll:        Float,
    val lat:         Double,
    val lon:         Double,
    val sogKts:      Float,
    val cogDeg:      Float,
    val fixType:     Int,
    val rtkStatus:   Int
)

data class SessionMeta(
    val filePath:        String,
    val fileName:        String,
    val date:            String,   // display string, e.g. "15 Jan 2024  14:30"
    val durationMinutes: Int,
    val pointCount:      Int,
    val fileSizeKb:      Long
)

data class NtripCaster(
    val id: Int,
    val name: String,
    val host: String,
    val port: Int = 2101,
    val mountpoint: String = "",
    val username: String = "",
    val password: String = ""
) {
    companion object {
        val DEFAULTS = listOf(
            NtripCaster(id = 0, name = "RTK2go", host = "rtk2go.com", port = 2101, username = "rtk2go", password = "none"),
            NtripCaster(id = 1, name = "Centipede", host = "caster.centipede.fr", port = 2101, username = "centipede", password = "centipede")
        )
    }
}

data class SensorData(
    val timestampMs: Long,
    val heading: Float,
    val pitch: Float,
    val roll: Float,
    val gyroZ: Float,
    val accelX: Float,
    val accelY: Float,
    val accelZ: Float,
    val imuAccuracy: Int,
    val lat: Double,
    val lon: Double,
    val sogKts: Float,
    val cogDeg: Float,
    val fixType: Int,
    // RTK carrier solution type (F9P only): 0=none, 1=float (~10-30 cm), 2=fixed (~1-2 cm)
    val rtkStatus: Int = 0,
    // True only when this packet carries freshly-received GPS data (not carried over from IMU injection).
    // Used to update lastGpsFixMs without counting IMU-only frames as a GPS heartbeat.
    val isDirectGpsReading: Boolean = false
)

/**
 * Snapshot of all data the dashboard map needs, emitted at a throttled rate
 * (max mapRefreshIntervalMs, and only when the boat has moved/turned enough).
 * Decouples the map from the 25 Hz sensor flow.
 */
data class MapSnapshot(
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val heading: Float = 0f,
    val imuAccuracy: Int = 0,
    val cogDeg: Float = 0f,
    val sogKts: Float = 0f,
    val fixType: Int = 0,
    val historicalCogDeg: Float? = null,
    val startLine: StartLine? = null,
    val marks: List<RaceMark> = emptyList(),
    val activeMarkIndex: Int = 0,
    val trail: List<SensorData> = emptyList(),
    val showHeadingLines: Boolean = true,
    val headingLineMeters: Int = 1000
)

/** A single bearing sighting used by the mark triangulator. */
data class Sighting(
    val id: Int,
    val lat: Double,
    val lon: Double,
    val bearingDeg: Float,
    val active: Boolean = true
)

enum class Tack {
    STARBOARD, PORT
}

enum class HeadingTrend {
    LIFTED_STRONG, LIFTED, NEUTRAL, HEADED, HEADED_STRONG
}

data class StartLine(
    val pin: LatLng,
    val boat: LatLng
)

enum class Rounding {
    PORT, STARBOARD
}

enum class DashboardChartType { SPEED, HEADING, VMG, DIRECTION, ALL }

data class RaceMark(
    val id: Int,
    val name: String,
    val position: LatLng,
    val rounding: Rounding,
    val isGate: Boolean = false,
    val gateEnd: LatLng? = null
)

data class TimerState(
    val targetMs: Long = 0L,
    val remainingMs: Long = 0L,
    val running: Boolean = false,
    val finished: Boolean = false,
    // Non-null when the timer was set to an absolute clock time — ticks stay
    // wall-clock synced so no drift even if START is tapped late.
    val targetEpochMs: Long? = null
)

data class StartLineStatus(
    // Time based on current COG ray intersecting the extended start line
    val trueTimeToLineSeconds: Float?,
    // Time based on perpendicular (shortest-path) distance / SOG
    val optimalTimeToLineSeconds: Float?,
    val distanceToLineNm: Float,
    // Positive = running late (arrive after gun), negative = running early/fast (OCS risk)
    val earlyOrLate: Float
)

data class RaceState(
    val connected: Boolean = false,
    val pairedDevices: List<android.bluetooth.BluetoothDevice> = emptyList(),
    val latestData: SensorData? = null,
    val headingTrend: HeadingTrend = HeadingTrend.NEUTRAL,
    val trendDegrees: Float = 0f,
    val tack: Tack = Tack.STARBOARD,
    val startLine: StartLine? = null,
    val startLineStatus: StartLineStatus? = null,
    val marks: List<RaceMark> = emptyList(),
    val activeMarkIndex: Int = 0,
    val vmgKts: Float? = null,
    val distToMarkNm: Float? = null,
    val bearingToMarkDeg: Float? = null,
    val timerState: TimerState = TimerState(),
    val historyWindowSeconds: Int = 30,
    val showMap: Boolean = true,
    val showHeadingLines: Boolean = true,
    val headingLineMeters: Int = 1000,
    val imuInverted: Boolean = false,
    // Mounting offset calibration: pitch/roll values recorded when the boat was level.
    // Subtracted from raw IMU readings so a tilted phone reports 0° heel when the hull is flat.
    val imuMountOffsetPitch: Float = 0f,
    val imuMountOffsetRoll: Float = 0f,
    val usePhoneGps: Boolean = true,
    val phoneGpsActive: Boolean = false,
    val usePhoneImu: Boolean = true,
    val phoneImuActive: Boolean = false,
    // GPS staleness tracking — avoids flickering UI at slow GPS update rates (e.g. 1 Hz phone GPS)
    val lastGpsFixMs: Long = 0L,
    val gpsStale: Boolean = false,
    val gpsStaleThresholdSeconds: Int = 5,
    val narrateTimer: Boolean = true,
    val headingShortWindowSec: Int = 3,
    val headingLongWindowSec: Int = 10,
    // Non-null while only one end of the start line has been set
    val pendingStartPin: LatLng? = null,
    val pendingStartBoat: LatLng? = null,
    val trailWindowSeconds: Int = 60,
    // Observed COG averaged over the last cogWindowSeconds (circular mean of GPS COG readings)
    val cogWindowSeconds: Int = 1,
    val historicalCogDeg: Float? = null,
    val dashboardCharts: Set<DashboardChartType> = emptySet(),
    // Session recording — isRecording/startMs/filePath are runtime only, maxRecordingHours persisted
    val isRecording: Boolean = false,
    val recordingStartMs: Long = 0L,
    val recordingFilePath: String = "",
    val maxRecordingHours: Int = 24,
    // NTRIP — settings persisted, runtime fields (ntripConnected/Auto*/RetryCount) are not persisted
    val ntripEnabled: Boolean = true,
    val ntripCasters: List<NtripCaster> = NtripCaster.DEFAULTS,
    val ntripSelectedCasterId: Int = 0,
    val ntripConnected: Boolean = false,
    // Number of reconnect attempts since last successful connection (0 = never tried yet / just started).
    // Used to distinguish "initial connecting…" from "failed, retrying — GPS still active".
    val ntripRetryCount: Int = 0,
    // Auto-select state (populated at connect time when mountpoint is blank)
    val ntripAutoMountpoint: String = "",
    val ntripNearbyMountpoints: List<String> = emptyList(),
    val ntripAutoMountpointIndex: Int = 0,
    // Kalman-filtered SOG for display — raw sogKts in latestData is still used for recording/charts
    val smoothedSogKts: Float = 0f,
    // Latest phone IMU heading — updated regardless of BT state for start-line preview/override
    val phoneImuHeading: Float? = null,
    // Dashboard map throttle — how often to rebuild overlays and minimum change to trigger a redraw
    val mapRefreshIntervalMs: Int = 200,    // 200 ms = 5 Hz default
    val mapMinMovementMeters: Int = 2,      // metres
    val mapMinHeadingChangeDeg: Int = 3,    // degrees
    // Triangulator sightings — session-only, cleared on explicit "clear all"
    val triangulatorSightings: List<Sighting> = emptyList()
)
