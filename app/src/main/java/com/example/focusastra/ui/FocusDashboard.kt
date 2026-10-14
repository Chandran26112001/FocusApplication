package com.example.focusastra.ui

import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.focusastra.FocusApplication
import com.example.focusastra.data.*
import com.example.focusastra.screentime.UsageHistory
import com.example.focusastra.ui.theme.FocusPalette
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun FocusDashboard(app: FocusApplication) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val state by app.store.state.collectAsStateWithLifecycle()
    val music by app.musicStatus.collectAsStateWithLifecycle()
    val monitor by app.monitorConnected.collectAsStateWithLifecycle()
    val monitorError by app.monitorError.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var access by remember { mutableStateOf(context.accessState()) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var disclosure by rememberSaveable { mutableStateOf<String?>(null) }
    var addLimit by rememberSaveable { mutableStateOf(false) }
    var editingPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var addTask by rememberSaveable { mutableStateOf(false) }
    val editing = state.limits.firstOrNull { it.packageName == editingPackage }
    val ready = state.loaded && state.error == null
    val enforcementReady = access.usage && access.accessibility && monitor && state.accessibilityConsent
    fun action(block: suspend () -> Unit) { scope.launch { runCatching { block() }.onFailure { snackbar.showSnackbar(it.message ?: "Could not save this change.") } } }

    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                access = context.accessState()
                action { app.store.refreshPeriods() }
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { app.store.awaitReady(); runCatching { app.store.refreshPeriods() } }

    Box(Modifier.fillMaxSize()) {
        FocusHome(
            state = state, music = music, ready = ready, enforcementReady = enforcementReady,
            notificationsReady = access.notifications, monitorError = monitorError,
            onSettings = { settings = true }, onAddLimit = { addLimit = true },
            onEditLimit = { editingPackage = it }, onAddTask = { addTask = true },
            onCompleteTask = { id, completed -> action { app.tasks.complete(id, completed) } },
            onDeleteTask = { id -> action { app.tasks.delete(id) } },
            onMusicChange = { enabled ->
                if (enabled) disclosure = "music" else action { app.store.update { it.copy(musicEnabled = false) } }
            },
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    if (settings) AlertDialog(onDismissRequest = { settings = false },
        shape = RoundedCornerShape(14.dp), containerColor = FocusPalette.panel,
        title = { Text("Settings & access", style = MaterialTheme.typography.titleLarge) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PermissionRow("Usage access", access.usage, "Restores foreground usage after service or phone restarts.") { disclosure = "usage" }
            PermissionRow("Accessibility", access.accessibility && monitor, "Detects foreground apps and displays limits and task reminders.") { disclosure = "accessibility" }
            PermissionRow("Notification access", access.notifications, "Observes and pauses only the YouTube Music media session.") { disclosure = "notification" }
            HorizontalDivider()
            Text("Limits and tasks stay on this device. Cloud backup is disabled. The app does not read screen text, type, click app controls, inspect notification messages, or send data anywhere.", style = MaterialTheme.typography.bodySmall)
            Text("You can still revoke permissions, force-stop, clear data, or uninstall using Android. This app cannot make a device-owner-proof lock.", style = MaterialTheme.typography.bodySmall)
            Text("Current reset instants remain fixed across timezone changes. The next period uses your new local timezone. Manual date changes can affect resets.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = { settings = false }) { Text("DONE", style = MaterialTheme.typography.labelLarge) } })

    if (disclosure != null) {
        val type = disclosure!!
        val title = when (type) { "accessibility" -> "App detection & blocking"; "usage" -> "Usage history access"; "music" -> "Stop after one song"; else -> "YouTube Music access" }
        val description = when (type) {
            "accessibility" -> "FocusAstra uses Android accessibility to identify the package of the foreground app and to show blocking and reminder overlays. Android grants access to window information; FocusAstra reads only package names, never screen text or typed content. It stores usage locally and shares nothing. Enable FocusAstra on the next screen only if you agree."
            "usage" -> "Android usage access lets FocusAstra read app foreground timestamps to recover usage after restarts and include earlier usage when you start a restriction. Only usage for restricted apps is stored, on this device. Enable usage access for FocusAstra on the next screen."
            "music" -> "Android cannot reliably distinguish manual song selection from autoplay in another app. FocusAstra allows early changes and pauses track changes near the prior song’s end. Changes in the final 1.5 seconds, repeat-one, missing duration, ads, casting, and delayed callbacks may not work as expected. Pause/resume re-arms the current track. Notification access is required."
            else -> "Android notification access is a broad permission. FocusAstra uses it only to obtain the YouTube Music media session and issue pause commands. It does not read, save, or dismiss notification messages. Enable access for FocusAstra on the next screen."
        }
        AlertDialog(onDismissRequest = { disclosure = null }, shape = RoundedCornerShape(14.dp),
            containerColor = FocusPalette.panel, title = { Text(title, style = MaterialTheme.typography.titleLarge) },
            text = { Text(description, style = MaterialTheme.typography.bodyMedium, color = FocusPalette.muted) },
            dismissButton = { TextButton(onClick = { disclosure = null }) { Text("Cancel") } }, confirmButton = {
                TextButton(onClick = {
                    disclosure = null
                    action {
                        when (type) {
                            "accessibility" -> { app.store.update { it.copy(accessibilityConsent = true) }; context.openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
                            "usage" -> context.openSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                            "music" -> { app.store.update { it.copy(musicEnabled = true) }; if (!access.notifications) disclosure = "notification" }
                            else -> context.openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        }
                    }
                }) { Text(if (type == "music") "Enable best-effort mode" else "Agree & open Settings") }
            })
    }

    if (addTask) TaskDialog(onDismiss = { addTask = false }, onSave = { title -> app.tasks.add(title); addTask = false })
    if (addLimit || editing != null) BoundaryScreen(context = context, current = editing, existing = state.limits.map { it.packageName }.toSet(),
        onDismiss = { addLimit = false; editingPackage = null },
        onSave = { selected, policy, remove ->
            check(context.accessState().let { it.usage && it.accessibility } && app.monitorConnected.value) { "Restore usage and accessibility access first." }
            val now = System.currentTimeMillis()
            val (start, end) = Periods.containing(now, policy.resetMinute)
            val used = if (editing == null) {
                UsageHistory(context).read(start - 86_400_000, now).slices.filter { it.packageName == selected.packageName }
                    .sumOf { (it.end - maxOf(start, it.start)).coerceAtLeast(0) }
            } else 0L
            app.store.update { s ->
                val limits = s.limits.mapNotNull { LimitRules.roll(it, now) }
                val found = limits.firstOrNull { it.packageName == selected.packageName }
                if (editing != null) {
                    check(found != null) { "This restriction has reset. Reopen it to configure the next period." }
                    s.copy(limits = limits.map { if (it.packageName == selected.packageName) LimitRules.queue(it, policy, remove) else it })
                } else {
                    check(found == null) { "This app already has a restriction." }
                    s.copy(limits = limits + AppLimit(selected.packageName, selected.label, policy, start, end, used.coerceAtMost(policy.minutes * MINUTE_MS),
                        nextReminderMs = used + policy.reminderMinutes * MINUTE_MS))
                }
            }
            addLimit = false; editingPackage = null
        })
}

@Composable private fun PermissionRow(title: String, enabled: Boolean, description: String, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text(if (enabled) "READY" else "NEEDED", style = MaterialTheme.typography.labelSmall,
                color = if (enabled) FocusPalette.muted else FocusPalette.crimson)
        }
        Text(description, style = MaterialTheme.typography.bodySmall, color = FocusPalette.muted)
        TextButton(onClick = onClick) { Text(if (enabled) "VIEW ANDROID SETTINGS" else "SET UP",
            style = MaterialTheme.typography.labelMedium) }
        HorizontalDivider(color = FocusPalette.line)
    }
}

@Composable private fun TaskDialog(onDismiss: () -> Unit, onSave: suspend (String) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, shape = RoundedCornerShape(14.dp),
        containerColor = FocusPalette.panel, title = { Text("Make time for…", style = MaterialTheme.typography.titleLarge) }, text = {
        Column { OutlinedTextField(title, { if (it.length <= 160) title = it }, label = { Text("Task") }, singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = FocusPalette.crimson,
                unfocusedBorderColor = FocusPalette.line))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
    }, dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") } }, confirmButton = {
        TextButton(enabled = !saving && title.isNotBlank(), onClick = { scope.launch {
            saving = true; runCatching { onSave(title) }.onFailure { error = it.message }; saving = false
        } }) { Text("Add task") }
    })
}

internal fun formatReset(minute: Int) = String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60)
internal fun parseReset(text: String): Int? {
    if (!text.matches(Regex("[0-2][0-9]:[0-5][0-9]"))) return null
    val hour = text.take(2).toInt()
    return if (hour < 24) hour * 60 + text.takeLast(2).toInt() else null
}


