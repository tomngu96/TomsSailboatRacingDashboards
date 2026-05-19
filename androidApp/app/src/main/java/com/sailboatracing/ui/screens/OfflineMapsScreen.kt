package com.sailboatracing.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sailboatracing.offline.SelectionBoxOverlay
import com.sailboatracing.offline.TileDownloader
import com.sailboatracing.ui.theme.PrimaryColor
import com.sailboatracing.viewmodel.RaceViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

@Composable
fun OfflineMapsScreen(viewModel: RaceViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
    val latestData = viewModel.state.collectAsStateWithLifecycle().value.latestData

    val startLat = latestData?.lat?.takeIf { it != 0.0 } ?: 47.6
    val startLon = latestData?.lon?.takeIf { it != 0.0 } ?: -122.3

    var north by remember { mutableDoubleStateOf(startLat + 0.03) }
    var south by remember { mutableDoubleStateOf(startLat - 0.03) }
    var west  by remember { mutableDoubleStateOf(startLon - 0.05) }
    var east  by remember { mutableDoubleStateOf(startLon + 0.05) }

    val tileCount = remember(north, south, west, east) {
        TileDownloader.tileCount(north, south, west, east)
    }
    val estimatedMb = remember(tileCount) {
        TileDownloader.estimatedBytes(north, south, west, east) / 1_000_000.0
    }

    var mapView by remember { mutableStateOf<MapView?>(null) }
    var overlay by remember { mutableStateOf<SelectionBoxOverlay?>(null) }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE  -> mapView?.onPause()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // Keep overlay in sync if the state is updated externally (e.g. re-entry)
    LaunchedEffect(overlay) {
        // overlay drives state via callback
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0F))) {

        // Map
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                Configuration.getInstance().userAgentValue = ctx.packageName
                MapView(ctx).also { mv ->
                    mv.setTileSource(TileSourceFactory.MAPNIK)
                    mv.setMultiTouchControls(true)
                    mv.isHorizontalMapRepetitionEnabled = false
                    mv.isVerticalMapRepetitionEnabled = false
                    mv.controller.setZoom(13.0)
                    mv.controller.setCenter(GeoPoint(startLat, startLon))

                    val sel = SelectionBoxOverlay(north, south, west, east) { n, s, w, e ->
                        north = n; south = s; west = w; east = e
                    }
                    mv.overlays.add(sel)
                    overlay = sel
                    mapView = mv
                }
            }
        )

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xCC0A0A0F))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onBack,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                shape = RoundedCornerShape(6.dp)
            ) { Text("← Back", fontSize = 13.sp) }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Select Download Area",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        // Bottom panel
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xEE0A0A0F))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Zoom ${TileDownloader.MIN_ZOOM}–${TileDownloader.MAX_ZOOM}  ·  $tileCount tiles  ·  ~${"%.0f".format(estimatedMb)} MB",
                color = Color(0xFFAAAAAA),
                fontSize = 12.sp
            )

            val dl = downloadState
            if (dl != null && !dl.finished) {
                val progress = if (dl.total > 0) dl.downloaded.toFloat() / dl.total else 0f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = PrimaryColor,
                    trackColor = Color(0xFF333333)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${dl.downloaded} / ${dl.total} tiles",
                        color = Color(0xFF888888),
                        fontSize = 11.sp
                    )
                    OutlinedButton(
                        onClick = { viewModel.cancelDownload() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF6666)),
                        shape = RoundedCornerShape(6.dp)
                    ) { Text("Cancel", fontSize = 12.sp) }
                }
            } else {
                if (dl?.finished == true) {
                    Text(
                        text = "Download complete — ${dl.downloaded} tiles saved.",
                        color = Color(0xFF00FF88),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Button(
                    onClick = { viewModel.startDownload(context, north, south, west, east) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "Download  (~${"%.0f".format(estimatedMb)} MB)",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
