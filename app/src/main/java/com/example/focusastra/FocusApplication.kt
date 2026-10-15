package com.example.focusastra

import android.app.Application
import android.content.res.Configuration
import com.example.focusastra.data.FocusStore
import com.example.focusastra.music.MusicStatus
import com.example.focusastra.tasks.LocalTaskProvider
import com.example.focusastra.widgets.TodoWidgetRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class FocusApplication : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var store: FocusStore
        private set
    lateinit var tasks: LocalTaskProvider
        private set
    val musicStatus = MutableStateFlow(MusicStatus())
    val monitorConnected = MutableStateFlow(false)
    val monitorError = MutableStateFlow<String?>(null)
    override fun onCreate() {
        super.onCreate()
        store = FocusStore(this, appScope)
        tasks = LocalTaskProvider(store)
        appScope.launch {
            store.awaitReady()
            store.state.map { it.tasks }.distinctUntilChanged().collect { TodoWidgetRenderer.refreshAll(this@FocusApplication) }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        appScope.launch {
            store.awaitReady()
            TodoWidgetRenderer.refreshAll(this@FocusApplication)
        }
    }
}
