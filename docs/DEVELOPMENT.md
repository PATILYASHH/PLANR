# Developing PLANR

Build, release and architecture notes. Start with [CONTRIBUTING.md](../CONTRIBUTING.md).

## Releasing (maintainers)

Releases must be signed with the ExoSynk Labs key: see [SIGNING.md](SIGNING.md).


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
