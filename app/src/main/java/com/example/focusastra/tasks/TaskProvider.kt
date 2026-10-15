package com.example.focusastra.tasks

import com.example.focusastra.data.FocusStore
import com.example.focusastra.data.FocusTask
import java.util.UUID

interface TaskProvider { suspend fun getTasks(): List<FocusTask> }

class LocalTaskProvider(private val store: FocusStore) : TaskProvider {
    override suspend fun getTasks() = store.awaitReady().tasks
    suspend fun add(title: String) {
        val trimmed = title.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= 160) { "Use 1–160 characters for a task." }
        store.update { it.copy(tasks = it.tasks + FocusTask(UUID.randomUUID().toString(), trimmed)) }
    }
    suspend fun complete(id: String, completed: Boolean) = store.update { s -> s.copy(tasks = s.tasks.map { if (it.id == id) it.copy(completed = completed) else it }) }
    suspend fun toggle(id: String) = store.update { s -> s.copy(tasks = s.tasks.map { if (it.id == id) it.copy(completed = !it.completed) else it }) }
    suspend fun delete(id: String) = store.update { s -> s.copy(tasks = s.tasks.filterNot { it.id == id }) }
}
