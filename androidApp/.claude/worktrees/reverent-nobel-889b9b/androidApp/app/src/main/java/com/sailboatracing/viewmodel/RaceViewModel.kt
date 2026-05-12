package com.sailboatracing.viewmodel

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sailboatracing.algorithm.HeadedLiftedDetector
import com.sailboatracing.algorithm.StartLineCalculator
import com.sailboatracing.algorithm.VMGCalculator
import com.sailboatracing.bluetooth.BluetoothService
import com.sailboatracing.bluetooth.PacketParser
import com.sailboatracing.location.PhoneGpsService
import com.sailboatracing.location.PhoneImuService
import com.sailboatracing.model.DashboardChartType
import com.sailboatracing.model.LatLng
import com.sailboatracing.model.RaceMark
import com.sailboatracing.model.RaceState
import com.sailboatracing.model.Rounding
import com.sailboatracing.model.SensorData
import com.sailboatracing.model.StartLine
import com.sailboatracing.model.Tack
import com.sailboatracing.model.TimerState
import com.sailboatracing.preferences.AppPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class RaceViewModel(private val app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(RaceState())
    val state: StateFlow<RaceState> = _state.asStateFlow()

    val bluetoothService = BluetoothService(app.applicationContext)
    private val headedLiftedDetector = HeadedLiftedDetector()
    private val phoneGpsService = PhoneGpsService(app.applicationContext)
    private val phoneImuService = PhoneImuService(app.applicationContext)

    private var timerJob: Job? = null
    private var phoneGpsJob: Job? = null
    private var phoneImuJob: Job? = null
    var nextMarkId = 0

    private var lastGoodHeading: Float? = null
    // Kept at 3 minutes regardless of the display historyWindowSeconds, so the headed/lifted
    // detector always has enough baseline data.
    private var detectorHistory: List<SensorData> = emptyList()

    // Map viewport state — stored outside StateFlow so map survives tab switches without
    // triggering Compose recomposition on every pan/zoom event.
    var mapZoom: Double = 15.0
    var mapCenterLat: Double = 0.0
    var mapCenterLon: Double = 0.0
    // True once the user has manually panned/zoomed — suppresses auto-center on tab re-entry.
    var mapUserPanned: Boolean = false

    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(app.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale.US
        }
    }

    init {
        loadPersistedData()

        viewModelScope.launch {
            bluetoothService.lines.collect { line ->
                PacketParser.parse(line)?.let { onNewData(it) }
            }
        }
        viewModelScope.launch {
            bluetoothService.connected.collect { connected ->
                _state.update { it.copy(connected = connected) }
            }
        }
        // GPS staleness check — runs every second and marks GPS stale if no direct fix recently
        viewModelScope.launch {
            while (true) {
                delay(1000L)
                _state.update { current ->
                    val threshold = current.gpsStaleThresholdSeconds * 1000L
                    val stale = current.lastGpsFixMs > 0L &&
                        (System.currentTimeMillis() - current.lastGpsFixMs) > threshold
                    current.copy(gpsStale = stale)
                }
            }
        }
        loadPairedDevices()
    }

    private fun loadPersistedData() {
        val ctx = app.applicationContext

        val settings = AppPreferences.loadSettings(ctx)
        val (marks, loadedNextId) = AppPreferences.loadMarks(ctx)
        val startLine = AppPreferences.loadStartLine(ctx)
        val activeMarkIndex = AppPreferences.loadActiveMarkIndex(ctx)
            .coerceIn(0, (marks.size - 1).coerceAtLeast(0))

        nextMarkId = loadedNextId

        _state.update { current ->
            current.copy(
                marks = marks,
                activeMarkIndex = activeMarkIndex,
                startLine = startLine,
                historyWindowSeconds = settings.historyWindowSeconds,
                headingShortWindowSec = settings.headingShortWindowSec,
                headingLongWindowSec = settings.headingLongWindowSec,
                trailWindowSeconds = settings.trailWindowSeconds,
                narrateTimer = settings.narrateTimer,
                gpsStaleThresholdSeconds = settings.gpsStaleThresholdSeconds,
                showMap = settings.showMap,
                usePhoneGps = settings.usePhoneGps,
                usePhoneImu = settings.usePhoneImu,
                cogWindowSeconds = settings.cogWindowSeconds,
                dashboardCharts = settings.dashboardCharts
            )
        }

        // Re-start phone services if they were enabled
        if (settings.usePhoneGps) startPhoneGps()
        if (settings.usePhoneImu) startPhoneImu()
    }

    private fun saveMarks() {
        AppPreferences.saveMarks(app.applicationContext, _state.value.marks, nextMarkId)
    }

    private fun saveStartLine() {
        AppPreferences.saveStartLine(app.applicationContext, _state.value.startLine)
    }

    private fun saveSettings() {
        val s = _state.value
        AppPreferences.saveSettings(
            app.applicationContext,
            historyWindowSeconds = s.historyWindowSeconds,
            headingShortWindowSec = s.headingShortWindowSec,
            headingLongWindowSec = s.headingLongWindowSec,
            trailWindowSeconds = s.trailWindowSeconds,
            narrateTimer = s.narrateTimer,
            gpsStaleThresholdSeconds = s.gpsStaleThresholdSeconds,
            showMap = s.showMap,
            usePhoneGps = s.usePhoneGps,
            usePhoneImu = s.usePhoneImu,
            cogWindowSeconds = s.cogWindowSeconds,
            dashboardCharts = s.dashboardCharts
        )
    }

    fun loadPairedDevices() {
        val devices = bluetoothService.pairedDevices()
        _state.update { it.copy(pairedDevices = devices) }
    }

    fun connect(address: String) {
        // Disable phone sensor fallbacks when switching to hardware
        _state.update { it.copy(usePhoneGps = false, usePhoneImu = false) }
        stopPhoneGps()
        stopPhoneImu()
        saveSettings()
        bluetoothService.connect(address, viewModelScope)
    }

    fun disconnect() {
        bluetoothService.disconnect()
    }

    fun setTack(tack: Tack) {
        _state.update { it.copy(tack = tack) }
    }

    // ── Timer ──────────────────────────────────────────────────────────

    fun addMinuteToTimer() {
        _state.update { current ->
            val ts = current.timerState
            if (ts.running) {
                current.copy(timerState = ts.copy(targetMs = ts.targetMs + 60_000L))
            } else {
                current.copy(
                    timerState = ts.copy(
                        targetMs = ts.targetMs + 60_000L,
                        remainingMs = ts.remainingMs + 60_000L,
                        finished = false
                    )
                )
            }
        }
    }

    fun clearTimer() {
        timerJob?.cancel()
        timerJob = null
        _state.update { it.copy(timerState = TimerState()) }
    }

    fun setTimerDuration(ms: Long) {
        timerJob?.cancel()
        timerJob = null
        _state.update { current ->
            current.copy(
                timerState = current.timerState.copy(
                    targetMs = ms,
                    remainingMs = ms,
                    running = false,
                    finished = false,
                    targetEpochMs = null
                )
            )
        }
    }

    fun startTimer() {
        val ts = _state.value.timerState
        if (ts.running) return
        if (ts.remainingMs == 0L) {
            _state.update { it.copy(timerState = it.timerState.copy(finished = true)) }
            if (_state.value.narrateTimer) tts?.speak("Go", TextToSpeech.QUEUE_FLUSH, null, null)
            return
        }
        _state.update { it.copy(timerState = it.timerState.copy(running = true, finished = false)) }
        launchCountdownTimer()
    }

    private fun launchCountdownTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var prevRemainingMs = _state.value.timerState.remainingMs
            while (true) {
                delay(100L)
                _state.update { current ->
                    val ts = current.timerState
                    if (!ts.running) return@update current
                    val newRemaining = if (ts.targetEpochMs != null) {
                        (ts.targetEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
                    } else {
                        (ts.remainingMs - 100L).coerceAtLeast(0L)
                    }
                    if (newRemaining <= 0L) {
                        current.copy(timerState = ts.copy(remainingMs = 0L, running = false, finished = true))
                    } else {
                        current.copy(timerState = ts.copy(remainingMs = newRemaining))
                    }
                }
                val ts = _state.value.timerState
                if (_state.value.narrateTimer) {
                    if (ts.finished && prevRemainingMs > 0L) {
                        tts?.speak("Go", TextToSpeech.QUEUE_FLUSH, null, null)
                    } else {
                        ttsThresholdCrossed(prevRemainingMs, ts.remainingMs)?.let { threshMs ->
                            tts?.speak(ttsText(threshMs), TextToSpeech.QUEUE_FLUSH, null, null)
                        }
                    }
                }
                prevRemainingMs = ts.remainingMs
                if (!ts.running) break
            }
        }
    }

    private fun ttsThresholdCrossed(prevMs: Long, currentMs: Long): Long? {
        if (currentMs >= prevMs) return null
        if (prevMs > 300_000L) {
            val k = (prevMs - 1L) / 60_000L
            val t = k * 60_000L
            if (t >= currentMs && t >= 300_000L) return t
        }
        if (prevMs > 60_000L) {
            val hi = minOf(prevMs - 1L, 299_999L)
            if (hi >= 60_000L) {
                val k = hi / 30_000L
                val t = k * 30_000L
                if (t >= currentMs && t >= 60_000L) return t
            }
        }
        if (prevMs > 5_000L) {
            val hi = minOf(prevMs - 1L, 59_999L)
            if (hi >= 5_000L) {
                val k = hi / 5_000L
                val t = k * 5_000L
                if (t >= currentMs && t >= 5_000L) return t
            }
        }
        return null
    }

    private fun ttsText(threshMs: Long): String {
        val totalSec = threshMs / 1000L
        val minutes = totalSec / 60L
        val seconds = totalSec % 60L
        return if (threshMs >= 60_000L) {
            val minWord = if (minutes == 1L) "minute" else "minutes"
            if (seconds == 0L) "$minutes $minWord left"
            else "$minutes $minWord ${seconds} ${if (seconds == 1L) "second" else "seconds"} left"
        } else {
            "$totalSec ${if (totalSec == 1L) "second" else "seconds"} left"
        }
    }

    fun setTimerToTime(targetEpochMs: Long) {
        timerJob?.cancel()
        val remaining = (targetEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
        _state.update { current ->
            current.copy(
                timerState = current.timerState.copy(
                    targetMs = remaining,
                    remainingMs = remaining,
                    targetEpochMs = targetEpochMs,
                    running = true,
                    finished = false
                )
            )
        }
        launchCountdownTimer()
    }

    // ── Start Line ─────────────────────────────────────────────────────

    fun markStartLinePin() {
        val st = _state.value
        val data = st.latestData ?: return
        if (st.gpsStale || st.lastGpsFixMs == 0L) return
        markStartLinePinAt(data.lat, data.lon)
    }

    fun markStartLineBoat() {
        val st = _state.value
        val data = st.latestData ?: return
        if (st.gpsStale || st.lastGpsFixMs == 0L) return
        markStartLineBoatAt(data.lat, data.lon)
    }

    fun markStartLinePinAt(lat: Double, lon: Double) {
        val pos = LatLng(lat, lon)
        _state.update { current ->
            val existing = current.startLine
            if (existing != null && current.pendingStartPin == null) {
                current.copy(startLine = existing.copy(pin = pos))
            } else {
                current.copy(
                    pendingStartPin = pos,
                    startLine = StartLine(pin = pos, boat = pos)
                )
            }
        }
        saveStartLine()
    }

    fun markStartLineBoatAt(lat: Double, lon: Double) {
        val pos = LatLng(lat, lon)
        _state.update { current ->
            when {
                current.pendingStartPin != null ->
                    current.copy(
                        startLine = StartLine(pin = current.pendingStartPin, boat = pos),
                        pendingStartPin = null
                    )
                current.startLine != null ->
                    current.copy(startLine = current.startLine.copy(boat = pos))
                else -> current
            }
        }
        saveStartLine()
    }

    fun clearStartLine() {
        _state.update { it.copy(startLine = null, startLineStatus = null, pendingStartPin = null) }
        saveStartLine()
    }

    // ── Marks ──────────────────────────────────────────────────────────

    fun addMarkHere(name: String, rounding: Rounding, isGate: Boolean = false, gateEnd: LatLng? = null) {
        val st = _state.value
        val data = st.latestData ?: return
        if (st.gpsStale || st.lastGpsFixMs == 0L) return
        val id = nextMarkId++
        val mark = RaceMark(
            id = id,
            name = name.ifBlank { "m${id + 1}" },
            position = LatLng(data.lat, data.lon),
            rounding = rounding,
            isGate = isGate,
            gateEnd = gateEnd
        )
        _state.update { it.copy(marks = it.marks + mark) }
        saveMarks()
    }

    fun addMarkAt(name: String, lat: Double, lon: Double, rounding: Rounding, isGate: Boolean = false, gateEnd: LatLng? = null) {
        val id = nextMarkId++
        val mark = RaceMark(
            id = id,
            name = name.ifBlank { "m${id + 1}" },
            position = LatLng(lat, lon),
            rounding = rounding,
            isGate = isGate,
            gateEnd = gateEnd
        )
        _state.update { it.copy(marks = it.marks + mark) }
        saveMarks()
    }

    fun editMark(id: Int, name: String, rounding: Rounding, position: LatLng, isGate: Boolean = false, gateEnd: LatLng? = null) {
        _state.update { current ->
            current.copy(marks = current.marks.map { mark ->
                if (mark.id == id) mark.copy(name = name, rounding = rounding, position = position, isGate = isGate, gateEnd = gateEnd)
                else mark
            })
        }
        saveMarks()
    }

    fun removeMark(id: Int) {
        _state.update { current ->
            val newMarks = current.marks.filter { it.id != id }
            val newActiveIndex = current.activeMarkIndex.coerceAtMost((newMarks.size - 1).coerceAtLeast(0))
            current.copy(marks = newMarks, activeMarkIndex = newActiveIndex)
        }
        saveMarks()
        AppPreferences.saveActiveMarkIndex(app.applicationContext, _state.value.activeMarkIndex)
    }

    fun reorderMarks(from: Int, to: Int) {
        _state.update { current ->
            val marks = current.marks.toMutableList()
            if (from < 0 || from >= marks.size || to < 0 || to >= marks.size) return@update current
            val item = marks.removeAt(from)
            marks.add(to, item)
            current.copy(marks = marks)
        }
        saveMarks()
    }

    fun duplicateMark(id: Int) {
        _state.update { current ->
            val source = current.marks.find { it.id == id } ?: return@update current
            val newId = nextMarkId++
            val copy = source.copy(id = newId, name = "${source.name} copy")
            current.copy(marks = current.marks + copy)
        }
        saveMarks()
    }

    fun resetMarks() {
        _state.update { it.copy(marks = emptyList(), activeMarkIndex = 0, startLine = null, startLineStatus = null, pendingStartPin = null) }
        nextMarkId = 0
        AppPreferences.clearMarks(app.applicationContext)
        AppPreferences.saveStartLine(app.applicationContext, null)
    }

    fun advanceMark() {
        _state.update { current ->
            if (current.activeMarkIndex < current.marks.size - 1)
                current.copy(activeMarkIndex = current.activeMarkIndex + 1)
            else current
        }
        AppPreferences.saveActiveMarkIndex(app.applicationContext, _state.value.activeMarkIndex)
    }

    fun previousMark() {
        _state.update { current ->
            if (current.activeMarkIndex > 0)
                current.copy(activeMarkIndex = current.activeMarkIndex - 1)
            else current
        }
        AppPreferences.saveActiveMarkIndex(app.applicationContext, _state.value.activeMarkIndex)
    }

    fun setActiveMark(index: Int) {
        _state.update { current ->
            current.copy(
                activeMarkIndex = index.coerceIn(0, (current.marks.size - 1).coerceAtLeast(0))
            )
        }
        AppPreferences.saveActiveMarkIndex(app.applicationContext, _state.value.activeMarkIndex)
    }

    // ── Settings ───────────────────────────────────────────────────────

    fun setHistoryWindow(seconds: Int) {
        _state.update { it.copy(historyWindowSeconds = seconds) }
        saveSettings()
    }

    fun setShowMap(show: Boolean) {
        _state.update { it.copy(showMap = show) }
        saveSettings()
    }

    fun setGpsStaleThreshold(seconds: Int) {
        _state.update { it.copy(gpsStaleThresholdSeconds = seconds) }
        saveSettings()
    }

    fun setNarrateTimer(enabled: Boolean) {
        _state.update { it.copy(narrateTimer = enabled) }
        saveSettings()
    }

    fun setTrailWindow(seconds: Int) {
        _state.update { it.copy(trailWindowSeconds = seconds) }
        saveSettings()
    }

    fun setHeadingShortWindow(seconds: Int) {
        _state.update { it.copy(headingShortWindowSec = seconds) }
        saveSettings()
    }

    fun setHeadingLongWindow(seconds: Int) {
        _state.update { it.copy(headingLongWindowSec = seconds) }
        saveSettings()
    }

    fun setCogWindow(seconds: Int) {
        _state.update { it.copy(cogWindowSeconds = seconds) }
        saveSettings()
    }

    fun toggleDashboardChart(type: DashboardChartType) {
        _state.update { current ->
            val charts = current.dashboardCharts
            current.copy(dashboardCharts = if (type in charts) charts - type else charts + type)
        }
        saveSettings()
    }

    fun setUsePhoneGps(enabled: Boolean) {
        _state.update { it.copy(usePhoneGps = enabled) }
        saveSettings()
        if (enabled) startPhoneGps() else stopPhoneGps()
    }

    fun setUsePhoneImu(enabled: Boolean) {
        _state.update { it.copy(usePhoneImu = enabled) }
        saveSettings()
        if (enabled) startPhoneImu() else stopPhoneImu()
    }

    fun resetSettings() {
        val defaults = AppPreferences.Settings()
        _state.update { current ->
            current.copy(
                historyWindowSeconds = defaults.historyWindowSeconds,
                headingShortWindowSec = defaults.headingShortWindowSec,
                headingLongWindowSec = defaults.headingLongWindowSec,
                trailWindowSeconds = defaults.trailWindowSeconds,
                narrateTimer = defaults.narrateTimer,
                gpsStaleThresholdSeconds = defaults.gpsStaleThresholdSeconds,
                showMap = defaults.showMap,
                cogWindowSeconds = defaults.cogWindowSeconds,
                dashboardCharts = defaults.dashboardCharts
            )
        }
        AppPreferences.clearSettings(app.applicationContext)
    }

    // ── Phone GPS / IMU ────────────────────────────────────────────────

    private fun startPhoneGps() {
        phoneGpsJob?.cancel()
        phoneGpsJob = viewModelScope.launch {
            try {
                phoneGpsService.start()
                _state.update { it.copy(phoneGpsActive = true) }
                phoneGpsService.location.collect { loc ->
                    val teensyHasFix = _state.value.connected &&
                        (_state.value.latestData?.fixType ?: 0) >= 2
                    if (!teensyHasFix) {
                        val last = _state.value.latestData
                        onNewData(SensorData(
                            timestampMs    = System.currentTimeMillis(),
                            heading        = last?.heading     ?: 0f,
                            pitch          = last?.pitch       ?: 0f,
                            roll           = last?.roll        ?: 0f,
                            gyroZ          = last?.gyroZ      ?: 0f,
                            accelX         = last?.accelX     ?: 0f,
                            accelY         = last?.accelY     ?: 0f,
                            accelZ         = last?.accelZ     ?: 0f,
                            imuAccuracy    = last?.imuAccuracy ?: 0,
                            lat            = loc.lat,
                            lon            = loc.lon,
                            sogKts         = loc.sogKts,
                            cogDeg         = loc.cogDeg,
                            fixType        = 3,
                            isDirectGpsReading = true
                        ))
                    }
                }
            } catch (e: SecurityException) {
                _state.update { it.copy(usePhoneGps = false, phoneGpsActive = false) }
            }
        }
    }

    private fun stopPhoneGps() {
        phoneGpsJob?.cancel()
        phoneGpsService.stop()
        _state.update { it.copy(phoneGpsActive = false) }
    }

    private fun startPhoneImu() {
        phoneImuJob?.cancel()
        phoneImuJob = viewModelScope.launch {
            phoneImuService.start()
            _state.update { it.copy(phoneImuActive = true) }
            phoneImuService.readings.collect { reading ->
                if (!_state.value.connected) {
                    val last = _state.value.latestData
                    onNewData(SensorData(
                        timestampMs    = System.currentTimeMillis(),
                        heading        = reading.heading,
                        pitch          = reading.pitch,
                        roll           = reading.roll,
                        gyroZ          = reading.gyroZ,
                        accelX         = reading.accelX,
                        accelY         = reading.accelY,
                        accelZ         = reading.accelZ,
                        imuAccuracy    = 2,
                        lat            = last?.lat    ?: 0.0,
                        lon            = last?.lon    ?: 0.0,
                        sogKts         = last?.sogKts ?: 0f,
                        cogDeg         = last?.cogDeg ?: 0f,
                        fixType        = last?.fixType ?: 0,
                        isDirectGpsReading = false
                    ))
                }
            }
        }
    }

    private fun stopPhoneImu() {
        phoneImuJob?.cancel()
        phoneImuService.stop()
        _state.update { it.copy(phoneImuActive = false) }
    }

    override fun onCleared() {
        super.onCleared()
        tts?.shutdown()
        tts = null
        stopPhoneGps()
        stopPhoneImu()
        bluetoothService.disconnect()
    }

    // ── Data Processing ────────────────────────────────────────────────

    private fun filterHeading(incoming: Float): Float {
        val last = lastGoodHeading
        if (last == null) {
            lastGoodHeading = incoming
            return incoming
        }
        var diff = incoming - last
        if (diff > 180f) diff -= 360f
        if (diff < -180f) diff += 360f
        return if (abs(diff) > 60f) last else { lastGoodHeading = incoming; incoming }
    }

    private fun onNewData(data: SensorData) {
        val currentLast = _state.value.latestData
        val gpsAugmented = if (!data.isDirectGpsReading &&
            currentLast != null && (currentLast.lat != 0.0 || currentLast.lon != 0.0)
        ) {
            data.copy(
                lat     = currentLast.lat,
                lon     = currentLast.lon,
                sogKts  = currentLast.sogKts,
                cogDeg  = currentLast.cogDeg,
                fixType = currentLast.fixType
            )
        } else if (data.isDirectGpsReading && currentLast != null && data.sogKts < 0.3f) {
            data.copy(cogDeg = currentLast.cogDeg)
        } else data

        val filteredData = gpsAugmented.copy(heading = filterHeading(gpsAugmented.heading))

        val detectorCutoff = filteredData.timestampMs - 180_000L
        detectorHistory = (detectorHistory + filteredData).filter { it.timestampMs >= detectorCutoff }

        _state.update { current ->
            val windowMs = current.historyWindowSeconds * 1000L
            val cutoff = filteredData.timestampMs - windowMs
            val newHistory = (current.history + filteredData).filter { it.timestampMs >= cutoff }

            val detectorResult = headedLiftedDetector.evaluate(
                detectorHistory,
                current.tack,
                shortWindowMs = current.headingShortWindowSec * 1000L,
                longWindowMs  = current.headingLongWindowSec  * 1000L
            )

            val activeMark = if (current.marks.isNotEmpty() &&
                current.activeMarkIndex < current.marks.size
            ) current.marks[current.activeMarkIndex] else null

            val vmgKts: Float?
            val distToMarkNm: Float?
            val bearingToMarkDeg: Float?
            if (activeMark != null && filteredData.fixType >= 2) {
                val boatPos = LatLng(filteredData.lat, filteredData.lon)
                // For gate marks, target the midpoint of the two gate ends
                val targetPos = if (activeMark.isGate && activeMark.gateEnd != null) {
                    LatLng(
                        (activeMark.position.latitude + activeMark.gateEnd.latitude) / 2.0,
                        (activeMark.position.longitude + activeMark.gateEnd.longitude) / 2.0
                    )
                } else {
                    activeMark.position
                }
                vmgKts = VMGCalculator.vmg(boatPos, targetPos, filteredData.cogDeg, filteredData.sogKts)
                distToMarkNm = VMGCalculator.distanceNm(boatPos, targetPos)
                bearingToMarkDeg = VMGCalculator.bearing(boatPos, targetPos)
            } else {
                vmgKts = null
                distToMarkNm = null
                bearingToMarkDeg = null
            }

            val lineConfirmed = current.startLine != null && current.pendingStartPin == null
            val startLineStatus = if (lineConfirmed && filteredData.fixType >= 2) {
                StartLineCalculator.evaluate(filteredData, current.startLine!!, current.timerState.remainingMs)
            } else null

            val newLastGpsFixMs = if (filteredData.isDirectGpsReading && filteredData.fixType >= 2) {
                filteredData.timestampMs
            } else {
                current.lastGpsFixMs
            }

            val trailCutoff = filteredData.timestampMs - (current.trailWindowSeconds * 1000L)
            val newTrailHistory = if (filteredData.isDirectGpsReading && filteredData.fixType >= 2) {
                (current.trailHistory + filteredData).filter { it.timestampMs >= trailCutoff }
            } else {
                current.trailHistory.filter { it.timestampMs >= trailCutoff }
            }

            // Circular mean of COG readings over the last cogWindowSeconds — only when GPS active
            val cogCutoff = filteredData.timestampMs - (current.cogWindowSeconds * 1000L)
            val cogSamples = newHistory.filter { it.timestampMs >= cogCutoff && it.fixType >= 2 && it.sogKts >= 0.3f }
            val historicalCogDeg: Float? = if (cogSamples.size >= 2) {
                val sinMean = cogSamples.map { sin(Math.toRadians(it.cogDeg.toDouble())) }.average()
                val cosMean = cogSamples.map { cos(Math.toRadians(it.cogDeg.toDouble())) }.average()
                ((Math.toDegrees(atan2(sinMean, cosMean)) + 360.0) % 360.0).toFloat()
            } else if (filteredData.fixType >= 2 && filteredData.sogKts >= 0.3f) {
                filteredData.cogDeg
            } else null

            current.copy(
                latestData = filteredData,
                history = newHistory,
                headingTrend = detectorResult.trend,
                trendDegrees = detectorResult.degrees,
                vmgKts = vmgKts,
                distToMarkNm = distToMarkNm,
                bearingToMarkDeg = bearingToMarkDeg,
                startLineStatus = startLineStatus,
                lastGpsFixMs = newLastGpsFixMs,
                trailHistory = newTrailHistory,
                historicalCogDeg = historicalCogDeg
            )
        }
    }
}
