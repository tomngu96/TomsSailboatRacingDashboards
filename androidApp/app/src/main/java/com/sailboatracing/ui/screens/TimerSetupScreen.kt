package com.sailboatracing.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sailboatracing.ui.theme.PrimaryColor
import com.sailboatracing.viewmodel.RaceViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerSetupScreen(viewModel: RaceViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val timerState = state.timerState

    var showTimePicker by remember { mutableStateOf(false) }
    var showManualEntry by remember { mutableStateOf(false) }

    // Live clock
    val currentTimeText by produceState(initialValue = "") {
        while (true) {
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            value = sdf.format(Date())
            delay(1000L)
        }
    }

    val remaining = timerState.remainingMs
    val minutes = remaining / 60000L
    val seconds = (remaining % 60000L) / 1000L
    val tenths = (remaining % 1000L) / 100L

    val urgencyColor = when {
        timerState.finished -> Color(0xFFFF4444)
        remaining <= 10_000L -> Color(0xFFFF4444)
        remaining <= 60_000L -> Color(0xFFFFAB40)
        else -> PrimaryColor
    }

    val canEditTime = !timerState.running && !timerState.finished

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F))
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Live clock
        Text(text = currentTimeText, fontSize = 16.sp, color = Color(0xFF666666))

        Spacer(modifier = Modifier.height(8.dp))

        // ── Section header ──
        Text(
            text = "MANUAL COUNTDOWN",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF888888),
            letterSpacing = 1.5.sp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Large countdown — fills most of the vertical space; tap to edit when idle
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val countdownText = when {
                timerState.finished -> "START!"
                !timerState.running && remaining == 0L -> "0:00"
                else -> "%d:%02d.%d".format(minutes, seconds, tenths)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (canEditTime) Modifier.clickable { showManualEntry = true } else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = countdownText,
                        fontSize = 80.sp,
                        fontWeight = FontWeight.Black,
                        color = urgencyColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (canEditTime) {
                        Text(
                            text = "tap to set time",
                            fontSize = 12.sp,
                            color = Color(0xFF555555)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (remaining == 0L && !timerState.running)
                    "Add time with +1 MIN or tap the display"
                else
                    "Target: %d min".format(timerState.targetMs / 60000L),
                fontSize = 13.sp,
                color = Color(0xFF666666),
                textAlign = TextAlign.Center
            )
        }

        // Control buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.addMinuteToTimer() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryColor)
            ) {
                Text(text = "+1 MIN", fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { viewModel.startTimer() },
                enabled = !timerState.running && !timerState.finished,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryColor,
                    contentColor = Color.Black
                )
            ) {
                Text(text = "START", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = { viewModel.clearTimer() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF4444))
            ) {
                Text(text = "CLEAR", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Narrate countdown toggle (duplicated from Settings for quick access)
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

        Spacer(modifier = Modifier.height(16.dp))

        // ── Divider above clock-based auto-countdown ──
        HorizontalDivider(color = Color(0xFF333333))

        Spacer(modifier = Modifier.height(16.dp))

        // Auto countdown by clock
        OutlinedButton(
            onClick = { showTimePicker = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFAB40))
        ) {
            Text(text = "AUTO COUNTDOWN BY CLOCK", fontWeight = FontWeight.Bold)
        }
    }

    // ── Manual time entry dialog ──
    if (showManualEntry) {
        var entryMinutes by remember { mutableStateOf("") }
        var entrySeconds by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showManualEntry = false },
            containerColor = Color(0xFF14141E),
            title = { Text("Set Countdown Time", color = Color.White) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Enter minutes and seconds",
                        fontSize = 13.sp,
                        color = Color(0xFF888888)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        OutlinedTextField(
                            value = entryMinutes,
                            onValueChange = { entryMinutes = it.filter { c -> c.isDigit() }.take(3) },
                            label = { Text("Min", color = Color(0xFF888888)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = PrimaryColor,
                                unfocusedBorderColor = Color(0xFF555555),
                                cursorColor = PrimaryColor
                            ),
                            modifier = Modifier.width(90.dp)
                        )
                        Text(
                            text = ":",
                            fontSize = 32.sp,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        OutlinedTextField(
                            value = entrySeconds,
                            onValueChange = {
                                val filtered = it.filter { c -> c.isDigit() }.take(2)
                                val v = filtered.toIntOrNull() ?: 0
                                entrySeconds = if (v > 59) "59" else filtered
                            },
                            label = { Text("Sec", color = Color(0xFF888888)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = PrimaryColor,
                                unfocusedBorderColor = Color(0xFF555555),
                                cursorColor = PrimaryColor
                            ),
                            modifier = Modifier.width(90.dp)
                        )
                    }
                }
            },
            confirmButton = {
                val totalMs = (entryMinutes.toLongOrNull() ?: 0L) * 60_000L +
                              (entrySeconds.toLongOrNull() ?: 0L) * 1_000L
                Button(
                    onClick = { viewModel.setTimerDuration(totalMs); showManualEntry = false },
                    enabled = totalMs > 0L,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryColor,
                        contentColor = Color.Black
                    )
                ) { Text("SET", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showManualEntry = false }) {
                    Text("CANCEL", color = Color(0xFF888888))
                }
            }
        )
    }

    // ── Wall-clock time picker dialog ──
    if (showTimePicker) {
        val now = Calendar.getInstance()
        val timePickerState = rememberTimePickerState(
            initialHour = now.get(Calendar.HOUR_OF_DAY),
            initialMinute = now.get(Calendar.MINUTE),
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = Color(0xFF14141E),
            title = { Text("Set Start Time (clock)", color = Color.White) },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showTimePicker = false
                        val selected = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            set(Calendar.MINUTE, timePickerState.minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        if (selected.timeInMillis <= System.currentTimeMillis()) {
                            selected.add(Calendar.DAY_OF_MONTH, 1)
                        }
                        viewModel.setTimerToTime(selected.timeInMillis)
                    }
                ) { Text("SET", color = PrimaryColor) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("CANCEL", color = Color(0xFF888888))
                }
            }
        )
    }
}
