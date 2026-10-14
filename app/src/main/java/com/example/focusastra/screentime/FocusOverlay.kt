package com.example.focusastra.screentime

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.focusastra.FocusApplication
import com.example.focusastra.data.AppLimit
import com.example.focusastra.ui.theme.FocusAstraTheme
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class FocusOverlay(
    private val context: Context,
    private val app: FocusApplication,
    private val onContinue: () -> Unit,
    private val onHome: () -> Unit,
) {
    private val manager = context.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    private var content by mutableStateOf<OverlayContent?>(null)
    val isBlocked get() = content?.blocked == true
    private data class OverlayContent(val limit: AppLimit, val blocked: Boolean, val problem: String?)

    fun show(limit: AppLimit, blocked: Boolean, problem: String? = null) {
        content = OverlayContent(limit, blocked, problem)
        if (view != null) return
        val lifecycle = OverlayOwner()
        owner = lifecycle
        val compose = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycle)
            setViewTreeSavedStateRegistryOwner(lifecycle)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                FocusAstraTheme {
                    val item = content
                    val state by app.store.state.collectAsState()
                    val scope = rememberCoroutineScope()
                    if (item != null) {
                        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                            .systemBarsPadding().padding(28.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.Center) {
                            Text("FOCUSASTRA", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(24.dp))
                            Text(if (item.blocked) "Daily limit reached" else "Before you keep scrolling…", style = MaterialTheme.typography.headlineLarge)
                            Spacer(Modifier.height(12.dp))
                            Text(item.limit.label, style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(20.dp))
                            if (item.blocked) {
                                Text(item.problem ?: "You used your ${item.limit.policy.minutes} minute allowance. Take this time for something that matters to you.")
                                Spacer(Modifier.height(16.dp))
                                Text("0m remaining", style = MaterialTheme.typography.titleMedium)
                                Text("Available again ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.limit.periodEnd))}.")
                            } else {
                                Text("Did you finish these?", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(12.dp))
                                state.tasks.filter { !it.completed }.forEach { task ->
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        Checkbox(false, onCheckedChange = { checked -> scope.launch {
                                            runCatching { app.tasks.complete(task.id, checked) }.onFailure { app.monitorError.value = it.message }
                                        } })
                                        Text(task.title, Modifier.weight(1f))
                                    }
                                }
                                if (state.tasks.none { !it.completed }) Text("Everything is checked off. Nice work.")
                                Spacer(Modifier.height(20.dp))
                                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
                            }
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) { Text("Go Home") }
                        }
                    }
                }
            }
        }
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.OPAQUE).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "FocusAstra reminder and limit"
        }
        compose.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                if (event.action == android.view.KeyEvent.ACTION_UP) onHome()
                true
            } else false
        }
        compose.isFocusableInTouchMode = true
        try {
            manager.addView(compose, params)
            view = compose
            lifecycle.registry.currentState = Lifecycle.State.RESUMED
            compose.requestFocus()
        } catch (e: RuntimeException) { lifecycle.destroy(); owner = null; throw e }
    }

    fun hide() {
        view?.let { if (it.isAttachedToWindow) manager.removeViewImmediate(it) }
        view = null; owner?.destroy(); owner = null; content = null
    }
    fun destroy() = hide()

    private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
        val registry = LifecycleRegistry(this)
        private val controller = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry
        init { controller.performAttach(); controller.performRestore(null); registry.currentState = Lifecycle.State.CREATED }
        fun destroy() { registry.currentState = Lifecycle.State.DESTROYED }
    }
}
