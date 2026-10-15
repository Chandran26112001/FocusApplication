package com.example.focusastra.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.edit
import androidx.core.net.toUri
import com.example.focusastra.FocusApplication
import com.example.focusastra.MainActivity
import com.example.focusastra.R
import com.example.focusastra.data.FocusTask
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val ACTION_TOGGLE = "com.example.focusastra.widgets.TOGGLE_TASK"
private const val EXTRA_TASK_ID = "task_id"
private const val EXTRA_TOGGLE_COMPLETED = "toggle_completed"
private const val WIDGET_PREFS = "todo_widget_ui"

class CompactTodoWidgetProvider : TodoWidgetProvider(false)
class ExpandedTodoWidgetProvider : TodoWidgetProvider(true)

abstract class TodoWidgetProvider(private val expanded: Boolean) : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = update(context, manager, ids)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        update(context, manager, intArrayOf(id))

    override fun onDeleted(context: Context, ids: IntArray) {
        context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE).edit {
            ids.forEach { remove(it.toString()) }
        }
    }

    private fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        val app = context.applicationContext as FocusApplication
        app.appScope.launch {
            try {
                app.store.awaitReady()
                ids.forEach { TodoWidgetRenderer.update(context, manager, it, expanded) }
                if (ids.isNotEmpty()) manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
            } catch (e: Exception) {
                Log.e("FocusAstraWidget", "Could not update todo widget", e)
            } finally {
                pending.finish()
            }
        }
    }
}

/** A separate non-exported receiver handles checkbox taps without opening the app. */
class TodoWidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE) return
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (intent.getBooleanExtra(EXTRA_TOGGLE_COMPLETED, false)) {
            if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
            val prefs = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
            prefs.edit { putBoolean(widgetId.toString(), !prefs.getBoolean(widgetId.toString(), false)) }
            TodoWidgetRenderer.refreshAll(context)
            return
        }
        val id = intent.getStringExtra(EXTRA_TASK_ID)?.takeIf { it.isNotBlank() } ?: return
        val pending = goAsync()
        val app = context.applicationContext as FocusApplication
        app.appScope.launch {
            try {
                app.tasks.toggle(id)
                TodoWidgetRenderer.refreshAll(context)
            } catch (e: Exception) {
                Log.e("FocusAstraWidget", "Could not change todo", e)
            } finally {
                pending.finish()
            }
        }
    }
}

class TodoWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = TodoWidgetFactory(applicationContext,
        intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID))
}

private sealed interface WidgetRow {
    data class Task(val task: FocusTask) : WidgetRow
    data class CheckedSummary(val count: Int, val expanded: Boolean) : WidgetRow
}

private class TodoWidgetFactory(private val context: Context, private val widgetId: Int) : RemoteViewsService.RemoteViewsFactory {
    @Volatile private var rows: List<WidgetRow> = emptyList()

    override fun onCreate() = load()
    override fun onDataSetChanged() = load()
    override fun onDestroy() { rows = emptyList() }
    override fun getCount() = rows.size
    override fun getViewTypeCount() = 3
    override fun getItemId(position: Int) = position.toLong()
    override fun hasStableIds() = false
    override fun getLoadingView(): RemoteViews? = null

    override fun getViewAt(position: Int): RemoteViews? {
        return when (val row = rows.getOrNull(position) ?: return null) {
            is WidgetRow.Task -> RemoteViews(context.packageName,
                if (row.task.completed) R.layout.widget_todo_row_done else R.layout.widget_todo_row_open).apply {
                setTextViewText(R.id.widget_row_title, row.task.title)
                setContentDescription(R.id.widget_row_root,
                    (if (row.task.completed) "Mark not done: " else "Mark done: ") + row.task.title)
                setOnClickFillInIntent(R.id.widget_row_root, Intent().putExtra(EXTRA_TASK_ID, row.task.id))
            }
            is WidgetRow.CheckedSummary -> RemoteViews(context.packageName, R.layout.widget_todo_row_summary).apply {
                setImageViewResource(R.id.widget_checked_arrow,
                    if (row.expanded) R.drawable.widget_checked_collapse else R.drawable.widget_checked_expand)
                setTextViewText(R.id.widget_checked_count, "${row.count} checked ${if (row.count == 1) "item" else "items"}")
                setContentDescription(R.id.widget_row_root,
                    (if (row.expanded) "Hide" else "Show") + " ${row.count} checked items")
                setOnClickFillInIntent(R.id.widget_row_root, Intent().putExtra(EXTRA_TOGGLE_COMPLETED, true))
            }
        }
    }

    private fun load() {
        val app = context.applicationContext as FocusApplication
        val tasks = runBlocking { app.store.awaitReady().tasks }
        val completed = tasks.filter { it.completed }
        val expanded = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
            .getBoolean(widgetId.toString(), false)
        rows = buildList {
            tasks.filterNot { it.completed }.forEach { add(WidgetRow.Task(it)) }
            if (completed.isNotEmpty()) {
                add(WidgetRow.CheckedSummary(completed.size, expanded))
                if (expanded) completed.forEach { add(WidgetRow.Task(it)) }
            }
        }
    }
}

object TodoWidgetRenderer {
    fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        listOf(CompactTodoWidgetProvider::class.java to false, ExpandedTodoWidgetProvider::class.java to true)
            .forEach { (provider, expanded) ->
                val ids = manager.getAppWidgetIds(ComponentName(context, provider))
                ids.forEach { update(context, manager, it, expanded) }
                if (ids.isNotEmpty()) manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
            }
    }

    fun update(context: Context, manager: AppWidgetManager, id: Int, expanded: Boolean) {
        val views = RemoteViews(context.packageName,
            if (expanded) R.layout.widget_todos_expanded else R.layout.widget_todos_compact)
        val adapter = Intent(context, TodoWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            data = "focusastra://todos/$id".toUri()
        }
        views.setRemoteAdapter(R.id.widget_list, adapter)
        views.setEmptyView(R.id.widget_list, R.id.widget_empty)
        val toggle = Intent(context, TodoWidgetActionReceiver::class.java).apply {
            action = ACTION_TOGGLE
            data = "focusastra://toggle/$id".toUri()
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
        val template = PendingIntent.getBroadcast(context, id, toggle,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        views.setPendingIntentTemplate(R.id.widget_list, template)
        val open = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_TODOS, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        views.setOnClickPendingIntent(R.id.widget_open, PendingIntent.getActivity(context, id, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        manager.updateAppWidget(id, views)
    }
}
