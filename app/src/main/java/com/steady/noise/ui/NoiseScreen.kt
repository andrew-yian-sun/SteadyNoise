package com.steady.noise.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.steady.noise.NoiseService
import com.steady.noise.NoiseType
import com.steady.noise.PlaybackState
import com.steady.noise.UserPrefs
import com.steady.noise.formatRemaining
import com.steady.noise.settingsDataStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.util.Calendar

private val presetMinutes = listOf(15, 30, 60, 90)

private enum class StopMode {
    DURATION,
    UNTIL,
}

@Composable
fun NoiseScreen() {
    val context = LocalContext.current
    val playback by PlaybackState.snapshot.collectAsStateWithLifecycle()
    var noiseType by remember { mutableStateOf(NoiseType.WHITE) }
    var stopMode by remember { mutableStateOf(StopMode.DURATION) }
    var minutesText by remember { mutableStateOf(NoiseService.DEFAULT_MINUTES.toString()) }
    var untilHour by remember { mutableStateOf(7) }
    var untilMinute by remember { mutableStateOf(0) }
    var showTimePicker by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val is24Hour = remember { DateFormat.is24HourFormat(context) }

    LaunchedEffect(Unit) {
        val prefs = context.settingsDataStore.data.first()
        noiseType = prefs[UserPrefs.noiseType]
            ?.let { runCatching { NoiseType.valueOf(it) }.getOrNull() }
            ?: NoiseType.WHITE
        stopMode = prefs[UserPrefs.stopMode]
            ?.let { runCatching { StopMode.valueOf(it) }.getOrNull() }
            ?: StopMode.DURATION
        minutesText = (prefs[UserPrefs.durationMinutes] ?: NoiseService.DEFAULT_MINUTES).toString()
        untilHour = (prefs[UserPrefs.untilHour] ?: 7).coerceIn(0, 23)
        untilMinute = (prefs[UserPrefs.untilMinute] ?: 0).coerceIn(0, 59)
        loaded = true
    }

    LaunchedEffect(noiseType, stopMode, minutesText, untilHour, untilMinute, loaded) {
        if (!loaded) return@LaunchedEffect
        val minutes = minutesText.toIntOrNull()
        context.settingsDataStore.edit { prefs ->
            prefs[UserPrefs.noiseType] = noiseType.name
            prefs[UserPrefs.stopMode] = stopMode.name
            prefs[UserPrefs.untilHour] = untilHour
            prefs[UserPrefs.untilMinute] = untilMinute
            if (minutes != null && minutes in 1..NoiseService.MAX_MINUTES) {
                prefs[UserPrefs.durationMinutes] = minutes
            }
        }
    }

    LaunchedEffect(playback.playing, playback.endElapsedRealtime) {
        while (playback.playing) {
            now = SystemClock.elapsedRealtime()
            delay(250)
        }
    }

    val minutes = minutesText.toIntOrNull()
    val minutesValid = minutes != null && minutes in 1..NoiseService.MAX_MINUTES
    val canPlay = when (stopMode) {
        StopMode.DURATION -> minutesValid
        StopMode.UNTIL -> true
    }

    fun startPlayback() {
        val end: Long
        val detail: String
        when (stopMode) {
            StopMode.DURATION -> {
                val duration = minutesText.toIntOrNull() ?: return
                if (duration !in 1..NoiseService.MAX_MINUTES) return
                end = SystemClock.elapsedRealtime() + duration * 60_000L
                detail = ""
            }
            StopMode.UNTIL -> {
                end = wallClockToElapsedRealtime(untilHour, untilMinute)
                detail = "Until " + formatClockTime(context, untilHour, untilMinute)
            }
        }
        val intent = Intent(context, NoiseService::class.java).apply {
            action = NoiseService.ACTION_START
            putExtra(NoiseService.EXTRA_TYPE, noiseType.name)
            putExtra(NoiseService.EXTRA_MINUTES, minutes ?: NoiseService.DEFAULT_MINUTES)
            putExtra(NoiseService.EXTRA_END_ELAPSED, end)
            putExtra(NoiseService.EXTRA_DETAIL, detail)
        }
        context.startForegroundService(intent)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { startPlayback() }

    fun onPlay() {
        val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        if (needsNotificationPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startPlayback()
        }
    }

    if (showTimePicker) {
        UntilTimeDialog(
            initialHour = untilHour,
            initialMinute = untilMinute,
            is24Hour = is24Hour,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                untilHour = hour
                untilMinute = minute
                showTimePicker = false
            },
        )
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "SteadyNoise",
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = "Plays for a set length or until a clock time, including while the screen is off.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                NoiseType.entries.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = noiseType == type,
                        onClick = { noiseType = type },
                        shape = SegmentedButtonDefaults.itemShape(index, NoiseType.entries.size),
                        enabled = !playback.playing,
                    ) {
                        Text(if (type == NoiseType.WHITE) "White" else "Brown")
                    }
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                StopMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = stopMode == mode,
                        onClick = { stopMode = mode },
                        shape = SegmentedButtonDefaults.itemShape(index, StopMode.entries.size),
                        enabled = !playback.playing,
                    ) {
                        Text(if (mode == StopMode.DURATION) "Duration" else "Until")
                    }
                }
            }

            when (stopMode) {
                StopMode.DURATION -> DurationPicker(
                    minutes = minutes,
                    minutesText = minutesText,
                    enabled = !playback.playing,
                    onMinutesText = { minutesText = it },
                )
                StopMode.UNTIL -> UntilPicker(
                    timeLabel = formatClockTime(context, untilHour, untilMinute),
                    enabled = !playback.playing,
                    onPickTime = { showTimePicker = true },
                )
            }

            if (playback.playing) {
                val remaining = (playback.endElapsedRealtime - now).coerceAtLeast(0)
                Text(
                    text = formatRemaining(remaining),
                    style = MaterialTheme.typography.displayMedium,
                )
                Text(
                    text = if (playback.noiseType == NoiseType.WHITE) "White noise" else "Brown noise",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (playback.detail.isNotEmpty()) {
                    Text(
                        text = playback.detail,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            Spacer(Modifier.weight(1f))
            Button(
                onClick = { if (playback.playing) stopPlayback(context) else onPlay() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = playback.playing || canPlay,
            ) {
                Text(if (playback.playing) "Stop" else "Play")
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun DurationPicker(
    minutes: Int?,
    minutesText: String,
    enabled: Boolean,
    onMinutesText: (String) -> Unit,
) {
    Text(text = "Duration", style = MaterialTheme.typography.titleMedium)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        presetMinutes.forEach { preset ->
            FilterChip(
                selected = minutes == preset,
                onClick = { onMinutesText(preset.toString()) },
                enabled = enabled,
                label = { Text("$preset") },
            )
        }
    }
    OutlinedTextField(
        value = minutesText,
        onValueChange = { updated ->
            if (updated.length <= 3 && updated.all { it.isDigit() }) {
                onMinutesText(updated)
            }
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text("Minutes") },
        supportingText = { Text("1 to ${NoiseService.MAX_MINUTES} minutes") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
private fun UntilPicker(
    timeLabel: String,
    enabled: Boolean,
    onPickTime: () -> Unit,
) {
    Text(text = "Stop at", style = MaterialTheme.typography.titleMedium)
    OutlinedButton(
        onClick = onPickTime,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Text(timeLabel, style = MaterialTheme.typography.titleLarge)
    }
    Text(
        text = "If that time has already passed today, playback stops at the same time tomorrow.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UntilTimeDialog(
    initialHour: Int,
    initialMinute: Int,
    is24Hour: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = is24Hour,
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(360.dp)
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Stop at",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
                TimePicker(state = state)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("OK") }
                }
            }
        }
    }
}

private fun stopPlayback(context: Context) {
    val intent = Intent(context, NoiseService::class.java).apply {
        action = NoiseService.ACTION_STOP
    }
    context.startService(intent)
}

private fun wallClockToElapsedRealtime(hour: Int, minute: Int): Long {
    val now = Calendar.getInstance()
    val target = (now.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (!target.after(now)) {
        target.add(Calendar.DATE, 1)
    }
    return SystemClock.elapsedRealtime() + (target.timeInMillis - now.timeInMillis)
}

private fun formatClockTime(context: Context, hour: Int, minute: Int): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }
    return DateFormat.getTimeFormat(context).format(calendar.time)
}
