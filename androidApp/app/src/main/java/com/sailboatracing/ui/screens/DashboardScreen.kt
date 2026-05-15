package com.sailboatracing.ui.screens

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.graphics.Color as AndroidColor
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import kotlin.math.abs
import com.sailboatracing.model.DashboardChartType
import com.sailboatracing.model.DashboardTile
import com.sailboatracing.model.HeadingTrend
import com.sailboatracing.model.RaceMark
import com.sailboatracing.model.RaceState
import com.sailboatracing.model.SensorData
import com.sailboatracing.model.StartLine
import com.sailboatracing.model.StartLineStatus
import com.sailboatracing.model.Tack
import com.sailboatracing.model.WidgetType
import com.sailboatracing.ui.theme.PrimaryColor
import com.sailboatracing.viewmodel.RaceViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@Composable
fun DashboardScreen(viewModel: RaceViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val data = state.latestData

    // Keep screen on while dashboard is visible
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 2.dp, vertical = 0.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        StatusBar(
            connected = state.connected,
            imuAccuracy = data?.imuAccuracy ?: 0,
            fixType = data?.fixType ?: 0,
            rtkStatus = data?.rtkStatus ?: 0,
            phoneImuActive = state.phoneImuActive,
            phoneGpsActive = state.phoneGpsActive,
            gpsStale = state.gpsStale,
            editMode = state.dashboardEditMode,
            onToggleEditMode = { viewModel.setDashboardEditMode(!state.dashboardEditMode) }
        )

        DashboardGrid(state = state, viewModel = viewModel)

        // Speed row — large kts number, heel angle to its right
        val imuOk = (data?.imuAccuracy ?: 0) >= 1
        val rollDeg = data?.roll ?: 0f
        // Box lets speed stay truly centered while heel is anchored to the right edge,
        // so varying heel digit width never shifts the speed number.
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // Speed — always centered
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (data != null) "%.1f".format(data.sogKts) else "--.-",
                    fontSize = 80.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    lineHeight = 70.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "kts",
                    fontSize = 20.sp,
                    color = Color(0xFF888888),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            // Heel — pinned to right edge, never affects speed position
            val heelAbs = abs(rollDeg)
            val heelSuffix = if (heelAbs < 1f) "" else if (rollDeg > 0f) "P" else "S"
            Column(
                modifier = Modifier.align(Alignment.CenterEnd).padding(bottom = 6.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "%.1f°%s".format(heelAbs, heelSuffix),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (imuOk) Color(0xFF00CFCF) else Color(0xFF886600),
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "HEEL",
                    fontSize = 11.sp,
                    color = Color(0xFF666666)
                )
            }
        }

        // Headed/Lifted banner — compact vertical padding
        HeadedLiftedBanner(
            trend = state.headingTrend,
            trendDegrees = state.trendDegrees,
            tack = state.tack,
            onToggleTack = {
                viewModel.setTack(if (state.tack == Tack.STARBOARD) Tack.PORT else Tack.STARBOARD)
            }
        )

        // Race timer state — used both for CourseInfoCard and TimerDisplay
        val ts = state.timerState

        // Combined course info card — swipeable across start line then each mark individually
        CourseInfoCard(
            startLineStatus = state.startLineStatus,
            marks = state.marks,
            activeMarkIndex = state.activeMarkIndex,
            vmgKts = state.vmgKts,
            distToMarkNm = state.distToMarkNm,
            bearingToMarkDeg = state.bearingToMarkDeg,
            timerRemainingSeconds = if (ts.running) ts.remainingMs / 1000f else null,
            onSetActiveMark = { viewModel.setActiveMark(it) }
        )

        // Race timer — hidden after the timer fires (the map can expand upward)
        if (ts.running || (!ts.finished && ts.remainingMs > 0L)) {
            TimerDisplay(timerState = ts)
        }

        if (state.showMap) {
            RaceMap(
                viewModel = viewModel,
                latestData = state.latestData,
                startLine = state.startLine,
                marks = state.marks,
                activeMarkIndex = state.activeMarkIndex,
                trailHistory = state.trailHistory,
                showHeadingLines = state.showHeadingLines,
                headingLineMeters = state.headingLineMeters,
                historicalCogDeg = state.historicalCogDeg,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        }

        // Inline charts (one per enabled type, in enum order)
        if (state.dashboardCharts.isNotEmpty() && state.history.isNotEmpty()) {
            DashboardChartType.entries.forEach { type ->
                if (type in state.dashboardCharts) {
                    when (type) {
                        DashboardChartType.DIRECTION -> DashboardDirectionCard(state.history)
                        DashboardChartType.ALL       -> DashboardAllCard(state.history)
                        else -> DashboardInlineChart(history = state.history, chartType = type)
                    }
                }
            }
        }

        RecordingBar(
            isRecording = state.isRecording,
            recordingStartMs = state.recordingStartMs,
            recordingFilePath = state.recordingFilePath,
            onStart = { viewModel.startRecording() },
            onStop = { viewModel.stopRecording() }
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------------------------
// Dashboard grid — 3-column tile layout
// ---------------------------------------------------------------------------

private val GRID_COLS = 3
private val ROW_HEIGHT = 62.dp

@Composable
private fun DashboardGrid(state: RaceState, viewModel: RaceViewModel) {
    val tiles = state.dashboardTiles
    val editMode = state.dashboardEditMode

    var movingTile by remember { mutableStateOf<DashboardTile?>(null) }
    var addAtCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    // Cancel move state when edit mode exits or tiles change externally
    LaunchedEffect(editMode) { if (!editMode) movingTile = null }
    LaunchedEffect(tiles) { if (movingTile != null && movingTile !in tiles) movingTile = null }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val colW = maxWidth / GRID_COLS

        val occupiedCells: Set<Pair<Int, Int>> = tiles.flatMap { tile ->
            (tile.col until tile.col + tile.colSpan).flatMap { c ->
                (tile.row until tile.row + tile.rowSpan).map { r -> c to r }
            }
        }.toSet()

        val maxRow = tiles.maxOfOrNull { it.row + it.rowSpan } ?: 0
        val gridRows = if (editMode) maxRow + 3 else maxRow

        Box(modifier = Modifier.fillMaxWidth().height(ROW_HEIGHT * gridRows)) {

            // Empty cells — only in edit mode
            if (editMode) {
                for (r in 0 until gridRows) {
                    for (c in 0 until GRID_COLS) {
                        if ((c to r) !in occupiedCells) {
                            val isDropTarget = movingTile != null
                            Box(
                                modifier = Modifier
                                    .offset(x = colW * c, y = ROW_HEIGHT * r)
                                    .width(colW)
                                    .height(ROW_HEIGHT)
                                    .border(0.5.dp, Color(0xFF2A2A3A))
                                    .clickable {
                                        val mv = movingTile
                                        if (mv != null) {
                                            viewModel.moveDashboardTile(mv, c, r)
                                            movingTile = null
                                        } else {
                                            addAtCell = c to r
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isDropTarget) "⬇" else "+",
                                    color = if (isDropTarget) Color(0xFF00FF88) else Color(0xFF444444),
                                    fontSize = if (isDropTarget) 18.sp else 14.sp
                                )
                            }
                        }
                    }
                }
            }

            // Tiles
            tiles.forEach { tile ->
                val isMoving = tile == movingTile
                Box(
                    modifier = Modifier
                        .offset(x = colW * tile.col, y = ROW_HEIGHT * tile.row)
                        .width(colW * tile.colSpan)
                        .height(ROW_HEIGHT * tile.rowSpan)
                        .border(
                            1.dp,
                            if (editMode && isMoving) Color(0xFF00FF88)
                            else if (editMode) Color(0xFF2A2A4A)
                            else Color(0xFF1E1E2A)
                        )
                        .then(
                            if (editMode && isMoving) Modifier.background(Color(0x2200FF88))
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (!editMode) {
                        WidgetContent(tile = tile, state = state, viewModel = viewModel)
                    } else {
                        // In edit mode: dim content, show label, tap tile to select for move
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(tile, movingTile) {
                                    detectTapGestures { movingTile = if (isMoving) null else tile }
                                }
                        ) {
                            WidgetContent(tile = tile, state = state, viewModel = viewModel)
                            // Dim overlay
                            Box(modifier = Modifier.fillMaxSize().background(Color(0x88000000)))
                            // Widget label
                            Text(
                                text = tile.widgetType.label,
                                color = Color(0xDDFFFFFF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.Center)
                            )
                            // Remove button — pointerInput so it doesn't bubble to tile tap
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(22.dp)
                                    .background(Color(0xCC330000), CircleShape)
                                    .pointerInput(tile) {
                                        detectTapGestures { viewModel.removeDashboardTile(tile) }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("✕", color = Color(0xFFFF6666), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            // Move indicator when selected
                            if (isMoving) {
                                Text(
                                    text = "TAP ⬇ TO PLACE",
                                    color = Color(0xFF00FF88),
                                    fontSize = 9.sp,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Add tile dialog — shown when a + cell is tapped
    val cell = addAtCell
    if (cell != null) {
        AddTileDialog(
            onDismiss = { addAtCell = null },
            onAdd = { widgetType, colSpan, rowSpan ->
                viewModel.addDashboardTile(
                    DashboardTile(
                        widgetType = widgetType,
                        col = cell.first,
                        row = cell.second,
                        colSpan = colSpan,
                        rowSpan = rowSpan
                    )
                )
                addAtCell = null
            }
        )
    }
}

@Composable
private fun WidgetContent(tile: DashboardTile, state: RaceState, viewModel: RaceViewModel) {
    val data = state.latestData
    when (tile.widgetType) {
        WidgetType.HEADING -> HeadingRow(
            hdgValue = data?.heading ?: 0f,
            historicalCogDeg = state.historicalCogDeg,
            imuAccuracy = data?.imuAccuracy ?: 0
        )
        WidgetType.SPEED -> SpeedHeelWidget(data = data)
        WidgetType.TREND -> HeadedLiftedBanner(
            trend = state.headingTrend,
            trendDegrees = state.trendDegrees,
            tack = state.tack,
            onToggleTack = { viewModel.setTack(if (state.tack == Tack.STARBOARD) Tack.PORT else Tack.STARBOARD) }
        )
        WidgetType.COURSE_INFO -> {
            val ts = state.timerState
            CourseInfoCard(
                startLineStatus = state.startLineStatus,
                marks = state.marks,
                activeMarkIndex = state.activeMarkIndex,
                vmgKts = state.vmgKts,
                distToMarkNm = state.distToMarkNm,
                bearingToMarkDeg = state.bearingToMarkDeg,
                timerRemainingSeconds = if (ts.running) ts.remainingMs / 1000f else null,
                onSetActiveMark = { viewModel.setActiveMark(it) }
            )
        }
        WidgetType.TIMER -> TimerDisplay(timerState = state.timerState)
        WidgetType.MAP -> RaceMap(
            viewModel = viewModel,
            latestData = state.latestData,
            startLine = state.startLine,
            marks = state.marks,
            activeMarkIndex = state.activeMarkIndex,
            trailHistory = state.trailHistory,
            showHeadingLines = state.showHeadingLines,
            headingLineMeters = state.headingLineMeters,
            historicalCogDeg = state.historicalCogDeg,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
        )
        WidgetType.CHART_SPEED ->
            if (state.history.isNotEmpty()) DashboardInlineChart(state.history, DashboardChartType.SPEED)
        WidgetType.CHART_HEADING ->
            if (state.history.isNotEmpty()) DashboardInlineChart(state.history, DashboardChartType.HEADING)
        WidgetType.CHART_VMG ->
            if (state.history.isNotEmpty()) DashboardInlineChart(state.history, DashboardChartType.VMG)
        WidgetType.CHART_DIRECTION ->
            if (state.history.isNotEmpty()) DashboardDirectionCard(state.history)
        WidgetType.CHART_ALL ->
            if (state.history.isNotEmpty()) DashboardAllCard(state.history)
    }
}

@Composable
private fun SpeedHeelWidget(data: SensorData?) {
    val imuOk = (data?.imuAccuracy ?: 0) >= 1
    val rollDeg = data?.roll ?: 0f
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = if (data != null) "%.1f".format(data.sogKts) else "--.-",
                fontSize = 80.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                lineHeight = 70.sp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "kts",
                fontSize = 20.sp,
                color = Color(0xFF888888),
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        val heelAbs = abs(rollDeg)
        val heelSuffix = if (!imuOk || heelAbs < 1f) "" else if (rollDeg > 0f) "P" else "S"
        Column(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp, bottom = 6.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = if (imuOk) "%.1f°%s".format(heelAbs, heelSuffix) else "---.-°",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = if (imuOk) Color(0xFF00CFCF) else Color(0xFF444444),
                maxLines = 1,
                softWrap = false
            )
            Text(text = "HEEL", fontSize = 11.sp, color = Color(0xFF666666))
        }
    }
}

@Composable
private fun AddTileDialog(
    onDismiss: () -> Unit,
    onAdd: (WidgetType, Int, Int) -> Unit
) {
    var selectedType by remember { mutableStateOf(WidgetType.MAP) }
    var colSpan by remember { mutableIntStateOf(3) }
    var rowSpan by remember { mutableIntStateOf(defaultRowSpan(WidgetType.MAP)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A2E),
        title = { Text("Add Widget", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Widget type", fontSize = 11.sp, color = Color(0xFF888888))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    WidgetType.entries.forEach { type ->
                        val selected = type == selectedType
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (selected) Color(0xFF2A2A5A) else Color(0xFF111122),
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    selectedType = type
                                    rowSpan = defaultRowSpan(type)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(type.label, color = if (selected) PrimaryColor else Color(0xFFAAAAAA), fontSize = 13.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text("Columns (${colSpan})", fontSize = 11.sp, color = Color(0xFF888888))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(1, 2, 3).forEach { n ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(
                                            if (colSpan == n) PrimaryColor else Color(0xFF2A2A3A),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .clickable { colSpan = n },
                                    contentAlignment = Alignment.Center
                                ) { Text("$n", color = Color.White, fontSize = 12.sp) }
                            }
                        }
                    }
                    Column {
                        Text("Rows (${rowSpan})", fontSize = 11.sp, color = Color(0xFF888888))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(1, 2, 3, 5).forEach { n ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(
                                            if (rowSpan == n) PrimaryColor else Color(0xFF2A2A3A),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .clickable { rowSpan = n },
                                    contentAlignment = Alignment.Center
                                ) { Text("$n", color = Color.White, fontSize = 12.sp) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(selectedType, colSpan, rowSpan) },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF888888)) }
        }
    )
}

private fun defaultRowSpan(type: WidgetType) = when (type) {
    WidgetType.HEADING     -> 2
    WidgetType.SPEED       -> 2
    WidgetType.TREND       -> 1
    WidgetType.COURSE_INFO -> 2
    WidgetType.TIMER       -> 1
    WidgetType.MAP         -> 5
    else                   -> 3
}

// ---------------------------------------------------------------------------
// Course info card — swipeable: start line → mark[0] → mark[1] → …
// ---------------------------------------------------------------------------

@Composable
private fun CourseInfoCard(
    startLineStatus: StartLineStatus?,
    marks: List<RaceMark>,
    activeMarkIndex: Int,
    vmgKts: Float?,
    distToMarkNm: Float?,
    bearingToMarkDeg: Float?,
    timerRemainingSeconds: Float?,  // null when timer not running
    onSetActiveMark: (Int) -> Unit
) {
    val hasStartLine = startLineStatus != null
    val startOffset = if (hasStartLine) 1 else 0
    val totalPages = startOffset + marks.size.coerceAtLeast(if (hasStartLine) 0 else 1)

    var pageIndex by remember(hasStartLine) {
        mutableIntStateOf(if (hasStartLine) 0 else activeMarkIndex.coerceAtMost(marks.size - 1).coerceAtLeast(0))
    }
    LaunchedEffect(activeMarkIndex) {
        if (!hasStartLine || pageIndex != 0) {
            pageIndex = (activeMarkIndex + startOffset).coerceAtMost(totalPages - 1).coerceAtLeast(0)
        }
    }
    LaunchedEffect(hasStartLine) {
        if (!hasStartLine && pageIndex == 0) pageIndex = 0
    }

    // Distance unit: true = meters (default), false = nautical miles
    var showMeters by remember { mutableStateOf(true) }
    // TRUE TIME TO: tap to toggle delta-vs-timer display (only when timer running)
    var showDelta by remember { mutableStateOf(false) }

    val cardBg = if (hasStartLine && pageIndex == 0 && startLineStatus != null) {
        val el = startLineStatus.earlyOrLate
        // positive el = late, negative el = early/fast (OCS risk)
        when { el > 30f -> Color(0xFF2A1A00); el < -10f -> Color(0xFF330000); else -> Color(0xFF1A1A2E) }
    } else Color(0xFF14141E)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(pageIndex, totalPages) {
                var totalX = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalX = 0f },
                    onHorizontalDrag = { change, amount -> change.consume(); totalX += amount },
                    onDragEnd = {
                        val newPage = when {
                            totalX < -60f -> (pageIndex + 1).coerceAtMost(totalPages - 1)
                            totalX > 60f  -> (pageIndex - 1).coerceAtLeast(0)
                            else          -> pageIndex
                        }
                        if (newPage != pageIndex) {
                            pageIndex = newPage
                            val markIdx = newPage - startOffset
                            if (markIdx >= 0) onSetActiveMark(markIdx)
                        }
                    }
                )
            },
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (hasStartLine && pageIndex == 0 && startLineStatus != null) {
                // ── Start line page ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // LINE DIST — tappable to toggle m / nm
                    val distNm = startLineStatus.distanceToLineNm
                    InfoCell(
                        label = "LINE DIST",
                        value = if (showMeters) "%.0f".format(distNm * 1852f) else "%.3f".format(distNm),
                        unit = if (showMeters) "m" else "nm",
                        modifier = Modifier.clickable { showMeters = !showMeters }
                    )

                    // TRUE TIME TO — tappable to show delta vs race timer
                    val trueTime = startLineStatus.trueTimeToLineSeconds
                    val delta: Float? = if (showDelta && timerRemainingSeconds != null && trueTime != null) {
                        trueTime - timerRemainingSeconds  // positive = late, negative = early
                    } else null
                    val trueTimeValue = when {
                        delta != null && delta > 0f  -> "+%.0f".format(delta)
                        delta != null && delta <= 0f -> "%.0f".format(delta)
                        trueTime != null             -> "%.0f".format(trueTime)
                        else                         -> "--"
                    }
                    val trueTimeColor = when {
                        delta == null     -> Color.White
                        delta < -5f       -> Color(0xFFFF4444)  // early/fast = OCS risk
                        delta < 0f        -> Color(0xFFFFAB40)
                        delta > 0f        -> Color(0xFFFFAB40)  // running late
                        else              -> Color.White
                    }
                    InfoCell(
                        label = "CUR TIME TO",
                        value = trueTimeValue,
                        unit = "s",
                        valueColor = trueTimeColor,
                        modifier = Modifier.clickable {
                            if (timerRemainingSeconds != null) showDelta = !showDelta
                        }
                    )

                    // OPTIMAL TIME TO — straight-line charge distance / SOG
                    InfoCell(
                        label = "OPTIMAL TIME",
                        value = startLineStatus.optimalTimeToLineSeconds?.let { "%.0f".format(it) } ?: "--",
                        unit = "s"
                    )
                }
            } else {
                // ── Mark page ──
                val markIdx = (pageIndex - startOffset).coerceAtLeast(0)
                val shownMark = marks.getOrNull(markIdx)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    InfoCell(
                        label = "VMG",
                        value = if (vmgKts != null) "%.1f".format(vmgKts) else "--.-",
                        unit = "kts"
                    )
                    // Distance — tappable to toggle m / nm
                    InfoCell(
                        label = shownMark?.name ?: "MARK",
                        value = if (distToMarkNm != null) {
                            if (showMeters) "%.0f".format(distToMarkNm * 1852f)
                            else "%.2f".format(distToMarkNm)
                        } else "-",
                        unit = if (showMeters) "m" else "nm",
                        modifier = Modifier.clickable { showMeters = !showMeters }
                    )
                    InfoCell(
                        label = "REQ COG",
                        value = if (bearingToMarkDeg != null) "%03.0f°".format(bearingToMarkDeg) else "---",
                        unit = ""
                    )
                }
            }

            // Page indicator dots
            if (totalPages > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(totalPages) { i ->
                        if (i > 0) Spacer(modifier = Modifier.width(5.dp))
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(
                                    color = if (i == pageIndex) PrimaryColor else Color(0xFF444444),
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCell(
    label: String,
    value: String,
    unit: String,
    valueColor: Color = Color.White,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 10.sp, color = Color(0xFF888888))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = valueColor)
            if (unit.isNotEmpty()) {
                Spacer(modifier = Modifier.width(3.dp))
                Text(text = unit, fontSize = 12.sp, color = Color(0xFF888888))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Map
// ---------------------------------------------------------------------------

@Composable
private fun RaceMap(
    viewModel: RaceViewModel,
    latestData: SensorData?,
    startLine: StartLine?,
    marks: List<RaceMark>,
    activeMarkIndex: Int,
    trailHistory: List<SensorData>,
    showHeadingLines: Boolean,
    headingLineMeters: Int,
    historicalCogDeg: Float?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    Configuration.getInstance().userAgentValue = context.packageName

    // Auto-center is false if the user has previously panned (survives tab switches via ViewModel var).
    val autoCenter = remember { mutableStateOf(!viewModel.mapUserPanned) }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(viewModel.mapZoom)
            if (viewModel.mapCenterLat != 0.0 || viewModel.mapCenterLon != 0.0) {
                controller.setCenter(GeoPoint(viewModel.mapCenterLat, viewModel.mapCenterLon))
            }
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
            addMapListener(object : MapListener {
                override fun onScroll(event: ScrollEvent?): Boolean {
                    autoCenter.value = false
                    viewModel.mapUserPanned = true
                    viewModel.mapCenterLat = this@apply.mapCenter.latitude
                    viewModel.mapCenterLon = this@apply.mapCenter.longitude
                    return false
                }
                override fun onZoom(event: ZoomEvent?): Boolean {
                    autoCenter.value = false
                    viewModel.mapUserPanned = true
                    viewModel.mapZoom = this@apply.zoomLevelDouble
                    return false
                }
            })
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

    Box(modifier = modifier) {
        AndroidView(
            factory = { mapView },
            update = { mv ->
                mv.overlays.clear()

                // GPS trail — amber/orange so it reads on water
                val trailPoints = trailHistory
                    .filter { it.lat != 0.0 || it.lon != 0.0 }
                    .map { GeoPoint(it.lat, it.lon) }
                if (trailPoints.size >= 2) {
                    Polyline(mv).apply {
                        setPoints(trailPoints)
                        outlinePaint.color = android.graphics.Color.argb(200, 255, 140, 0)
                        outlinePaint.strokeWidth = 5f
                        outlinePaint.style = Paint.Style.STROKE
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.strokeJoin = Paint.Join.ROUND
                        mv.overlays.add(this)
                    }
                }

                // Heading / COG projection lines — drawn before boat so marker renders on top
                val hasPosition = latestData != null && (latestData.lat != 0.0 || latestData.lon != 0.0)
                val hasFix = (latestData?.fixType ?: 0) >= 2
                if (showHeadingLines && hasPosition) {
                    val boatPt = GeoPoint(latestData!!.lat, latestData.lon)
                    // Green — IMU heading (bow direction), only when IMU is calibrated
                    if ((latestData.imuAccuracy) >= 1) {
                        val hdgEnd = projectGeoPoint(latestData.lat, latestData.lon, latestData.heading.toDouble(), headingLineMeters.toDouble())
                        Polyline(mv).apply {
                            setPoints(listOf(boatPt, hdgEnd))
                            outlinePaint.color = android.graphics.Color.argb(210, 0, 220, 80)
                            outlinePaint.strokeWidth = 3f
                            outlinePaint.style = Paint.Style.STROKE
                            outlinePaint.strokeCap = Paint.Cap.ROUND
                            mv.overlays.add(this)
                        }
                    }
                    // Red — COG (actual course over ground), only when moving and fix active
                    val cogDeg = historicalCogDeg ?: latestData.cogDeg
                    if (hasFix && latestData.sogKts >= 0.3f) {
                        val cogEnd = projectGeoPoint(latestData.lat, latestData.lon, cogDeg.toDouble(), headingLineMeters.toDouble())
                        Polyline(mv).apply {
                            setPoints(listOf(boatPt, cogEnd))
                            outlinePaint.color = android.graphics.Color.argb(210, 255, 68, 68)
                            outlinePaint.strokeWidth = 3f
                            outlinePaint.style = Paint.Style.STROKE
                            outlinePaint.strokeCap = Paint.Cap.ROUND
                            mv.overlays.add(this)
                        }
                    }
                }

                // Boat — orange chevron with black border
                if (hasPosition) {
                    val boatPoint = GeoPoint(latestData!!.lat, latestData.lon)
                    Marker(mv).apply {
                        position = boatPoint
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = boatBitmapDrawable(context, latestData.heading)
                        title = ""
                        setOnMarkerClickListener { _, _ -> false }
                        mv.overlays.add(this)
                    }
                    if (hasFix && autoCenter.value) mv.controller.animateTo(boatPoint)
                }

                // Start line
                if (startLine != null) {
                    val pinPt  = GeoPoint(startLine.pin.latitude,  startLine.pin.longitude)
                    val boatPt = GeoPoint(startLine.boat.latitude, startLine.boat.longitude)
                    Marker(mv).apply {
                        position = pinPt; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = pinEndBitmapDrawable(context)
                        title = ""; setOnMarkerClickListener { _, _ -> false }
                        mv.overlays.add(this)
                    }
                    if (pinPt != boatPt) {
                        Marker(mv).apply {
                            position = boatPt; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            icon = committeeEndBitmapDrawable(context)
                            title = ""; setOnMarkerClickListener { _, _ -> false }
                            mv.overlays.add(this)
                        }
                        Polyline(mv).apply {
                            setPoints(listOf(pinPt, boatPt))
                            outlinePaint.color = android.graphics.Color.parseColor("#FF4444")
                            outlinePaint.strokeWidth = 4f
                            outlinePaint.style = Paint.Style.STROKE
                            mv.overlays.add(this)
                        }
                    }
                }

                // Race marks — non-active first, active last so it renders on top of overlaps
                fun addMarkOverlays(index: Int, mark: RaceMark) {
                    val isActive = index == activeMarkIndex
                    val point = GeoPoint(mark.position.latitude, mark.position.longitude)
                    if (mark.isGate && mark.gateEnd != null) {
                        val gatePoint = GeoPoint(mark.gateEnd.latitude, mark.gateEnd.longitude)
                        Polyline(mv).apply {
                            setPoints(listOf(point, gatePoint))
                            outlinePaint.color = if (isActive)
                                android.graphics.Color.argb(220, 255, 221, 0)
                            else
                                android.graphics.Color.argb(140, 180, 160, 0)
                            outlinePaint.strokeWidth = if (isActive) 4f else 2f
                            outlinePaint.style = Paint.Style.STROKE
                            mv.overlays.add(this)
                        }
                        Marker(mv).apply {
                            position = gatePoint
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            icon = markBitmapDrawable(context, "${mark.name}B", isActive)
                            title = ""; setOnMarkerClickListener { _, _ -> false }
                            mv.overlays.add(this)
                        }
                    }
                    Marker(mv).apply {
                        position = point
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = markBitmapDrawable(context, mark.name, isActive)
                        title = ""; setOnMarkerClickListener { _, _ -> false }
                        mv.overlays.add(this)
                    }
                }
                marks.forEachIndexed { index, mark ->
                    if (index != activeMarkIndex) addMarkOverlays(index, mark)
                }
                // Active mark drawn last — always on top
                marks.getOrNull(activeMarkIndex)?.let { addMarkOverlays(activeMarkIndex, it) }

                mv.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )

        if (!autoCenter.value) {
            Button(
                onClick = {
                    autoCenter.value = true
                    viewModel.mapUserPanned = false
                    latestData?.let { d ->
                        if (d.lat != 0.0 || d.lon != 0.0) mapView.controller.animateTo(GeoPoint(d.lat, d.lon))
                    }
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xCC14141E),
                    contentColor = PrimaryColor
                )
            ) { Text("CENTER", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

// ---------------------------------------------------------------------------
// Map helpers
// ---------------------------------------------------------------------------

/** Destination point given start lat/lon, bearing (degrees true), distance (metres). */
private fun projectGeoPoint(lat: Double, lon: Double, bearingDeg: Double, distanceM: Double): GeoPoint {
    val R = 6_371_000.0
    val d = distanceM / R
    val lat1 = Math.toRadians(lat)
    val lon1 = Math.toRadians(lon)
    val brng = Math.toRadians(bearingDeg)
    val lat2 = Math.asin(Math.sin(lat1) * Math.cos(d) + Math.cos(lat1) * Math.sin(d) * Math.cos(brng))
    val lon2 = lon1 + Math.atan2(Math.sin(brng) * Math.sin(d) * Math.cos(lat1), Math.cos(d) - Math.sin(lat1) * Math.sin(lat2))
    return GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lon2))
}

// ---------------------------------------------------------------------------
// Map marker bitmaps — high-contrast for water/chart backgrounds
// ---------------------------------------------------------------------------

// Boat: 20 dp orange chevron with black border
private fun boatBitmapDrawable(context: Context, headingDeg: Float): BitmapDrawable {
    val dp = context.resources.displayMetrics.density
    val size = (20 * dp).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val cv = AndroidCanvas(bmp)
    cv.rotate(headingDeg, size / 2f, size / 2f)
    val w = size.toFloat(); val h = size.toFloat()
    cv.drawPath(AndroidPath().apply {
        moveTo(w * 0.50f, h * 0.02f); lineTo(w * 0.86f, h * 0.92f)
        lineTo(w * 0.50f, h * 0.65f); lineTo(w * 0.14f, h * 0.92f); close()
    }, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; style = Paint.Style.FILL })
    cv.drawPath(AndroidPath().apply {
        moveTo(w * 0.50f, h * 0.10f); lineTo(w * 0.79f, h * 0.88f)
        lineTo(w * 0.50f, h * 0.65f); lineTo(w * 0.21f, h * 0.88f); close()
    }, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#FF8C00"); style = Paint.Style.FILL
    })
    return BitmapDrawable(context.resources, bmp)
}

// Race mark: 5 dp radius yellow circle with black border + white label on dark pill
private fun markBitmapDrawable(context: Context, name: String, active: Boolean): BitmapDrawable {
    val dp = context.resources.displayMetrics.density
    val r = 5f * dp
    val borderR = r + 1.5f * dp
    val textSz = 8f * dp
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE; textSize = textSz; typeface = Typeface.DEFAULT_BOLD
    }
    val tw = textPaint.measureText(name)
    val padH = 3f * dp
    val padV = 2f * dp
    val circleTop = borderR + 1f * dp
    val labelTop = circleTop + borderR + 3f * dp
    val bmpW = maxOf(borderR * 2f + 4f, tw + padH * 2f).toInt()
    val bmpH = (labelTop + textSz + padV).toInt()
    val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
    val cv = AndroidCanvas(bmp)
    // Black border circle
    cv.drawCircle(bmpW / 2f, circleTop, borderR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK; style = Paint.Style.FILL
    })
    // Yellow (active) or dim fill
    cv.drawCircle(bmpW / 2f, circleTop, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (active) android.graphics.Color.parseColor("#FFDD00")
                else        android.graphics.Color.parseColor("#997700")
        style = Paint.Style.FILL
    })
    // Dark label background pill
    val lx = (bmpW - tw) / 2f - padH
    val ly = labelTop - padV
    cv.drawRoundRect(RectF(lx, ly, lx + tw + padH * 2f, ly + textSz + padV * 2f),
        3f * dp, 3f * dp,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(210, 0, 0, 0); style = Paint.Style.FILL
        })
    cv.drawText(name, (bmpW - tw) / 2f, labelTop + textSz, textPaint)
    return BitmapDrawable(context.resources, bmp)
}

// Pin end of start line: 8 dp diameter red circle with black border + "PIN" on dark pill
private fun pinEndBitmapDrawable(context: Context): BitmapDrawable {
    val dp = context.resources.displayMetrics.density
    val r = 4f * dp
    val borderR = r + 1.5f * dp
    val label = "PIN"
    val textSz = 7f * dp
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE; textSize = textSz; typeface = Typeface.DEFAULT_BOLD
    }
    val tw = textPaint.measureText(label)
    val padH = 2.5f * dp; val padV = 1.5f * dp
    val circleTop = borderR + 1f * dp
    val labelTop = circleTop + borderR + 2f * dp
    val bmpW = maxOf(borderR * 2f + 4f, tw + padH * 2f).toInt()
    val bmpH = (labelTop + textSz + padV).toInt()
    val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
    val cv = AndroidCanvas(bmp)
    cv.drawCircle(bmpW / 2f, circleTop, borderR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK; style = Paint.Style.FILL
    })
    cv.drawCircle(bmpW / 2f, circleTop, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.RED; style = Paint.Style.FILL
    })
    val lx = (bmpW - tw) / 2f - padH
    val ly = labelTop - padV
    cv.drawRoundRect(RectF(lx, ly, lx + tw + padH * 2f, ly + textSz + padV * 2f),
        2f * dp, 2f * dp,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(210, 0, 0, 0); style = Paint.Style.FILL
        })
    cv.drawText(label, (bmpW - tw) / 2f, labelTop + textSz, textPaint)
    return BitmapDrawable(context.resources, bmp)
}

// Committee boat end: 8 dp red square with black border + "BOAT" on dark pill
private fun committeeEndBitmapDrawable(context: Context): BitmapDrawable {
    val dp = context.resources.displayMetrics.density
    val sz = 8f * dp
    val border = 1.5f * dp
    val label = "BOAT"
    val textSz = 7f * dp
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE; textSize = textSz; typeface = Typeface.DEFAULT_BOLD
    }
    val tw = textPaint.measureText(label)
    val padH = 2.5f * dp; val padV = 1.5f * dp
    val squareTop = (sz + border * 2f) / 2f + 1f * dp
    val labelTop = squareTop + (sz + border * 2f) / 2f + 2f * dp
    val bmpW = maxOf(sz + border * 2f + 4f, tw + padH * 2f).toInt()
    val bmpH = (labelTop + textSz + padV).toInt()
    val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
    val cv = AndroidCanvas(bmp)
    val bLeft = (bmpW - sz - border * 2f) / 2f
    cv.drawRect(bLeft, squareTop - (sz + border * 2f) / 2f,
                bLeft + sz + border * 2f, squareTop + (sz + border * 2f) / 2f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; style = Paint.Style.FILL })
    val sLeft = (bmpW - sz) / 2f
    cv.drawRect(sLeft, squareTop - sz / 2f, sLeft + sz, squareTop + sz / 2f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.RED; style = Paint.Style.FILL })
    val lx = (bmpW - tw) / 2f - padH
    val ly = labelTop - padV
    cv.drawRoundRect(RectF(lx, ly, lx + tw + padH * 2f, ly + textSz + padV * 2f),
        2f * dp, 2f * dp,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(210, 0, 0, 0); style = Paint.Style.FILL
        })
    cv.drawText(label, (bmpW - tw) / 2f, labelTop + textSz, textPaint)
    return BitmapDrawable(context.resources, bmp)
}

// ---------------------------------------------------------------------------
// Recording bar
// ---------------------------------------------------------------------------

@Composable
private fun RecordingBar(
    isRecording: Boolean,
    recordingStartMs: Long,
    recordingFilePath: String,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    var elapsedText by remember { mutableStateOf("") }

    LaunchedEffect(isRecording, recordingStartMs) {
        if (!isRecording) { elapsedText = ""; return@LaunchedEffect }
        while (true) {
            val elapsed = System.currentTimeMillis() - recordingStartMs
            val h = elapsed / 3_600_000
            val m = (elapsed % 3_600_000) / 60_000
            val s = (elapsed % 60_000) / 1_000
            elapsedText = "%02d:%02d:%02d".format(h, m, s)
            kotlinx.coroutines.delay(1_000)
        }
    }

    if (isRecording) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A0A0A), RoundedCornerShape(6.dp))
                .padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).background(Color(0xFFFF4444), CircleShape))
                Spacer(modifier = Modifier.width(8.dp))
                Text("REC  $elapsedText", color = Color(0xFFFF4444), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onStop) {
                Text("STOP", color = Color(0xFFFF8888), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    } else {
        OutlinedButton(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF888888)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333))
        ) {
            Text("⏺  START RECORDING", fontSize = 12.sp)
        }
    }
}

// Status bar
// ---------------------------------------------------------------------------

@Composable
private fun StatusBar(
    connected: Boolean,
    imuAccuracy: Int,
    fixType: Int,
    rtkStatus: Int,
    phoneImuActive: Boolean,
    phoneGpsActive: Boolean,
    gpsStale: Boolean,
    editMode: Boolean = false,
    onToggleEditMode: () -> Unit = {}
) {
    var showImuInitDialog by remember { mutableStateOf(false) }

    val imuText = when {
        connected && imuAccuracy >= 1 -> "BT IMU"
        connected                     -> "IMU INIT"
        phoneImuActive                -> "PHONE IMU"
        else                          -> "NO IMU"
    }
    val imuColor = when {
        connected && imuAccuracy >= 2  -> Color(0xFF00FF88)
        connected && imuAccuracy >= 1  -> Color(0xFFFFAB40)
        connected                      -> Color(0xFFFF8800)
        phoneImuActive                 -> Color(0xFFFFAB40)
        else                           -> Color(0xFFFF4444)
    }
    val gpsText = when {
        connected && rtkStatus == 2    -> "RTK FIXED"
        connected && rtkStatus == 1    -> "RTK FLOAT"
        connected && fixType == 3      -> "GPS 3D"
        connected && fixType == 2      -> "GPS 2D"
        connected                      -> "NO FIX"
        gpsStale                       -> "GPS STALE"
        phoneGpsActive && fixType >= 2 -> "PHONE GPS"
        else                           -> "NO GPS"
    }
    val gpsColor = when {
        connected && rtkStatus == 2    -> Color(0xFF00CFFF)  // cyan — centimeter accuracy
        connected && rtkStatus == 1    -> Color(0xFF88AAFF)  // blue-ish — float, ~10-30 cm
        connected && fixType == 3      -> Color(0xFF00FF88)
        connected && fixType == 2      -> Color(0xFFFFAB40)
        gpsStale                       -> Color(0xFFFF8800)
        phoneGpsActive && fixType >= 2 -> Color(0xFFFFAB40)
        else                           -> Color(0xFFFF4444)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(if (connected) Color(0xFF00FF88) else Color(0xFF888888), CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (connected) "CONNECTED" else "DISCONNECTED",
                fontSize = 11.sp,
                color = if (connected) Color(0xFF00FF88) else Color(0xFF888888)
            )
        }
        Text(
            text = imuText,
            fontSize = 11.sp,
            color = imuColor,
            modifier = if (imuText == "IMU INIT") Modifier.clickable { showImuInitDialog = true } else Modifier
        )
        Text(text = gpsText, fontSize = 11.sp, color = gpsColor)
        // Edit mode toggle
        Text(
            text = if (editMode) "🔓" else "🔒",
            fontSize = 16.sp,
            modifier = Modifier
                .clickable { onToggleEditMode() }
                .padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }

    if (showImuInitDialog) {
        AlertDialog(
            onDismissRequest = { showImuInitDialog = false },
            title = { Text("IMU Calibrating") },
            text = { Text("The compass hasn't been calibrated yet — values may drift. Move the device in a figure-8 pattern a few times to complete calibration.") },
            confirmButton = {
                TextButton(onClick = { showImuInitDialog = false }) { Text("OK") }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Heading row — fixed-width containers so the arrow never shifts sideways
// ---------------------------------------------------------------------------

@Composable
private fun HeadingRow(hdgValue: Float, historicalCogDeg: Float?, imuAccuracy: Int) {
    val accumulatedDeg = remember { mutableStateOf(hdgValue) }
    val prevRawValue = remember { mutableStateOf(hdgValue) }

    LaunchedEffect(hdgValue) {
        val prev = prevRawValue.value
        var diff = hdgValue - prev
        while (diff > 180f) diff -= 360f
        while (diff < -180f) diff += 360f
        accumulatedDeg.value += diff
        prevRawValue.value = hdgValue
    }

    val animatedRotation by animateFloatAsState(
        targetValue = accumulatedDeg.value,
        animationSpec = tween(durationMillis = 80),
        label = "heading_rotation"
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Arrow pinned to far left, followed by HDG
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            val imuOk = imuAccuracy >= 1
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "↑",
                    fontSize = 40.sp,
                    color = if (imuOk) PrimaryColor else Color(0xFF886600),
                    modifier = Modifier.rotate(animatedRotation)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "%.1f°".format(hdgValue),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (imuOk) Color.White else Color(0xFF886600),
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "HDG",
                    fontSize = 12.sp,
                    color = Color(0xFF666666),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }

        // Right: COG
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = if (historicalCogDeg != null) "%.1f°".format(historicalCogDeg) else "---.-",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = if (historicalCogDeg != null) Color(0xFFFF4444) else Color(0xFF555555),
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = "COG",
                fontSize = 11.sp,
                color = Color(0xFF666666),
                modifier = Modifier.padding(end = 4.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Headed/Lifted banner — compact padding
// ---------------------------------------------------------------------------

@Composable
private fun HeadedLiftedBanner(
    trend: HeadingTrend,
    trendDegrees: Float,
    tack: Tack,
    onToggleTack: () -> Unit
) {
    val bannerColor by animateColorAsState(
        targetValue = when (trend) {
            HeadingTrend.LIFTED_STRONG -> Color(0xFF003300)
            HeadingTrend.LIFTED        -> Color(0xFF002200)
            HeadingTrend.NEUTRAL       -> Color(0xFF1A1A2E)
            HeadingTrend.HEADED        -> Color(0xFF330000)
            HeadingTrend.HEADED_STRONG -> Color(0xFF440000)
        },
        animationSpec = tween(durationMillis = 500),
        label = "banner_color"
    )
    val trendTextColor = when (trend) {
        HeadingTrend.LIFTED_STRONG, HeadingTrend.LIFTED -> Color(0xFF00FF88)
        HeadingTrend.NEUTRAL                             -> Color(0xFFAAAAAA)
        HeadingTrend.HEADED, HeadingTrend.HEADED_STRONG -> Color(0xFFFF4444)
    }
    val trendLabel = when (trend) {
        HeadingTrend.LIFTED_STRONG -> "▲ LIFTED (+%.1f°)".format(trendDegrees)
        HeadingTrend.LIFTED        -> "▲ LIFTED (+%.1f°)".format(trendDegrees)
        HeadingTrend.NEUTRAL       -> "— NEUTRAL"
        HeadingTrend.HEADED        -> "▼ HEADED (%.1f°)".format(trendDegrees)
        HeadingTrend.HEADED_STRONG -> "▼ HEADED (%.1f°)".format(trendDegrees)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bannerColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = trendLabel, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = trendTextColor)
        Text(
            text = if (tack == Tack.STARBOARD) "STBD" else "PORT",
            fontSize = 14.sp, fontWeight = FontWeight.Bold,
            color = if (tack == Tack.STARBOARD) PrimaryColor else Color(0xFFFFAB40),
            modifier = Modifier
                .clickable { onToggleTack() }
                .padding(vertical = 4.dp, horizontal = 4.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// Timer display
// ---------------------------------------------------------------------------

@Composable
private fun TimerDisplay(timerState: com.sailboatracing.model.TimerState) {
    val remaining = timerState.remainingMs
    val minutes = remaining / 60000L
    val seconds = (remaining % 60000L) / 1000L
    val tenths = (remaining % 1000L) / 100L
    val urgencyColor = when {
        timerState.finished  -> Color(0xFFFF4444)
        remaining <= 10_000L -> Color(0xFFFF4444)
        remaining <= 60_000L -> Color(0xFFFFAB40)
        else                 -> PrimaryColor
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "RACE TIMER", fontSize = 10.sp, color = Color(0xFF888888))
        Text(
            text = if (timerState.finished) "START!" else "%d:%02d.%d".format(minutes, seconds, tenths),
            fontSize = 36.sp, fontWeight = FontWeight.Bold, color = urgencyColor
        )
    }
}

// ---------------------------------------------------------------------------
// Inline chart for the dashboard bottom panel
// ---------------------------------------------------------------------------

@Composable
private fun DashboardInlineChart(history: List<SensorData>, chartType: DashboardChartType) {
    val endTs = history.last().timestampMs
    val entries = history.map { d ->
        Entry(
            (d.timestampMs - endTs) / 1000f,
            when (chartType) {
                DashboardChartType.SPEED   -> d.sogKts
                DashboardChartType.HEADING -> d.heading
                DashboardChartType.VMG     -> d.sogKts
                else                       -> 0f
            }
        )
    }
    val latestVal = entries.lastOrNull()?.y ?: 0f
    val label = when (chartType) {
        DashboardChartType.SPEED   -> "Speed (kts)"
        DashboardChartType.HEADING -> "Heading (°)"
        DashboardChartType.VMG     -> "VMG (kts)"
        else                       -> ""
    }
    val lineColor = when (chartType) {
        DashboardChartType.SPEED   -> "#00C8FF"
        DashboardChartType.HEADING -> "#AA88FF"
        DashboardChartType.VMG     -> "#00FF88"
        else                       -> "#00C8FF"
    }

    val selectedX = remember { mutableStateOf<Float?>(null) }
    val displayVal = selectedX.value?.let { tx ->
        entries.minByOrNull { abs(it.x - tx) }?.y
    } ?: latestVal
    val valueText = when (chartType) {
        DashboardChartType.HEADING -> "%.1f°".format(displayVal)
        else                       -> "%.1f kts".format(displayVal)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = label, fontSize = 10.sp, color = Color(0xFF888888))
                Text(
                    text = valueText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(AndroidColor.parseColor(lineColor))
                )
            }
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                factory = { ctx ->
                    LineChart(ctx).apply {
                        setBackgroundColor(AndroidColor.parseColor("#14141E"))
                        setDrawGridBackground(false)
                        description.isEnabled = false
                        legend.isEnabled = false
                        setTouchEnabled(true)
                        setHighlightPerDragEnabled(true)
                        isDragEnabled = false
                        setScaleEnabled(false)
                        xAxis.apply {
                            position = XAxis.XAxisPosition.BOTTOM
                            textColor = AndroidColor.parseColor("#555555")
                            gridColor = AndroidColor.parseColor("#222222")
                            axisLineColor = AndroidColor.parseColor("#333333")
                            textSize = 8f
                            valueFormatter = object : ValueFormatter() {
                                override fun getFormattedValue(value: Float): String {
                                    val sec = (-value).toInt()
                                    return if (sec == 0) "now" else "${sec}s"
                                }
                            }
                        }
                        axisLeft.apply {
                            textColor = AndroidColor.parseColor("#555555")
                            gridColor = AndroidColor.parseColor("#222222")
                            axisLineColor = AndroidColor.parseColor("#333333")
                            textSize = 8f
                        }
                        axisRight.isEnabled = false
                        setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                            override fun onValueSelected(e: Entry?, h: Highlight?) {
                                e?.let { selectedX.value = it.x }
                            }
                            override fun onNothingSelected() { selectedX.value = null }
                        })
                    }
                },
                update = { chart ->
                    val parsed = AndroidColor.parseColor(lineColor)
                    val dataSet = LineDataSet(entries, label).apply {
                        color = parsed
                        setDrawValues(false)
                        setDrawCircles(false)
                        lineWidth = 2f
                        mode = LineDataSet.Mode.CUBIC_BEZIER
                        cubicIntensity = 0.2f
                        setDrawFilled(true)
                        fillColor = parsed
                        fillAlpha = 25
                        highLightColor = AndroidColor.parseColor("#FFDD00")
                        highlightLineWidth = 1.5f
                        setDrawHorizontalHighlightIndicator(true)
                    }
                    chart.data = LineData(dataSet)
                    val hx = selectedX.value
                    if (hx != null) {
                        val hy = entries.minByOrNull { abs(it.x - hx) }?.y ?: latestVal
                        chart.highlightValue(Highlight(hx, hy, 0), false)
                    } else {
                        val lastEntry = entries.lastOrNull()
                        if (lastEntry != null) chart.highlightValue(Highlight(lastEntry.x, lastEntry.y, 0), false)
                    }
                    chart.invalidate()
                }
            )
        }
    }
}

@Composable
private fun DashboardDirectionCard(history: List<SensorData>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
            DirectionRoseChart(history = history, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun DashboardAllCard(history: List<SensorData>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(380.dp)) {
            AllChartsQuadrant(history = history, modifier = Modifier.fillMaxSize())
        }
    }
}
