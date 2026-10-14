package com.example.focusastra

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.focusastra.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FocusAppTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as FocusApplication

    @Before fun prepare(): Unit = runBlocking {
        app.store.awaitReady()
        app.store.update { it.copy(tasks = emptyList(), limits = emptyList(), musicEnabled = false) }
        Unit
    }

    @Test fun dashboardAndTaskCreation() {
        compose.onNodeWithText("Your limits").assertIsDisplayed()
        compose.onNodeWithText("TODOS").performClick()
        compose.onNodeWithText("ADD A TASK").performClick()
        compose.onNodeWithText("Task").performTextInput("Prepare interview")
        compose.onNodeWithText("Add task").performClick()
        compose.waitUntil(5_000) { app.store.state.value.tasks.any { it.title == "Prepare interview" } }
        compose.onNodeWithText("Prepare interview").assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { app.store.state.value.tasks.single().completed }
    }

    @Test fun explicitAccessibilityDisclosure() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onAllNodesWithText("SET UP")[1].performClick()
        compose.onNodeWithText("App detection & blocking").assertIsDisplayed()
        compose.onNodeWithText("Agree & open Settings").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Settings & access").assertIsDisplayed()
    }

    @Test fun storageRoundTripIncludesUsagePendingChangesAndTasks() {
        val original = AppState(
            limits = listOf(AppLimit("test.app", "Test app", LimitPolicy(), 100, 200, 42,
                pendingPolicy = LimitPolicy(45, 5, 360))),
            tasks = listOf(FocusTask("one", "A task with emoji 🌿 and quotes \"test\"", true)),
            musicEnabled = true, accessibilityConsent = true,
            checkpoint = UsageCheckpoint(120, 90, 2, "test.app"),
        )
        assertEquals(original, FocusStore.decode(FocusStore.encode(original).toString()))
    }

    @Test fun dataStoreCommitSurvivesFreshRead() = runBlocking {
        app.tasks.add("Persist this task")
        val expected = app.store.state.value.tasks
        // A new store wrapper reads the same singleton on-disk DataStore rather than UI state.
        val reread = FocusStore(compose.activity, app.appScope).awaitReady()
        assertEquals(expected, reread.tasks)
    }
}
