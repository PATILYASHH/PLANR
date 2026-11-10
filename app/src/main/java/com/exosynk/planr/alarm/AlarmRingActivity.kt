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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.exosynk.planr.ui.theme.Danger
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
        // Show over the lock screen and switch the display on (manifest attributes alone
        // aren't honoured by every launcher/OEM, so set them in code too).
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // True full screen: hide status and navigation bars (swipe to peek).
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
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
    val context = LocalContext.current
    var solving by remember(ring.id) { mutableStateOf(false) }
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            kotlinx.coroutines.delay(1_000)
        }
    }
    val pulse = rememberInfiniteTransition(label = "pulse")
    val glow by pulse.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "glow",
    )
    val isReminder = ring.kind == AlarmService.Kind.REMINDER

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF1B2347), Color(0xFF0B0D14), Color.Black))
        ),
    ) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            Gap(32.dp)
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(88.dp).scale(glow).clip(CircleShape).background(Accent.copy(alpha = 0.18f)))
                Icon(
                    if (isReminder) Icons.Outlined.Notifications else Icons.Outlined.Alarm,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(36.dp),
                )
            }
            Gap(24.dp)
            if (isReminder) {
                Text("Reminder", color = Accent, style = MaterialTheme.typography.titleMedium)
                Gap(8.dp)
                Text(
                    ring.title,
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                if (ring.subtitle != "Reminder") {
                    Gap(8.dp)
                    Text(ring.subtitle, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                }
                Gap(16.dp)
                Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(now), color = Color.White.copy(alpha = 0.6f))
            } else {
                Text(
                    DateFormat.getTimeInstance(DateFormat.SHORT).format(now),
                    fontSize = 84.sp,
                    fontWeight = FontWeight.Thin,
                    color = Color.White,
                )
                Text(
                    DateFormat.getDateInstance(DateFormat.FULL).format(now),
                    color = Color.White.copy(alpha = 0.65f),
                )
                Gap(20.dp)
                Text(ring.subtitle, style = MaterialTheme.typography.titleLarge, color = Color.White, textAlign = TextAlign.Center)
            }
            ring.contact?.let { c ->
                Gap(24.dp)
                Button(
                    onClick = {
                        com.exosynk.planr.reminder.ContactActions.intent(context, c)?.let {
                            runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Success, contentColor = Color.Black),
                    modifier = Modifier.height(56.dp),
                ) {
                    Icon(Icons.Outlined.Call, null)
                    Text("  ${c.action.verb} ${c.name}")
                }
            }
            Gap(24.dp)
            if (solving) MathDismiss(onSolved = onDismiss)
          }
            if (!solving) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 32.dp),
                    horizontalArrangement = if (ring.canSnooze) Arrangement.SpaceBetween else Arrangement.Center,
                ) {
                    SwipeCircle(
                        icon = Icons.Default.Close,
                        label = when {
                            isReminder -> "Complete"
                            ring.challenge == DismissChallenge.MATH -> "Dismiss (math)"
                            else -> "Dismiss"
                        },
                        color = Danger,
                        onTriggered = { if (ring.challenge == DismissChallenge.MATH) solving = true else onDismiss() },
                    )
                    if (ring.canSnooze) {
                        SwipeCircle(
                            icon = Icons.Outlined.Snooze,
                            label = "Snooze ${ring.snoozeMinutes}m" + (ring.snoozesLeft?.let { " · $it" } ?: ""),
                            color = Accent,
                            onTriggered = onSnooze,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Samsung-style control: drag the circle out in any direction to trigger it.
 * (Accessibility services can still activate it with a plain click.)
 */
@Composable
private fun SwipeCircle(icon: ImageVector, label: String, color: Color, onTriggered: () -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val threshold = with(density) { 90.dp.toPx() }
    var drag by remember { mutableStateOf(Offset.Zero) }
    var fired by remember { mutableStateOf(false) }
    val distance = drag.getDistance()
    val progress = (distance / threshold).coerceIn(0f, 1f)
    val animatedRadius by animateDpAsState(with(density) { (36.dp.toPx() + distance).toDp() }, label = "radius")

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(150.dp, 170.dp).pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        if (!fired && drag.getDistance() >= threshold) {
                            fired = true
                            onTriggered()
                        }
                        drag = Offset.Zero
                    },
                    onDragCancel = { drag = Offset.Zero },
                ) { change, amount ->
                    change.consume()
                    drag += amount
                }
            }.semantics {
                contentDescription = label
                onClick { onTriggered(); true }
            },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.requiredSize(animatedRadius * 2).clip(CircleShape)
                    .background(color.copy(alpha = 0.12f + 0.25f * progress))
                    .border(2.dp, color.copy(alpha = 0.4f + 0.6f * progress), CircleShape),
            )
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(color),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp)) }
        }
        Text(label, color = Color.White.copy(alpha = 0.8f))
        Text("Swipe", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.bodyMedium)
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
