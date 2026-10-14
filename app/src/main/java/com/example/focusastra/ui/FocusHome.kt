package com.example.focusastra.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.focusastra.data.AppLimit
import com.example.focusastra.data.AppState
import com.example.focusastra.data.FocusTask
import com.example.focusastra.data.MINUTE_MS
import com.example.focusastra.music.MusicStatus
import com.example.focusastra.ui.theme.FocusPalette
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val cardShape = RoundedCornerShape(13.dp)

@Composable
internal fun FocusHome(
    state: AppState,
    music: MusicStatus,
    ready: Boolean,
    enforcementReady: Boolean,
    notificationsReady: Boolean,
    monitorError: String?,
    onSettings: () -> Unit,
    onAddLimit: () -> Unit,
    onEditLimit: (String) -> Unit,
    onAddTask: () -> Unit,
    onCompleteTask: (String, Boolean) -> Unit,
    onDeleteTask: (String) -> Unit,
    onMusicChange: (Boolean) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(FocusPalette.background).systemBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 25.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(SimpleDateFormat("EEE dd MMM · HH:mm", Locale.getDefault()).format(Date()).uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall, color = FocusPalette.muted)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(FocusPalette.crimson, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(if (enforcementReady) "FOCUS ACTIVE" else "SETUP NEEDED", style = MaterialTheme.typography.titleSmall)
                }
            }
            Box(Modifier.size(46.dp).clip(CircleShape).background(FocusPalette.panel).border(1.dp, FocusPalette.line, CircleShape)
                .clickable(onClick = onSettings).semantics { contentDescription = "Settings" }, contentAlignment = Alignment.Center) {
                Text("⚙", fontSize = 23.sp, color = FocusPalette.text)
            }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 25.dp, end = 25.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (!ready) item { FocusNotice(if (!state.loaded) "Loading your local data…" else state.error.orEmpty(), true) }
            if (ready && !enforcementReady) item {
                FocusNotice("Finish setting up usage access and app detection to enforce daily boundaries.", false, "SET UP ACCESS", onSettings)
            }
            monitorError?.let { item { FocusNotice(it, true) } }
            when (tab) {
                0 -> {
                    item { RemainingHero(state.limits) }
                    item {
                        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                            Column(Modifier.weight(1f)) {
                                Eyebrow("RESTRICTED APPS")
                                Spacer(Modifier.height(5.dp))
                                Text("Your limits", style = MaterialTheme.typography.titleLarge)
                            }
                            TextButton(onClick = onAddLimit, enabled = ready && enforcementReady) {
                                Text("ADD APP", color = if (ready && enforcementReady) FocusPalette.crimson else FocusPalette.subdued,
                                    style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    if (state.limits.isEmpty()) item {
                        FocusNotice("Choose an app and set a daily boundary. Its current usage will count from today’s reset.")
                    } else item {
                        Column(Modifier.fillMaxWidth().clip(cardShape).background(FocusPalette.panel)
                            .border(1.dp, FocusPalette.line, cardShape).padding(horizontal = 16.dp)) {
                            state.limits.forEachIndexed { index, limit ->
                                LimitRow(limit, ready && !limit.exhausted) { onEditLimit(limit.packageName) }
                                if (index < state.limits.lastIndex) HorizontalDivider(color = FocusPalette.line)
                            }
                        }
                    }
                    item {
                        Text("Changes to an active limit take effect at its next reset. Reached limits stay locked until then.",
                            style = MaterialTheme.typography.bodySmall, color = FocusPalette.muted)
                    }
                    item { MusicCard(state.musicEnabled, music, ready, notificationsReady, onMusicChange) }
                }
                1 -> {
                    item {
                        Eyebrow("COGNITIVE INTERCEPT")
                        Spacer(Modifier.height(12.dp))
                        Text("Things to do.", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Keep a few clear actions close. They’ll appear when a restricted app pauses for a check-in.",
                            style = MaterialTheme.typography.bodyMedium, color = FocusPalette.muted)
                    }
                    item { CrimsonButton("ADD A TASK", ready, onAddTask) }
                    if (state.tasks.isEmpty()) item { FocusNotice("Your list is clear. Add a task you want to make time for.") }
                    else item {
                        Column(Modifier.fillMaxWidth()) {
                            HorizontalDivider(color = FocusPalette.line)
                            state.tasks.forEach { task ->
                                TaskLine(task, ready, { onCompleteTask(task.id, !task.completed) }, { onDeleteTask(task.id) })
                                HorizontalDivider(color = FocusPalette.line)
                            }
                        }
                    }
                }
                else -> {
                    item {
                        Eyebrow("AMBIENT PROGRESS")
                        Spacer(Modifier.height(12.dp))
                        Text("A quieter day.", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Your boundaries and the space they leave for other things.", style = MaterialTheme.typography.bodyMedium,
                            color = FocusPalette.muted)
                    }
                    item { MetricsCard(state.limits, state.tasks) }
                    item { MusicCard(state.musicEnabled, music, ready, notificationsReady, onMusicChange) }
                    item { Text("Everything stays on this device. No account, ads, analytics, or network access.",
                        style = MaterialTheme.typography.bodySmall, color = FocusPalette.subdued) }
                }
            }
        }
        HorizontalDivider(color = FocusPalette.line)
        Row(Modifier.fillMaxWidth().height(72.dp).background(FocusPalette.background).padding(horizontal = 17.dp),
            verticalAlignment = Alignment.CenterVertically) {
            listOf("LIMITS", "TODOS", "METRICS").forEachIndexed { index, label ->
                Box(Modifier.weight(1f).fillMaxHeight().clickable { tab = index }
                    .semantics { contentDescription = label; selected = tab == index }, contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.labelLarge,
                        color = if (tab == index) FocusPalette.text else FocusPalette.muted)
                    if (tab == index) Box(Modifier.align(Alignment.TopCenter).width(24.dp).height(2.dp).background(FocusPalette.crimson))
                }
            }
        }
    }
}

@Composable
internal fun Eyebrow(text: String, color: androidx.compose.ui.graphics.Color = FocusPalette.muted) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
internal fun CrimsonButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(11.dp),
        colors = ButtonDefaults.buttonColors(containerColor = FocusPalette.crimson, contentColor = FocusPalette.background,
            disabledContainerColor = FocusPalette.panelRaised, disabledContentColor = FocusPalette.subdued)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun ProgressRing(progress: Float, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(450), label = "Time remaining")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx()
            val diameter = size.minDimension
            drawCircle(FocusPalette.panelRaised, radius = diameter / 2 - stroke / 2,
                style = Stroke(stroke))
            val inset = 18.dp.toPx()
            val innerSize = diameter - inset * 2
            val topLeft = androidx.compose.ui.geometry.Offset((size.width - innerSize) / 2, (size.height - innerSize) / 2)
            val arcSize = androidx.compose.ui.geometry.Size(innerSize, innerSize)
            drawArc(FocusPalette.line, -90f, 360f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(FocusPalette.crimson, -90f, 360f * animated, false, topLeft, arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round))
        }
        content()
    }
}

@Composable
private fun RemainingHero(limits: List<AppLimit>) {
    val total = limits.sumOf { it.allowanceMs }
    val left = limits.sumOf { it.remainingMs }
    val used = limits.sumOf { it.usedMs.coerceAtMost(it.allowanceMs) }
    val minutesLeft = (left + MINUTE_MS - 1) / MINUTE_MS
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(7.dp))
        Eyebrow("TODAY’S REMAINING TIME")
        Spacer(Modifier.height(18.dp))
        ProgressRing(if (total == 0L) 0f else left.toFloat() / total, Modifier.size(236.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(String.format(Locale.ROOT, "%02dH %02dM", minutesLeft / 60, minutesLeft % 60),
                    style = MaterialTheme.typography.headlineMedium, color = FocusPalette.text)
                Spacer(Modifier.height(5.dp))
                Text("OF ${formatHours(total)}", style = MaterialTheme.typography.labelSmall, color = FocusPalette.muted)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(if (limits.isEmpty()) "Your day is yours to shape." else "${used / MINUTE_MS} minutes used with intent.",
            style = MaterialTheme.typography.bodyMedium, color = FocusPalette.muted)
        if (limits.size > 1) Text("Across ${limits.size} independent limits", style = MaterialTheme.typography.bodySmall, color = FocusPalette.subdued)
    }
}

private fun formatHours(ms: Long): String {
    val minutes = ms / MINUTE_MS
    return String.format(Locale.ROOT, "%02dH %02dM", minutes / 60, minutes % 60)
}

@Composable
private fun LimitRow(limit: AppLimit, canEdit: Boolean, onEdit: () -> Unit) {
    val context = LocalContext.current
    val icon = remember(limit.packageName) {
        runCatching { context.packageManager.getApplicationIcon(limit.packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull()
    }
    Row(Modifier.fillMaxWidth().clickable(enabled = canEdit, onClick = onEdit).padding(vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(43.dp).clip(RoundedCornerShape(12.dp)).background(FocusPalette.panelRaised), contentAlignment = Alignment.Center) {
            if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(30.dp))
            else Text(limit.label.take(1).uppercase(), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(limit.label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(5.dp))
                Text("${limit.usedMs / MINUTE_MS} / ${limit.policy.minutes}M", style = MaterialTheme.typography.labelMedium, color = FocusPalette.text)
            }
            Spacer(Modifier.height(9.dp))
            LinearProgressIndicator(progress = { (limit.usedMs.toFloat() / limit.allowanceMs).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape), color = FocusPalette.crimson,
                trackColor = FocusPalette.line, gapSize = 0.dp, drawStopIndicator = {})
            Spacer(Modifier.height(5.dp))
            Text(if (limit.exhausted) "LOCKED UNTIL RESET" else "${(limit.remainingMs + MINUTE_MS - 1) / MINUTE_MS}M LEFT · RESET ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(limit.periodEnd))}",
                style = MaterialTheme.typography.labelSmall, color = FocusPalette.muted, maxLines = 1)
            if (limit.pendingRemoval) Text("REMOVAL AT NEXT RESET", style = MaterialTheme.typography.labelSmall, color = FocusPalette.crimson)
            else limit.pendingPolicy?.let {
                Text("NEXT: ${it.minutes}M · RESET ${formatReset(it.resetMinute)}", style = MaterialTheme.typography.labelSmall,
                    color = FocusPalette.crimson)
            }
        }
        Spacer(Modifier.width(11.dp))
        Text(if (limit.exhausted) "•" else "↗", color = FocusPalette.crimson, fontSize = 20.sp)
    }
}

@Composable
private fun TaskLine(task: FocusTask, enabled: Boolean, onToggle: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 68.dp)
        .clickable(enabled = enabled, onClick = onToggle)
        .semantics { role = Role.Checkbox; stateDescription = if (task.completed) "Completed" else "Not completed" }
        .padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(26.dp).border(1.dp, if (task.completed) FocusPalette.crimson else FocusPalette.text, RoundedCornerShape(7.dp))
            .background(if (task.completed) FocusPalette.crimson else FocusPalette.background, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center) {
            if (task.completed) Text("✓", color = FocusPalette.background)
        }
        Spacer(Modifier.width(14.dp))
        Text(task.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
            textDecoration = if (task.completed) TextDecoration.LineThrough else null,
            color = if (task.completed) FocusPalette.subdued else FocusPalette.text)
        TextButton(onClick = onDelete, enabled = enabled,
            modifier = Modifier.semantics { contentDescription = "Delete task ${task.title}" }) {
            Text("×", fontSize = 23.sp, color = FocusPalette.muted)
        }
    }
}

@Composable
private fun MetricsCard(limits: List<AppLimit>, tasks: List<FocusTask>) {
    Column(Modifier.fillMaxWidth().clip(cardShape).background(FocusPalette.panel).border(1.dp, FocusPalette.line, cardShape)
        .padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Eyebrow("TODAY AT A GLANCE")
        Text("${limits.sumOf { it.usedMs } / MINUTE_MS}m", style = MaterialTheme.typography.headlineLarge)
        Text("Used across restricted apps", style = MaterialTheme.typography.bodyMedium, color = FocusPalette.muted)
        HorizontalDivider(color = FocusPalette.line)
        Row {
            Column(Modifier.weight(1f)) {
                Text("${limits.count { it.exhausted }} / ${limits.size}", style = MaterialTheme.typography.titleMedium)
                Eyebrow("LIMITS REACHED")
            }
            Column(Modifier.weight(1f)) {
                Text("${tasks.count { it.completed }} / ${tasks.size}", style = MaterialTheme.typography.titleMedium)
                Eyebrow("TASKS DONE")
            }
        }
    }
}

@Composable
private fun MusicCard(enabled: Boolean, music: MusicStatus, ready: Boolean, notificationsReady: Boolean, onChange: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(cardShape).background(FocusPalette.panel).border(1.dp, FocusPalette.line, cardShape)
        .padding(22.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Eyebrow("YOUTUBE MUSIC")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Stop after this song", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = onChange, enabled = ready,
                colors = SwitchDefaults.colors(checkedThumbColor = FocusPalette.background, checkedTrackColor = FocusPalette.crimson))
        }
        if (enabled && music.title.isNotEmpty()) {
            Text(music.title, style = MaterialTheme.typography.titleMedium)
            if (music.artist.isNotBlank()) Text(music.artist, style = MaterialTheme.typography.bodySmall, color = FocusPalette.muted)
        }
        Text(when {
            !enabled -> "OFF"
            !notificationsReady -> "NOTIFICATION ACCESS NEEDED"
            !music.connected -> "WAITING FOR MUSIC"
            else -> music.status.uppercase(Locale.getDefault())
        }, style = MaterialTheme.typography.labelSmall, color = FocusPalette.crimson)
        Text("Best effort: a manual change near the end can look like autoplay. The next song may briefly be audible.",
            style = MaterialTheme.typography.bodySmall, color = FocusPalette.muted)
    }
}

@Composable
private fun FocusNotice(message: String, error: Boolean = false, action: String? = null, onClick: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().clip(cardShape).background(FocusPalette.panel).border(1.dp, FocusPalette.line, cardShape)
        .padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = if (error) FocusPalette.crimson else FocusPalette.text)
        if (action != null) TextButton(onClick = onClick) { Text(action, style = MaterialTheme.typography.labelLarge) }
    }
}
