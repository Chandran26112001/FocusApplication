package com.example.focusastra.screentime

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.focusastra.ui.Eyebrow
import com.example.focusastra.ui.theme.FocusPalette
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
                        Column(Modifier.fillMaxSize().background(FocusPalette.background).systemBarsPadding().padding(horizontal = 25.dp)) {
                            Row(Modifier.fillMaxWidth().padding(top = 34.dp), verticalAlignment = Alignment.CenterVertically) {
                                Eyebrow(if (item.blocked) "FOCUSASTRA / BOUNDARY" else "FOCUSASTRA / CHECK-IN")
                                Spacer(Modifier.weight(1f))
                                Box(Modifier.size(10.dp).background(FocusPalette.crimson, RoundedCornerShape(50)))
                            }
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                                Spacer(Modifier.height(43.dp))
                                Text(if (item.blocked) "DAILY LIMIT REACHED" else "ARE YOU SURE?",
                                    style = MaterialTheme.typography.titleMedium, color = FocusPalette.crimson)
                                Spacer(Modifier.height(15.dp))
                                Text(if (item.blocked) "The boundary\nholds." else "You have\nthings to do.",
                                    style = MaterialTheme.typography.headlineLarge, color = FocusPalette.text)
                                Spacer(Modifier.height(15.dp))
                                Text(if (item.blocked) item.problem ?: "${item.limit.label} has used its ${item.limit.policy.minutes}-minute daily allowance."
                                    else "${item.limit.label} is paused. Take one clear action before returning.",
                                    style = MaterialTheme.typography.bodyLarge, color = FocusPalette.muted)
                                Spacer(Modifier.height(46.dp))
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Eyebrow(if (item.blocked) "AVAILABLE AGAIN" else "YOUR TASKS")
                                    Spacer(Modifier.weight(1f))
                                    Text(if (item.blocked) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.limit.periodEnd))
                                        else "${item.limit.usedMs / 60_000}M USED", style = MaterialTheme.typography.labelMedium,
                                        color = FocusPalette.text)
                                }
                                Spacer(Modifier.height(16.dp))
                                if (item.blocked) {
                                    HorizontalDivider(color = FocusPalette.line)
                                    Row(Modifier.fillMaxWidth().padding(vertical = 21.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("0m remaining", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                        Text("●", color = FocusPalette.crimson)
                                    }
                                    HorizontalDivider(color = FocusPalette.line)
                                } else {
                                    val open = state.tasks.filter { !it.completed }
                                    if (open.isEmpty()) Text("Everything is checked off.", style = MaterialTheme.typography.bodyMedium,
                                        color = FocusPalette.muted)
                                    open.forEach { task ->
                                        HorizontalDivider(color = FocusPalette.line)
                                        Row(Modifier.fillMaxWidth().clickable { scope.launch {
                                            runCatching { app.tasks.complete(task.id, true) }.onFailure { app.monitorError.value = it.message }
                                        } }.padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.size(25.dp).border(1.dp, FocusPalette.text, RoundedCornerShape(7.dp)))
                                            Spacer(Modifier.width(16.dp))
                                            Text(task.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Text("↗", color = FocusPalette.muted, fontSize = 19.sp)
                                        }
                                    }
                                    HorizontalDivider(color = FocusPalette.line)
                                }
                                Spacer(Modifier.height(26.dp))
                            }
                            Button(onClick = onHome, modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(
                                    containerColor = FocusPalette.text, contentColor = FocusPalette.background)) {
                                Text("LEAVE APP", style = MaterialTheme.typography.labelLarge)
                            }
                            if (!item.blocked) {
                                Spacer(Modifier.height(12.dp))
                                OutlinedButton(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(52.dp),
                                    shape = RoundedCornerShape(50), border = androidx.compose.foundation.BorderStroke(1.dp, FocusPalette.text)) {
                                    Text("CONTINUE", style = MaterialTheme.typography.labelLarge, color = FocusPalette.text)
                                }
                                Spacer(Modifier.height(13.dp))
                                Text("NEXT CHECK-IN AFTER YOUR SET INTERVAL", modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.labelSmall, color = FocusPalette.muted,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                            Spacer(Modifier.height(22.dp))
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
