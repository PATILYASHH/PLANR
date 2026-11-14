# Privacy

PLANR is offline by design. **It has no `INTERNET` permission**, so it can't send anything anywhere.

## What PLANR stores
Your alarms, reminders, notes, routines, shields, schedules, settings, Off-Grid history, focus history, XP and daily screen-time totals. All of it lives in PLANR's private app storage on your phone. No account, no server, no analytics, no ads.

## Permissions, and why
| Permission | Used for |
|---|---|
| Accessibility service | Seeing **which app is in front**, to show pauses, enforce schedules and keep Off-Grid locked. It never reads screen content or what you type. |
| Usage access | Screen-time totals and daily limits. |
| Notifications, exact alarms, full-screen intent, display over other apps | Making alarms and reminders ring on time and fill the screen. |
| Contacts (optional) | Matching "call Parth" to a contact. Read on-device only, and only after you allow it. |
| Calendar (optional) | Showing your phone's calendar events on Today. Read-only, on-device, only after you turn it on. |
| Phone (optional) | Bringing the full-screen call screen forward when a call rings during Off-Grid. PLANR never reads numbers or call history. |
| Do Not Disturb access (optional) | Wind Down and routines that turn DND on or off. |
| Device admin (optional) | Uninstall protection. It uses no admin powers. |

## Backups and links
- **Backups** are JSON files written only where you choose: when you export, or daily to a folder you picked for auto-backup.
- **Crash reports** are saved on the phone only, and leave it only if you tap **Share** in About.
- **Shared text** (Share → PLANR) is parsed on the phone and saved only if you tap Add.
- Links in **About** (source code, exosynk.com) open in your browser. PLANR itself makes no connection.
- **Planned**: ExoSynk apps (XPENC, FITNS, CONECTR) will be able to show their items in PLANR, on-device only and after your approval. See [docs/exosynk-link.md](docs/exosynk-link.md).

Questions: open an [issue](https://github.com/PATILYASHH/PLANR/issues).
