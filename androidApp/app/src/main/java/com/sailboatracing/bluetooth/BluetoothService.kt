package com.sailboatracing.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.util.UUID

class BluetoothService(private val context: Context) {

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _lines = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val lines: SharedFlow<String> = _lines.asSharedFlow()

    private var socket: BluetoothSocket? = null
    private var readJob: Job? = null

    fun pairedDevices(): List<BluetoothDevice> {
        if (bluetoothAdapter == null) return emptyList()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // On Android 12+, BLUETOOTH_CONNECT permission required
                bluetoothAdapter.bondedDevices?.toList() ?: emptyList()
            } else {
                bluetoothAdapter.bondedDevices?.toList() ?: emptyList()
            }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    fun connect(address: String, scope: CoroutineScope) {
        readJob?.cancel()
        readJob = scope.launch(Dispatchers.IO) {
            try {
                val device: BluetoothDevice = bluetoothAdapter?.getRemoteDevice(address)
                    ?: return@launch
                val newSocket: BluetoothSocket = try {
                    device.createRfcommSocketToServiceRecord(SPP_UUID)
                } catch (e: SecurityException) {
                    return@launch
                }
                socket?.close()
                socket = newSocket
                try {
                    newSocket.connect()
                } catch (e: IOException) {
                    try { newSocket.close() } catch (_: IOException) {}
                    socket = null
                    withContext(Dispatchers.Main) { _connected.value = false }
                    return@launch
                }
                withContext(Dispatchers.Main) { _connected.value = true }
                readLoop(newSocket.inputStream, this)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { _connected.value = false }
            }
        }
    }

    private suspend fun readLoop(inputStream: InputStream, scope: CoroutineScope) {
        val buffer = StringBuilder()
        val byteArray = ByteArray(1024)
        try {
            while (scope.isActive) {
                val bytesRead = try {
                    inputStream.read(byteArray)
                } catch (e: IOException) {
                    break
                }
                if (bytesRead < 0) break
                val chunk = String(byteArray, 0, bytesRead, Charsets.US_ASCII)
                buffer.append(chunk)
                // Extract complete lines
                while (true) {
                    val newlineIndex = buffer.indexOf('\n')
                    if (newlineIndex < 0) break
                    val line = buffer.substring(0, newlineIndex).trimEnd('\r')
                    buffer.delete(0, newlineIndex + 1)
                    if (line.isNotEmpty()) {
                        _lines.emit(line)
                    }
                }
            }
        } finally {
            withContext(Dispatchers.Main) { _connected.value = false }
            try { socket?.close() } catch (_: IOException) {}
            socket = null
        }
    }

    fun disconnect() {
        readJob?.cancel()
        readJob = null
        try { socket?.close() } catch (_: IOException) {}
        socket = null
        _connected.value = false
    }
}
