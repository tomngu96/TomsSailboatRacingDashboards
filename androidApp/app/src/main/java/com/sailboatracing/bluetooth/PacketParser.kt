package com.sailboatracing.bluetooth

import com.sailboatracing.model.SensorData

object PacketParser {

    fun parse(line: String): SensorData? {
        return try {
            // Must start with $SAL,
            if (!line.startsWith("\$SAL,")) return null

            // Find CRC delimiter
            val starIndex = line.lastIndexOf('*')
            if (starIndex < 0) return null

            // CRC is XOR of all bytes between $ and * (exclusive of $ and *)
            val body = line.substring(1, starIndex) // everything between $ and *
            val expectedCrc = line.substring(starIndex + 1).trim().toIntOrNull(16) ?: return null

            var computedCrc = 0
            for (ch in body) {
                computedCrc = computedCrc xor ch.code
            }
            if (computedCrc != expectedCrc) return null

            // Strip the $SAL, prefix and everything after *
            val payload = line.substring(5, starIndex) // after "$SAL,"
            val fields = payload.split(",")

            // Minimum fields: hdg, pitch, roll, gyroZ, ax, ay, az, imuAcc = 8 fields
            if (fields.size < 8) return null

            val heading = fields[0].toFloatOrNull() ?: return null
            val pitch = fields[1].toFloatOrNull() ?: return null
            val roll = fields[2].toFloatOrNull() ?: return null
            val gyroZ = fields[3].toFloatOrNull() ?: return null
            val ax = fields[4].toFloatOrNull() ?: return null
            val ay = fields[5].toFloatOrNull() ?: return null
            val az = fields[6].toFloatOrNull() ?: return null
            val imuAcc = fields[7].toIntOrNull() ?: return null

            // GPS fields optional (fields 8-13)
            val lat = if (fields.size > 8) fields[8].toDoubleOrNull() ?: 0.0 else 0.0
            val lon = if (fields.size > 9) fields[9].toDoubleOrNull() ?: 0.0 else 0.0
            val sogKts = if (fields.size > 10) fields[10].toFloatOrNull() ?: 0f else 0f
            val cog = if (fields.size > 11) fields[11].toFloatOrNull() ?: 0f else 0f
            val fixType = if (fields.size > 12) fields[12].toIntOrNull() ?: 0 else 0

            SensorData(
                timestampMs = System.currentTimeMillis(),
                heading = heading,
                pitch = pitch,
                roll = roll,
                gyroZ = gyroZ,
                accelX = ax,
                accelY = ay,
                accelZ = az,
                imuAccuracy = imuAcc,
                lat = lat,
                lon = lon,
                sogKts = sogKts,
                cogDeg = cog,
                fixType = fixType,
                isDirectGpsReading = fixType >= 2
            )
        } catch (e: Exception) {
            null
        }
    }
}
