package com.sailboatracing.preferences

import android.content.Context
import com.sailboatracing.model.DashboardChartType
import com.sailboatracing.model.LatLng
import com.sailboatracing.model.NtripCaster
import com.sailboatracing.model.RaceMark
import com.sailboatracing.model.Rounding
import com.sailboatracing.model.StartLine
import org.json.JSONArray
import org.json.JSONObject

object AppPreferences {
    private const val PREFS_NAME = "sailboat_gps_prefs"
    private const val KEY_MARKS = "marks"
    private const val KEY_START_LINE = "start_line"
    private const val KEY_NEXT_MARK_ID = "next_mark_id"
    private const val KEY_ACTIVE_MARK_INDEX = "active_mark_index"
    private const val KEY_HISTORY_WINDOW = "history_window"
    private const val KEY_HEADING_SHORT_WINDOW = "heading_short_window"
    private const val KEY_HEADING_LONG_WINDOW = "heading_long_window"
    private const val KEY_TRAIL_WINDOW = "trail_window"
    private const val KEY_NARRATE_TIMER = "narrate_timer"
    private const val KEY_GPS_STALE_THRESHOLD = "gps_stale_threshold"
    private const val KEY_SHOW_MAP = "show_map"
    private const val KEY_HEADING_LINES = "heading_lines"
    private const val KEY_HEADING_LINE_METERS = "heading_line_meters"
    private const val KEY_USE_PHONE_GPS = "use_phone_gps"
    private const val KEY_USE_PHONE_IMU = "use_phone_imu"
    private const val KEY_COG_WINDOW = "cog_window"
    private const val KEY_DASHBOARD_CHART = "dashboard_chart"
    private const val KEY_MAX_RECORDING_HOURS = "max_recording_hours"
    private const val KEY_NTRIP_ENABLED = "ntrip_enabled"
    private const val KEY_NTRIP_CASTERS = "ntrip_casters"
    private const val KEY_NTRIP_SELECTED_ID = "ntrip_selected_id"
    private const val KEY_NTRIP_NEXT_ID = "ntrip_next_id"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Marks ──────────────────────────────────────────────────────────

    fun saveMarks(context: Context, marks: List<RaceMark>, nextMarkId: Int) {
        val arr = JSONArray()
        for (m in marks) {
            val obj = JSONObject()
            obj.put("id", m.id)
            obj.put("name", m.name)
            obj.put("lat", m.position.latitude)
            obj.put("lon", m.position.longitude)
            obj.put("rounding", m.rounding.name)
            obj.put("isGate", m.isGate)
            m.gateEnd?.let {
                obj.put("gateLat", it.latitude)
                obj.put("gateLon", it.longitude)
            }
            arr.put(obj)
        }
        prefs(context).edit()
            .putString(KEY_MARKS, arr.toString())
            .putInt(KEY_NEXT_MARK_ID, nextMarkId)
            .apply()
    }

    fun loadMarks(context: Context): Pair<List<RaceMark>, Int> {
        val p = prefs(context)
        val nextId = p.getInt(KEY_NEXT_MARK_ID, 0)
        val json = p.getString(KEY_MARKS, null) ?: return Pair(emptyList(), nextId)
        return try {
            val arr = JSONArray(json)
            val marks = mutableListOf<RaceMark>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val gateEnd = if (obj.has("gateLat")) {
                    LatLng(obj.getDouble("gateLat"), obj.getDouble("gateLon"))
                } else null
                marks.add(
                    RaceMark(
                        id = obj.getInt("id"),
                        name = obj.getString("name"),
                        position = LatLng(obj.getDouble("lat"), obj.getDouble("lon")),
                        rounding = Rounding.valueOf(obj.getString("rounding")),
                        isGate = obj.optBoolean("isGate", false),
                        gateEnd = gateEnd
                    )
                )
            }
            Pair(marks, nextId)
        } catch (_: Exception) {
            Pair(emptyList(), nextId)
        }
    }

    fun clearMarks(context: Context) {
        prefs(context).edit()
            .remove(KEY_MARKS)
            .remove(KEY_NEXT_MARK_ID)
            .remove(KEY_ACTIVE_MARK_INDEX)
            .apply()
    }

    fun saveActiveMarkIndex(context: Context, index: Int) {
        prefs(context).edit().putInt(KEY_ACTIVE_MARK_INDEX, index).apply()
    }

    fun loadActiveMarkIndex(context: Context): Int =
        prefs(context).getInt(KEY_ACTIVE_MARK_INDEX, 0)

    // ── Start Line ─────────────────────────────────────────────────────

    fun saveStartLine(context: Context, startLine: StartLine?) {
        val edit = prefs(context).edit()
        if (startLine == null) {
            edit.remove(KEY_START_LINE)
        } else {
            val obj = JSONObject()
            obj.put("pinLat", startLine.pin.latitude)
            obj.put("pinLon", startLine.pin.longitude)
            obj.put("boatLat", startLine.boat.latitude)
            obj.put("boatLon", startLine.boat.longitude)
            edit.putString(KEY_START_LINE, obj.toString())
        }
        edit.apply()
    }

    fun loadStartLine(context: Context): StartLine? {
        val json = prefs(context).getString(KEY_START_LINE, null) ?: return null
        return try {
            val obj = JSONObject(json)
            StartLine(
                pin = LatLng(obj.getDouble("pinLat"), obj.getDouble("pinLon")),
                boat = LatLng(obj.getDouble("boatLat"), obj.getDouble("boatLon"))
            )
        } catch (_: Exception) {
            null
        }
    }

    // ── Settings ───────────────────────────────────────────────────────

    data class Settings(
        val historyWindowSeconds: Int = 30,
        val headingShortWindowSec: Int = 3,
        val headingLongWindowSec: Int = 10,
        val trailWindowSeconds: Int = 60,
        val narrateTimer: Boolean = true,
        val gpsStaleThresholdSeconds: Int = 5,
        val showMap: Boolean = true,
        val showHeadingLines: Boolean = true,
        val headingLineMeters: Int = 1000,
        val usePhoneGps: Boolean = true,
        val usePhoneImu: Boolean = true,
        val cogWindowSeconds: Int = 1,
        val dashboardCharts: Set<DashboardChartType> = emptySet(),
        val maxRecordingHours: Int = 24,
        val ntripEnabled: Boolean = true,
        val ntripCasters: List<NtripCaster> = NtripCaster.DEFAULTS,
        val ntripSelectedCasterId: Int = 0,
        val ntripNextCasterId: Int = NtripCaster.DEFAULTS.size
    )

    fun saveSettings(
        context: Context,
        historyWindowSeconds: Int,
        headingShortWindowSec: Int,
        headingLongWindowSec: Int,
        trailWindowSeconds: Int,
        narrateTimer: Boolean,
        gpsStaleThresholdSeconds: Int,
        showMap: Boolean,
        showHeadingLines: Boolean,
        headingLineMeters: Int,
        usePhoneGps: Boolean,
        usePhoneImu: Boolean,
        cogWindowSeconds: Int,
        dashboardCharts: Set<DashboardChartType>,
        maxRecordingHours: Int,
        ntripEnabled: Boolean,
        ntripCasters: List<NtripCaster>,
        ntripSelectedCasterId: Int,
        ntripNextCasterId: Int
    ) {
        val castersJson = JSONArray().also { arr ->
            ntripCasters.forEach { c ->
                arr.put(JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("host", c.host)
                    put("port", c.port)
                    put("mountpoint", c.mountpoint)
                    put("username", c.username)
                    put("password", c.password)
                })
            }
        }.toString()
        prefs(context).edit()
            .putInt(KEY_HISTORY_WINDOW, historyWindowSeconds)
            .putInt(KEY_HEADING_SHORT_WINDOW, headingShortWindowSec)
            .putInt(KEY_HEADING_LONG_WINDOW, headingLongWindowSec)
            .putInt(KEY_TRAIL_WINDOW, trailWindowSeconds)
            .putBoolean(KEY_NARRATE_TIMER, narrateTimer)
            .putInt(KEY_GPS_STALE_THRESHOLD, gpsStaleThresholdSeconds)
            .putBoolean(KEY_SHOW_MAP, showMap)
            .putBoolean(KEY_HEADING_LINES, showHeadingLines)
            .putInt(KEY_HEADING_LINE_METERS, headingLineMeters)
            .putBoolean(KEY_USE_PHONE_GPS, usePhoneGps)
            .putBoolean(KEY_USE_PHONE_IMU, usePhoneImu)
            .putInt(KEY_COG_WINDOW, cogWindowSeconds)
            .putString(KEY_DASHBOARD_CHART, dashboardCharts.joinToString(",") { it.name })
            .putInt(KEY_MAX_RECORDING_HOURS, maxRecordingHours)
            .putBoolean(KEY_NTRIP_ENABLED, ntripEnabled)
            .putString(KEY_NTRIP_CASTERS, castersJson)
            .putInt(KEY_NTRIP_SELECTED_ID, ntripSelectedCasterId)
            .putInt(KEY_NTRIP_NEXT_ID, ntripNextCasterId)
            .apply()
    }

    fun loadSettings(context: Context): Settings {
        val p = prefs(context)
        return Settings(
            historyWindowSeconds = p.getInt(KEY_HISTORY_WINDOW, 30),
            headingShortWindowSec = p.getInt(KEY_HEADING_SHORT_WINDOW, 3),
            headingLongWindowSec = p.getInt(KEY_HEADING_LONG_WINDOW, 10),
            trailWindowSeconds = p.getInt(KEY_TRAIL_WINDOW, 60),
            narrateTimer = p.getBoolean(KEY_NARRATE_TIMER, true),
            gpsStaleThresholdSeconds = p.getInt(KEY_GPS_STALE_THRESHOLD, 5),
            showMap = p.getBoolean(KEY_SHOW_MAP, true),
            showHeadingLines = p.getBoolean(KEY_HEADING_LINES, true),
            headingLineMeters = p.getInt(KEY_HEADING_LINE_METERS, 1000),
            usePhoneGps = p.getBoolean(KEY_USE_PHONE_GPS, true),
            usePhoneImu = p.getBoolean(KEY_USE_PHONE_IMU, true),
            cogWindowSeconds = p.getInt(KEY_COG_WINDOW, 1),
            dashboardCharts = p.getString(KEY_DASHBOARD_CHART, null)
                ?.split(",")
                ?.mapNotNull { runCatching { DashboardChartType.valueOf(it.trim()) }.getOrNull() }
                ?.toSet()
                ?: emptySet(),
            maxRecordingHours = p.getInt(KEY_MAX_RECORDING_HOURS, 24),
            ntripEnabled = p.getBoolean(KEY_NTRIP_ENABLED, true),
            ntripCasters = run {
                val json = p.getString(KEY_NTRIP_CASTERS, null) ?: return@run NtripCaster.DEFAULTS
                try {
                    val arr = JSONArray(json)
                    (0 until arr.length()).map { i ->
                        val o = arr.getJSONObject(i)
                        val host = o.getString("host")
                        val savedUsername = o.optString("username", "")
                        val savedPassword = o.optString("password", "")
                        // Back-fill credentials for known casters if they were saved before defaults were set
                        val defaultCreds = NtripCaster.DEFAULTS.find { it.host == host }
                        NtripCaster(
                            id = o.getInt("id"),
                            name = o.getString("name"),
                            host = host,
                            port = o.getInt("port"),
                            mountpoint = o.optString("mountpoint", ""),
                            username = if (savedUsername.isBlank() && defaultCreds != null) defaultCreds.username else savedUsername,
                            password = if (savedPassword.isBlank() && defaultCreds != null) defaultCreds.password else savedPassword
                        )
                    }
                } catch (_: Exception) { NtripCaster.DEFAULTS }
            },
            ntripSelectedCasterId = p.getInt(KEY_NTRIP_SELECTED_ID, 0),
            ntripNextCasterId = p.getInt(KEY_NTRIP_NEXT_ID, NtripCaster.DEFAULTS.size)
        )
    }

    fun clearSettings(context: Context) {
        prefs(context).edit()
            .remove(KEY_HISTORY_WINDOW)
            .remove(KEY_HEADING_SHORT_WINDOW)
            .remove(KEY_HEADING_LONG_WINDOW)
            .remove(KEY_TRAIL_WINDOW)
            .remove(KEY_NARRATE_TIMER)
            .remove(KEY_GPS_STALE_THRESHOLD)
            .remove(KEY_SHOW_MAP)
            .remove(KEY_HEADING_LINES)
            .remove(KEY_HEADING_LINE_METERS)
            .remove(KEY_USE_PHONE_GPS)
            .remove(KEY_USE_PHONE_IMU)
            .remove(KEY_COG_WINDOW)
            .remove(KEY_DASHBOARD_CHART)
            .apply()
    }
}
