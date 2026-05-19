package com.sailboatracing.session

import android.content.Context
import com.sailboatracing.model.ReplayFrame
import com.sailboatracing.model.SessionMeta
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SessionLoader {

    /**
     * Scans the sessions directory and returns metadata for every CSV file,
     * newest first. Reads only the first and last data rows for speed.
     */
    fun listSessions(context: Context): List<SessionMeta> {
        val dir = context.getExternalFilesDir("sessions") ?: return emptyList()
        return dir.listFiles { f -> f.extension == "csv" }
            ?.sortedByDescending { it.lastModified() }
            ?.mapNotNull { file -> metaFromFile(file) }
            ?: emptyList()
    }

    private fun metaFromFile(file: File): SessionMeta? = try {
        file.bufferedReader().use { reader ->
            reader.readLine() // skip header
            var firstMs = 0L
            var lastMs  = 0L
            var count   = 0
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                if (l.isBlank()) continue
                val ts = l.substringBefore(',').toLongOrNull() ?: continue
                if (count == 0) firstMs = ts
                lastMs = ts
                count++
            }
            if (count == 0) return@use null
            SessionMeta(
                filePath        = file.absolutePath,
                fileName        = file.name,
                date            = SimpleDateFormat("dd MMM yyyy  HH:mm", Locale.getDefault())
                                    .format(Date(file.lastModified())),
                durationMinutes = ((lastMs - firstMs) / 60_000L).toInt(),
                pointCount      = count,
                fileSizeKb      = file.length() / 1024
            )
        }
    } catch (_: Exception) { null }

    /**
     * Parses every row in the CSV into ReplayFrames. Runs on IO thread — can
     * take a second or two for large files.
     */
    fun loadFrames(filePath: String): List<ReplayFrame> = try {
        File(filePath).bufferedReader().use { reader ->
            reader.readLine() // skip header
            val frames = mutableListOf<ReplayFrame>()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                if (l.isBlank()) continue
                try {
                    val f = l.split(",")
                    if (f.size < 16) continue
                    frames.add(ReplayFrame(
                        timestampMs = f[0].toLong(),
                        heading     = f[2].toFloat(),
                        pitch       = f[3].toFloat(),
                        roll        = f[4].toFloat(),
                        lat         = f[10].toDouble(),
                        lon         = f[11].toDouble(),
                        sogKts      = f[12].toFloat(),
                        cogDeg      = f[13].toFloat(),
                        fixType     = f[14].toInt(),
                        rtkStatus   = f[15].trim().toInt()
                    ))
                } catch (_: Exception) {}
            }
            frames
        }
    } catch (_: Exception) { emptyList() }

    fun deleteSession(filePath: String) {
        try { File(filePath).delete() } catch (_: Exception) {}
    }
}
