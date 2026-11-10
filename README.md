# PLANR

*An [ExoSynk Labs](https://github.com/PATILYASHH) project.*

[![Build APK](https://github.com/PATILYASHH/PLANR/actions/workflows/build.yml/badge.svg)](https://github.com/PATILYASHH/PLANR/actions/workflows/build.yml)

**Plan your day. Guard your attention.** PLANR is an offline Android planner with
five modules that work together: **Habit** (mindful pauses, focus, schedules and
**Off-Grid Mode**, which locks the phone down to calls, camera and a few apps),
**Alarm**, **Reminders**, **Notes** and **Routines**.

## Habit module

| | |
|---|---|
| **Off-Grid Mode** | Whole-phone lock for 15 min – 24 h. Dialer & emergency calls always work, camera is optional, and you can keep up to 5 extra apps. Home, recents, Settings and every other app bounce back to the lock screen. It survives reboots. |
| **Early-unlock step** | Pick one per session: typing challenge, 10-minute cooldown, or 10 math problems in a row. Early exits break your streak. |
| **Shields** | Breathing pause + "why are you opening this?", optional forced delay, daily time limit, daily **open limit**, and a pass window after you continue. |
| **Mindful Gateway** | Optional pause before *every* app you open, with a skip list. |
| **Emergency passes** | 0–5 per day to open an app that's over its limit or blocked by a schedule or focus block. Never during Off-Grid. |
| **Lockdown** | Hours when rules can only get stricter. It can also block Android Settings. |
| **Scheduled Off-Grid** | Recurring Off-Grid windows. The **Bedtime** template locks the phone 11 PM – 7 AM every night. |
| **Wind Down & Eye Care** | Bedtime reminder with optional auto Do Not Disturb, and 20-20-20 eye breaks. |
| **Focus (Pomodoro)** | 25/50/90-minute focus blocks with breaks. Chosen apps (your shields by default) are blocked during focus and open again on breaks. |
| **Schedules** | Block-list or allow-list by time and weekday, including overnight ranges. |
| **Insights** | 5-week heatmap, weekly top apps, 14-day history per app, XP, levels and achievements. |
| **Widget** | Screen time or Off-Grid end time, plus level, streak and focus time. |
| **Backup & restore** | Export to and import from a JSON file you control. |
| **Uninstall protection** | Optional device admin with no policies, so PLANR can't be deleted on impulse. |
| **Privacy** | No internet permission. No content or keystroke reading. All data stays on the device. |

## Today & smart input (v0.5)

| | |
|---|---|
| **Today** | Week/month calendar with reminders, notes and alarms per day; day agenda; quick actions. |
| **Smart input** | “call to parth at 9 am”, “wake me at 6:30 weekdays”, “note: …”. Understands dates, times, repeats and relative times offline; matches names to contacts and adds a Call/Message/WhatsApp button. |
| **Full-screen alarms** | Wakes the screen and fills it; setup checklist and a 10-second test alarm. |

## Planner modules (v0.4)

| | |
|---|---|
| **Alarm** | Alarm groups with a master switch, repeat days or a date, skip next, ringtone, per-alarm volume with fade-in, vibration patterns, snooze interval/repeat, read aloud, math-to-dismiss, morning brief. |
| **Reminders** | Date/time, all-day or anytime; repeat; early alert; notification or alarm-style alert; categories; pin; Complete/Snooze from the notification. |
| **Notes** | Text and checklists, colours, pin, archive, search, "Remind me" (linked reminder), quick notes from the Off-Grid lock screen. |
| **Routines** | If-then automation with undo: time windows, alarm dismissed, charging, battery, headphones, app opened, Off-Grid, focus → DND, sound mode, volume, Off-Grid, Focus, Gateway, alarm groups, messages, today's brief, open app. |
| **Together** | Today card on Habit home, morning brief after alarms, routines that drive Off-Grid/Focus/Gateway/alarm groups, XP for reminders and no-snooze wake-ups, one backup for everything. |

See [ROADMAP.md](ROADMAP.md) for the full plan (Pomodoro, bedtime, Reels
blocking, website shields, streaks/XP, widgets, AI intent check, and more).

## Download

Get the latest APK from **[Releases](https://github.com/PATILYASHH/PLANR/releases)**.
It needs Android 11 or newer.

## Releasing (maintainers)

1. Write `release-notes/vX.Y.Z.md`.
2. Set `VERSION` to `X.Y.Z` and push to `main`. You can also use **Actions → Release APK →
   Run workflow**, or push a `vX.Y.Z` tag.
3. The **Release APK** workflow builds `PLANR-X.Y.Z.apk`, signs it with the
   ExoSynk Labs key from repo secrets, and publishes the GitHub Release. 0.x
   versions are marked pre-release.

`versionCode` comes from the tag: `MAJOR*10000 + MINOR*100 + PATCH`.

## Build

Every push to `main` builds a debug APK on GitHub Actions. Download it from the
latest run's **Artifacts** section.

Local build requirements: Android Studio (latest), JDK 21, Android SDK 36.

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # schedules, lockdown, XP, alarms, reminders, routines, smart parser, calendar, backup
```

## First run

1. Open PLANR → **Finish setup**.
2. Turn on **PLANR** under Accessibility.
3. Grant **Usage access**.
4. Add a shield, create a schedule, or go Off-Grid.
5. In **Alarm**, allow notifications and full-screen alarms if PLANR asks.

## How Off-Grid works

An `AccessibilityService` listens only for window-state changes (which app is
in front). While a session is active, any package outside the allow-list makes
PLANR bring `LockActivity` to the front. Session state is stored in
SharedPreferences, so it survives process death and reboot. When the service
reconnects after boot, it re-locks right away.

## Project layout

```
app/src/main/java/com/exosynk/planr/
├── PlanrApp.kt                  service locator, reschedules everything on start
├── alarm/                       alarms & groups: scheduler, ringing service, ring screen, editor
├── reminder/                    reminders & categories: scheduler, receiver, screens
├── notes/                       notes & checklists
├── routine/                     routines: model/codec, engine (triggers, actions, undo), screens
├── today/                       Today hub: calendar model and screen, cross-module summary
├── smart/                       smart input parser, contact lookup, smart bar and contact bar
├── data/                        Room DB, Off-Grid/settings/progress stores, stats, backup
├── admin/                       optional uninstall protection
├── engine/                      accessibility service, Enforcer, Off-Grid scheduler, routines, notifications
└── ui/                          Compose: screens, lock, intercept, widget, components
```

## License

All rights reserved © ExoSynk Labs. Choose a license before publishing.
