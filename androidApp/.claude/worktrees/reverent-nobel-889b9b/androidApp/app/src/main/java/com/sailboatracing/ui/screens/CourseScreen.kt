package com.sailboatracing.ui.screens

import android.view.MotionEvent
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
import com.sailboatracing.model.StartLine
import com.sailboatracing.ui.theme.PrimaryColor
import com.sailboatracing.viewmodel.RaceViewModel
import kotlin.math.roundToInt
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay

@Composable
fun CourseScreen(viewModel: RaceViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("START LINE", "MARKS")

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
    val lineConfirmed = line != null && pendingPin == null

    Column(
        modifier = Modifier
            .fillMaxSize()
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
            position = line?.pin,
            confirmed = lineConfirmed || pendingPin != null,
            hasRecentGpsFix = hasRecentGpsFix,
            currentGpsPosition = state.latestData?.let { d ->
                if (d.lat != 0.0 || d.lon != 0.0) LatLng(d.lat, d.lon) else null
            },
            onMark = { viewModel.markStartLinePin() },
            onMarkAt = { lat, lon -> viewModel.markStartLinePinAt(lat, lon) }
        )

        // BOAT end — disabled until pin is set
        StartLinePointRow(
            label = "BOAT END",
            position = if (lineConfirmed) line?.boat else null,
            confirmed = lineConfirmed,
            hasRecentGpsFix = hasRecentGpsFix,
            enabled = pendingPin != null || lineConfirmed,
            currentGpsPosition = state.latestData?.let { d ->
                if (d.lat != 0.0 || d.lon != 0.0) LatLng(d.lat, d.lon) else null
            },
            onMark = { viewModel.markStartLineBoat() },
            onMarkAt = { lat, lon -> viewModel.markStartLineBoatAt(lat, lon) }
        )

        if (pendingPin != null && !lineConfirmed) {
            Text(
                text = "Sail to the boat end of the line, then tap MARK BOAT END.",
                color = Color(0xFF888888),
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
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
                onClear = { viewModel.clearStartLine() },
                onEdit = onNavigateToStartLineTab
            )

            // Copy start line as a gate mark (e.g. for a finish or mid-course gate)
            val lineConfirmed = state.startLine != null && state.pendingStartPin == null
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
    onClear: () -> Unit = {},
    onEdit: () -> Unit = {}
) {
    val lineConfirmed = startLine != null && pendingStartPin == null

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
                            else -> "NOT SET"
                        },
                        fontSize = 10.sp,
                        color = when {
                            lineConfirmed -> Color(0xFF00FF88)
                            pendingStartPin != null -> Color(0xFFFFAB40)
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
            onDismiss = { showMapPickerA = false },
            onConfirm = { pos -> pickedPosition = pos; showMapPickerA = false }
        )
    }
    if (showMapPickerB) {
        MapPickerDialog(
            initialCenter = pickedPosition ?: currentGpsPosition,
            existingPosition = pickedGateEnd,
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
            onDismiss = { showMapPickerA = false },
            onConfirm = { pos -> pickedPosition = pos; showMapPickerA = false }
        )
    }
    if (showMapPickerB) {
        MapPickerDialog(
            initialCenter = pickedGateEnd ?: displayPosition,
            existingPosition = pickedGateEnd,
            onDismiss = { showMapPickerB = false },
            onConfirm = { pos -> pickedGateEnd = pos; showMapPickerB = false }
        )
    }
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

// ──────────────────────────── MAP PICKER DIALOG ──────────────────────

@Composable
private fun MapPickerDialog(
    initialCenter: LatLng?,
    existingPosition: LatLng?,
    onDismiss: () -> Unit,
    onConfirm: (LatLng) -> Unit
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val selectedGeoPoint = remember { mutableStateOf<GeoPoint?>(null) }

    Configuration.getInstance().userAgentValue = context.packageName

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
                val startGeo = GeoPoint(pos.latitude, pos.longitude)
                selectedGeoPoint.value = startGeo
                overlays.add(Marker(this).apply {
                    position = startGeo
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                })
            }

            overlays.add(object : Overlay() {
                override fun onSingleTapConfirmed(e: MotionEvent, mv: MapView): Boolean {
                    val proj = mv.projection
                    val gp = proj.fromPixels(e.x.toInt(), e.y.toInt())
                    val tapped = GeoPoint(gp.latitude, gp.longitude)
                    selectedGeoPoint.value = tapped
                    mv.overlays.removeAll { it is Marker }
                    mv.overlays.add(Marker(mv).apply {
                        position = tapped
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    })
                    mv.invalidate()
                    return true
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
                    update = { /* tap overlay handles state */ },
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
