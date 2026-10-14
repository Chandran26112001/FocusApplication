package com.example.focusastra.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.focusastra.data.AppLimit
import com.example.focusastra.data.LimitPolicy
import com.example.focusastra.ui.theme.FocusPalette
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.roundToInt

@Composable
internal fun BoundaryScreen(
    context: Context,
    current: AppLimit?,
    existing: Set<String>,
    onDismiss: () -> Unit,
    onSave: suspend (InstalledApp, LimitPolicy, Boolean) -> Unit,
) {
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var loading by remember { mutableStateOf(current == null) }
    var selected by remember { mutableStateOf(current?.let { InstalledApp(it.packageName, it.label) }) }
    val initial = current?.pendingPolicy ?: current?.policy ?: LimitPolicy()
    var minutes by rememberSaveable { mutableStateOf(initial.minutes.toString()) }
    var reminder by rememberSaveable { mutableStateOf(initial.reminderMinutes.toString()) }
    var reset by rememberSaveable { mutableStateOf(formatReset(initial.resetMinute)) }
    var search by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(current) {
        if (current == null) {
            runCatching { context.installedApps() }
                .onSuccess { apps = it.filterNot { item -> item.packageName in existing } }
                .onFailure { error = it.message }
            loading = false
        }
    }
    fun save(remove: Boolean) {
        val policy = LimitPolicy(minutes.toIntOrNull() ?: 0, reminder.toIntOrNull() ?: 0, parseReset(reset) ?: -1)
        if (!policy.valid()) { error = "Use 1–1440 minutes and a valid reset time (HH:mm)."; return }
        val chosen = selected ?: return
        scope.launch {
            saving = true
            runCatching { onSave(chosen, policy, remove) }.onFailure { error = it.message ?: "Could not save this boundary." }
            saving = false
        }
    }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(FocusPalette.background).systemBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 25.dp, top = 18.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).background(FocusPalette.panel, RoundedCornerShape(50))
                    .border(1.dp, FocusPalette.line, RoundedCornerShape(50)).clickable(enabled = !saving) { onDismiss() }, contentAlignment = Alignment.Center) {
                    Text("←", color = FocusPalette.text, fontSize = 22.sp)
                }
                Spacer(Modifier.weight(1f))
                Eyebrow(if (current == null) "BOUNDARY 01" else "NEXT PERIOD")
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 25.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                Column {
                    Text("Set Boundaries", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Choose a hard edge for attention—not a vague intention.",
                        style = MaterialTheme.typography.bodyMedium, color = FocusPalette.muted)
                }
                if (selected == null) {
                    FocusField(search, { search = it }, "FIND AN APP")
                    if (loading) Text("Loading apps…", color = FocusPalette.muted)
                    else {
                        val filtered = apps.filter { it.label.contains(search, ignoreCase = true) }
                        if (filtered.isEmpty()) Text("No available apps found.", color = FocusPalette.muted)
                        filtered.forEach { item ->
                            Row(Modifier.fillMaxWidth().clickable { selected = item }.padding(vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(item.label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                Text("↗", color = FocusPalette.crimson, fontSize = 20.sp)
                            }
                            HorizontalDivider(color = FocusPalette.line)
                        }
                    }
                } else {
                    Column(Modifier.fillMaxWidth().background(FocusPalette.panel, RoundedCornerShape(13.dp))
                        .border(1.dp, FocusPalette.line, RoundedCornerShape(13.dp)).padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Eyebrow("DAILY LIMIT")
                                Spacer(Modifier.height(4.dp))
                                Text(selected!!.label, style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Box(Modifier.size(11.dp).background(FocusPalette.crimson, RoundedCornerShape(50)))
                        }
                        Spacer(Modifier.height(18.dp))
                        BoundaryArc(minutes.toIntOrNull() ?: 0) { minutes = it.toString() }
                        Spacer(Modifier.height(10.dp))
                        FocusField(minutes, { minutes = it.filter(Char::isDigit).take(4) }, "MINUTES PER DAY",
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                        Spacer(Modifier.height(9.dp))
                        Text("Drag the crimson arc for up to four hours, or enter any allowance up to 24 hours.",
                            style = MaterialTheme.typography.bodySmall, color = FocusPalette.muted)
                    }
                    if (current == null) TextButton(onClick = { selected = null }) {
                        Text("CHOOSE ANOTHER APP", style = MaterialTheme.typography.labelMedium)
                    }
                    Column {
                        Eyebrow("COGNITIVE INTERCEPT")
                        Spacer(Modifier.height(8.dp))
                        Text("Check in every", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(13.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(10, 15, 30).forEach { value ->
                                val active = reminder.toIntOrNull() == value
                                Box(Modifier.weight(1f).height(47.dp)
                                    .background(if (active) FocusPalette.crimson else FocusPalette.panel, RoundedCornerShape(11.dp))
                                    .border(1.dp, if (active) FocusPalette.crimson else FocusPalette.line, RoundedCornerShape(11.dp))
                                    .clickable { reminder = value.toString() }, contentAlignment = Alignment.Center) {
                                    Text("${value}M", style = MaterialTheme.typography.labelLarge,
                                        color = if (active) FocusPalette.background else FocusPalette.text)
                                }
                            }
                        }
                        Spacer(Modifier.height(11.dp))
                        FocusField(reminder, { reminder = it.filter(Char::isDigit).take(4) }, "CUSTOM INTERVAL (MINUTES)",
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    }
                    FocusField(reset, { reset = it.take(5) }, "DAILY RESET · HH:MM")
                    Text(if (current == null) "Usage earlier in this period counts. Once started, today’s boundary is locked."
                        else "Changes and removal begin at the next reset. Today’s allowance stays in place.",
                        style = MaterialTheme.typography.bodySmall, color = FocusPalette.muted)
                    if (current != null) TextButton(enabled = !saving && !current.exhausted, onClick = { save(true) }) {
                        Text("REMOVE AT NEXT RESET", style = MaterialTheme.typography.labelMedium)
                    }
                    error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = FocusPalette.crimson) }
                    Spacer(Modifier.height(10.dp))
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 25.dp, vertical = 18.dp)) {
                CrimsonButton(if (saving) "SAVING…" else if (current == null) "SAVE BOUNDARY" else "SCHEDULE CHANGES",
                    enabled = selected != null && !saving && current?.exhausted != true) { save(false) }
            }
        }
    }
}

@Composable
private fun FocusField(value: String, onChange: (String) -> Unit, label: String,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = FocusPalette.crimson,
            unfocusedBorderColor = FocusPalette.line, focusedContainerColor = FocusPalette.panel,
            unfocusedContainerColor = FocusPalette.panel))
}

@Composable
private fun BoundaryArc(minutes: Int, onMinutesChange: (Int) -> Unit) {
    val display = minutes.coerceAtLeast(0)
    val latestOnChange by rememberUpdatedState(onMinutesChange)
    Box(Modifier.fillMaxWidth().height(174.dp)
            .semantics { contentDescription = "Daily allowance arc. Drag to adjust up to four hours." }
            .pointerInput(Unit) {
                fun adjust(x: Float) {
                    val inset = 16.5.dp.toPx()
                    val radius = (size.width / 2f - inset).coerceAtLeast(1f)
                    val ratio = ((x - size.width / 2f) / radius).coerceIn(-1f, 1f)
                    val fraction = 1.0 - acos(ratio.toDouble()) / PI
                    latestOnChange(((fraction * 48).roundToInt() * 5).coerceIn(5, 240))
                }
                detectDragGestures(onDragStart = { adjust(it.x) }, onDrag = { change, _ ->
                    adjust(change.position.x)
                    change.consume()
                })
            }, contentAlignment = Alignment.BottomCenter) {
        Canvas(Modifier.fillMaxWidth().height(164.dp)) {
            val stroke = 13.dp.toPx()
            val inset = stroke / 2 + 10.dp.toPx()
            val diameter = size.width - inset * 2
            val top = size.height - diameter / 2
            val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)
            val offset = androidx.compose.ui.geometry.Offset(inset, top)
            drawArc(FocusPalette.line, 180f, 180f, false, offset, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
            drawArc(FocusPalette.crimson, 180f, 180f * (display.coerceAtMost(240) / 240f), false,
                offset, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(Modifier.padding(bottom = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(String.format(Locale.ROOT, "%02d:%02d", display / 60, display % 60),
                style = MaterialTheme.typography.headlineLarge, color = FocusPalette.text)
            Eyebrow("HOURS / DAY")
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("00", "01", "02", "03", "04").forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = FocusPalette.muted) }
    }
}
