package com.sailboatracing.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.drawable.BitmapDrawable
import android.view.MotionEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sailboatracing.model.LatLng
import com.sailboatracing.model.RaceMark
import com.sailboatracing.model.Rounding
import com.sailboatracing.model.SensorData
import com.sailboatracing.model.Sighting
import com.sailboatracing.model.StartLine
import com.sailboatracing.ui.theme.PrimaryColor
import com.sailboatracing.viewmodel.RaceViewModel
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlin.math.sin
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline

@Composable
fun CourseScreen(viewModel: RaceViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("START LINE", "MARKS", "TRIANGULATE")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F))
            .pointerInput(Unit) {
                var totalX = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalX = 0f },
                    onDragEnd = {
                        if (totalX < -80f && selectedTab < tabs.size - 1) selectedTab++
                        else if (totalX > 80f && selectedTab > 0) selectedTab--
                    },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        totalX += amount
                    }
                )
            }
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color(0xFF14141E),
            contentColor = PrimaryColor,
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = PrimaryColor
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            color = if (selectedTab == index) PrimaryColor else Color(0xFF888888),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                )
            }
        }

        when (selectedTab) {
            0 -> StartLineTab(viewModel = viewModel, state = state)
            1 -> MarksTab(viewModel = viewModel, state = state, onNavigateToStartLineTab = { selectedTab = 0 })
            2 -> TriangulatorTab(viewModel = viewModel, state = state)
        }
    }
}

// ─────────────────────────── START LINE TAB ───────────────────────────

@Composable
private fun StartLineTab(
    viewModel: RaceViewModel,
    state: com.sailboatracing.model.RaceState
) {
    val hasRecentGpsFix = !state.gpsStale && state.lastGpsFixMs > 0L
    val line = state.startLine
    val pendingPin = state.pendingStartPin
    val pendingBoat = state.pendingStartBoat
    val lineConfirmed = line != null && pendingPin == null && pendingBoat == null
    val currentGpsPosition = state.latestData?.let { d ->
        if (d.lat != 0.0 || d.lon != 0.0) LatLng(d.lat, d.lon) else null
    }
    val imuCalibrated = (state.latestData?.imuAccuracy ?: 0) >= 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        Text(
            text = when {
                hasRecentGpsFix -> "GPS ready"
                state.lastGpsFixMs > 0L ->
                    "GPS stale — last fix ${(System.currentTimeMillis() - state.lastGpsFixMs) / 1000}s ago"
                else -> "Waiting for GPS fix..."
            },
            color = if (hasRecentGpsFix) Color(0xFF00FF88) else Color(0xFFFFAB40),
            fontSize = 12.sp
        )

        // PIN end
        StartLinePointRow(
            label = "PIN",
            position = pendingPin ?: line?.pin,
            confirmed = lineConfirmed || pendingPin != null,
            hasRecentGpsFix = hasRecentGpsFix,
            currentGpsPosition = currentGpsPosition,
            latestData = state.latestData,
            marks = state.marks,
            historicalCogDeg = state.historicalCogDeg,
            showHeadingLines = state.showHeadingLines,
            headingLineMeters = state.headingLineMeters,
            onMark = { viewModel.markStartLinePin() },
            onMarkAt = { lat, lon -> viewModel.markStartLinePinAt(lat, lon) }
        )

        // BOAT end — always enabled, can be marked independently
        StartLinePointRow(
            label = "BOAT END",
            position = pendingBoat ?: if (lineConfirmed) line?.boat else null,
            confirmed = lineConfirmed || pendingBoat != null,
            hasRecentGpsFix = hasRecentGpsFix,
            currentGpsPosition = currentGpsPosition,
            latestData = state.latestData,
            marks = state.marks,
            historicalCogDeg = state.historicalCogDeg,
            showHeadingLines = state.showHeadingLines,
            headingLineMeters = state.headingLineMeters,
            onMark = { viewModel.markStartLineBoat() },
            onMarkAt = { lat, lon -> viewModel.markStartLineBoatAt(lat, lon) }
        )

        if (pendingPin != null && pendingBoat == null && !lineConfirmed) {
            Text(
                text = "Sail to the boat end of the line, then tap MARK BOAT END.",
                color = Color(0xFF888888),
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        } else if (pendingBoat != null && pendingPin == null && !lineConfirmed) {
            Text(
                text = "Sail to the pin end of the line, then tap MARK PIN.",
                color = Color(0xFF888888),
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }

        // ── Live map preview ───────────────────────────────────────────────
        StartLineMapPreview(
            latestData = state.latestData,
            startLine = line,
            marks = state.marks,
            historicalCogDeg = state.historicalCogDeg,
            showHeadingLines = state.showHeadingLines,
            headingLineMeters = state.headingLineMeters,
            phoneImuHeading = state.phoneImuHeading
        )

        // ── Draw from heading ──────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1A22)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "DRAW LINE FROM HEADING",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4488CC)
                )
                Text(
                    text = "Sail along the start line. Your current position and heading define the line bearing. " +
                           "Tentative endpoints are placed ±250 m along your heading — re-mark individual ends afterwards if needed.",
                    fontSize = 11.sp,
                    color = Color(0xFF777777),
                    lineHeight = 16.sp
                )

                // Primary: use external/hardware IMU heading (no calibration guard — trust the heading)
                Button(
                    onClick = { viewModel.setStartLineFromHeading() },
                    enabled = hasRecentGpsFix,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1A3A55),
                        contentColor = Color(0xFF66BBFF),
                        disabledContainerColor = Color(0xFF1A1A2E),
                        disabledContentColor = Color(0xFF444466)
                    )
                ) {
                    Text(
                        text = if (!hasRecentGpsFix) "⚠ NO GPS FIX"
                               else if (imuCalibrated) "↑  SET LINE FROM HEADING"
                               else "↑  SET LINE FROM HEADING  (IMU uncal)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                // Override: use phone's own compass — always available, always calibrated
                OutlinedButton(
                    onClick = { viewModel.setStartLineFromPhoneHeading() },
                    enabled = hasRecentGpsFix,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFFFAB40),
                        disabledContentColor = Color(0xFF444433)
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (hasRecentGpsFix) Color(0xFFFFAB40) else Color(0xFF333322)
                    )
                ) {
                    Text(
                        text = "📱  USE PHONE COMPASS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                Text(
                    text = "Phone compass is always ready — use it when the external IMU isn't calibrated or when Bluetooth isn't connected.",
                    fontSize = 10.sp,
                    color = Color(0xFF555555),
                    lineHeight = 14.sp
                )
            }
        }

        if (line != null) {
            OutlinedButton(
                onClick = { viewModel.clearStartLine() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF4444))
            ) {
                Text(text = "CLEAR LINE", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

// Compact live map showing the boat, heading line and start line for the Start Line tab.
@Composable
private fun StartLineMapPreview(
    latestData: SensorData?,
    startLine: StartLine?,
    marks: List<RaceMark>,
    historicalCogDeg: Float?,
    showHeadingLines: Boolean,
    headingLineMeters: Int,
    phoneImuHeading: Float? = null
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    Configuration.getInstance().userAgentValue = context.packageName

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(16.0)
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
        }
    }

    // Auto-center on boat position once we have a fix
    val hasPosition = latestData != null && (latestData.lat != 0.0 || latestData.lon != 0.0)
    if (hasPosition) {
        mapView.controller.setCenter(GeoPoint(latestData!!.lat, latestData.lon))
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE  -> mapView.onPause()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0F))
    ) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            update = { mv ->
                mv.overlays.clear()

                // 1. Heading + COG lines first so projected start-line previews render on top
                if (hasPosition) {
                    val boatLat = latestData!!.lat
                    val boatLon = latestData.lon
                    val boatGeo = GeoPoint(boatLat, boatLon)

                    if (showHeadingLines) {
                        val hdgEnd = projectGeoPoint(boatLat, boatLon, latestData.heading.toDouble(), headingLineMeters.toDouble())
                        mv.overlays.add(Polyline(mv).apply {
                            setPoints(listOf(boatGeo, hdgEnd))
                            outlinePaint.color = android.graphics.Color.argb(210, 0, 220, 80)
                            outlinePaint.strokeWidth = 3f
                            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                        })
                        val cogDeg = historicalCogDeg ?: latestData.cogDeg
                        if (latestData.fixType >= 2 && latestData.sogKts >= 0.3f) {
                            val cogEnd = projectGeoPoint(boatLat, boatLon, cogDeg.toDouble(), headingLineMeters.toDouble())
                            mv.overlays.add(Polyline(mv).apply {
                                setPoints(listOf(boatGeo, cogEnd))
                                outlinePaint.color = android.graphics.Color.argb(210, 255, 68, 68)
                                outlinePaint.strokeWidth = 3f
                                outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                            })
                        }
                    }
                }

                // 2. Projected start-line previews (dashed) — drawn over heading lines
                fun addProjectedLine(bearingDeg: Double, color: Int) {
                    if (!hasPosition || latestData == null) return
                    val lat = latestData.lat; val lon = latestData.lon
                    val pinEnd  = projectGeoPoint(lat, lon, bearingDeg,               250.0)
                    val boatEnd = projectGeoPoint(lat, lon, (bearingDeg + 180.0) % 360.0, 250.0)
                    mv.overlays.add(Polyline(mv).apply {
                        setPoints(listOf(pinEnd, boatEnd))
                        outlinePaint.color = color
                        outlinePaint.strokeWidth = 5f
                        outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                        outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(18f, 12f), 0f)
                    })
                }

                // Phone compass — amber, drawn first so hardware line renders on top
                phoneImuHeading?.let { hdg ->
                    addProjectedLine(hdg.toDouble(), android.graphics.Color.argb(220, 255, 171, 64))
                }
                // Hardware IMU — blue
                latestData?.let { d ->
                    addProjectedLine(d.heading.toDouble(), android.graphics.Color.argb(220, 102, 187, 255))
                }

                // 3. Confirmed start line (solid bright amber) on top of previews
                if (startLine != null) {
                    val pinGeo  = GeoPoint(startLine.pin.latitude,  startLine.pin.longitude)
                    val boatGeo = GeoPoint(startLine.boat.latitude, startLine.boat.longitude)
                    mv.overlays.add(Polyline(mv).apply {
                        setPoints(listOf(pinGeo, boatGeo))
                        outlinePaint.color = android.graphics.Color.argb(230, 255, 200, 0)
                        outlinePaint.strokeWidth = 6f
                        outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                    })
                    mv.overlays.add(Marker(mv).apply {
                        position = pinGeo
                        title = "PIN"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        setOnMarkerClickListener { _, _ -> false }
                    })
                    mv.overlays.add(Marker(mv).apply {
                        position = boatGeo
                        title = "BOAT END"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        setOnMarkerClickListener { _, _ -> false }
                    })
                }

                // 4. Boat marker on top of everything
                if (hasPosition) {
                    mv.overlays.add(Marker(mv).apply {
                        position = GeoPoint(latestData!!.lat, latestData.lon)
                        title = ""
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = boatBitmapDrawable(mv.context, latestData.heading)
                        setOnMarkerClickListener { _, _ -> false }
                    })
                }

                mv.invalidate()
            }
        )
    }
}

@Composable
private fun StartLinePointRow(
    label: String,
    position: LatLng?,
    confirmed: Boolean,
    hasRecentGpsFix: Boolean,
    enabled: Boolean = true,
    currentGpsPosition: LatLng?,
    latestData: SensorData? = null,
    marks: List<RaceMark> = emptyList(),
    historicalCogDeg: Float? = null,
    showHeadingLines: Boolean = true,
    headingLineMeters: Int = 1000,
    onMark: () -> Unit,
    onMarkAt: (Double, Double) -> Unit
) {
    var showMapPicker by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (confirmed) Color(0xFF14141E) else Color(0xFF0E0E18)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (confirmed) PrimaryColor else Color(0xFF666666)
                    )
                    Text(
                        text = if (position != null) "%.6f, %.6f".format(position.latitude, position.longitude)
                               else "Not set",
                        fontSize = 12.sp,
                        color = if (position != null) Color.White else Color(0xFF555555)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onMark,
                    enabled = hasRecentGpsFix && enabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (confirmed) Color(0xFF1E3050) else PrimaryColor,
                        contentColor = if (confirmed) PrimaryColor else Color.Black
                    )
                ) {
                    Text(
                        text = if (confirmed) "RE-MARK" else "MARK",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(
                onClick = { showMapPicker = true },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFAB40))
            ) {
                Text(text = "PICK ON MAP", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showMapPicker) {
        MapPickerDialog(
            initialCenter = currentGpsPosition,
            existingPosition = position,
            latestData = latestData,
            marks = marks,
            historicalCogDeg = historicalCogDeg,
            showHeadingLines = showHeadingLines,
            headingLineMeters = headingLineMeters,
            onDismiss = { showMapPicker = false },
            onConfirm = { pos ->
                onMarkAt(pos.latitude, pos.longitude)
                showMapPicker = false
            }
        )
    }
}

// ─────────────────────────────── MARKS TAB ────────────────────────────

@Composable
private fun MarksTab(
    viewModel: RaceViewModel,
    state: com.sailboatracing.model.RaceState,
    onNavigateToStartLineTab: () -> Unit = {}
) {
    var showAddMarkDialog by remember { mutableStateOf(false) }
    var editingMark by remember { mutableStateOf<RaceMark?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showCopyStartLineDialog by remember { mutableStateOf(false) }

    val hasRecentGpsFix = !state.gpsStale && state.lastGpsFixMs > 0L
    val currentGpsPosition = state.latestData?.let { d ->
        if (d.lat != 0.0 || d.lon != 0.0) LatLng(d.lat, d.lon) else null
    }

    var draggingId by remember { mutableStateOf<Int?>(null) }
    var draggingFromIndex by remember { mutableIntStateOf(-1) }
    var dragTargetIndex by remember { mutableIntStateOf(-1) }
    var cumulativeDragY by remember { mutableFloatStateOf(0f) }
    val itemHeightPx = with(LocalDensity.current) { 84.dp.toPx() }
    val isDraggingAny = draggingId != null

    Scaffold(
        containerColor = Color(0xFF0A0A0F),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddMarkDialog = true },
                containerColor = PrimaryColor,
                contentColor = Color.Black
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Mark")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState(), enabled = !isDraggingAny),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Start line summary card always at top
            StartLineSummaryCard(
                startLine = state.startLine,
                pendingStartPin = state.pendingStartPin,
                pendingStartBoat = state.pendingStartBoat,
                onClear = { viewModel.clearStartLine() },
                onEdit = onNavigateToStartLineTab
            )

            // Copy start line as a gate mark (e.g. for a finish or mid-course gate)
            val lineConfirmed = state.startLine != null && state.pendingStartPin == null && state.pendingStartBoat == null
            if (lineConfirmed) {
                OutlinedButton(
                    onClick = { showCopyStartLineDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFAB40))
                ) {
                    Text("COPY START LINE AS GATE MARK", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            if (state.marks.isEmpty()) {
                Text(
                    text = "No marks added yet.\nTap + to add a mark.",
                    color = Color(0xFF888888),
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                state.marks.forEachIndexed { index, mark ->
                    val isDragging = mark.id == draggingId
                    val isDropTarget = isDraggingAny && index == dragTargetIndex && index != draggingFromIndex

                    MarkRow(
                        mark = mark,
                        isDragging = isDragging,
                        isDropTarget = isDropTarget,
                        onDelete = { viewModel.removeMark(mark.id) },
                        onEdit = { editingMark = mark },
                        onDuplicate = { viewModel.duplicateMark(mark.id) },
                        modifier = Modifier.pointerInput(mark.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { _ ->
                                    draggingId = mark.id
                                    draggingFromIndex = index
                                    dragTargetIndex = index
                                    cumulativeDragY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    cumulativeDragY += dragAmount.y
                                    val delta = (cumulativeDragY / itemHeightPx).roundToInt()
                                    dragTargetIndex = (draggingFromIndex + delta)
                                        .coerceIn(0, state.marks.lastIndex)
                                },
                                onDragEnd = {
                                    if (draggingFromIndex >= 0 && draggingFromIndex != dragTargetIndex) {
                                        viewModel.reorderMarks(draggingFromIndex, dragTargetIndex)
                                    }
                                    draggingId = null; draggingFromIndex = -1; dragTargetIndex = -1
                                    cumulativeDragY = 0f
                                },
                                onDragCancel = {
                                    draggingId = null; draggingFromIndex = -1; dragTargetIndex = -1
                                    cumulativeDragY = 0f
                                }
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Reset all button
            OutlinedButton(
                onClick = { showResetConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF4444))
            ) {
                Text(text = "RESET ALL MARKS & START LINE", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(72.dp))  // FAB clearance
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            containerColor = Color(0xFF14141E),
            title = { Text("Reset All?", color = Color.White) },
            text = {
                Text(
                    text = "This will delete all marks and clear the start line. This cannot be undone.",
                    color = Color(0xFF888888),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.resetMarks(); showResetConfirm = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF660000),
                        contentColor = Color(0xFFFF4444)
                    )
                ) { Text("RESET", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("CANCEL", color = Color(0xFF888888))
                }
            }
        )
    }

    if (showAddMarkDialog) {
        AddMarkDialog(
            defaultName = "m${state.marks.size + 1}",
            hasRecentGpsFix = hasRecentGpsFix,
            currentGpsPosition = currentGpsPosition,
            latestData = state.latestData,
            marks = state.marks,
            historicalCogDeg = state.historicalCogDeg,
            showHeadingLines = state.showHeadingLines,
            headingLineMeters = state.headingLineMeters,
            onDismiss = { showAddMarkDialog = false },
            onAdd = { name, position, rounding, isGate, gateEnd ->
                viewModel.addMarkAt(name, position.latitude, position.longitude, rounding, isGate, gateEnd)
                showAddMarkDialog = false
            }
        )
    }

    editingMark?.let { mark ->
        EditMarkDialog(
            mark = mark,
            hasRecentGpsFix = hasRecentGpsFix,
            currentGpsPosition = currentGpsPosition,
            latestData = state.latestData,
            marks = state.marks,
            historicalCogDeg = state.historicalCogDeg,
            showHeadingLines = state.showHeadingLines,
            headingLineMeters = state.headingLineMeters,
            onDismiss = { editingMark = null },
            onSave = { name, position, rounding, isGate, gateEnd ->
                viewModel.editMark(mark.id, name, rounding, position, isGate, gateEnd)
                editingMark = null
            }
        )
    }

    if (showCopyStartLineDialog) {
        state.startLine?.let { line ->
            CopyStartLineDialog(
                onDismiss = { showCopyStartLineDialog = false },
                onConfirm = { name ->
                    viewModel.addMarkAt(name, line.pin.latitude, line.pin.longitude, Rounding.STARBOARD, isGate = true, gateEnd = line.boat)
                    showCopyStartLineDialog = false
                }
            )
        }
    }
}

// ──────────────────────── START LINE SUMMARY CARD ─────────────────────

@Composable
private fun StartLineSummaryCard(
    startLine: StartLine?,
    pendingStartPin: LatLng?,
    pendingStartBoat: LatLng? = null,
    onClear: () -> Unit = {},
    onEdit: () -> Unit = {}
) {
    val lineConfirmed = startLine != null && pendingStartPin == null && pendingStartBoat == null

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (lineConfirmed) Color(0xFF141A18) else Color(0xFF0E0E18)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "START LINE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (lineConfirmed) Color(0xFFFFAB40) else Color(0xFF555555)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = when {
                            lineConfirmed -> "CONFIRMED"
                            pendingStartPin != null -> "PIN ONLY"
                            pendingStartBoat != null -> "BOAT ONLY"
                            else -> "NOT SET"
                        },
                        fontSize = 10.sp,
                        color = when {
                            lineConfirmed -> Color(0xFF00FF88)
                            pendingStartPin != null || pendingStartBoat != null -> Color(0xFFFFAB40)
                            else -> Color(0xFF555555)
                        }
                    )
                    if (startLine != null) {
                        IconButton(onClick = onClear, modifier = Modifier.then(Modifier.padding(0.dp))) {
                            Icon(Icons.Filled.Delete, contentDescription = "Clear start line", tint = Color(0xFFFF4444))
                        }
                    }
                }
            }
            if (startLine != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "PIN   %.5f, %.5f".format(startLine.pin.latitude, startLine.pin.longitude),
                    fontSize = 11.sp,
                    color = Color(0xFF888888)
                )
                if (lineConfirmed) {
                    Text(
                        text = "BOAT  %.5f, %.5f".format(startLine.boat.latitude, startLine.boat.longitude),
                        fontSize = 11.sp,
                        color = Color(0xFF888888)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(
                    onClick = onEdit,
                    modifier = Modifier.padding(0.dp)
                ) {
                    Text("Edit in Start Line tab →", fontSize = 11.sp, color = Color(0xFF4499CC))
                }
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = onEdit, modifier = Modifier.padding(0.dp)) {
                    Text("Set start line →", fontSize = 11.sp, color = Color(0xFF4499CC))
                }
            }
        }
    }
}

// ───────────────────────────── MARK ROW ──────────────────────────────

@Composable
private fun MarkRow(
    mark: RaceMark,
    isDragging: Boolean = false,
    isDropTarget: Boolean = false,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val baseColor = when {
        isDragging -> Color(0xFF1E3050)
        else       -> Color(0xFF14141E)
    }
    val borderMod = when {
        isDragging   -> Modifier.border(1.dp, PrimaryColor, RoundedCornerShape(8.dp))
        isDropTarget -> Modifier.border(2.dp, Color(0xFFFFAB40), RoundedCornerShape(8.dp))
        else         -> Modifier
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(borderMod),
        colors = CardDefaults.cardColors(containerColor = baseColor),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Menu,
                contentDescription = "Drag to reorder",
                tint = Color(0xFF555555),
                modifier = Modifier.padding(end = 8.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = mark.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (mark.isGate) {
                        Text(
                            text = "GATE",
                            fontSize = 10.sp,
                            color = Color(0xFFFFAB40),
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                    Text(
                        text = mark.rounding.name,
                        fontSize = 11.sp,
                        color = when (mark.rounding) {
                            Rounding.PORT      -> Color(0xFFFF4444)
                            Rounding.STARBOARD -> Color(0xFF00FF88)
                        }
                    )
                }
                Text(
                    text = "%.5f, %.5f".format(mark.position.latitude, mark.position.longitude),
                    fontSize = 11.sp,
                    color = Color(0xFF888888)
                )
                if (mark.isGate && mark.gateEnd != null) {
                    Text(
                        text = "End B: %.5f, %.5f".format(mark.gateEnd.latitude, mark.gateEnd.longitude),
                        fontSize = 11.sp,
                        color = Color(0xFF666666)
                    )
                }
            }

            IconButton(onClick = onDuplicate) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate", tint = Color(0xFF666666))
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = Color(0xFF888888))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color(0xFFFF4444))
            }
        }
    }
}

// ──────────────────────────── ADD MARK DIALOG ────────────────────────

@Composable
private fun AddMarkDialog(
    defaultName: String = "",
    hasRecentGpsFix: Boolean,
    currentGpsPosition: LatLng?,
    latestData: SensorData? = null,
    marks: List<RaceMark> = emptyList(),
    historicalCogDeg: Float? = null,
    showHeadingLines: Boolean = true,
    headingLineMeters: Int = 1000,
    onDismiss: () -> Unit,
    onAdd: (name: String, position: LatLng, rounding: Rounding, isGate: Boolean, gateEnd: LatLng?) -> Unit
) {
    var name by remember { mutableStateOf(defaultName) }
    var rounding by remember { mutableStateOf(Rounding.STARBOARD) }
    var pickedPosition by remember { mutableStateOf<LatLng?>(null) }
    var pickedGateEnd by remember { mutableStateOf<LatLng?>(null) }
    var isGate by remember { mutableStateOf(false) }
    var showMapPickerA by remember { mutableStateOf(false) }
    var showMapPickerB by remember { mutableStateOf(false) }

    val resolvedPosition = pickedPosition
    val canAdd = resolvedPosition != null && (!isGate || pickedGateEnd != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF14141E),
        title = { Text("Add Mark", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Mark Name", color = Color(0xFF888888)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryColor,
                        unfocusedBorderColor = Color(0xFF444444),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = PrimaryColor
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Gate toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Gate / line (two ends)", color = Color.White, fontSize = 14.sp)
                        Text("Two-ended mark (like a start line)", color = Color(0xFF666666), fontSize = 11.sp)
                    }
                    Switch(
                        checked = isGate,
                        onCheckedChange = { isGate = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Color(0xFFFFAB40),
                            uncheckedThumbColor = Color(0xFF888888),
                            uncheckedTrackColor = Color(0xFF333333)
                        )
                    )
                }

                Text("Rounding", fontSize = 12.sp, color = Color(0xFF888888))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = rounding == Rounding.STARBOARD,
                        onClick = { rounding = Rounding.STARBOARD },
                        label = { Text("STARBOARD") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF003300),
                            selectedLabelColor = Color(0xFF00FF88),
                            labelColor = Color(0xFF888888)
                        )
                    )
                    FilterChip(
                        selected = rounding == Rounding.PORT,
                        onClick = { rounding = Rounding.PORT },
                        label = { Text("PORT") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF330000),
                            selectedLabelColor = Color(0xFFFF4444),
                            labelColor = Color(0xFF888888)
                        )
                    )
                }

                // Position A
                Text(
                    text = if (isGate) "Gate End A" else "Position",
                    fontSize = 12.sp,
                    color = Color(0xFF888888)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showMapPickerA = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryColor)
                    ) {
                        Text(if (pickedPosition != null) "CHANGE MAP" else "PICK ON MAP")
                    }
                    if (hasRecentGpsFix && currentGpsPosition != null) {
                        OutlinedButton(
                            onClick = { pickedPosition = currentGpsPosition },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00FF88))
                        ) {
                            Text("USE GPS")
                        }
                    }
                }
                if (resolvedPosition != null) {
                    Text(
                        text = "%.5f, %.5f".format(resolvedPosition.latitude, resolvedPosition.longitude),
                        color = PrimaryColor,
                        fontSize = 12.sp
                    )
                } else {
                    Text(
                        text = if (hasRecentGpsFix) "Tap USE GPS or PICK ON MAP" else "No GPS fix — pick on map",
                        color = Color(0xFFFFAB40),
                        fontSize = 12.sp
                    )
                }

                // Position B (gate only)
                if (isGate) {
                    HorizontalDivider(color = Color(0xFF2A2A2A))
                    Text("Gate End B", fontSize = 12.sp, color = Color(0xFF888888))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showMapPickerB = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFAB40))
                        ) {
                            Text(if (pickedGateEnd != null) "CHANGE MAP" else "PICK ON MAP")
                        }
                        if (hasRecentGpsFix && currentGpsPosition != null) {
                            OutlinedButton(
                                onClick = { pickedGateEnd = currentGpsPosition },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00FF88))
                            ) {
                                Text("USE GPS")
                            }
                        }
                    }
                    if (pickedGateEnd != null) {
                        Text(
                            text = "%.5f, %.5f".format(pickedGateEnd!!.latitude, pickedGateEnd!!.longitude),
                            color = Color(0xFFFFAB40),
                            fontSize = 12.sp
                        )
                    } else {
                        Text(
                            text = "Select the second gate end",
                            color = Color(0xFF555555),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    resolvedPosition?.let {
                        onAdd(name.trim(), it, rounding, isGate, if (isGate) pickedGateEnd else null)
                    }
                },
                enabled = canAdd,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryColor,
                    contentColor = Color.Black
                )
            ) { Text("ADD", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", color = Color(0xFF888888)) }
        }
    )

    if (showMapPickerA) {
        MapPickerDialog(
            initialCenter = currentGpsPosition,
            existingPosition = pickedPosition,
            latestData = latestData,
            marks = marks,
            historicalCogDeg = historicalCogDeg,
            showHeadingLines = showHeadingLines,
            headingLineMeters = headingLineMeters,
            onDismiss = { showMapPickerA = false },
            onConfirm = { pos -> pickedPosition = pos; showMapPickerA = false }
        )
    }
    if (showMapPickerB) {
        MapPickerDialog(
            initialCenter = pickedPosition ?: currentGpsPosition,
            existingPosition = pickedGateEnd,
            latestData = latestData,
            marks = marks,
            historicalCogDeg = historicalCogDeg,
            showHeadingLines = showHeadingLines,
            headingLineMeters = headingLineMeters,
            onDismiss = { showMapPickerB = false },
            onConfirm = { pos -> pickedGateEnd = pos; showMapPickerB = false }
        )
    }
}

// ─────────────────────────── EDIT MARK DIALOG ────────────────────────

@Composable
private fun EditMarkDialog(
    mark: RaceMark,
    hasRecentGpsFix: Boolean,
    currentGpsPosition: LatLng?,
    latestData: SensorData? = null,
    marks: List<RaceMark> = emptyList(),
    historicalCogDeg: Float? = null,
    showHeadingLines: Boolean = true,
    headingLineMeters: Int = 1000,
    onDismiss: () -> Unit,
    onSave: (name: String, position: LatLng, rounding: Rounding, isGate: Boolean, gateEnd: LatLng?) -> Unit
) {
    var name by remember { mutableStateOf(mark.name) }
    var rounding by remember { mutableStateOf(mark.rounding) }
    var pickedPosition by remember { mutableStateOf<LatLng?>(null) }
    var pickedGateEnd by remember { mutableStateOf<LatLng?>(mark.gateEnd) }
    var isGate by remember { mutableStateOf(mark.isGate) }
    var showMapPickerA by remember { mutableStateOf(false) }
    var showMapPickerB by remember { mutableStateOf(false) }

    val displayPosition = pickedPosition ?: mark.position

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF14141E),
        title = { Text("Edit Mark", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Mark Name", color = Color(0xFF888888)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryColor,
                        unfocusedBorderColor = Color(0xFF444444),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = PrimaryColor
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Gate / line (two ends)", color = Color.White, fontSize = 14.sp)
                        Text("Two-ended mark", color = Color(0xFF666666), fontSize = 11.sp)
                    }
                    Switch(
                        checked = isGate,
                        onCheckedChange = { isGate = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Color(0xFFFFAB40),
                            uncheckedThumbColor = Color(0xFF888888),
                            uncheckedTrackColor = Color(0xFF333333)
                        )
                    )
                }

                Text("Rounding", fontSize = 12.sp, color = Color(0xFF888888))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = rounding == Rounding.STARBOARD,
                        onClick = { rounding = Rounding.STARBOARD },
                        label = { Text("STARBOARD") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF003300),
                            selectedLabelColor = Color(0xFF00FF88),
                            labelColor = Color(0xFF888888)
                        )
                    )
                    FilterChip(
                        selected = rounding == Rounding.PORT,
                        onClick = { rounding = Rounding.PORT },
                        label = { Text("PORT") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF330000),
                            selectedLabelColor = Color(0xFFFF4444),
                            labelColor = Color(0xFF888888)
                        )
                    )
                }

                Text(
                    text = if (isGate) "Gate End A" else "Position",
                    fontSize = 12.sp,
                    color = Color(0xFF888888)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showMapPickerA = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryColor)
                    ) {
                        Text("MOVE ON MAP")
                    }
                    if (hasRecentGpsFix && currentGpsPosition != null) {
                        OutlinedButton(
                            onClick = { pickedPosition = currentGpsPosition },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00FF88))
                        ) {
                            Text("USE GPS")
                        }
                    }
                }
                Text(
                    text = "%.5f, %.5f".format(displayPosition.latitude, displayPosition.longitude),
                    color = if (pickedPosition != null) PrimaryColor else Color(0xFF888888),
                    fontSize = 12.sp
                )

                if (isGate) {
                    HorizontalDivider(color = Color(0xFF2A2A2A))
                    Text("Gate End B", fontSize = 12.sp, color = Color(0xFF888888))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showMapPickerB = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFAB40))
                        ) {
                            Text(if (pickedGateEnd != null) "MOVE ON MAP" else "PICK ON MAP")
                        }
                        if (hasRecentGpsFix && currentGpsPosition != null) {
                            OutlinedButton(
                                onClick = { pickedGateEnd = currentGpsPosition },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00FF88))
                            ) {
                                Text("USE GPS")
                            }
                        }
                    }
                    if (pickedGateEnd != null) {
                        Text(
                            text = "%.5f, %.5f".format(pickedGateEnd!!.latitude, pickedGateEnd!!.longitude),
                            color = Color(0xFFFFAB40),
                            fontSize = 12.sp
                        )
                    } else {
                        Text(
                            text = "Select the second gate end",
                            color = Color(0xFF555555),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        name.trim().ifBlank { mark.name },
                        displayPosition,
                        rounding,
                        isGate,
                        if (isGate) pickedGateEnd else null
                    )
                },
                enabled = name.isNotBlank() && (!isGate || pickedGateEnd != null),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryColor,
                    contentColor = Color.Black
                )
            ) { Text("SAVE", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", color = Color(0xFF888888)) }
        }
    )

    if (showMapPickerA) {
        MapPickerDialog(
            initialCenter = displayPosition,
            existingPosition = displayPosition,
            latestData = latestData,
            marks = marks,
            historicalCogDeg = historicalCogDeg,
            showHeadingLines = showHeadingLines,
            headingLineMeters = headingLineMeters,
            onDismiss = { showMapPickerA = false },
            onConfirm = { pos -> pickedPosition = pos; showMapPickerA = false }
        )
    }
    if (showMapPickerB) {
        MapPickerDialog(
            initialCenter = pickedGateEnd ?: displayPosition,
            existingPosition = pickedGateEnd,
            latestData = latestData,
            marks = marks,
            historicalCogDeg = historicalCogDeg,
            showHeadingLines = showHeadingLines,
            headingLineMeters = headingLineMeters,
            onDismiss = { showMapPickerB = false },
            onConfirm = { pos -> pickedGateEnd = pos; showMapPickerB = false }
        )
    }
}

// ─────────────────────────── GEO UTILITIES ───────────────────────────

private fun projectGeoPoint(lat: Double, lon: Double, bearingDeg: Double, distanceM: Double): GeoPoint {
    val r = 6371000.0
    val d = distanceM / r
    val b = Math.toRadians(bearingDeg)
    val lat1 = Math.toRadians(lat)
    val lon1 = Math.toRadians(lon)
    val lat2 = asin(sin(lat1) * cos(d) + cos(lat1) * sin(d) * cos(b))
    val lon2 = lon1 + atan2(sin(b) * sin(d) * cos(lat1), cos(d) - sin(lat1) * sin(lat2))
    return GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lon2))
}

// ──────────────────────── COPY START LINE DIALOG ─────────────────────

@Composable
private fun CopyStartLineDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String) -> Unit
) {
    var name by remember { mutableStateOf("Finish") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF14141E),
        title = { Text("Copy as Gate Mark", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "The start line's pin and boat ends will become Gate End A and End B.",
                    color = Color(0xFF888888),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Mark Name", color = Color(0xFF888888)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryColor,
                        unfocusedBorderColor = Color(0xFF444444),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = PrimaryColor
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim().ifBlank { "Finish" }) },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor, contentColor = Color.Black)
            ) { Text("ADD", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", color = Color(0xFF888888)) }
        }
    )
}

// ──────────────────────── MARK TRIANGULATOR TAB ──────────────────────

/** Compose color + matching Android color int for each sighting ray. */
private val SIGHTING_COLORS: List<Pair<Int, Color>> = listOf(
    Pair(android.graphics.Color.rgb(255, 107, 107), Color(0xFFFF6B6B)),
    Pair(android.graphics.Color.rgb(107, 203, 119), Color(0xFF6BCB77)),
    Pair(android.graphics.Color.rgb( 77, 150, 255), Color(0xFF4D96FF)),
    Pair(android.graphics.Color.rgb(255, 217,  61), Color(0xFFFFD93D)),
    Pair(android.graphics.Color.rgb(255, 159,  28), Color(0xFFFF9F1C)),
    Pair(android.graphics.Color.rgb(204, 119, 255), Color(0xFFCC77FF)),
    Pair(android.graphics.Color.rgb(  0, 217, 255), Color(0xFF00D9FF)),
    Pair(android.graphics.Color.rgb(255, 119, 168), Color(0xFFFF77A8)),
)

private data class TriResult(val lat: Double, val lon: Double, val intersectionCount: Int)

/**
 * Triangulate a mark from bearing sightings using pairwise ray intersections (Cramer's rule).
 * Projects all points to a local Cartesian frame, finds forward intersections, then averages.
 */
private fun computeTriangulation(sightings: List<Sighting>): TriResult? {
    if (sightings.size < 2) return null
    val refLat = sightings.map { it.lat }.average()
    val refLon = sightings.map { it.lon }.average()
    val refLatRad = Math.toRadians(refLat)
    val mPerDeg = 111320.0

    data class Ray(val x: Double, val y: Double, val dx: Double, val dy: Double)
    val rays = sightings.map { s ->
        val x  = (s.lon - refLon) * cos(refLatRad) * mPerDeg
        val y  = (s.lat - refLat) * mPerDeg
        val br = Math.toRadians(s.bearingDeg.toDouble())
        Ray(x, y, sin(br), cos(br))
    }

    val intersections = mutableListOf<Pair<Double, Double>>()
    for (i in rays.indices) {
        for (j in i + 1 until rays.size) {
            val a = rays[i]; val b = rays[j]
            // Solve: a.x + t1*a.dx = b.x + t2*b.dx
            //        a.y + t1*a.dy = b.y + t2*b.dy
            val det = a.dx * (-b.dy) + b.dx * a.dy
            if (abs(det) < 1e-10) continue          // parallel rays
            val ddx = b.x - a.x; val ddy = b.y - a.y
            val t1 = (ddx * (-b.dy) + b.dx * ddy) / det
            val t2 = (a.dx *   ddy  - ddx * a.dy) / det
            if (t1 < 0 || t2 < 0) continue          // intersection is behind one of the rays
            intersections += Pair(a.x + t1 * a.dx, a.y + t1 * a.dy)
        }
    }
    if (intersections.isEmpty()) return null

    val avgX = intersections.map { it.first  }.average()
    val avgY = intersections.map { it.second }.average()
    val lat  = refLat + avgY / mPerDeg
    val lon  = refLon + avgX / (mPerDeg * cos(refLatRad))
    return TriResult(lat, lon, intersections.size)
}

@Composable
private fun TriangulatorTab(
    viewModel: RaceViewModel,
    state: com.sailboatracing.model.RaceState
) {
    val sightings        = state.triangulatorSightings
    val activeSightings  = sightings.filter { it.active }
    val triResult        = remember(activeSightings) { computeTriangulation(activeSightings) }
    val hasRecentGpsFix  = !state.gpsStale && state.lastGpsFixMs > 0L
    val hasPhoneHeading  = state.phoneImuHeading != null
    val canShoot         = hasRecentGpsFix && hasPhoneHeading

    var showAddMarkDialog by remember { mutableStateOf(false) }
    var pendingMarkName   by remember { mutableStateOf("Mark") }
    var initialCentered   by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F))
    ) {
        // ── Map ─────────────────────────────────────────────────────────
        TriangulatorMap(
            latestData      = state.latestData,
            phoneImuHeading = state.phoneImuHeading,
            sightings       = sightings,
            triResult       = triResult,
            initialCentered = initialCentered,
            onInitialCenter = { initialCentered = true },
            modifier        = Modifier
                .fillMaxWidth()
                .height(280.dp)
        )

        // ── Scrollable controls ─────────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // SHOOT button
            Button(
                onClick  = { viewModel.addTriangulatorSighting() },
                enabled  = canShoot,
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = PrimaryColor,
                    contentColor           = Color.Black,
                    disabledContainerColor = Color(0xFF1A1A2E),
                    disabledContentColor   = Color(0xFF444466)
                )
            ) {
                Text(
                    text = when {
                        !hasRecentGpsFix  -> "⚠  WAITING FOR GPS"
                        !hasPhoneHeading  -> "⚠  PHONE COMPASS NOT READY"
                        else -> "📍  SHOOT BEARING  (${sightings.size} shot${if (sightings.size != 1) "s" else ""})"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize   = 14.sp
                )
            }

            Text(
                text = "Point your phone toward a mark and tap SHOOT BEARING. Move several hundred meters and repeat. " +
                       "The app estimates the mark's position by averaging bearing intersections.",
                color    = Color(0xFF666666),
                fontSize = 11.sp,
                lineHeight = 16.sp
            )

            // ── Sightings list ──────────────────────────────────────────
            if (sightings.isNotEmpty()) {
                Text(
                    text       = "SIGHTINGS  (${activeSightings.size}/${sightings.size} active)",
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color(0xFF4488CC)
                )
                sightings.forEachIndexed { idx, sighting ->
                    SightingRow(
                        index    = idx + 1,
                        sighting = sighting,
                        color    = SIGHTING_COLORS[idx % SIGHTING_COLORS.size].second,
                        onToggle = { viewModel.toggleTriangulatorSighting(sighting.id) },
                        onDelete = { viewModel.removeTriangulatorSighting(sighting.id) }
                    )
                }
                OutlinedButton(
                    onClick  = { viewModel.clearTriangulatorSightings() },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF4444))
                ) {
                    Text("CLEAR ALL SIGHTINGS", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            // ── Result card ─────────────────────────────────────────────
            when {
                triResult != null -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors   = CardDefaults.cardColors(containerColor = Color(0xFF0E1A10)),
                        shape    = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text       = "ESTIMATED MARK POSITION",
                                fontSize   = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color      = Color(0xFF00FF88)
                            )
                            Text(
                                text       = "%.6f,  %.6f".format(triResult.lat, triResult.lon),
                                fontSize   = 14.sp,
                                color      = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text     = "${triResult.intersectionCount} bearing intersection${if (triResult.intersectionCount != 1) "s" else ""} averaged",
                                fontSize = 11.sp,
                                color    = Color(0xFF888888)
                            )

                            HorizontalDivider(color = Color(0xFF2A2A2A))
                            Text("ADD AS:", fontSize = 11.sp, color = Color(0xFF666666))

                            Button(
                                onClick  = { showAddMarkDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors   = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1A3A55),
                                    contentColor   = Color(0xFF66BBFF)
                                )
                            ) {
                                Text("ADD AS MARK", fontWeight = FontWeight.Bold)
                            }

                            Row(
                                modifier            = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick   = { viewModel.markStartLinePinAt(triResult.lat, triResult.lon) },
                                    modifier  = Modifier.weight(1f),
                                    colors    = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFAB40)),
                                    border    = BorderStroke(1.dp, Color(0xFFFFAB40))
                                ) {
                                    Text("START LINE PIN", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                                OutlinedButton(
                                    onClick   = { viewModel.markStartLineBoatAt(triResult.lat, triResult.lon) },
                                    modifier  = Modifier.weight(1f),
                                    colors    = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFAB40)),
                                    border    = BorderStroke(1.dp, Color(0xFFFFAB40))
                                ) {
                                    Text("COMMITTEE BOAT", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
                activeSightings.size == 1 -> {
                    Text(
                        text     = "Add at least one more active sighting to triangulate.",
                        color    = Color(0xFF666666),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showAddMarkDialog && triResult != null) {
        AddMarkFromTriangulatorDialog(
            defaultName = pendingMarkName,
            position    = LatLng(triResult.lat, triResult.lon),
            onDismiss   = { showAddMarkDialog = false },
            onAdd       = { name, rounding ->
                pendingMarkName = name
                viewModel.addMarkAt(name, triResult.lat, triResult.lon, rounding, false, null)
                showAddMarkDialog = false
            }
        )
    }
}

// ────────────────────────── TRIANGULATOR MAP ─────────────────────────

@Composable
private fun TriangulatorMap(
    latestData:      SensorData?,
    phoneImuHeading: Float?,
    sightings:       List<Sighting>,
    triResult:       TriResult?,
    initialCentered: Boolean,
    onInitialCenter: () -> Unit,
    modifier:        Modifier = Modifier
) {
    val context   = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    Configuration.getInstance().userAgentValue = context.packageName

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(14.0)
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled   = false
        }
    }

    val hasPosition = latestData != null && (latestData.lat != 0.0 || latestData.lon != 0.0)

    // Center once on first GPS fix so the user can freely pan afterwards
    if (!initialCentered && hasPosition) {
        mapView.controller.setCenter(GeoPoint(latestData!!.lat, latestData.lon))
        onInitialCenter()
    }

    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE  -> mapView.onPause()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    Card(
        modifier = modifier,
        shape    = RoundedCornerShape(0.dp),
        colors   = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0F))
    ) {
        AndroidView(
            factory  = { mapView },
            modifier = Modifier.fillMaxSize(),
            update   = { mv ->
                mv.overlays.clear()

                // 1. Sighting rays — colored, 4 km forward, dimmed when inactive
                sightings.forEachIndexed { idx, sighting ->
                    val (androidColor, _) = SIGHTING_COLORS[idx % SIGHTING_COLORS.size]
                    val alpha    = if (sighting.active) 210 else 70
                    val r = (androidColor shr 16) and 0xFF
                    val g = (androidColor shr  8) and 0xFF
                    val b =  androidColor         and 0xFF
                    val startGeo = GeoPoint(sighting.lat, sighting.lon)
                    val endGeo   = projectGeoPoint(sighting.lat, sighting.lon, sighting.bearingDeg.toDouble(), 4000.0)
                    mv.overlays.add(Polyline(mv).apply {
                        setPoints(listOf(startGeo, endGeo))
                        outlinePaint.color       = android.graphics.Color.argb(alpha, r, g, b)
                        outlinePaint.strokeWidth = if (sighting.active) 4f else 2f
                        outlinePaint.strokeCap   = android.graphics.Paint.Cap.ROUND
                    })
                    // Small colored dot at the sighting origin — same color as the ray
                    mv.overlays.add(Marker(mv).apply {
                        position = startGeo
                        title    = "Sighting ${idx + 1}: ${sighting.bearingDeg.roundToInt()}°"
                        icon     = sightingDotDrawable(mv.context, androidColor, active = sighting.active)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        setOnMarkerClickListener { _, _ -> false }
                    })
                }

                // 2. Estimated mark — gold target circle, visually distinct from sighting dots
                if (triResult != null) {
                    mv.overlays.add(Marker(mv).apply {
                        position = GeoPoint(triResult.lat, triResult.lon)
                        title    = "Estimated Mark  (${triResult.intersectionCount} intersections)"
                        icon     = estimatedMarkDrawable(mv.context)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        setOnMarkerClickListener { _, _ -> false }
                    })
                }

                // 3. Live phone heading line + boat marker (on top)
                if (hasPosition && latestData != null) {
                    val boatGeo = GeoPoint(latestData.lat, latestData.lon)

                    phoneImuHeading?.let { hdg ->
                        val hdgEnd = projectGeoPoint(latestData.lat, latestData.lon, hdg.toDouble(), 2000.0)
                        mv.overlays.add(Polyline(mv).apply {
                            setPoints(listOf(boatGeo, hdgEnd))
                            outlinePaint.color       = android.graphics.Color.argb(200, 255, 171, 64)
                            outlinePaint.strokeWidth = 3f
                            outlinePaint.strokeCap   = android.graphics.Paint.Cap.ROUND
                            outlinePaint.pathEffect  = android.graphics.DashPathEffect(floatArrayOf(15f, 10f), 0f)
                        })
                    }

                    mv.overlays.add(Marker(mv).apply {
                        position = boatGeo
                        title    = ""
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon     = boatBitmapDrawable(mv.context, latestData.heading)
                        setOnMarkerClickListener { _, _ -> false }
                    })
                }

                mv.invalidate()
            }
        )
    }
}

// ──────────────────────────── SIGHTING ROW ───────────────────────────

@Composable
private fun SightingRow(
    index:    Int,
    sighting: Sighting,
    color:    Color,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val activeColor = if (sighting.active) color else color.copy(alpha = 0.25f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = if (sighting.active) Color(0xFF14141E) else Color(0xFF0C0C14)
        ),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color dot
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .height(12.dp)
                    .background(activeColor, RoundedCornerShape(50))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = "#$index  ${sighting.bearingDeg.roundToInt()}° bearing",
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color      = if (sighting.active) Color.White else Color(0xFF555555)
                )
                Text(
                    text     = "%.5f, %.5f".format(sighting.lat, sighting.lon),
                    fontSize = 11.sp,
                    color    = Color(0xFF666666)
                )
            }
            Switch(
                checked         = sighting.active,
                onCheckedChange = { onToggle() },
                colors          = SwitchDefaults.colors(
                    checkedThumbColor   = Color.Black,
                    checkedTrackColor   = color,
                    uncheckedThumbColor = Color(0xFF555555),
                    uncheckedTrackColor = Color(0xFF333333)
                )
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove sighting", tint = Color(0xFFFF4444))
            }
        }
    }
}

// ──────────────────── ADD MARK FROM TRIANGULATOR DIALOG ──────────────

@Composable
private fun AddMarkFromTriangulatorDialog(
    defaultName: String,
    position:    LatLng,
    onDismiss:   () -> Unit,
    onAdd:       (name: String, rounding: Rounding) -> Unit
) {
    var name     by remember { mutableStateOf(defaultName) }
    var rounding by remember { mutableStateOf(Rounding.STARBOARD) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Color(0xFF14141E),
        title            = { Text("Add Triangulated Mark", color = Color.White) },
        text             = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text     = "Position: %.5f,  %.5f".format(position.latitude, position.longitude),
                    color    = Color(0xFF888888),
                    fontSize = 12.sp
                )
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it },
                    label         = { Text("Mark Name", color = Color(0xFF888888)) },
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = PrimaryColor,
                        unfocusedBorderColor = Color(0xFF444444),
                        focusedTextColor     = Color.White,
                        unfocusedTextColor   = Color.White,
                        cursorColor          = PrimaryColor
                    ),
                    modifier    = Modifier.fillMaxWidth(),
                    singleLine  = true
                )
                Text("Rounding", fontSize = 12.sp, color = Color(0xFF888888))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = rounding == Rounding.STARBOARD,
                        onClick  = { rounding = Rounding.STARBOARD },
                        label    = { Text("STARBOARD") },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF003300),
                            selectedLabelColor     = Color(0xFF00FF88),
                            labelColor             = Color(0xFF888888)
                        )
                    )
                    FilterChip(
                        selected = rounding == Rounding.PORT,
                        onClick  = { rounding = Rounding.PORT },
                        label    = { Text("PORT") },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF330000),
                            selectedLabelColor     = Color(0xFFFF4444),
                            labelColor             = Color(0xFF888888)
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = { onAdd(name.trim().ifBlank { "Mark" }, rounding) },
                enabled  = name.isNotBlank(),
                colors   = ButtonDefaults.buttonColors(containerColor = PrimaryColor, contentColor = Color.Black)
            ) { Text("ADD", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", color = Color(0xFF888888)) }
        }
    )
}

// ─────────────────── TRIANGULATOR MARKER DRAWABLES ──────────────────

/**
 * Small filled circle in [androidColor], white border.
 * Used as a sighting-origin pin on the triangulator map.
 * Dimmed (low alpha) when the sighting is inactive.
 */
private fun sightingDotDrawable(context: Context, androidColor: Int, active: Boolean = true): BitmapDrawable {
    val dp   = context.resources.displayMetrics.density
    val r    = (8f * dp)
    val pad  = 3f
    val size = ((r + pad) * 2).toInt()
    val bmp  = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val cv   = AndroidCanvas(bmp)
    val cx   = size / 2f
    val cy   = size / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    // Extract RGB from the (fully opaque) sighting color and apply desired alpha
    val alpha = if (active) 230 else 90
    paint.color = android.graphics.Color.argb(
        alpha,
        (androidColor shr 16) and 0xFF,
        (androidColor shr  8) and 0xFF,
         androidColor         and 0xFF
    )
    paint.style = Paint.Style.FILL
    cv.drawCircle(cx, cy, r, paint)
    // White border
    paint.color       = android.graphics.Color.argb(alpha, 255, 255, 255)
    paint.style       = Paint.Style.STROKE
    paint.strokeWidth = 2f * dp
    cv.drawCircle(cx, cy, r - dp, paint)
    return BitmapDrawable(context.resources, bmp)
}

/**
 * Gold target circle — outer ring + small black centre dot.
 * Used for the triangulated estimated-mark position.
 */
private fun estimatedMarkDrawable(context: Context): BitmapDrawable {
    val dp   = context.resources.displayMetrics.density
    val r    = (5.5f * dp)
    val pad  = 2f
    val size = ((r + pad) * 2).toInt()
    val bmp  = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val cv   = AndroidCanvas(bmp)
    val cx   = size / 2f
    val cy   = size / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    // Gold fill
    paint.color = android.graphics.Color.rgb(255, 200, 0)
    paint.style = Paint.Style.FILL
    cv.drawCircle(cx, cy, r, paint)
    // White border
    paint.color       = android.graphics.Color.WHITE
    paint.style       = Paint.Style.STROKE
    paint.strokeWidth = 1.5f * dp
    cv.drawCircle(cx, cy, r - dp * 0.75f, paint)
    // Black centre dot
    paint.color = android.graphics.Color.BLACK
    paint.style = Paint.Style.FILL
    cv.drawCircle(cx, cy, 1.75f * dp, paint)
    return BitmapDrawable(context.resources, bmp)
}

// ──────────────────────────── MAP PICKER DIALOG ──────────────────────

@Composable
private fun MapPickerDialog(
    initialCenter: LatLng?,
    existingPosition: LatLng?,
    latestData: SensorData? = null,
    marks: List<RaceMark> = emptyList(),
    historicalCogDeg: Float? = null,
    showHeadingLines: Boolean = true,
    headingLineMeters: Int = 1000,
    onDismiss: () -> Unit,
    onConfirm: (LatLng) -> Unit
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val selectedGeoPoint = remember { mutableStateOf<GeoPoint?>(null) }

    Configuration.getInstance().userAgentValue = context.packageName

    // Tap overlay is stable — stored outside mapView so we can re-add it in the update lambda
    val tapOverlay = remember {
        object : Overlay() {
            override fun onSingleTapConfirmed(e: MotionEvent, mv: MapView): Boolean {
                val proj = mv.projection
                val gp = proj.fromPixels(e.x.toInt(), e.y.toInt())
                selectedGeoPoint.value = GeoPoint(gp.latitude, gp.longitude)
                // recomposition triggered by selectedGeoPoint change → update lambda redraws all overlays
                return true
            }
        }
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(16.0)
            val center = existingPosition ?: initialCenter
            center?.let { controller.setCenter(GeoPoint(it.latitude, it.longitude)) }
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
            existingPosition?.let { pos ->
                selectedGeoPoint.value = GeoPoint(pos.latitude, pos.longitude)
            }
            overlays.add(tapOverlay)
        }
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE  -> mapView.onPause()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Tap to place mark",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = selectedGeoPoint.value?.let {
                        "%.5f, %.5f".format(it.latitude, it.longitude)
                    } ?: "Tap the map to select a position",
                    color = if (selectedGeoPoint.value != null) PrimaryColor else Color(0xFF888888),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                AndroidView(
                    factory = { mapView },
                    update = { mv ->
                        mv.overlays.clear()

                        // Boat position + heading lines
                        val boatLat = latestData?.lat
                        val boatLon = latestData?.lon
                        if (boatLat != null && boatLon != null && (boatLat != 0.0 || boatLon != 0.0)) {
                            val boatGeo = GeoPoint(boatLat, boatLon)
                            if (showHeadingLines) {
                                if ((latestData.imuAccuracy) >= 1) {
                                    val hdgEnd = projectGeoPoint(boatLat, boatLon, latestData.heading.toDouble(), headingLineMeters.toDouble())
                                    mv.overlays.add(Polyline(mv).apply {
                                        setPoints(listOf(boatGeo, hdgEnd))
                                        outlinePaint.color = android.graphics.Color.argb(210, 0, 220, 80)
                                        outlinePaint.strokeWidth = 3f
                                        outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                                    })
                                }
                                val cogDeg = historicalCogDeg ?: latestData.cogDeg
                                if (latestData.fixType >= 2 && latestData.sogKts >= 0.3f) {
                                    val cogEnd = projectGeoPoint(boatLat, boatLon, cogDeg.toDouble(), headingLineMeters.toDouble())
                                    mv.overlays.add(Polyline(mv).apply {
                                        setPoints(listOf(boatGeo, cogEnd))
                                        outlinePaint.color = android.graphics.Color.argb(210, 255, 68, 68)
                                        outlinePaint.strokeWidth = 3f
                                        outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                                    })
                                }
                            }
                            mv.overlays.add(Marker(mv).apply {
                                position = boatGeo
                                title = ""
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                icon = boatBitmapDrawable(mv.context, latestData.heading)
                                setOnMarkerClickListener { _, _ -> false }
                            })
                        }

                        // Existing marks
                        for (mark in marks) {
                            val markGeo = GeoPoint(mark.position.latitude, mark.position.longitude)
                            mv.overlays.add(Marker(mv).apply {
                                position = markGeo
                                title = mark.name
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            })
                            if (mark.isGate && mark.gateEnd != null) {
                                val gateEndGeo = GeoPoint(mark.gateEnd.latitude, mark.gateEnd.longitude)
                                mv.overlays.add(Marker(mv).apply {
                                    position = gateEndGeo
                                    title = "${mark.name}B"
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                })
                                mv.overlays.add(Polyline(mv).apply {
                                    setPoints(listOf(markGeo, gateEndGeo))
                                    outlinePaint.color = android.graphics.Color.argb(180, 255, 171, 64)
                                    outlinePaint.strokeWidth = 2f
                                })
                            }
                        }

                        // User-selected position marker (on top of marks)
                        selectedGeoPoint.value?.let { gp ->
                            mv.overlays.add(Marker(mv).apply {
                                position = gp
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            })
                        }

                        // Tap overlay must be last so it intercepts touch before markers
                        mv.overlays.add(tapOverlay)
                        mv.invalidate()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("CANCEL", color = Color(0xFF888888))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            selectedGeoPoint.value?.let { gp ->
                                onConfirm(LatLng(gp.latitude, gp.longitude))
                            }
                        },
                        enabled = selectedGeoPoint.value != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryColor,
                            contentColor = Color.Black
                        )
                    ) {
                        Text("CONFIRM", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
