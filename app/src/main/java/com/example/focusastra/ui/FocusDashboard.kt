package com.example.focusastra.ui

import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.focusastra.FocusApplication
import com.example.focusastra.data.*
import com.example.focusastra.screentime.UsageHistory
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
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

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("FOCUSASTRA", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text("Make room for today.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(onClick = { settings = true }) { Text("Settings") }
                }
            }
            if (!ready) item {
                NoteCard(if (!state.loaded) "Loading your local data…" else state.error.orEmpty(), error = state.error != null)
            }
            if (ready && !enforcementReady) item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Finish setting up protection", style = MaterialTheme.typography.titleMedium)
                        Text("Daily limits need usage access and the FocusAstra accessibility service. Music uses notification access separately.")
                        Button(onClick = { settings = true }) { Text("Set up access") }
                    }
                }
            }
            monitorError?.let { message -> item { NoteCard(message, error = true) } }
            item {
                SectionTitle("Daily limits", "Add app", ready && enforcementReady) { addLimit = true }
                Text("Allowances repeat daily. Current periods stay locked.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.limits.isEmpty()) item {
                NoteCard("Choose a distracting app, give it a daily allowance, and let the rest of your day belong to you.")
            }
            items(state.limits, key = { it.packageName }) { limit ->
                LimitCard(limit, ready) { editingPackage = limit.packageName }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("YOUTUBE MUSIC", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Stop after current song", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Switch(state.musicEnabled, enabled = ready, onCheckedChange = { enabled ->
                                if (enabled) disclosure = "music" else action { app.store.update { it.copy(musicEnabled = false) } }
                            })
                        }
                        if (music.title.isNotEmpty() && state.musicEnabled) {
                            Text(music.title, style = MaterialTheme.typography.titleLarge)
                            if (music.artist.isNotBlank()) Text(music.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(when {
                            !state.musicEnabled -> "Off"
                            !access.notifications -> "Notification access needed"
                            !music.connected -> "Waiting for notification service"
                            else -> music.status
                        }, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        Text("Best effort: manual changes near the song’s end can be mistaken for autoplay. A brief start of the next song may be audible.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { SectionTitle("Your tasks", "Add task", ready) { addTask = true } }
            if (state.tasks.isEmpty()) item { Text("What would you like to make time for? Add a task to include it in your reminders.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(state.tasks, key = { it.id }) { task ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(task.completed, enabled = ready, onCheckedChange = { action { app.tasks.complete(task.id, it) } })
                    Text(task.title, Modifier.weight(1f), textDecoration = if (task.completed) TextDecoration.LineThrough else null)
                    TextButton(enabled = ready, onClick = { action { app.tasks.delete(task.id) } }) { Text("Delete") }
                }
            }
            item { Text("On your device. No accounts, ads, or network access.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }

    if (settings) AlertDialog(onDismissRequest = { settings = false }, title = { Text("Settings & access") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PermissionRow("Usage access", access.usage, "Restores foreground usage after service or phone restarts.") { disclosure = "usage" }
            PermissionRow("Accessibility", access.accessibility && monitor, "Detects foreground apps and displays limits and task reminders.") { disclosure = "accessibility" }
            PermissionRow("Notification access", access.notifications, "Observes and pauses only the YouTube Music media session.") { disclosure = "notification" }
            HorizontalDivider()
            Text("Limits and tasks stay on this device. Cloud backup is disabled. The app does not read screen text, type, click app controls, inspect notification messages, or send data anywhere.", style = MaterialTheme.typography.bodySmall)
            Text("You can still revoke permissions, force-stop, clear data, or uninstall using Android. This app cannot make a device-owner-proof lock.", style = MaterialTheme.typography.bodySmall)
            Text("Current reset instants remain fixed across timezone changes. The next period uses your new local timezone. Manual date changes can affect resets.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = { settings = false }) { Text("Done") } })

    if (disclosure != null) {
        val type = disclosure!!
        val title = when (type) { "accessibility" -> "App detection & blocking"; "usage" -> "Usage history access"; "music" -> "Stop after one song"; else -> "YouTube Music access" }
        val description = when (type) {
            "accessibility" -> "FocusAstra uses Android accessibility to identify the package of the foreground app and to show blocking and reminder overlays. Android grants access to window information; FocusAstra reads only package names, never screen text or typed content. It stores usage locally and shares nothing. Enable FocusAstra on the next screen only if you agree."
            "usage" -> "Android usage access lets FocusAstra read app foreground timestamps to recover usage after restarts and include earlier usage when you start a restriction. Only usage for restricted apps is stored, on this device. Enable usage access for FocusAstra on the next screen."
            "music" -> "Android cannot reliably distinguish manual song selection from autoplay in another app. FocusAstra allows early changes and pauses track changes near the prior song’s end. Changes in the final 1.5 seconds, repeat-one, missing duration, ads, casting, and delayed callbacks may not work as expected. Pause/resume re-arms the current track. Notification access is required."
            else -> "Android notification access is a broad permission. FocusAstra uses it only to obtain the YouTube Music media session and issue pause commands. It does not read, save, or dismiss notification messages. Enable access for FocusAstra on the next screen."
        }
        AlertDialog(onDismissRequest = { disclosure = null }, title = { Text(title) }, text = { Text(description) },
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
    if (addLimit || editing != null) LimitDialog(context = context, current = editing, existing = state.limits.map { it.packageName }.toSet(),
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

@Composable private fun SectionTitle(title: String, action: String, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        TextButton(enabled = enabled, onClick = onClick) { Text(action) }
    }
}

@Composable private fun NoteCard(text: String, error: Boolean = false) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer)) {
        Text(text, Modifier.padding(18.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable private fun LimitCard(limit: AppLimit, enabled: Boolean, onEdit: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(limit.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(if (limit.exhausted) "Locked" else "${(limit.remainingMs + MINUTE_MS - 1) / MINUTE_MS}m left", color = MaterialTheme.colorScheme.primary)
            }
            LinearProgressIndicator(progress = { (limit.usedMs.toFloat() / limit.allowanceMs).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Text("${limit.usedMs / MINUTE_MS} / ${limit.policy.minutes} min · Reminder every ${limit.policy.reminderMinutes} min", style = MaterialTheme.typography.bodySmall)
            Text("Resets ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(limit.periodEnd))}", style = MaterialTheme.typography.bodySmall)
            if (limit.pendingRemoval) Text("Removal scheduled for reset.", style = MaterialTheme.typography.bodySmall)
            limit.pendingPolicy?.let { Text("Next period: ${it.minutes} min · ${formatReset(it.resetMinute)} reset", style = MaterialTheme.typography.bodySmall) }
            if (limit.exhausted) Text("Changes are locked until reset.", style = MaterialTheme.typography.labelMedium)
            else TextButton(onClick = onEdit, enabled = enabled) { Text("Configure next period") }
        }
    }
}

@Composable private fun PermissionRow(title: String, enabled: Boolean, description: String, onClick: () -> Unit) {
    Column {
        Text("$title · ${if (enabled) "Ready" else "Needed"}", style = MaterialTheme.typography.titleSmall)
        Text(description, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onClick) { Text(if (enabled) "View Android settings" else "Set up") }
    }
}

@Composable private fun TaskDialog(onDismiss: () -> Unit, onSave: suspend (String) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("Make time for…") }, text = {
        Column { OutlinedTextField(title, { if (it.length <= 160) title = it }, label = { Text("Task") }, singleLine = true)
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

@Composable private fun LimitDialog(
    context: android.content.Context, current: AppLimit?, existing: Set<String>, onDismiss: () -> Unit,
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
    LaunchedEffect(Unit) { if (current == null) {
        runCatching { context.installedApps() }.onSuccess { apps = it.filterNot { item -> item.packageName in existing } }.onFailure { error = it.message }
        loading = false
    } }
    fun save(remove: Boolean) {
        val policy = LimitPolicy(minutes.toIntOrNull() ?: 0, reminder.toIntOrNull() ?: 0, parseReset(reset) ?: -1)
        if (!policy.valid()) { error = "Use 1–1440 minutes and a valid 24-hour reset time (HH:mm)."; return }
        val chosen = selected ?: return
        scope.launch { saving = true; runCatching { onSave(chosen, policy, remove) }.onFailure { error = it.message }; saving = false }
    }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text(if (current == null) "Set a daily limit" else "Next restriction period") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (selected == null) {
                OutlinedTextField(search, { search = it }, label = { Text("Find an app") }, singleLine = true)
                if (loading) Text("Loading installed apps…")
                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    items(apps.filter { it.label.contains(search, true) }, key = { it.packageName }) { item ->
                        Column(Modifier.fillMaxWidth().clickable { selected = item }.padding(vertical = 12.dp)) {
                            Text(item.label, style = MaterialTheme.typography.titleSmall)
                            Text(item.packageName, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (!loading && apps.isEmpty()) Text("No additional apps available. Install an app in the emulator first.")
            } else {
                Text(selected!!.label, style = MaterialTheme.typography.titleMedium)
                if (current == null) TextButton(onClick = { selected = null }) { Text("Choose another app") }
                OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(4) }, label = { Text("Daily allowance (minutes)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(reminder, { reminder = it.filter(Char::isDigit).take(4) }, label = { Text("Remind every (minutes)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(reset, { reset = it.take(5) }, label = { Text("Daily reset (HH:mm)") }, singleLine = true)
                Text(if (current == null) "Earlier usage in this period counts. Once started, this period’s settings cannot be changed."
                    else "Changes or removal take effect at the current period’s reset. Today’s allowance stays the same.", style = MaterialTheme.typography.bodySmall)
                if (current != null) TextButton(enabled = !saving && !current.exhausted, onClick = { save(true) }) { Text("Remove at next reset") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") } }, confirmButton = {
        TextButton(enabled = selected != null && !saving && current?.exhausted != true, onClick = { save(false) }) {
            Text(if (saving) "Saving…" else if (current == null) "Start restriction" else "Schedule changes")
        }
    })
}
