# PLANR

**Plan your attention.** PLANR is an offline Android focus app: mindful pauses
before distracting apps, schedules, daily limits, and **Off-Grid Mode**, which
locks the whole phone down to calls, camera and a few apps you choose for a set
time.

## Features (v0.1)

| | |
|---|---|
| **Off-Grid Mode** | Whole-phone lock for 15 min – 24 h. Dialer & emergency calls always work, camera optional, up to 5 extra apps. Home, recents, Settings and every other app bounce back to the lock screen. It survives reboots. |
| **Early-unlock step** | Pick one per session: typing challenge, 10-minute cooldown, or 10 math problems in a row. Early exits break your streak. |
| **Shields** | Breathing pause + "why are you opening this?", optional forced delay, daily limit, and a pass window after you continue. |
| **Schedules** | Block-list or allow-list by time and weekday, including overnight ranges. |
| **Dashboard** | Today's screen time, top apps, Off-Grid streak and history. |
| **Privacy** | No internet permission. No content or keystroke reading. All data stays on the device. |

See [ROADMAP.md](ROADMAP.md) for the full plan (Pomodoro, bedtime, Reels
blocking, website shields, streaks/XP, widgets, AI intent check, and more).

## Build

Requirements: Android Studio (latest), JDK 21, Android SDK 36.

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # schedule logic tests
```

## First run

1. Open PLANR → **Finish setup**.
2. Turn on **PLANR** under Accessibility.
3. Grant **Usage access**.
4. Add a shield, create a schedule, or go Off-Grid.

## How Off-Grid works

An `AccessibilityService` listens only for window-state changes (which app is
in front). While a session is active, any package outside the allow-list makes
PLANR bring `LockActivity` to the front. Session state is stored in
SharedPreferences, so it survives process death and reboot. When the service
reconnects after boot, it re-locks right away.

## Project layout

```
app/src/main/java/com/planr/app/
├── PlanrApp.kt                  service locator
├── data/                        Room DB, Off-Grid store, apps repository
├── engine/                      accessibility service, Enforcer, usage, boot
└── ui/                          Compose: screens, lock, intercept, components
```

## License

All rights reserved © PATILYASHH. Choose a license before publishing.
