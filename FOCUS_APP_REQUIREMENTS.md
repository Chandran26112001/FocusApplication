# Focus Companion — Android App Requirements

### 1. Product goal

Build a **minimal, privacy-first, battery-efficient native Android application** that helps control digital distractions.

The application has two primary capabilities:

1. **YouTube Music — Stop After Current Song**
2. **Screen-Time Enforcement — Hard Daily App Limits + Task Reminders**

The application should prioritize:

```text
Minimal battery consumption
Minimal background processing
Small application size
No unnecessary network traffic
No backend/server
Local-first storage
Privacy
Reliability
Simple UI
Modern Android APIs
```

Use **Kotlin + Jetpack Compose**.

---

# Feature 1 — YouTube Music: Stop After Current Song

## Objective

When I manually start a song in YouTube Music, allow that song to play completely but prevent YouTube Music from continuing playback into the automatically selected next song.

Example:

```text
User opens YouTube Music

Searches:
"Numb"

User taps Numb

Numb plays normally
        ↓
Numb finishes
        ↓
YouTube Music advances to another song
        ↓
Companion app detects track transition
        ↓
Companion app pauses YouTube Music
```

The user should NOT need to configure a timer based on the song duration.

## Expected behavior

When the feature is enabled:

```text
Stop After Current Song: ON
```

The app observes YouTube Music playback.

When the user manually starts Track A:

```text
Current allowed track:
Track A

Status:
ARMED
```

Track A is allowed to play normally.

When Track A naturally finishes and YouTube Music automatically transitions to Track B:

```text
Track A
   ↓
ends
   ↓
Track B begins
   ↓
PAUSE immediately
```

Track B should preferably produce no audible playback, although a tiny transition may be unavoidable depending on Android/YouTube Music behavior.

## Manual song selection

A critical requirement:

If the user manually selects another song before Track A finishes:

```text
Track A playing

User manually taps Track X

Track X becomes the new allowed track

Track X plays normally until completion
```

Do NOT pause Track X simply because the media ID changed.

Investigate whether Android's media-session APIs provide enough information to distinguish:

```text
Manual user track change

vs

Automatic queue advancement
```

If this distinction cannot be reliably determined, document the limitation before implementing workarounds.

## Playback controls

Normal user actions must continue working:

```text
Pause
Resume
Seek
Bluetooth controls
Headset controls
Lock-screen controls
Volume
```

Seeking near the end of a song should not break the feature.

## UI

Home screen card:

```text
┌──────────────────────────────┐
│ YouTube Music                │
│                              │
│ Stop after current song  ON  │
│                              │
│ Numb                         │
│ Linkin Park                  │
│                              │
│ ● Armed                      │
└──────────────────────────────┘
```

The feature must have a global ON/OFF switch.

---

# Feature 2 — Screen-Time Enforcement

## Objective

Allow the user to assign strict daily usage limits to selected applications.

Example:

```text
Instagram
Daily limit: 30 minutes
```

Usage should represent actual foreground usage as accurately as Android permits.

The timer should accumulate across sessions:

```text
10 minutes morning
+
8 minutes afternoon
+
12 minutes evening
=
30 minutes

LIMIT REACHED
```

Closing the application must NOT reset the timer.

Restarting the phone must NOT reset the timer.

---

# Hard Lock

When the daily allowance reaches zero, opening the restricted application should immediately result in a blocking experience.

Example:

```text
┌──────────────────────────────┐
│      Daily limit reached     │
│                              │
│ Instagram                    │
│                              │
│ You used your 30 minutes.    │
│                              │
│ Available again tomorrow.    │
│                              │
│          0m remaining        │
└──────────────────────────────┘
```

There must be:

```text
NO "5 more minutes"
NO "Ignore limit"
NO "Unlock"
NO emergency override
NO limit modification after limit reached
```

inside our application.

The restriction automatically resets according to the configured daily reset time.

Default:

```text
00:00 local time
```

---

# Anti-Circumvention Philosophy

The application should make bypassing the restriction **deliberately inconvenient**, while staying within supported Android security boundaries.

Do NOT claim that restrictions are impossible for the device owner to bypass.

A normal Android device owner may ultimately be able to:

```text
Uninstall the app
Revoke permissions
Force-stop it
Use ADB
Modify device configuration
```

Do not use root.

Do not exploit Android security mechanisms.

Do not implement malicious persistence.

Investigate supported Android mechanisms that provide the strongest reasonable enforcement.

---

# Feature 3 — Task Reminder During Screen Time

While the user still has time remaining in a distracting application, periodically interrupt them with a task reminder.

Example:

```text
Instagram

30 minute allowance

0 min ─────────────────── 30 min
           ↑
        reminder
```

The reminder should display something like:

```text
┌──────────────────────────────┐
│ Before you keep scrolling... │
│                              │
│ Did you finish these?        │
│                              │
│ □ Prepare interview          │
│ □ Complete workout           │
│ □ Reply to email             │
│                              │
│      Continue                │
└──────────────────────────────┘
```

Reminder frequency should be configurable.

For example:

```text
Every 5 minutes
Every 10 minutes
Every 15 minutes
```

Default:

```text
10 minutes
```

Avoid constant polling.

---

# Feature 4 — Task Sources

Design the task system around a provider abstraction.

For example:

```kotlin
interface TaskProvider {
    suspend fun getTasks(): List<Task>
}
```

Potential providers:

```text
Local tasks
Other future integrations (optional, outside the MVP)
```

The app must always support **local tasks** without requiring an account or internet connection.

Example:

```text
Today's Tasks

□ Prepare Java interview questions
□ Gym
□ Finish API implementation
```

Store them locally.

---

# Feature 5 — Multiple Restricted Applications

The architecture should support multiple apps.

Example:

```text
DAILY LIMITS

Instagram       30m     12m left
Reddit          20m      8m left
YouTube         45m     31m left
Chrome          60m     42m left
```

Each application gets an independent daily allowance.

---

# Feature 6 — Lock Configuration

The user should configure restrictions before starting them.

Example:

```text
Instagram

Daily allowance:
30 minutes

Reminder:
Every 10 minutes

Reset:
12:00 AM

[ Start Restriction ]
```

Once activated for the current restriction period, important settings should not be trivially editable.

For example, don't allow:

```text
30 minutes
     ↓
limit reached
     ↓
user changes limit to 3 hours
```

Design a sensible lock-period model.

---

# Dashboard

Keep the main interface extremely simple.

```text
FOCUS
────────────────────────────

TODAY

Instagram
████████████████░░░░
22 / 30 min

Reddit
████████░░░░░░░░░░░░
8 / 20 min


YOUTUBE MUSIC

Stop after current song      ON

Numb
Linkin Park

● Armed


TODAY'S TASKS

□ Prepare interview
✓ Gym
□ Finish API


Settings
```

No unnecessary animations or elaborate navigation.

---

# Privacy Requirements

Prefer entirely on-device processing.

The application should NOT contain:

```text
Advertising
Analytics SDKs
Tracking
Telemetry
Unnecessary cloud services
```

No backend should be required.

Network permission should only be introduced if a future task provider actually requires networking.

Sensitive usage information should remain local.

---

# Battery Requirements

Battery efficiency is a major requirement.

Do NOT implement:

```text
while(true)
poll every second
poll active application constantly
poll YouTube Music constantly
```

Prefer event-driven Android mechanisms wherever possible.

The application should do essentially nothing when neither relevant playback nor monitored applications are active.

Avoid unnecessary wake locks.

---

# Persistence

Use the smallest appropriate persistence mechanism.

Likely:

```text
DataStore
```

for configuration.

Use Room only if the data model genuinely requires it.

Persist:

```text
App restrictions
Daily usage state where necessary
Task list
Task completion
Settings
YouTube Music feature state
```

Do not add Room simply because it is common Android architecture.

---

# Technology Constraints

Use:

```text
Kotlin
Jetpack Compose
Material 3
Coroutines
StateFlow
DataStore
```

Consider:

```text
MediaSession / MediaController APIs
UsageStatsManager
NotificationListenerService
AccessibilityService
DevicePolicyManager
Foreground Service
WorkManager
```

**only after evaluating which are actually necessary.**

Do not introduce a service simply because it makes implementation easier.

In particular, AccessibilityService should be considered carefully because of its security/privacy implications and Android/Play Store restrictions.

---

# Architecture

Do not over-engineer this application.

Prefer something approximately like:

```text
app/
 ├── ui/
 │    ├── dashboard/
 │    ├── limits/
 │    ├── music/
 │    └── tasks/
 │
 ├── screentime/
 │    ├── UsageMonitor
 │    └── RestrictionController
 │
 ├── music/
 │    └── MusicPlaybackController
 │
 ├── tasks/
 │    ├── TaskProvider
 │    └── LocalTaskProvider
 │
 ├── data/
 │    └── preferences/
 │
 └── MainActivity
```

Do NOT automatically create:

```text
domain/
data/
presentation/
usecases/
repositories/
mappers/
```

for every feature unless those abstractions provide real value.

This application should remain small.

---

# Performance Targets

Aim for:

```text
Minimal idle CPU usage
Minimal wakeups
Minimal memory footprint
Minimal APK size
Minimal battery impact
Fast cold startup
```

Measure rather than assume.

Avoid large third-party libraries where Android SDK functionality is sufficient.

---

# Reliability Requirements

Test:

```text
Device reboot

App process killed

YouTube Music process killed/restarted

Screen locked

Screen unlocked

Bluetooth playback

Headphones

User pauses/resumes music

User manually changes songs

YouTube Music automatically changes songs

Restricted application opened repeatedly

Restricted application stays open when limit expires

Midnight reset

Timezone change

Date change

Android battery optimization

Application update
```

State must recover gracefully.
