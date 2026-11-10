# PLANR — Product Spec & Roadmap

*An ExoSynk Labs project.*

PLANR is a from-scratch Android focus app: gentle nudges for everyday use, and a
hard **Off-Grid Mode** that locks the whole phone down to emergency essentials
for a chosen time.

> Name for the phone-avoider feature: **Off-Grid Mode**.
> Alternatives considered: Monk Mode, Vault, Hard Lock, Detox Lock.

---

## Off-Grid Mode (headline feature)

The phone becomes a "dumb phone" for a chosen duration.

| Aspect | Behaviour |
|---|---|
| Duration | Presets 15 m – 8 h, or custom up to 24 h |
| What still works | Phone dialer (always, cannot be removed), Camera (default on), up to 5 extra apps the user picks (Maps, Messages, banking, ride app…) |
| Everything else | Launcher, Settings, Play Store, recents → bounced straight back to the PLANR lock screen |
| Lock screen | Full-screen countdown, quick buttons for Call / Camera / extra apps, "Emergency unlock" button |
| Survives | Home button, recents, notification taps, app switching, reboot (service reconnects and re-locks) |
| Uninstall / disable | Settings & package installer are blocked while Off-Grid is active |
| Early exit ("the step") | User picks one when starting: **Typing challenge** (retype a long passage exactly), **Cooldown** (request → wait 10 min → confirm), **Math** (solve 10 problems). Early exits are logged and break the streak |
| Safety | Emergency dialer is never blocked; incoming calls always come through; max 24 h |

**How it works (no root, no device-owner):** an AccessibilityService watches
the foreground window. During Off-Grid, any package not on the allow-list makes
PLANR bring its `LockActivity` to the front. Apps with a system-bound
accessibility service are exempt from Android's background-activity-start
limits, so no overlay permission is needed.

Known limits (same as similar Play Store apps): rebooting into Android Safe Mode
disables third-party services; quick-settings toggles in the notification shade
still work; a pre-configured accessibility volume-key shortcut could turn the
service off. Optional device-admin uninstall protection and a 20-second
watchdog (for scheduled windows) are in place.

---

## Feature roadmap

Status: ✅ shipped · 🔜 next phase · 💡 idea

### Interventions
- ✅ **Shield (mindful pause)** — breathing pause + "why are you opening this?" before an app opens
- ✅ **Delay** — configurable forced wait before Continue is enabled
- ✅ **Daily limit** — hard block once today's usage passes a limit
- ✅ **Pass window** — after continuing, app stays open for N minutes without re-prompting
- ✅ Open-count limit per day
- ✅ Emergency-use quota (emergency passes)
- ✅ Mindful Gateway (pause before *every* non-whitelisted app)
- 🔜 Pause Point missions (walk, QR/NFC scan, puzzles) for shields
- 🔜 Reels/Shorts detection (block feeds, keep DMs)
- 🔜 Website tracking & shielding in browsers

### Schedules & modes
- ✅ **Schedules** — block-list or allow-list by time and weekday
- ✅ **Off-Grid Mode** — see above
- ✅ Scheduled Off-Grid (e.g. every night 11 PM–7 AM)
- ✅ Bedtime template (Off-Grid schedule)
- ✅ Wind Down reminder + auto Do-Not-Disturb
- ✅ Pomodoro focus/break cycles
- ✅ Lockdown (rules can only get stricter during set hours; optional Settings block)
- 🔜 Grace period, Early kick, Session HUD overlay
- ✅ Eye Care 20-20-20 reminders

### Tracking & motivation
- ✅ Today's screen time + top apps
- ✅ Off-Grid history (completed vs ended early) and streak
- ✅ 5-week heatmap, per-app 14-day history
- 🔜 Goals for productive apps, streaks per app
- ✅ XP, levels, achievements, home-screen widget
- ✅ Backup / restore (JSON file)

### New ideas beyond Zenith
- 💡 AI intent check (on-device) before opening distracting apps
- 💡 Accountability buddy approves early unlocks
- 💡 Commitment stakes (lose points for breaking Off-Grid)
- 💡 PC sync with the desktop agent
- 💡 Auto-tightening schedules learned from binge times
- 💡 Mood check-ins when stuck in a scroll loop

---

## Architecture

```
com.exosynk.planr
├── admin/PlanrDeviceAdmin  optional uninstall protection
├── PlanrApp                 Application + simple service locator
├── data/
│   ├── db/                  Room: ShieldRule, Schedule, OffGridSession, DailyUsage (migrated, not wiped)
│   ├── OffGridStore         SharedPreferences (sync reads for the service)
│   ├── SettingsStore        Gateway, emergency passes, Lockdown, Wind Down, Eye Care
│   ├── DailyCounters        opens per app and emergency passes used today
│   ├── PassStore            temporary "continue" and emergency passes
│   ├── Progression/ProgressStore  XP, levels, achievements
│   ├── StatsRepository      daily usage history beyond Android's retention
│   ├── Backup               JSON export/import
│   └── AppsRepository       launchable apps, default dialer/camera
├── engine/
│   ├── PlanrAccessibilityService   foreground watcher
│   ├── Enforcer             decides: allow / lock / intercept / block
│   ├── UsageHelper          UsageStatsManager queries
│   ├── Routines + Notifier  Wind Down, DND, Eye Care, achievement notifications
│   └── BootReceiver
└── ui/
    ├── MainActivity + nav   Home · Shields · Focus · Schedules · Off-Grid (+ Settings, Insights)
    ├── widget/PlanrWidget   home-screen widget
    ├── lock/LockActivity    Off-Grid lock screen + challenges
    └── intercept/InterceptActivity   shield pause / delay / limit
```

Stack: Kotlin, Jetpack Compose (Material 3), Room (KSP), Navigation Compose.
minSdk 30, targetSdk 36. No internet permission — fully offline.

## Play Store notes
- Accessibility use must be declared in the Play Console with a prominent
  in-app disclosure (PLANR shows one before opening settings).
- Uses a `<queries>` launcher intent instead of `QUERY_ALL_PACKAGES`.
