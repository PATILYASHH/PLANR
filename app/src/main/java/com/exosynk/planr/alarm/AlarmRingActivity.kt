package com.exosynk.planr.alarm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.exosynk.planr.PlanrApp
import com.exosynk.planr.reminder.Reminders
import com.exosynk.planr.today.TodayData
import com.exosynk.planr.today.TodaySummary
import com.exosynk.planr.ui.components.Gap
import com.exosynk.planr.ui.components.formatDuration
import com.exosynk.planr.ui.theme.Accent
import com.exosynk.planr.ui.theme.PlanrTheme
import com.exosynk.planr.ui.theme.Success
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.time.ZoneId
import java.util.Date
import kotlin.random.Random

/** Full-screen alarm / alarm-style reminder, shown over the lock screen; then the morning brief. */
class AlarmRingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            PlanrTheme {
                BackHandler { /* use Snooze or Dismiss */ }
                val ring by AlarmService.ringing.collectAsState()
                val brief by AlarmService.briefFor.collectAsState()
                val r = ring
                when {
                    r != null -> RingScreen(
                        ring = r,
                        onSnooze = { AlarmService.send(this, AlarmService.ACTION_SNOOZE) },
                        onDismiss = { AlarmService.send(this, AlarmService.ACTION_DISMISS) },
                    )
                    brief != null -> MorningBrief(onDone = {
                        AlarmService.clearBrief()
                        finish()
                    })
                    else -> LaunchedEffect(Unit) { finish() }
                }
            }
        }
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, AlarmRingActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}

@Composable
private fun RingScreen(ring: AlarmService.Ring, onSnooze: () -> Unit, onDismiss: () -> Unit) {
    var solving by remember(ring.id) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().padding(28.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            if (ring.kind == AlarmService.Kind.ALARM) Icons.Outlined.Alarm else Icons.Outlined.Notifications,
            contentDescription = null,
            tint = Accent,
        )
        Gap(16.dp)
        if (ring.kind == AlarmService.Kind.ALARM) {
            Text(ring.title, style = MaterialTheme.typography.displayLarge, color = Color.White)
            Text(ring.subtitle, style = MaterialTheme.typography.titleLarge, color = Color.White.copy(alpha = 0.8f))
        } else {
            Text("Reminder", color = Accent)
            Text(ring.title, style = MaterialTheme.typography.headlineMedium, color = Color.White, textAlign = TextAlign.Center)
            if (ring.subtitle != "Reminder") {
                Text(ring.subtitle, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
            }
        }
        Gap(48.dp)
        if (solving) {
            MathDismiss(onSolved = onDismiss)
        } else {
            if (ring.canSnooze) {
                OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text(
                        "Snooze ${ring.snoozeMinutes} min" +
                            (ring.snoozesLeft?.let { " · $it left" } ?: ""),
                    )
                }
                Gap(12.dp)
            }
            Button(
                onClick = { if (ring.challenge == DismissChallenge.MATH) solving = true else onDismiss() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(
                    when {
                        ring.kind == AlarmService.Kind.REMINDER -> "Complete"
                        ring.challenge == DismissChallenge.MATH -> "Dismiss (solve 3 problems)"
                        else -> "Dismiss"
                    }
                )
            }
        }
    }
}

/** Three quick problems so the alarm can't be dismissed half-asleep. */
@Composable
private fun MathDismiss(onSolved: () -> Unit) {
    var solved by remember { mutableIntStateOf(0) }
    var problem by remember { mutableStateOf(newProblem()) }
    var answer by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    Text("Problem ${solved + 1} of $MATH_PROBLEMS", color = Color.White.copy(alpha = 0.7f))
    Gap(8.dp)
    Text(problem.first, style = MaterialTheme.typography.headlineMedium, color = Color.White)
    Gap(12.dp)
    OutlinedTextField(
        value = answer,
        onValueChange = { answer = it.filter(Char::isDigit).take(4); wrong = false },
        singleLine = true,
        isError = wrong,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    Gap(12.dp)
    Button(
        onClick = {
            if (answer.toIntOrNull() == problem.second) {
                solved++
                answer = ""
                if (solved >= MATH_PROBLEMS) onSolved() else problem = newProblem()
            } else {
                wrong = true
            }
        },
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text("Check") }
}

private const val MATH_PROBLEMS = 3

private fun newProblem(): Pair<String, Int> = if (Random.nextBoolean()) {
    val a = Random.nextInt(12, 60)
    val b = Random.nextInt(12, 60)
    "$a + $b" to a + b
} else {
    val a = Random.nextInt(3, 13)
    val b = Random.nextInt(3, 13)
    "$a × $b" to a * b
}

/** After a wake-up alarm: the day at a glance, across every PLANR module. */
@Composable
private fun MorningBrief(onDone: () -> Unit) {
    val app = PlanrApp.instance
    val data by produceState<TodayData?>(null) { value = withContext(Dispatchers.IO) { TodaySummary.load() } }
    Column(
        Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().padding(28.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Gap(24.dp)
        Text("Good morning", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Text(
            DateFormat.getDateInstance(DateFormat.FULL).format(Date()),
            color = Color.White.copy(alpha = 0.6f),
        )
        Gap(24.dp)
        val d = data
        if (d != null) {
            BriefSection("Today's reminders") {
                if (d.dueToday.isEmpty() && d.overdue.isEmpty()) {
                    BriefLine("Nothing due today.")
                }
                (d.overdue + d.dueToday).take(6).forEach { r ->
                    var done by remember(r.id) { mutableStateOf(false) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val time = r.dueAt?.let {
                            if (r.allDay) "All day"
                            else DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it))
                        } ?: ""
                        Text(
                            (if (r in d.overdue) "Overdue · " else "$time · ") + r.title,
                            color = if (done) Success else Color.White,
                            modifier = Modifier.weight(1f),
                        )
                        if (!done) {
                            OutlinedButton(onClick = {
                                done = true
                                app.appScope.launch { Reminders.complete(app, r.id) }
                            }) { Text("Done") }
                        } else {
                            Icon(Icons.Outlined.CheckCircle, null, tint = Success)
                        }
                    }
                    Gap(4.dp)
                }
            }
            BriefSection("Habits") {
                BriefLine("Level ${d.level} · Off-Grid streak ${d.offGridStreak}")
                if (d.screenTimeYesterdayMs > 0) {
                    BriefLine("Screen time yesterday: ${formatDuration(d.screenTimeYesterdayMs)}")
                }
            }
            d.nextAlarm?.let { (a, at) ->
                BriefSection("Next alarm") {
                    val ms = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    BriefLine(
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms)) +
                            if (a.label.isNotBlank()) " · ${a.label}" else ""
                    )
                }
            }
            if (d.activeRoutines.isNotEmpty()) {
                BriefSection("Routines running") { BriefLine(d.activeRoutines.joinToString(", ")) }
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Start my day") }
        Gap(24.dp)
    }
}

@Composable
private fun BriefSection(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = Accent)
    Gap(6.dp)
    content()
    Gap(20.dp)
}

@Composable
private fun BriefLine(text: String) {
    Row {
        Spacer(Modifier.width(2.dp))
        Text(text, color = Color.White.copy(alpha = 0.85f))
    }
}
