package com.sailboatracing.bluetooth

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.net.Socket

data class NtripSourceEntry(
    val mountpoint: String,
    val lat: Double,
    val lon: Double,
    val format: String,
    val carrier: Int,
    val fee: String
)

object NtripClient {

    /**
     * Fetches the NTRIP source table from a caster and returns all stream entries.
     * Returns an empty list silently on any network or parse failure.
     */
    suspend fun fetchSourceTable(host: String, port: Int): List<NtripSourceEntry> =
        withContext(Dispatchers.IO) {
            val socket = try {
                Socket(host, port).apply { soTimeout = 15_000 }
            } catch (_: Exception) {
                return@withContext emptyList()
            }
            try {
                val request = "GET / HTTP/1.0\r\n" +
                    "User-Agent: NTRIP SailRacing/1.0\r\n" +
                    "Accept: */*\r\n" +
                    "\r\n"
                socket.getOutputStream().write(request.toByteArray(Charsets.US_ASCII))
                socket.getOutputStream().flush()

                val reader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                val entries = mutableListOf<NtripSourceEntry>()
                var inBody = false
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line ?: break
                    if (!inBody) {
                        if (l.isBlank()) inBody = true
                        continue
                    }
                    if (l.trimEnd() == "ENDSOURCETABLE") break
                    if (!l.startsWith("STR;")) continue
                    val f = l.split(";")
                    if (f.size < 17) continue
                    val lat = f[9].toDoubleOrNull() ?: continue
                    val lon = f[10].toDoubleOrNull() ?: continue
                    entries.add(NtripSourceEntry(
                        mountpoint = f[1],
                        lat = lat,
                        lon = lon,
                        format = f[3],
                        carrier = f[5].toIntOrNull() ?: 0,
                        fee = f[16]
                    ))
                }
                entries
            } catch (_: Exception) {
                emptyList()
            } finally {
                try { socket.close() } catch (_: Exception) {}
            }
        }

    /**
     * Connects to an NTRIP caster and emits RTCM3 binary chunks.
     *
     * [positionProvider] is called periodically to obtain the rover's current (lat, lon).
     * A NMEA GGA sentence is sent to the caster every 5 s so the connection stays alive —
     * most casters (especially VRS) close the stream after ~30 s without a position report.
     * An initial GGA is also sent immediately after the HTTP handshake.
     *
     * Fails silently (empty flow) if there is no internet, the host is unreachable,
     * or authentication fails — the caller should treat no emissions as "no corrections".
     */
    fun stream(
        host: String,
        port: Int,
        mountpoint: String,
        username: String,
        password: String,
        positionProvider: (() -> Pair<Double, Double>?)? = null
    ): Flow<ByteArray> = flow {
        val socket = try {
            Socket(host, port).apply { soTimeout = 10_000 }
        } catch (_: Exception) {
            return@flow
        }
        try {
            val credentials = Base64.encodeToString(
                "$username:$password".toByteArray(Charsets.UTF_8), Base64.NO_WRAP
            )
            val request = "GET /$mountpoint HTTP/1.0\r\n" +
                "Host: $host:$port\r\n" +
                "User-Agent: NTRIP SailRacing/1.0\r\n" +
                "Ntrip-Version: Ntrip/1.0\r\n" +
                "Authorization: Basic $credentials\r\n" +
                "Accept: */*\r\n" +
                "\r\n"
            socket.getOutputStream().write(request.toByteArray(Charsets.US_ASCII))
            socket.getOutputStream().flush()

            val input = socket.getInputStream()

            // Read headers byte-by-byte until \r\n\r\n to avoid buffering binary body data
            val header = StringBuilder()
            var state = 0  // FSM tracking \r\n\r\n sequence
            val oneByte = ByteArray(1)
            val deadline = System.currentTimeMillis() + 10_000L
            while (state < 4 && System.currentTimeMillis() < deadline) {
                if (input.read(oneByte) < 0) return@flow
                val ch = oneByte[0].toInt().and(0xFF)
                header.append(ch.toChar())
                state = when {
                    ch == '\r'.code && (state == 0 || state == 2) -> state + 1
                    ch == '\n'.code && (state == 1 || state == 3) -> state + 1
                    else -> 0
                }
            }
            if (state < 4) return@flow  // timed out reading headers
            if (!header.contains("200")) return@flow  // auth failure or error

            val out = socket.getOutputStream()

            // Send GGA immediately — VRS casters need rover position before sending corrections.
            positionProvider?.invoke()?.let { (lat, lon) ->
                try { out.write(buildNmeaGga(lat, lon).toByteArray(Charsets.US_ASCII)); out.flush() }
                catch (_: Exception) {}
            }

            // Stream RTCM binary chunks, refreshing GGA every 5 s to keep caster alive.
            // SocketTimeoutException means no data arrived — loop back and possibly send GGA
            // rather than breaking the connection (RTCM is bursty; gaps >5 s are normal).
            socket.soTimeout = 5_000
            val buffer = ByteArray(4096)
            var lastGgaSentMs = System.currentTimeMillis()
            while (currentCoroutineContext().isActive) {
                val now = System.currentTimeMillis()
                if (now - lastGgaSentMs >= 5_000L) {
                    positionProvider?.invoke()?.let { (lat, lon) ->
                        try { out.write(buildNmeaGga(lat, lon).toByteArray(Charsets.US_ASCII)); out.flush() }
                        catch (_: Exception) {}
                    }
                    lastGgaSentMs = now
                }
                val n = try {
                    input.read(buffer)
                } catch (_: java.net.SocketTimeoutException) {
                    continue   // no RTCM data yet — loop back, maybe send GGA
                } catch (_: Exception) {
                    break
                }
                if (n < 0) break
                if (n > 0) emit(buffer.copyOf(n))
            }
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Builds a minimal NMEA GGA sentence for the given position.
     * Checksum is XOR of all bytes between $ and * (NMEA convention).
     */
    private fun buildNmeaGga(lat: Double, lon: Double): String {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        val time = String.format(java.util.Locale.US, "%02d%02d%02d.00",
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE),
            cal.get(java.util.Calendar.SECOND))
        val absLat = Math.abs(lat)
        val latDeg = absLat.toInt()
        val latMin = (absLat - latDeg) * 60.0
        val latHemi = if (lat >= 0.0) "N" else "S"
        val absLon = Math.abs(lon)
        val lonDeg = absLon.toInt()
        val lonMin = (absLon - lonDeg) * 60.0
        val lonHemi = if (lon >= 0.0) "E" else "W"
        // DDMM.MMMMM for lat (02d + 08.5f = 10 chars), DDDMM.MMMMM for lon (03d + 08.5f = 11 chars)
        val body = String.format(java.util.Locale.US,
            "GPGGA,%s,%02d%08.5f,%s,%03d%08.5f,%s,1,08,1.0,0.0,M,0.0,M,,",
            time, latDeg, latMin, latHemi, lonDeg, lonMin, lonHemi)
        var cs = 0
        for (c in body) cs = cs xor c.code
        return "\$${body}*${String.format(java.util.Locale.US, "%02X", cs)}\r\n"
    }
}
