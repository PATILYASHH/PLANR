package com.exosynk.planr.alarm

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.exosynk.planr.PlanrApp
import com.exosynk.planr.ui.components.Chips
import com.exosynk.planr.ui.components.DayChips
import com.exosynk.planr.ui.components.EditorPage
import com.exosynk.planr.ui.components.Gap
import com.exosynk.planr.ui.components.SectionCard
import com.exosynk.planr.ui.components.SwitchRow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditor(modifier: Modifier, alarmId: Long, onDone: () -> Unit) {
    val app = PlanrApp.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(alarmId == 0L) }
    // A new alarm may come pre-filled from the smart bar ("wake me at 6:30 weekdays").
    var a by remember {
        mutableStateOf((if (alarmId == 0L) com.exosynk.planr.smart.Drafts.alarm else null) ?: Alarm(hour = 7, minute = 0))
    }
    LaunchedEffect(Unit) { com.exosynk.planr.smart.Drafts.alarm = null }
    val groups by app.db.alarmGroupDao().observeAll().collectAsState(initial = emptyList())

    LaunchedEffect(alarmId) {
        if (alarmId != 0L) {
            app.db.alarmDao().get(alarmId)?.let { a = it }
            loaded = true
        }
    }
    if (!loaded) return

    val timeState = rememberTimePickerState(a.hour, a.minute, DateFormat.is24HourFormat(context))
    val ringtoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) {
                res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            a = a.copy(sound = uri?.toString() ?: Alarm.SILENT)
        }
    }

    fun save() {
        val updated = a.copy(
            hour = timeState.hour,
            minute = timeState.minute,
            enabled = true,
            skippedAt = 0,
            // A date in the past (or a repeating alarm) has no use for a one-time date.
            date = if (a.isRepeating) null else a.date?.takeIf { d ->
                runCatching { LocalDate.parse(d).atTime(timeState.hour, timeState.minute).isAfter(LocalDateTime.now()) }
                    .getOrDefault(false)
            },
        )
        scope.launch {
            val id = Alarms.save(context.applicationContext, updated)
            val next = app.db.alarmDao().get(id)?.let { AlarmTime.next(it, LocalDateTime.now()) }
            if (next != null) toastOnMain(context, AlarmTime.untilText(LocalDateTime.now(), next))
            onDone()
        }
    }

    EditorPage(
        modifier = modifier,
        title = if (alarmId == 0L) "Add alarm" else "Edit alarm",
        onBack = onDone,
        onSave = { save() },
        actions = {
            if (alarmId != 0L) {
                IconButton(onClick = {
                    scope.launch { Alarms.delete(context.applicationContext, alarmId); onDone() }
                }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete alarm") }
            }
        },
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = timeState) }

        SectionCard {
            Text("Repeat", style = MaterialTheme.typography.titleMedium)
            Gap(4.dp)
            DayChips(a.daysMask) { a = a.copy(daysMask = it) }
            if (!a.isRepeating) {
                Gap(4.dp)
                val dateText = a.date?.let {
                    runCatching { LocalDate.parse(it).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }.getOrNull()
                } ?: "Next time it's ${"%d:%02d".format(timeState.hour, timeState.minute)}"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Rings once: $dateText", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = {
                        val d = a.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
                        DatePickerDialog(context, { _, y, m, day ->
                            a = a.copy(date = LocalDate.of(y, m + 1, day).toString())
                        }, d.year, d.monthValue - 1, d.dayOfMonth).apply {
                            datePicker.minDate = System.currentTimeMillis() - 1000
                        }.show()
                    }) { Text("Pick date") }
                }
            }
        }
        Gap(12.dp)

        OutlinedTextField(
            value = a.label,
            onValueChange = { a = a.copy(label = it.take(40)) },
            label = { Text("Alarm name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Gap(12.dp)

        if (groups.isNotEmpty()) {
            SectionCard {
                Chips(
                    "Alarm group",
                    listOf<Long?>(null) + groups.map { it.id },
                    a.groupId,
                    { id -> groups.firstOrNull { it.id == id }?.name ?: "None" },
                ) { a = a.copy(groupId = it) }
            }
            Gap(12.dp)
        }

        SectionCard {
            Text("Alarm sound", style = MaterialTheme.typography.titleMedium)
            val soundName = remember(a.sound) { soundTitle(context, a.sound) }
            Text(
                soundName,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().clickable {
                    val existing = when (a.sound) {
                        null -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        Alarm.SILENT -> null
                        else -> Uri.parse(a.sound)
                    }
                    ringtoneLauncher.launch(
                        Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm sound"),
                    )
                }.padding(vertical = 6.dp),
            )
            if (a.sound != Alarm.SILENT) {
                Text("Volume ${a.volume}%", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = a.volume.toFloat(),
                    onValueChange = { a = a.copy(volume = (it / 5f).roundToInt() * 5) },
                    valueRange = 5f..100f,
                )
                SwitchRow("Gradually increase volume", "Starts quiet and gets louder over 30 seconds", a.gradualVolume) {
                    a = a.copy(gradualVolume = it)
                }
            }
            SwitchRow("Read time and alarm name aloud", null, a.readAloud) { a = a.copy(readAloud = it) }
        }
        Gap(12.dp)

        SectionCard {
            SwitchRow("Vibration", null, a.vibrate) { a = a.copy(vibrate = it) }
            if (a.vibrate) {
                Chips(null, VibrationPattern.entries.toList(), a.pattern, { it.title }) {
                    a = a.copy(vibrationPattern = it.name)
                }
            }
        }
        Gap(12.dp)

        SectionCard {
            SwitchRow("Snooze", null, a.snoozeEnabled) { a = a.copy(snoozeEnabled = it) }
            if (a.snoozeEnabled) {
                Chips("Interval", listOf(5, 10, 15, 30), a.snoozeMinutes, { "$it min" }) { a = a.copy(snoozeMinutes = it) }
                Gap(4.dp)
                Chips("Repeat", listOf(1, 2, 3, 5, 0), a.snoozeRepeat, { if (it == 0) "Forever" else "${it}×" }) {
                    a = a.copy(snoozeRepeat = it)
                }
            }
        }
        Gap(12.dp)

        SectionCard {
            Chips("To dismiss", DismissChallenge.entries.toList(), a.challenge, { it.title }) {
                a = a.copy(dismissChallenge = it.name)
            }
            Gap(4.dp)
            SwitchRow(
                "Morning brief",
                "After dismissing: today's reminders, habit streak, next alarm and running routines",
                a.morningBrief,
            ) { a = a.copy(morningBrief = it) }
        }
        Gap(12.dp)
        Text(
            "Tip: create a routine with “When an alarm is dismissed” to turn off Do Not Disturb or start your day automatically.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Gap(32.dp)
    }
}

private fun soundTitle(context: android.content.Context, sound: String?): String = when (sound) {
    Alarm.SILENT -> "Silent (vibrate only)"
    null -> "Default alarm sound"
    else -> runCatching { RingtoneManager.getRingtone(context, Uri.parse(sound))?.getTitle(context) }.getOrNull()
        ?: "Custom sound"
}
