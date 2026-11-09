package com.planr.app.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.planr.app.PlanrApp
import com.planr.app.data.OffGridState
import com.planr.app.data.OffGridStore
import com.planr.app.data.UnlockChallenge
import com.planr.app.ui.components.Gap
import com.planr.app.ui.theme.Danger
import kotlin.random.Random

/** Shows the early-unlock "step" the user chose when starting Off-Grid. */
@Composable
internal fun ChallengeHost(
    state: OffGridState,
    now: Long,
    onCancel: () -> Unit,
    onPassed: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Emergency unlock", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Text(
            "Ending early is logged and breaks your streak. Is it really an emergency?",
            color = Color.White.copy(alpha = 0.6f),
        )
        Gap(24.dp)
        when (state.challenge) {
            UnlockChallenge.TYPING -> TypingChallenge(onPassed)
            UnlockChallenge.COOLDOWN -> CooldownChallenge(state, now, onPassed)
            UnlockChallenge.MATH -> MathChallenge(onPassed)
        }
        Gap(16.dp)
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Never mind, stay Off-Grid")
        }
    }
}

private val passages = listOf(
    "I chose to put my phone away because my time and attention matter. " +
        "Nothing on this screen is more important than the moment I am in right now. " +
        "If I unlock early, I am choosing distraction over the plan I made for myself.",
    "The urge to check my phone will pass in a few minutes if I let it. " +
        "I made a promise to myself to stay off-grid, and keeping small promises " +
        "is how I build the discipline to keep big ones.",
)

private fun normalize(s: String) = s.trim().replace(Regex("\\s+"), " ").lowercase()

@Composable
private fun TypingChallenge(onPassed: () -> Unit) {
    val passage = remember { passages.random() }
    var typed by remember { mutableStateOf("") }
    val target = normalize(passage)
    val current = normalize(typed)
    val matched = target.commonPrefixWith(current).length

    Text("Type this exactly:", color = Color.White)
    Gap(8.dp)
    // Plain Text (not selectable) so it can't be copied and pasted.
    Text(passage, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyLarge)
    Gap(16.dp)
    OutlinedTextField(
        value = typed,
        onValueChange = { typed = it },
        modifier = Modifier.fillMaxWidth().height(160.dp),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
        ),
        isError = current.isNotEmpty() && matched < current.length,
    )
    Gap(8.dp)
    LinearProgressIndicator(
        progress = { matched.toFloat() / target.length },
        modifier = Modifier.fillMaxWidth(),
    )
    if (current.isNotEmpty() && matched < current.length) {
        Text("Typo at character ${matched + 1}", color = Danger)
    }
    Gap(16.dp)
    Button(
        onClick = onPassed,
        enabled = current == target,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text("Unlock") }
}

@Composable
private fun CooldownChallenge(state: OffGridState, now: Long, onPassed: () -> Unit) {
    val requested = state.cooldownRequestedAt
    if (requested == 0L) {
        Text(
            "Request an unlock, then wait 10 minutes. Most urges fade before the timer does.",
            color = Color.White,
        )
        Gap(16.dp)
        Button(
            onClick = { PlanrApp.instance.offGrid.requestCooldown() },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Request unlock") }
        return
    }
    val left = requested + OffGridStore.COOLDOWN_MS - now
    if (left > 0) {
        Text("Unlock available in", color = Color.White)
        Text(formatHms(left), style = MaterialTheme.typography.displayLarge, color = Color.White)
        Text("Still want out when this hits zero?", color = Color.White.copy(alpha = 0.6f))
    } else {
        Text("Cooldown finished.", color = Color.White)
        Gap(16.dp)
        Button(onClick = onPassed, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Confirm unlock")
        }
    }
}

private const val MATH_TARGET = 10

@Composable
private fun MathChallenge(onPassed: () -> Unit) {
    fun newProblem() = Random.nextInt(12, 100) to Random.nextInt(3, 10)

    var problem by remember { mutableStateOf(newProblem()) }
    var solved by remember { mutableIntStateOf(0) }
    var answer by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    Text("Solve $MATH_TARGET in a row. A wrong answer starts over.", color = Color.White)
    Gap(8.dp)
    LinearProgressIndicator(
        progress = { solved.toFloat() / MATH_TARGET },
        modifier = Modifier.fillMaxWidth(),
    )
    Gap(24.dp)
    Text(
        "${problem.first} × ${problem.second} = ?",
        style = MaterialTheme.typography.displayLarge,
        color = Color.White,
    )
    Gap(16.dp)
    OutlinedTextField(
        value = answer,
        onValueChange = { v -> answer = v.filter { it.isDigit() }.take(4); wrong = false },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = wrong,
        modifier = Modifier.fillMaxWidth(),
    )
    if (wrong) Text("Wrong — back to zero.", color = Danger)
    Gap(16.dp)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                if (answer.toIntOrNull() == problem.first * problem.second) {
                    solved++
                    if (solved >= MATH_TARGET) onPassed()
                } else {
                    solved = 0
                    wrong = true
                }
                problem = newProblem()
                answer = ""
            },
            enabled = answer.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Check") }
    }
}
