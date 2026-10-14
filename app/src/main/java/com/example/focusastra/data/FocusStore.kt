package com.example.focusastra.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

private val Context.focusDataStore by preferencesDataStore("focus")

class FocusStore(context: Context, scope: CoroutineScope) {
    private val dataStore = context.applicationContext.focusDataStore
    private val key = stringPreferencesKey("state_v1")
    private val mutex = Mutex()
    private val ready = CompletableDeferred<Unit>()
    private val mutable = MutableStateFlow(AppState())
    val state = mutable.asStateFlow()

    init {
        scope.launch {
            try {
                mutable.value = decode(dataStore.data.first()[key]).copy(loaded = true)
            } catch (e: Exception) {
                mutable.value = AppState(loaded = true, error = "Local data could not be read. Restart the app; existing data has not been erased.")
            } finally { ready.complete(Unit) }
        }
    }

    suspend fun awaitReady(): AppState { ready.await(); return mutable.value }

    suspend fun update(transform: (AppState) -> AppState): AppState {
        ready.await()
        return mutex.withLock {
            check(mutable.value.error == null) { mutable.value.error.orEmpty() }
            val next = transform(mutable.value).copy(loaded = true)
            if (next == mutable.value) return@withLock next
            try {
                // Once a transaction starts, navigation must not separate its disk commit from publication.
                withContext(NonCancellable) {
                    dataStore.edit { it[key] = encode(next).toString() }
                    mutable.value = next
                }
                next
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(error = "Local data could not be saved. Restrictions cannot be changed. Check free storage and restart.")
                throw e
            }
        }
    }

    suspend fun refreshPeriods(now: Long = System.currentTimeMillis()) = update { s -> s.copy(limits = s.limits.mapNotNull { LimitRules.roll(it, now) }) }

    companion object {
        private fun policy(p: LimitPolicy) = JSONObject().put("minutes", p.minutes).put("reminder", p.reminderMinutes).put("reset", p.resetMinute)
        private fun readPolicy(o: JSONObject) = LimitPolicy(o.getInt("minutes"), o.getInt("reminder"), o.getInt("reset")).also { require(it.valid()) }

        internal fun encode(s: AppState): JSONObject = JSONObject().apply {
            put("version", 1)
            put("music", s.musicEnabled)
            put("consent", s.accessibilityConsent)
            put("checkpoint", JSONObject().put("wall", s.checkpoint.wallMs).put("elapsed", s.checkpoint.elapsedMs)
                .put("boot", s.checkpoint.bootCount).put("active", s.checkpoint.activePackage ?: JSONObject.NULL))
            put("limits", JSONArray().apply { s.limits.forEach { l -> put(JSONObject().apply {
                put("package", l.packageName); put("label", l.label); put("policy", policy(l.policy))
                put("start", l.periodStart); put("end", l.periodEnd); put("used", l.usedMs); put("reminderAt", l.nextReminderMs)
                put("remove", l.pendingRemoval); l.pendingPolicy?.let { put("pending", policy(it)) }
            }) } })
            put("tasks", JSONArray().apply { s.tasks.forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("done", it.completed)) } })
        }

        internal fun decode(raw: String?): AppState {
            if (raw == null) return AppState()
            val o = JSONObject(raw)
            require(o.getInt("version") == 1) { "Unsupported local data version" }
            val checkpoint = o.getJSONObject("checkpoint")
            val limits = o.getJSONArray("limits")
            val tasks = o.getJSONArray("tasks")
            return AppState(
                musicEnabled = o.getBoolean("music"), accessibilityConsent = o.getBoolean("consent"),
                checkpoint = UsageCheckpoint(checkpoint.getLong("wall"), checkpoint.getLong("elapsed"), checkpoint.getInt("boot"),
                    if (checkpoint.isNull("active")) null else checkpoint.getString("active")),
                limits = List(limits.length()) { i -> limits.getJSONObject(i).let { l ->
                    AppLimit(l.getString("package"), l.getString("label"), readPolicy(l.getJSONObject("policy")),
                        l.getLong("start"), l.getLong("end"), l.getLong("used"), l.getLong("reminderAt"),
                        l.optJSONObject("pending")?.let(::readPolicy), l.getBoolean("remove")).also {
                        require(it.periodEnd > it.periodStart && it.usedMs >= 0 && it.nextReminderMs > 0)
                    }
                } },
                tasks = List(tasks.length()) { i -> tasks.getJSONObject(i).let { FocusTask(it.getString("id"), it.getString("title"), it.getBoolean("done")) } },
            )
        }
    }
}
