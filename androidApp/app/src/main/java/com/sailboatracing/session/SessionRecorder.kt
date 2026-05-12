package com.sailboatracing.session

import android.content.Context
import com.sailboatracing.model.SensorData
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionRecorder(private val context: Context) {

    private var writer: BufferedWriter? = null
    private val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)

    var startMs: Long = 0L
        private set
    var maxDurationMs: Long = 0L
        private set
    var filePath: String = ""
        private set

    fun start(maxHours: Int): String? {
        stop()
        val dir = context.getExternalFilesDir("sessions") ?: return null
        dir.mkdirs()
        val name = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val file = File(dir, "sail_session_$name.csv")
        writer = file.bufferedWriter()
        writer?.write(HEADER)
        startMs = System.currentTimeMillis()
        maxDurationMs = maxHours * 3_600_000L
        filePath = file.absolutePath
        return filePath
    }

    /**
     * Records one sample. Returns false if the max duration has been reached — the caller
     * should call stop() and mark recording as finished.
     */
    fun record(data: SensorData): Boolean {
        val w = writer ?: return false
        if (System.currentTimeMillis() - startMs >= maxDurationMs) {
            stop()
            return false
        }
        w.write(
            "${data.timestampMs}," +
            "${isoFmt.format(Date(data.timestampMs))}," +
            "${"%.2f".format(data.heading)}," +
            "${"%.2f".format(data.pitch)}," +
            "${"%.2f".format(data.roll)}," +
            "${"%.3f".format(data.gyroZ)}," +
            "${"%.4f".format(data.accelX)}," +
            "${"%.4f".format(data.accelY)}," +
            "${"%.4f".format(data.accelZ)}," +
            "${data.imuAccuracy}," +
            "${"%.7f".format(data.lat)}," +
            "${"%.7f".format(data.lon)}," +
            "${"%.3f".format(data.sogKts)}," +
            "${"%.2f".format(data.cogDeg)}," +
            "${data.fixType}," +
            "${data.rtkStatus}\n"
        )
        return true
    }

    fun stop() {
        try { writer?.flush(); writer?.close() } catch (_: Exception) {}
        writer = null
    }

    companion object {
        private const val HEADER =
            "timestamp_ms,datetime,heading_deg,pitch_deg,roll_deg," +
            "gyro_z_dps,accel_x_ms2,accel_y_ms2,accel_z_ms2,imu_accuracy," +
            "lat,lon,sog_kts,cog_deg,fix_type,rtk_status\n"
    }
}
