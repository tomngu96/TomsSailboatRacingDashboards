package com.sailboatracing.ui.screens

import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sailboatracing.model.ReplayFrame
import com.sailboatracing.model.SessionMeta
import com.sailboatracing.viewmodel.RaceViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SessionsScreen(viewModel: RaceViewModel, onBack: () -> Unit) {
    val sessions      by viewModel.replaySessions.collectAsStateWithLifecycle()
    val frames        by viewModel.replayFrames.collectAsStateWithLifecycle()
    val replayIndex   by viewModel.replayIndex.collectAsStateWithLifecycle()
    val playing       by viewModel.replayPlaying.collectAsStateWithLifecycle()
    val speed         by viewModel.replaySpeed.collectAsStateWithLifecycle()
    val loading       by viewModel.replayLoading.collectAsStateWithLifecycle()

    var activeSession      by remember { mutableStateOf<SessionMeta?>(null) }
    var showDeleteConfirm  by remember { mutableStateOf<SessionMeta?>(null) }

    LaunchedEffect(Unit) { viewModel.loadSessionList() }

    val handleBack: () -> Unit = {
        if (activeSession != null) {
            viewModel.stopReplay()
            activeSession = null
        } else {
            onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F))
    ) {
        // ── Header bar ────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF14141E))
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = handleBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                text = if (activeSession != null) activeSession!!.date else "Session Recordings",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
        }

        if (activeSession == null) {
            // ── Session list ─────────────────────────────────────────────
            if (sessions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("No recordings yet", color = Color(0xFF666666), fontSize = 16.sp)
                        Text("Start a recording from the dashboard", color = Color(0xFF444444), fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sessions) { session ->
                        SessionCard(
                            session  = session,
                            onPlay   = { activeSession = session; viewModel.loadReplaySession(session) },
                            onDelete = { showDeleteConfirm = session }
                        )
                    }
                }
            }
        } else {
            // ── Replay viewer ─────────────────────────────────────────────
            if (loading || frames.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = Color(0xFF00FF88))
                        Text("Loading session…", color = Color(0xFF888888), fontSize = 14.sp)
                    }
                }
            } else {
                ReplayViewer(
                    frames            = frames,
                    replayIndex       = replayIndex,
                    playing           = playing,
                    speed             = speed,
                    onSeek            = { viewModel.setReplayIndex(it) },
                    onTogglePlayback  = { viewModel.toggleReplayPlayback() },
                    onSetSpeed        = { viewModel.setReplaySpeed(it) }
                )
            }
        }
    }

    // ── Delete confirm ────────────────────────────────────────────────────
    showDeleteConfirm?.let { session ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete Recording") },
            text  = { Text("Delete \"${session.date}\"?\nThis cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSession(session)
                    showDeleteConfirm = null
                }) { Text("Delete", color = Color(0xFFFF4444)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel") }
            }
        )
    }
}

// =============================================================================
// Session list card
// =============================================================================

@Composable
private fun SessionCard(
    session:  SessionMeta,
    onPlay:   () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
        shape    = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(session.date, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                val dur = if (session.durationMinutes >= 60)
                    "${session.durationMinutes / 60}h ${session.durationMinutes % 60}m"
                else "${session.durationMinutes} min"
                Text(
                    "$dur  •  ${"%,d".format(session.pointCount)} pts  •  ${session.fileSizeKb} KB",
                    color = Color(0xFF666666),
                    fontSize = 12.sp
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color(0xFF444444), modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onPlay) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = Color(0xFF00FF88), modifier = Modifier.size(28.dp))
            }
        }
    }
}

// =============================================================================
// Replay viewer — map + stats + scrubber + controls
// =============================================================================

@Composable
private fun ReplayViewer(
    frames:           List<ReplayFrame>,
    replayIndex:      Int,
    playing:          Boolean,
    speed:            Int,
    onSeek:           (Int) -> Unit,
    onTogglePlayback: () -> Unit,
    onSetSpeed:       (Int) -> Unit
) {
    val context   = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val frame     = frames.getOrNull(replayIndex) ?: return
    val firstMs   = frames.first().timestampMs
    val lastMs    = frames.last().timestampMs
    val durationMs = (lastMs - firstMs).coerceAtLeast(1L)
    val elapsedMs  = frame.timestampMs - firstMs

    // Pre-compute downsampled full trail — recreated only when frames change
    val fullTrailPoints = remember(frames) {
        val step = maxOf(1, frames.size / 600)
        frames.indices.filter { it % step == 0 }
            .map { frames[it] }
            .filter { it.lat != 0.0 || it.lon != 0.0 }
            .map { GeoPoint(it.lat, it.lon) }
    }

    // Marker bitmap — created once
    val markerDrawable = remember { boatBitmapDrawable(context, 0f) }

    val mapView = remember {
        MapView(context).apply {
            Configuration.getInstance().userAgentValue = context.packageName
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled   = false
        }
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

    // Auto-fit bounds once when frames first loads
    val autoFitted = remember(frames) { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Map ───────────────────────────────────────────────────────────
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                factory  = { mapView },
                modifier = Modifier.fillMaxSize(),
                update   = { mv ->
                    mv.overlays.clear()

                    // Full trail — dim grey
                    if (fullTrailPoints.size >= 2) {
                        Polyline(mv).apply {
                            setPoints(fullTrailPoints)
                            outlinePaint.color       = android.graphics.Color.argb(100, 120, 120, 120)
                            outlinePaint.strokeWidth = 3f
                            outlinePaint.style       = Paint.Style.STROKE
                            mv.overlays.add(this)
                        }
                    }

                    // Traveled portion — amber
                    val step = maxOf(1, replayIndex / 600)
                    val traveled = (0..replayIndex step step)
                        .map { frames[it] }
                        .filter { it.lat != 0.0 || it.lon != 0.0 }
                        .map { GeoPoint(it.lat, it.lon) }
                    if (traveled.size >= 2) {
                        Polyline(mv).apply {
                            setPoints(traveled)
                            outlinePaint.color       = android.graphics.Color.argb(220, 255, 140, 0)
                            outlinePaint.strokeWidth = 5f
                            outlinePaint.style       = Paint.Style.STROKE
                            outlinePaint.strokeCap   = Paint.Cap.ROUND
                            outlinePaint.strokeJoin  = Paint.Join.ROUND
                            mv.overlays.add(this)
                        }
                    }

                    // Boat marker at current position
                    if (frame.lat != 0.0 || frame.lon != 0.0) {
                        Marker(mv).apply {
                            position  = GeoPoint(frame.lat, frame.lon)
                            icon      = boatBitmapDrawable(context, frame.heading)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            mv.overlays.add(this)
                        }
                    }

                    // Auto-fit on first load
                    if (!autoFitted.value && fullTrailPoints.size >= 2) {
                        autoFitted.value = true
                        val box = BoundingBox.fromGeoPoints(fullTrailPoints)
                        mv.post { mv.zoomToBoundingBox(box, true, 80) }
                    }

                    mv.invalidate()
                }
            )
        }

        // ── Stats row ─────────────────────────────────────────────────────
        Surface(color = Color(0xFF14141E)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                StatChip("SOG",  "${"%.1f".format(frame.sogKts)} kts")
                StatChip("HDG",  "${"%.0f".format(frame.heading)}°")
                StatChip("HEEL", "${"%.1f".format(frame.roll)}°")
                val (rtkText, rtkColor) = when {
                    frame.rtkStatus == 2 -> "RTK FIXED" to Color(0xFF00CFFF)
                    frame.rtkStatus == 1 -> "RTK FLOAT" to Color(0xFF88AAFF)
                    frame.fixType >= 3   -> "GPS 3D"    to Color(0xFF00FF88)
                    frame.fixType >= 2   -> "GPS 2D"    to Color(0xFFFFAB40)
                    else                 -> "NO FIX"    to Color(0xFFFF4444)
                }
                Text(rtkText, color = rtkColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        HorizontalDivider(color = Color(0xFF222222))

        // Timestamp
        Surface(color = Color(0xFF14141E)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
            ) {
                Text(
                    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(frame.timestampMs)),
                    color    = Color(0xFF666666),
                    fontSize = 11.sp
                )
            }
        }

        // ── Scrubber ──────────────────────────────────────────────────────
        Surface(color = Color(0xFF0F0F18)) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
                Slider(
                    value        = replayIndex.toFloat(),
                    onValueChange = { onSeek(it.toInt()) },
                    valueRange   = 0f..(frames.size - 1).toFloat(),
                    colors       = SliderDefaults.colors(
                        thumbColor        = Color(0xFF00FF88),
                        activeTrackColor  = Color(0xFF00FF88),
                        inactiveTrackColor = Color(0xFF2A2A2A)
                    ),
                    modifier     = Modifier.fillMaxWidth()
                )
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatDuration(elapsedMs),  color = Color(0xFF888888), fontSize = 11.sp)
                    Text(formatDuration(durationMs), color = Color(0xFF555555), fontSize = 11.sp)
                }
            }
        }

        // ── Playback controls ─────────────────────────────────────────────
        Surface(color = Color(0xFF14141E)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Skip to start
                IconButton(onClick = { onSeek(0) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Start",
                        tint = Color(0xFF888888), modifier = Modifier.size(24.dp))
                }
                // Play / Pause
                IconButton(onClick = onTogglePlayback, modifier = Modifier.size(48.dp)) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        tint     = Color(0xFF00FF88),
                        modifier = Modifier.size(34.dp)
                    )
                }
                // Skip to end
                IconButton(onClick = { onSeek(frames.size - 1) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "End",
                        tint = Color(0xFF888888), modifier = Modifier.size(24.dp))
                }

                // Speed chips
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(1, 10, 30, 60).forEach { s ->
                        val selected = speed == s
                        OutlinedButton(
                            onClick          = { onSetSpeed(s) },
                            modifier         = Modifier.height(32.dp).width(42.dp),
                            contentPadding   = PaddingValues(0.dp),
                            shape            = RoundedCornerShape(4.dp),
                            border           = BorderStroke(1.dp, if (selected) Color(0xFF00FF88) else Color(0xFF333333)),
                            colors           = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selected) Color(0xFF00FF88) else Color.Transparent,
                                contentColor   = if (selected) Color.Black else Color(0xFF888888)
                            )
                        ) {
                            Text("${s}×", fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// Helpers
// =============================================================================

@Composable
private fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color(0xFF555555), fontSize = 9.sp)
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

private fun formatDuration(ms: Long): String {
    val s = ms / 1000L
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
