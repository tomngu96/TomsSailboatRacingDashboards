package com.sailboatracing.ui.screens

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sailboatracing.model.DashboardChartType
import com.sailboatracing.model.NtripCaster
import com.sailboatracing.ui.theme.PrimaryColor
import com.sailboatracing.viewmodel.RaceViewModel

@Composable
fun SettingsScreen(viewModel: RaceViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showResetConfirm by remember { mutableStateOf(false) }

    // Local text field state for trail window (supports free entry, max 36000 s)
    var trailInput by remember { mutableStateOf(state.trailWindowSeconds.toString()) }

    // NTRIP caster dialog state
    var ntripEditCaster by remember { mutableStateOf<NtripCaster?>(null) }
    var ntripShowAddDialog by remember { mutableStateOf(false) }
    LaunchedEffect(state.trailWindowSeconds) { trailInput = state.trailWindowSeconds.toString() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Bluetooth ──────────────────────────────────────────────────
        SectionHeader("BLUETOOTH")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.connected) {
                    Button(
                        onClick = { viewModel.disconnect() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF440000),
                            contentColor = Color(0xFFFF4444)
                        )
                    ) {
                        Text(text = "DISCONNECT", fontWeight = FontWeight.Bold)
                    }
                } else {
                    if (state.pairedDevices.isEmpty()) {
                        Text(
                            text = "No paired Bluetooth devices found.\nPair your HC-05 in Android Settings first.",
                            color = Color(0xFF888888),
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )
                    } else {
                        Text(
                            text = "Tap a device to connect:",
                            color = Color(0xFF888888),
                            fontSize = 12.sp
                        )
                        state.pairedDevices.forEach { device ->
                            val deviceName = try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    device.name ?: device.address
                                } else {
                                    @Suppress("MissingPermission")
                                    device.name ?: device.address
                                }
                            } catch (e: SecurityException) {
                                device.address
                            }
                            OutlinedButton(
                                onClick = { viewModel.connect(device.address) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryColor)
                            ) {
                                Text(text = deviceName, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { viewModel.loadPairedDevices() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF888888))
                    ) {
                        Text(text = "REFRESH DEVICES")
                    }
                }
            }
        }

        HorizontalDivider(color = Color(0xFF222222))

        // ── NTRIP RTK Corrections ──────────────────────────────────────
        SectionHeader("NTRIP RTK CORRECTIONS")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Send RTCM corrections", color = Color.White, fontSize = 14.sp)
                        val statusText = when {
                            !state.ntripEnabled -> "Disabled"
                            state.ntripConnected -> "Connected — corrections flowing"
                            state.connected -> "Enabled — connecting…"
                            else -> "Enabled — waiting for Bluetooth"
                        }
                        val statusColor = when {
                            state.ntripConnected -> Color(0xFF4CAF50)
                            state.ntripEnabled -> Color(0xFFFFAB40)
                            else -> Color(0xFF888888)
                        }
                        Text(text = statusText, fontSize = 11.sp, color = statusColor)
                    }
                    Switch(
                        checked = state.ntripEnabled,
                        onCheckedChange = { viewModel.setNtripEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Color(0xFF4CAF50),
                            uncheckedThumbColor = Color(0xFF888888),
                            uncheckedTrackColor = Color(0xFF333333)
                        )
                    )
                }
                Text(
                    text = "Forwards RTCM3 correction data from a free NTRIP caster over Bluetooth " +
                        "to the ZED-F9P for centimeter-level RTK accuracy. Silently skipped if no internet " +
                        "is available — the GPS keeps running at standard accuracy.",
                    color = Color(0xFF666666),
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                HorizontalDivider(color = Color(0xFF2A2A2A))

                // Caster list
                state.ntripCasters.forEach { caster ->
                    val isSelected = caster.id == state.ntripSelectedCasterId
                    val borderColor = if (isSelected) Color(0xFF4CAF50) else Color(0xFF2A2A2A)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A28)),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, borderColor),
                        onClick = { viewModel.selectNtripCaster(caster.id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = caster.name,
                                    color = if (isSelected) Color(0xFF4CAF50) else Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${caster.host}:${caster.port}",
                                    color = Color(0xFF888888),
                                    fontSize = 11.sp
                                )
                                if (caster.mountpoint.isNotBlank()) {
                                    Text(
                                        text = "/${caster.mountpoint}",
                                        color = Color(0xFF666666),
                                        fontSize = 11.sp
                                    )
                                } else {
                                    Text(
                                        text = "No mountpoint set",
                                        color = Color(0xFF884444),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            TextButton(onClick = { ntripEditCaster = caster }) {
                                Text("EDIT", fontSize = 11.sp, color = Color(0xFF888888))
                            }
                            if (state.ntripCasters.size > 1) {
                                TextButton(onClick = { viewModel.removeNtripCaster(caster.id) }) {
                                    Text("✕", fontSize = 13.sp, color = Color(0xFF664444))
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = { ntripShowAddDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF4CAF50))
                ) {
                    Text("+ ADD CASTER")
                }

                Text(
                    text = "Pick the caster with best coverage near your race area. " +
                        "Mountpoint must be within ~30–50 km for good corrections. " +
                        "RTK2go requires registration (email = username). Centipede is open.",
                    color = Color(0xFF555555),
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }

        HorizontalDivider(color = Color(0xFF222222))

        // ── Charts History Window ──────────────────────────────────────
        SectionHeader("CHARTS HISTORY WINDOW")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = "Window", color = Color(0xFF888888), fontSize = 13.sp)
                SliderWithInput(
                    value = state.historyWindowSeconds,
                    onValueChange = { viewModel.setHistoryWindow(it) },
                    valueRange = 5f..600f,
                    steps = 118,
                    unit = "s",
                    accentColor = PrimaryColor
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "5s", color = Color(0xFF666666), fontSize = 11.sp)
                    Text(text = "10 min", color = Color(0xFF666666), fontSize = 11.sp)
                }
                Text(
                    text = "Controls how much data is shown on the Charts screen.",
                    color = Color(0xFF666666),
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }

        HorizontalDivider(color = Color(0xFF222222))

        // ── Headed / Lifted Detection ──────────────────────────────────
        SectionHeader("HEADED / LIFTED DETECTION")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "Short window (\"right now\")", color = Color(0xFF888888), fontSize = 13.sp)
                SliderWithInput(
                    value = state.headingShortWindowSec,
                    onValueChange = { viewModel.setHeadingShortWindow(it) },
                    valueRange = 1f..60f,
                    steps = 58,
                    unit = "s",
                    accentColor = PrimaryColor
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "1s", color = Color(0xFF666666), fontSize = 11.sp)
                    Text(text = "60s", color = Color(0xFF666666), fontSize = 11.sp)
                }

                HorizontalDivider(color = Color(0xFF2A2A2A), modifier = Modifier.padding(vertical = 6.dp))

                Text(text = "Long window (baseline)", color = Color(0xFF888888), fontSize = 13.sp)
                SliderWithInput(
                    value = state.headingLongWindowSec,
                    onValueChange = { viewModel.setHeadingLongWindow(it) },
                    valueRange = 5f..600f,
                    steps = 118,
                    unit = "s",
                    accentColor = PrimaryColor
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "5s", color = Color(0xFF666666), fontSize = 11.sp)
                    Text(text = "10 min", color = Color(0xFF666666), fontSize = 11.sp)
                }
                Text(
                    text = "Compares your heading over the short window against the long baseline. " +
                           "A new trend must hold for 3 s before being reported.",
                    color = Color(0xFF666666),
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }

        HorizontalDivider(color = Color(0xFF222222))

        // ── COG Sampling Window ────────────────────────────────────────
        SectionHeader("COG SAMPLING WINDOW")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "Averaging window", color = Color(0xFF888888), fontSize = 13.sp)
                SliderWithInput(
                    value = state.cogWindowSeconds,
                    onValueChange = { viewModel.setCogWindow(it) },
                    valueRange = 1f..60f,
                    steps = 58,
                    unit = "s",
                    accentColor = Color(0xFFFF4444)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "1s", color = Color(0xFF666666), fontSize = 11.sp)
                    Text(text = "60s", color = Color(0xFF666666), fontSize = 11.sp)
                }
                Text(
                    text = "Controls how many seconds of GPS COG readings are averaged and displayed " +
                           "alongside the IMU heading on the dashboard.",
                    color = Color(0xFF666666),
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }

        HorizontalDivider(color = Color(0xFF222222))

        // ── GPS Staleness ──────────────────────────────────────────────
        SectionHeader("GPS STALENESS THRESHOLD")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = "Stale after", color = Color(0xFF888888), fontSize = 13.sp)
                SliderWithInput(
                    value = state.gpsStaleThresholdSeconds,
                    onValueChange = { viewModel.setGpsStaleThreshold(it) },
                    valueRange = 2f..30f,
                    steps = 13,
                    unit = "s",
                    accentColor = Color(0xFFFFAB40)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "2s", color = Color(0xFF666666), fontSize = 11.sp)
                    Text(text = "30s", color = Color(0xFF666666), fontSize = 11.sp)
                }
                Text(
                    text = "GPS fix is considered valid this many seconds after the last received fix. " +
                           "Increase if your GPS updates slowly (e.g. 1 Hz phone GPS).",
                    color = Color(0xFF666666),
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }

        HorizontalDivider(color = Color(0xFF222222))

        // ── Phone GPS fallback ─────────────────────────────────────────
        SectionHeader("PHONE GPS FALLBACK")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Use phone GPS", color = Color.White, fontSize = 14.sp)
                        Text(
                            text = if (state.phoneGpsActive) "Active — phone GPS in use" else "Inactive",
                            fontSize = 11.sp,
                            color = if (state.phoneGpsActive) Color(0xFFFFAB40) else Color(0xFF888888)
                        )
                    }
                    Switch(
                        checked = state.usePhoneGps,
                        onCheckedChange = { viewModel.setUsePhoneGps(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Color(0xFFFFAB40),
                            uncheckedThumbColor = Color(0xFF888888),
                            uncheckedTrackColor = Color(0xFF333333)
                        )
                    )
                }
                Text(
                    text = "Phone GPS is used only when the hardware GPS has no fix. " +
                           "Expect ~1 Hz updates (vs 25 Hz from hardware). " +
                           "IMU heading data is still sourced from hardware when connected.",
                    color = Color(0xFF666666),
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }

        HorizontalDivider(color = Color(0xFF222222))

        // ── Phone IMU fallback ─────────────────────────────────────────
        SectionHeader("PHONE IMU FALLBACK")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Use phone IMU", color = Color.White, fontSize = 14.sp)
                        Text(
                            text = if (state.phoneImuActive) "Active — phone IMU in use" else "Inactive",
                            fontSize = 11.sp,
                            color = if (state.phoneImuActive) Color(0xFFFFAB40) else Color(0xFF888888)
                        )
                    }
                    Switch(
                        checked = state.usePhoneImu,
                        onCheckedChange = { viewModel.setUsePhoneImu(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Color(0xFFFFAB40),
                            uncheckedThumbColor = Color(0xFF888888),
                            uncheckedTrackColor = Color(0xFF333333)
                        )
                    )
                }
                Text(
                    text = "Phone IMU is used only when Bluetooth is disconnected. " +
                           "Heading accuracy depends on your phone's magnetometer calibration.",
                    color = Color(0xFF666666),
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }

        HorizontalDivider(color = Color(0xFF222222))

        // ── Map Trail ──────────────────────────────────────────────────
        SectionHeader("MAP TRAIL")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Trail length (seconds)", color = Color(0xFF888888), fontSize = 13.sp)
                OutlinedTextField(
                    value = trailInput,
                    onValueChange = { input ->
                        trailInput = input
                        val v = input.toIntOrNull()
                        if (v != null && v in 1..36000) viewModel.setTrailWindow(v)
                    },
                    label = { Text("1 – 36000 s", color = Color(0xFF666666), fontSize = 12.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = PrimaryColor,
                        unfocusedBorderColor = Color(0xFF555555),
                        focusedLabelColor = PrimaryColor,
                        cursorColor = PrimaryColor
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Length of the path trail drawn behind the boat on the map. " +
                           "Max 10 hours (36000 s).",
                    color = Color(0xFF666666),
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }

        HorizontalDivider(color = Color(0xFF222222))

        // ── Timer ──────────────────────────────────────────────────────
        SectionHeader("TIMER")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Narrate countdown", color = Color.White, fontSize = 14.sp)
                    Switch(
                        checked = state.narrateTimer,
                        onCheckedChange = { viewModel.setNarrateTimer(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = PrimaryColor,
                            uncheckedThumbColor = Color(0xFF888888),
                            uncheckedTrackColor = Color(0xFF333333)
                        )
                    )
                }
                Text(
                    text = "Speaks aloud every minute (>5 min), every 30 seconds (1–5 min), and every 5 seconds (<1 min). Announces \"Go\" at zero.",
                    color = Color(0xFF666666),
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }

        HorizontalDivider(color = Color(0xFF222222))

        // ── Map ────────────────────────────────────────────────────────
        SectionHeader("MAP")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Show map on dashboard", color = Color.White, fontSize = 14.sp)
                Switch(
                    checked = state.showMap,
                    onCheckedChange = { viewModel.setShowMap(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = PrimaryColor,
                        uncheckedThumbColor = Color(0xFF888888),
                        uncheckedTrackColor = Color(0xFF333333)
                    )
                )
            }
        }

        HorizontalDivider(color = Color(0xFF222222))

        // ── Dashboard Charts ───────────────────────────────────────────
        SectionHeader("DASHBOARD CHARTS")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Charts enabled here are stacked below the map on the dashboard.",
                    color = Color(0xFF666666),
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                listOf(
                    DashboardChartType.SPEED     to "Speed",
                    DashboardChartType.HEADING   to "Heading",
                    DashboardChartType.VMG       to "VMG",
                    DashboardChartType.DIRECTION to "Direction (Rose)",
                    DashboardChartType.ALL       to "All Charts"
                ).forEach { (type, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = label, color = Color.White, fontSize = 14.sp)
                        Switch(
                            checked = type in state.dashboardCharts,
                            onCheckedChange = { viewModel.toggleDashboardChart(type) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = PrimaryColor,
                                uncheckedThumbColor = Color(0xFF888888),
                                uncheckedTrackColor = Color(0xFF333333)
                            )
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = Color(0xFF222222))

        // ── Reset ──────────────────────────────────────────────────────
        OutlinedButton(
            onClick = { showResetConfirm = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF4444))
        ) {
            Text(text = "RESET SETTINGS TO DEFAULTS", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(8.dp))
    }

    // ── NTRIP add dialog ──────────────────────────────────────────────
    if (ntripShowAddDialog) {
        NtripCasterDialog(
            title = "Add Caster",
            initial = NtripCaster(id = -1, name = "", host = "", port = 2101),
            onConfirm = { c ->
                viewModel.addNtripCaster(c.name, c.host, c.port, c.mountpoint, c.username, c.password)
                ntripShowAddDialog = false
            },
            onDismiss = { ntripShowAddDialog = false }
        )
    }

    // ── NTRIP edit dialog ─────────────────────────────────────────────
    ntripEditCaster?.let { editing ->
        NtripCasterDialog(
            title = "Edit Caster",
            initial = editing,
            onConfirm = { c ->
                viewModel.updateNtripCaster(c)
                ntripEditCaster = null
            },
            onDismiss = { ntripEditCaster = null }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            containerColor = Color(0xFF14141E),
            title = { Text("Reset Settings?", color = Color.White) },
            text = {
                Text(
                    text = "All settings will be reset to their default values. Marks and the start line are not affected.",
                    color = Color(0xFF888888),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.resetSettings(); showResetConfirm = false },
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
}

// ── Shared composables ─────────────────────────────────────────────────

@Composable
private fun SliderWithInput(
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    unit: String = "",
    accentColor: Color = PrimaryColor
) {
    var textValue by remember { mutableStateOf(value.toString()) }
    LaunchedEffect(value) {
        if (textValue.toIntOrNull() != value) textValue = value.toString()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Slider(
            value = value.toFloat(),
            onValueChange = { v ->
                val clamped = v.toInt().coerceIn(valueRange.start.toInt(), valueRange.endInclusive.toInt())
                onValueChange(clamped)
                textValue = clamped.toString()
            },
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = Color(0xFF333333)
            ),
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = textValue,
            onValueChange = { input ->
                textValue = input
                val v = input.toIntOrNull()
                if (v != null) {
                    val clamped = v.coerceIn(valueRange.start.toInt(), valueRange.endInclusive.toInt())
                    onValueChange(clamped)
                }
            },
            singleLine = true,
            suffix = {
                if (unit.isNotEmpty()) Text(unit, color = Color(0xFF888888), fontSize = 12.sp)
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = accentColor,
                unfocusedBorderColor = Color(0xFF555555),
                cursorColor = accentColor
            ),
            modifier = Modifier.width(80.dp)
        )
    }
}

@Composable
private fun NtripCasterDialog(
    title: String,
    initial: NtripCaster,
    onConfirm: (NtripCaster) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var host by remember { mutableStateOf(initial.host) }
    var port by remember { mutableStateOf(initial.port.toString()) }
    var mountpoint by remember { mutableStateOf(initial.mountpoint) }
    var username by remember { mutableStateOf(initial.username) }
    var password by remember { mutableStateOf(initial.password) }
    var passwordVisible by remember { mutableStateOf(false) }

    val ntripGreen = Color(0xFF4CAF50)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
        focusedBorderColor = ntripGreen, unfocusedBorderColor = Color(0xFF555555),
        focusedLabelColor = ntripGreen, cursorColor = ntripGreen
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF14141E),
        title = { Text(title, color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Name", color = Color(0xFF666666), fontSize = 12.sp) },
                    singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = host, onValueChange = { host = it },
                    label = { Text("Host", color = Color(0xFF666666), fontSize = 12.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = fieldColors, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = port, onValueChange = { port = it },
                    label = { Text("Port", color = Color(0xFF666666), fontSize = 12.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = fieldColors, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = mountpoint, onValueChange = { mountpoint = it },
                    label = { Text("Mountpoint", color = Color(0xFF666666), fontSize = 12.sp) },
                    singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = username, onValueChange = { username = it },
                    label = { Text("Username", color = Color(0xFF666666), fontSize = 12.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = fieldColors, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Password", color = Color(0xFF666666), fontSize = 12.sp) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { passwordVisible = !passwordVisible }) {
                            Text(if (passwordVisible) "HIDE" else "SHOW", fontSize = 11.sp, color = Color(0xFF888888))
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = fieldColors, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(initial.copy(
                        name = name.trim(),
                        host = host.trim(),
                        port = port.toIntOrNull() ?: 2101,
                        mountpoint = mountpoint.trim(),
                        username = username.trim(),
                        password = password
                    ))
                },
                enabled = name.isNotBlank() && host.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = ntripGreen, contentColor = Color.Black)
            ) { Text("SAVE", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", color = Color(0xFF888888)) }
        }
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF888888),
        letterSpacing = 1.5.sp
    )
}
