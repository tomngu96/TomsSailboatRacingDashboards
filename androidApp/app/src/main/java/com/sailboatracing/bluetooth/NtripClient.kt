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
     * Fails silently (empty flow) if there is no internet, the host is unreachable,
     * or authentication fails — the caller should treat no emissions as "no corrections".
     */
    fun stream(
        host: String,
        port: Int,
        mountpoint: String,
        username: String,
        password: String
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

            // Stream RTCM binary chunks
            socket.soTimeout = 5_000
            val buffer = ByteArray(4096)
            while (currentCoroutineContext().isActive) {
                val n = try {
                    input.read(buffer)
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
}
