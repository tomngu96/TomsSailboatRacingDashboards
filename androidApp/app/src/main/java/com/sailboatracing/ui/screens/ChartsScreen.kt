package com.sailboatracing.ui.screens

import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.sailboatracing.model.SensorData
import com.sailboatracing.viewmodel.RaceViewModel
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private enum class ChartType(val label: String) {
    SPEED("Speed"),
    HEADING("Heading"),
    VMG("VMG"),
    ROSE("Direction"),
    ALL("All")
}

@Composable
fun ChartsScreen(viewModel: RaceViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedChart by remember { mutableStateOf(ChartType.SPEED) }
    val history = state.history

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChartType.entries.forEach { chartType ->
                    FilterChip(
                        selected = selectedChart == chartType,
                        onClick = { selectedChart = chartType },
                        label = { Text(chartType.label, fontSize = 13.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF003344),
                            selectedLabelColor = Color(0xFF00C8FF),
                            labelColor = Color(0xFF888888),
                            containerColor = Color(0xFF14141E)
                        )
                    )
                }
            }
            // Scroll hint — only visible when "All" is not selected, so user knows to swipe right
            if (selectedChart != ChartType.ALL) {
                Text(
                    text = "›",
                    fontSize = 22.sp,
                    color = Color(0xFF444444),
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        if (history.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "No data yet — connect to device", color = Color(0xFF888888), fontSize = 14.sp)
            }
        } else {
            when (selectedChart) {
                ChartType.ROSE -> {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        DirectionRoseChart(history = history)
                    }
                }
                ChartType.ALL -> {
                    AllChartsQuadrant(history = history, modifier = Modifier.fillMaxWidth().weight(1f))
                }
                else -> {
                    LineChartView(
                        history = history,
                        chartType = selectedChart,
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared line chart (Speed / Heading / VMG)
// ---------------------------------------------------------------------------

@Composable
private fun LineChartView(
    history: List<SensorData>,
    chartType: ChartType,
    modifier: Modifier = Modifier
) {
    val touchedX = remember { mutableStateOf<Float?>(null) }
    val touchedY = remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(chartType) { touchedX.value = null; touchedY.value = null }

    val endTs = history.last().timestampMs
    val entries: List<Entry> = history.map { data ->
        val xSec = (data.timestampMs - endTs) / 1000f
        val y = when (chartType) {
            ChartType.SPEED   -> data.sogKts
            ChartType.HEADING -> data.heading
            ChartType.VMG     -> data.sogKts
            else              -> data.sogKts
        }
        Entry(xSec, y)
    }

    val latestY = entries.last().y
    val shownValue = touchedX.value?.let { tx ->
        entries.minByOrNull { abs(it.x - tx) }?.y
    } ?: latestY
    val valueText = when (chartType) {
        ChartType.HEADING -> "%.1f°".format(shownValue)
        else              -> "%.1f kts".format(shownValue)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = valueText,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFDD00),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        val lineLabel = when (chartType) {
            ChartType.SPEED   -> "Speed (kts)"
            ChartType.HEADING -> "Heading (°)"
            ChartType.VMG     -> "VMG (kts)"
            else              -> ""
        }

        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { ctx ->
                LineChart(ctx).apply {
                    setBackgroundColor(AndroidColor.parseColor("#0A0A0F"))
                    setDrawGridBackground(false)
                    description.isEnabled = false
                    legend.isEnabled = false
                    setTouchEnabled(true)
                    isDragEnabled = true
                    setScaleEnabled(true)
                    setPinchZoom(true)
                    setNoDataText("No data")
                    setNoDataTextColor(AndroidColor.parseColor("#888888"))
                    xAxis.apply {
                        position = XAxis.XAxisPosition.BOTTOM
                        textColor = AndroidColor.parseColor("#888888")
                        gridColor = AndroidColor.parseColor("#222222")
                        axisLineColor = AndroidColor.parseColor("#444444")
                        labelRotationAngle = -45f
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                val sec = (-value).toInt()
                                return if (sec == 0) "now" else "${sec}s"
                            }
                        }
                    }
                    axisLeft.apply {
                        textColor = AndroidColor.parseColor("#888888")
                        gridColor = AndroidColor.parseColor("#222222")
                        axisLineColor = AndroidColor.parseColor("#444444")
                    }
                    axisRight.isEnabled = false
                    setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                        override fun onValueSelected(e: Entry?, h: Highlight?) {
                            e?.let { touchedX.value = it.x; touchedY.value = it.y }
                        }
                        override fun onNothingSelected() { touchedX.value = null; touchedY.value = null }
                    })
                }
            },
            update = { chart ->
                val dataSet = LineDataSet(entries, lineLabel).apply {
                    color = AndroidColor.parseColor("#00C8FF")
                    setDrawValues(false)
                    setDrawCircles(false)
                    lineWidth = 2f
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    cubicIntensity = 0.2f
                    setDrawFilled(true)
                    fillColor = AndroidColor.parseColor("#00C8FF")
                    fillAlpha = 30
                    highLightColor = AndroidColor.argb(160, 255, 220, 0)
                    highlightLineWidth = 1.5f
                    setDrawHorizontalHighlightIndicator(true)
                    setDrawVerticalHighlightIndicator(true)
                }
                chart.data = LineData(dataSet)
                val hx = touchedX.value
                if (hx != null) {
                    val liveY = entries.minByOrNull { abs(it.x - hx) }?.y ?: entries.last().y
                    chart.highlightValue(Highlight(hx, liveY, 0), false)
                } else if (entries.isNotEmpty()) {
                    chart.highlightValue(Highlight(0f, entries.last().y, 0), false)
                }
                chart.invalidate()
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Direction Rose — Compose Canvas drawing
// ---------------------------------------------------------------------------

@Composable
fun DirectionRoseChart(
    history: List<SensorData>,
    modifier: Modifier = Modifier.fillMaxSize(),
    compact: Boolean = false
) {
    val cogSamples = remember(history) { history.filter { it.fixType >= 2 && it.sogKts >= 0.3f } }
    val latestHdg = history.lastOrNull()?.heading
    val latestCog = cogSamples.lastOrNull()?.cogDeg
    val currentSog = history.lastOrNull()?.sogKts ?: 0f

    // Adaptive scale: grows with session max SOG, never shrinks below 3 kt, always whole-number rings
    val maxSogKts = remember(history) { history.maxOfOrNull { it.sogKts } ?: 0f }
    val (scaleKts, ringStepKts) = when {
        maxSogKts <= 3f  -> 3f  to 1f
        maxSogKts <= 6f  -> 6f  to 2f
        maxSogKts <= 9f  -> 9f  to 3f
        maxSogKts <= 12f -> 12f to 4f
        maxSogKts <= 15f -> 15f to 5f
        maxSogKts <= 20f -> 20f to 5f
        maxSogKts <= 30f -> 30f to 10f
        else             -> (maxSogKts * 1.1f) to (maxSogKts / 3f)
    }
    val speedFraction = (currentSog / scaleKts).coerceIn(0f, 1f)

    Column(modifier = modifier) {
        // Full readout row — hidden in compact mode (quadrant)
        if (!compact) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("HDG", fontSize = 10.sp, color = Color(0xFF00FF88))
                    Text(
                        text = if (latestHdg != null) "%05.1f°".format(latestHdg) else "---.-",
                        fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00FF88)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("SOG", fontSize = 10.sp, color = Color(0xFFFFAB40))
                    Text(
                        text = "%.1f kts".format(currentSog),
                        fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFAB40)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("COG", fontSize = 10.sp, color = Color(0xFFFF4444))
                    Text(
                        text = if (latestCog != null) "%05.1f°".format(latestCog) else "---.-",
                        fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        color = if (latestCog != null) Color(0xFFFF4444) else Color(0xFF555555)
                    )
                }
            }
        }

        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = min(cx * 0.82f, cy * 0.85f)
            val arrowMaxR = radius * 0.92f
            val aw = radius * 0.09f

            // Label text size scales with canvas width so it stays readable in any container
            val labelSizePx = size.width * 0.052f
            val labelPaint = AndroidPaint().apply {
                color = AndroidColor.argb(150, 255, 255, 255)
                textSize = labelSizePx
                isAntiAlias = true
            }

            // Speed rings — one per step up to scale, labeled at 3-o'clock
            var ringSpeedKts = ringStepKts
            while (ringSpeedKts <= scaleKts + 0.001f) {
                val ringR = (ringSpeedKts / scaleKts) * radius
                val isOuter = ringSpeedKts >= scaleKts - 0.001f
                drawCircle(
                    color = Color(if (isOuter) 0x55FFFFFF else 0x1AFFFFFF),
                    radius = ringR,
                    center = androidx.compose.ui.geometry.Offset(cx, cy),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = if (isOuter) 1.5f else 1f)
                )
                drawContext.canvas.nativeCanvas.drawText(
                    "%.0f kt".format(ringSpeedKts),
                    cx + ringR + 3f,
                    cy - labelSizePx * 0.3f,
                    labelPaint
                )
                ringSpeedKts += ringStepKts
            }

            // Tick marks every 10°, longer at cardinals
            for (i in 0 until 36) {
                val angleRad = Math.toRadians((i * 10.0 - 90.0))
                val isCardinal = i % 9 == 0
                val inner = if (isCardinal) radius * 0.87f else radius * 0.94f
                drawLine(
                    color = Color(if (isCardinal) 0x66FFFFFF else 0x33FFFFFF),
                    start = androidx.compose.ui.geometry.Offset(
                        cx + inner * cos(angleRad).toFloat(),
                        cy + inner * sin(angleRad).toFloat()
                    ),
                    end = androidx.compose.ui.geometry.Offset(
                        cx + radius * cos(angleRad).toFloat(),
                        cy + radius * sin(angleRad).toFloat()
                    ),
                    strokeWidth = if (isCardinal) 2f else 1f
                )
            }

            val n = history.size
            if (n == 0) return@Canvas

            // HDG direction trail — arc at fixed radius (heading is not speed-weighted)
            val hdgTrailR = radius * 0.88f
            val trailLen = min(n, 120)
            val trailStart = n - trailLen
            for (i in trailStart until n - 1) {
                val alpha = ((i - trailStart).toFloat() / trailLen).coerceIn(0f, 1f)
                val angleA = Math.toRadians((history[i].heading - 90.0))
                val angleB = Math.toRadians((history[i + 1].heading - 90.0))
                drawLine(
                    color = Color(red = 0f, green = 1f, blue = 0.533f, alpha = alpha * 0.6f),
                    start = androidx.compose.ui.geometry.Offset(
                        cx + hdgTrailR * cos(angleA).toFloat(), cy + hdgTrailR * sin(angleA).toFloat()
                    ),
                    end = androidx.compose.ui.geometry.Offset(
                        cx + hdgTrailR * cos(angleB).toFloat(), cy + hdgTrailR * sin(angleB).toFloat()
                    ),
                    strokeWidth = 2f
                )
            }

            // COG velocity trail — connects the tips of where the COG arrow has been.
            // Each point = (sogKts / scaleKts) * arrowMaxR in the cogDeg direction,
            // so the trace encodes both speed (distance from centre) and direction (angle).
            val cogLen = min(cogSamples.size, 120)
            val cogStart = cogSamples.size - cogLen
            for (i in cogStart until cogSamples.size - 1) {
                val alpha = ((i - cogStart).toFloat() / cogLen).coerceIn(0f, 1f)
                val fracA = (cogSamples[i].sogKts / scaleKts).coerceIn(0f, 1f)
                val fracB = (cogSamples[i + 1].sogKts / scaleKts).coerceIn(0f, 1f)
                val angleA = Math.toRadians((cogSamples[i].cogDeg - 90.0))
                val angleB = Math.toRadians((cogSamples[i + 1].cogDeg - 90.0))
                drawLine(
                    color = Color(red = 1f, green = 0.267f, blue = 0.267f, alpha = alpha * 0.7f),
                    start = androidx.compose.ui.geometry.Offset(
                        cx + fracA * arrowMaxR * cos(angleA).toFloat(),
                        cy + fracA * arrowMaxR * sin(angleA).toFloat()
                    ),
                    end = androidx.compose.ui.geometry.Offset(
                        cx + fracB * arrowMaxR * cos(angleB).toFloat(),
                        cy + fracB * arrowMaxR * sin(angleB).toFloat()
                    ),
                    strokeWidth = 2f
                )
            }

            fun drawArrow(angleDeg: Float, length: Float, color: Color) {
                if (length < 2f) return
                val rad = Math.toRadians((angleDeg - 90.0))
                val tipX = cx + length * cos(rad).toFloat()
                val tipY = cy + length * sin(rad).toFloat()
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(cx, cy),
                    end = androidx.compose.ui.geometry.Offset(tipX, tipY),
                    strokeWidth = 4f
                )
                for (side in listOf(Math.PI * 0.8, -Math.PI * 0.8)) {
                    val sideRad = rad + side
                    drawLine(
                        color = color,
                        start = androidx.compose.ui.geometry.Offset(tipX, tipY),
                        end = androidx.compose.ui.geometry.Offset(
                            tipX + aw * cos(sideRad).toFloat(),
                            tipY + aw * sin(sideRad).toFloat()
                        ),
                        strokeWidth = 3f
                    )
                }
            }

            // COG arrow: tip position matches the most recent velocity trail point
            cogSamples.lastOrNull()?.let { drawArrow(it.cogDeg, speedFraction * arrowMaxR, Color(0xFFFF4444)) }
            // HDG arrow: always full length — direction reference only
            drawArrow(history.last().heading, arrowMaxR, Color(0xFF00FF88))
        }

        // Legend — hidden in compact mode
        if (!compact) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("— HDG", fontSize = 11.sp, color = Color(0xFF00FF88), fontWeight = FontWeight.Bold)
                    Text("← COG (speed)", fontSize = 11.sp, color = Color(0xFFFF4444), fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "outer = %.0f kt".format(scaleKts),
                    fontSize = 10.sp,
                    color = Color(0xFF666666)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// All charts — 2×2 quadrant layout
// ---------------------------------------------------------------------------

@Composable
internal fun AllChartsQuadrant(history: List<SensorData>, modifier: Modifier = Modifier.fillMaxSize()) {
    val endTs = history.last().timestampMs
    val latest = history.lastOrNull()

    fun makeEntries(yFn: (SensorData) -> Float): List<Entry> =
        history.map { Entry((it.timestampMs - endTs) / 1000f, yFn(it)) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            QuadrantCell(
                label = "SPEED",
                value = if (latest != null) "%.1f kts".format(latest.sogKts) else null,
                valueColor = Color(0xFF00C8FF),
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                MiniLineChart(entries = makeEntries { it.sogKts }, color = "#00C8FF")
            }
            QuadrantCell(
                label = "HEADING",
                value = if (latest != null) "%.1f°".format(latest.heading) else null,
                valueColor = Color(0xFFAA88FF),
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                MiniLineChart(entries = makeEntries { it.heading }, color = "#AA88FF")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            QuadrantCell(
                label = "VMG",
                value = if (latest != null) "%.1f kts".format(latest.sogKts) else null,
                valueColor = Color(0xFF00FF88),
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                MiniLineChart(entries = makeEntries { it.sogKts }, color = "#00FF88")
            }
            QuadrantCell(
                label = "DIRECTION",
                value = null,
                valueColor = Color.Transparent,
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                DirectionRoseChart(history = history, compact = true)
            }
        }
    }
}

@Composable
internal fun QuadrantCell(
    label: String,
    value: String?,
    valueColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier.background(Color(0xFF0E0E18))) {
        // Header: label on left, current value on right — above the chart so no overlap
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 5.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF666666))
            if (value != null) {
                Text(text = value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = valueColor)
            }
        }
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            content()
        }
    }
}

@Composable
internal fun MiniLineChart(entries: List<Entry>, color: String) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            LineChart(ctx).apply {
                setBackgroundColor(AndroidColor.parseColor("#0E0E18"))
                setDrawGridBackground(false)
                description.isEnabled = false
                legend.isEnabled = false
                setTouchEnabled(false)
                isDragEnabled = false
                setScaleEnabled(false)
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    textColor = AndroidColor.parseColor("#555555")
                    gridColor = AndroidColor.parseColor("#1A1A1A")
                    axisLineColor = AndroidColor.parseColor("#333333")
                    textSize = 8f
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float) = "${(-value).toInt()}s"
                    }
                }
                axisLeft.apply {
                    textColor = AndroidColor.parseColor("#555555")
                    gridColor = AndroidColor.parseColor("#1A1A1A")
                    axisLineColor = AndroidColor.parseColor("#333333")
                    textSize = 7f
                    setLabelCount(3, true)
                    xOffset = 2f  // nudge labels right so they don't clip
                }
                axisRight.isEnabled = false
                // Extra left padding so Y labels don't sit on top of the chart line
                setExtraLeftOffset(8f)
            }
        },
        update = { chart ->
            val dataSet = LineDataSet(entries, "").apply {
                this.color = AndroidColor.parseColor(color)
                setDrawValues(false)
                setDrawCircles(false)
                lineWidth = 1.5f
                mode = LineDataSet.Mode.CUBIC_BEZIER
                cubicIntensity = 0.2f
                setDrawFilled(true)
                fillColor = AndroidColor.parseColor(color)
                fillAlpha = 20
                highLightColor = AndroidColor.parseColor("#FFDD00")
                highlightLineWidth = 1f
                setDrawHorizontalHighlightIndicator(true)
            }
            chart.data = LineData(dataSet)
            val lastEntry = entries.lastOrNull()
            if (lastEntry != null) {
                chart.highlightValue(Highlight(lastEntry.x, lastEntry.y, 0), false)
            }
            chart.invalidate()
        }
    )
}
