package com.example.focusastra

import android.app.Application
import com.example.focusastra.data.FocusStore
import com.example.focusastra.music.MusicStatus
import com.example.focusastra.tasks.LocalTaskProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

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
    }
}
