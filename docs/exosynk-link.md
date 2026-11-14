# ExoSynk Link — connecting PLANR, XPENC, FITNS and CONECTR

Status: **plan**, nothing built yet. Written 2026-11-11 from the code of
PLANR 0.5.1, XPENC (`com.yash.xpenc`), FITNS (`com.trakvrs.fitns`) and
CONECTR (`com.yash.xpenc.conectr`).

## Goal

PLANR becomes the one place where your day is:

- **XPENC** → card dues, bill/payment reminders and upcoming auto-posts show
  in PLANR's calendar and Reminders, and alert like any PLANR reminder.
- **FITNS** → workout schedules ring as PLANR alarms; water and weigh-in
  reminders arrive as PLANR reminders.
- **CONECTR** → backs up all apps (not just XPENC), brings friends' shared
  dues into PLANR, and shows a status hub of every ExoSynk app.

## Rules we keep

1. **Offline apps stay offline.** PLANR, XPENC and FITNS keep no INTERNET
   permission. Only CONECTR talks to the network, as today.
2. **Agree on a contract, not on internals.** No app reads another's database.
   Sources push a documented JSON snapshot. That's the same idea as CONECTR
   and XPENC agreeing on a backup file.
3. **The source app owns its data.** PLANR shows linked items and alerts on
   them, but can't edit them. "Mark paid" and "Start workout" open the source
   app, and the source pushes a new snapshot.
4. **Every link is verified and approved.** Callers are checked by package
   name **and** signing-certificate SHA-256, exactly like
   `CONECTR/…/XpencProvider.callerIsXpenc`. You approve each app once in PLANR.

---

## Step 0 — fix signing (blocker, do first)

**Problem:** PLANR's release CI signs with the runner's debug key. GitHub
Actions creates a new `~/.android/debug.keystore` on every run, so 0.4.0,
0.5.0 and 0.5.1 each have a **different signature**. Android refuses to
install one over another ("App not installed"), and a certificate allowlist
can't name PLANR at all.

**Do:**
1. Create one release keystore per app, or one ExoSynk Labs key for all
   four. Keep it backed up offline.
2. In PLANR add the repo secrets `PLANR_KEYSTORE_BASE64`,
   `PLANR_KEYSTORE_PASSWORD`, `PLANR_KEY_ALIAS` and `PLANR_KEY_PASSWORD`. The
   workflow already reads them.
3. Make the release workflow **fail** rather than fall back to the debug key:
   delete the `else` branch in `app/build.gradle.kts` that picks
   `signingConfigs.debug` for release.
4. Record every certificate SHA-256 each app ships with: GitHub release key,
   Play App Signing key, and F-Droid's key for XPENC. One app can have
   several.
5. Tell users of 0.4.0–0.5.1 to export a backup, uninstall, install the
   first signed build, and restore. This happens once.

---

## Architecture

```
 XPENC ─┐                         ┌─ Reminders (card dues, bills)
 FITNS ─┼── ContentProvider.call ─┼─ Alarms (workout schedules, FITNS group)
CONECTR┘   "com.exosynk.planr.link"└─ Calendar / Today / notifications
             (verified + approved)
```

### Why a ContentProvider `call()` (not broadcasts or files)

- **Synchronous and instant**, and works while PLANR is closed: Android
  starts PLANR's process for the call.
- **Real caller identity:** `ContentProvider.getCallingPackage()` plus the
  caller's certificates. A broadcast can't prove who sent it.
- **Proven in this ecosystem:** CONECTR already serves XPENC this way.

### PLANR side: `LinkProvider`

Manifest:

```xml
<provider
    android:name=".link.LinkProvider"
    android:authorities="com.exosynk.planr.link"
    android:exported="true" />
```

Each source app's manifest (Android 11+ package visibility):

```xml
<queries><package android:name="com.exosynk.planr" /></queries>
```

Methods (`ContentResolver.call(Uri.parse("content://com.exosynk.planr.link"), method, arg, extras)`):

| method   | extras in               | bundle out                                   | effect |
|----------|-------------------------|----------------------------------------------|--------|
| `hello`  | `v` (Int, protocol)     | `v`, `state` = `approved` \| `pending` \| `blocked`, `planrVersion` | First call from an unknown app creates a **pending** request and posts a PLANR notification: "XPENC wants to show your dues in PLANR". |
| `replace`| `v`, `json` (String)    | `state`, `accepted` (Int), `error` (String?) | Replaces **everything** from that source with the snapshot. Only when approved. |
| `clear`  | `v`                     | `state`                                      | Removes all of that source's items (when you turn the feature off in the source app). |
| `status` | —                       | `state`, `lastSyncAt`, `itemCount`, `alarmGroupEnabled` | For CONECTR's hub and the source app's settings. |

Caller check (copy CONECTR's code):

```kotlin
private val ALLOWED = mapOf(
    "com.yash.xpenc" to setOf("<github-sha256>", "<play-sha256>", "<fdroid-sha256>"),
    "com.trakvrs.fitns" to setOf("<github-sha256>", "<play-sha256>"),
    "com.yash.xpenc.conectr" to setOf("<github-sha256>"),
)
fun callerSource(): Source? {
    val pkg = callingPackage ?: return null
    val certs = ALLOWED[pkg] ?: return null
    val debuggable = context!!.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    if (!debuggable && certSha256s(pkg).none { it in certs }) return null
    return Source.of(pkg)
}
```

Limits: payload ≤ 256 KB, ≤ 500 items, at most one `replace` per source
every 5 s (later calls are coalesced). An invalid item is skipped and counted
in `error`. It never fails the whole snapshot.

### Snapshot format (protocol v1)

Sources send **concrete occurrences for the next 90 days**, not repeat rules.
That keeps XPENC's own date rules (month-end snapping, Feb 29, promos) and
FITNS's schedules authoritative, and PLANR never reimplements them. Sources
re-send daily and on every change.

```json
{
  "v": 1,
  "source": "xpenc",
  "generatedAt": 1762851000000,
  "items": [
    {
      "id": "card:12:2026-12-15",
      "kind": "due",
      "title": "HDFC Regalia due",
      "body": "Statement ₹18,240 · min ₹910",
      "at": 1765758600000,
      "allDay": true,
      "date": "2026-12-15",
      "amount": { "minor": 1824000, "currency": "INR" },
      "direction": "pay",
      "alerts": [4320, 0],
      "style": "notification",
      "group": "Card dues",
      "open": "xpenc://card/12",
      "done": "xpenc://reminder/12/paid"
    }
  ]
}
```

| field | type | notes |
|-------|------|-------|
| `id` | string, ≤ 80 | Stable per occurrence. PLANR keys user prefs (e.g. snoozed or muted) on it. |
| `kind` | `due` \| `reminder` \| `alarm` \| `event` | `due` and `reminder` become reminders. `alarm` becomes an alarm. `event` is calendar-only. |
| `title`, `body` | string ≤ 80 / ≤ 300 | Shown as is. |
| `at` | epoch ms | Exact moment. For `allDay` items, `date` (ISO) wins and PLANR alerts at 9 AM like its own all-day reminders. |
| `amount` | `{minor, currency}` | Integer minor units (XPENC uses paise). Optional. PLANR can hide amounts. |
| `direction` | `pay` \| `receive` | XPENC's `ReminderDirection`. |
| `alerts` | minutes before `at`, ≤ 3 | `[4320, 0]` = 3 days before + on the day. Maps XPENC's `notifyDaysBefore`. |
| `style` | `notification` \| `alarm` | `alarm` rings full screen like a PLANR alarm (FITNS workouts). |
| `group` | string ≤ 30 | Category in PLANR (Reminders) or alarm-group name. |
| `open` | URI | "Open in XPENC/FITNS" button. |
| `done` | URI | "Mark paid" / "Start workout". PLANR opens it. The source does the work, then re-syncs. |

### PLANR storage and UI

**DB v6 migration**, keeping PLANR's own machinery so linked items get alerts,
the ring screen, the calendar and the widget for free:

- `reminders` gains `source TEXT NULL` and `externalId TEXT NULL`, plus a
  unique index on `(source, externalId)`.
- `alarms` and `alarm_groups` gain the same columns. Each source gets one
  locked alarm group ("FITNS") with its master switch.
- New table `link_sources (package TEXT PK, state TEXT, approvedAt, lastSyncAt, itemCount, hideAmounts INT, alertsOn INT)`.

`replace` runs in one Room transaction. It upserts by `(source, externalId)`,
keeps your local choices (enabled, alert style, snooze), deletes items no
longer in the snapshot, then calls `Reminders.schedule` and
`AlarmScheduler.rescheduleAll`.

Linked items are read-only in PLANR's editors. They show:
- a source badge
- **Open in XPENC**
- the `done` action
- the only things you can change: mute, alert style, and snooze.

**Settings → Connected apps** (new page) lists XPENC, FITNS and CONECTR. For
each it shows:
- status: Not installed / Waiting for approval / Connected / Blocked
- last sync and item count
- toggles: show in calendar, alerts on/off, hide amounts
- **Disconnect** (blocks the app and removes its items)

The approval notification opens this page.

---

## XPENC (Flutter, `com.yash.xpenc`)

What to send (all already in XPENC's Drift DB, `lib/data/tables.dart`):

| XPENC data | PLANR item |
|------------|------------|
| Credit cards: `creditCardNextDueDateProvider(card.id)` + statement balance + `notifyDaysBefore` from `credit_card_statement_section.dart` | `kind: due`, `group: "Card dues"`, one item per due in the next 90 days, `alerts: [notifyDaysBefore*1440, 0]`, `done: xpenc://card/<id>/pay` |
| `Reminders` where `status == open` (title, amount, direction, dueDate, repeat, notifyDaysBefore) | `kind: due`, expand `repeat` into occurrences with XPENC's own date code, `done: xpenc://reminder/<id>/paid` |
| `RecurringRules` where `isActive` (nextDueDate, notifyDaysBefore, amount/promoAmount) | `kind: event`, `group: "Auto-posts"` (informational: "Netflix ₹649 posts tomorrow") |
| Person dues with a due date | `kind: due`, `group: "People"` |

Build:
1. Kotlin `PlanrLink.kt` in `android/app/src/main/kotlin/…`, with a
   `MethodChannel("xpenc/planr_link")` and `hello`, `replace` and `clear`
   wrapping `contentResolver.call`. Return `notInstalled` when PLANR is
   missing.
2. Dart `PlanrLinkService`. Watch the Drift queries above (`.watch()`), debounce
   2 s, build the JSON and call `replace`. Also run it on app start and from
   the existing daily background job.
3. Settings → **Show in PLANR** (off by default), with toggles for card dues,
   reminders, auto-posts and people, and a status line from `hello`/`status`.
4. Deep links `xpenc://card/<id>`, `xpenc://card/<id>/pay`,
   `xpenc://reminder/<id>` and `xpenc://reminder/<id>/paid`, opening the
   existing screens.
5. Manifest `<queries><package android:name="com.exosynk.planr"/></queries>`.
6. Tests for occurrence expansion (month-end, Feb 29, `notifyDaysBefore`) and
   for the JSON shape against the schema above.

## FITNS (Flutter, `com.trakvrs.fitns`)

FITNS has `Routines` but **no schedule yet** (no days or time on a routine),
and reminders are only the rest timer (`core/notifications/rest_alerts.dart`).
So FITNS needs a small feature first:

1. New Drift table `RoutineSchedules (id, routineId, daysMask, minuteOfDay, ring BOOL, enabled BOOL)`
   with a "Schedule" section on the routine screen: days, time, and
   "Ring like an alarm".
2. Optional `HabitReminders (id, type water|weighIn|custom, minuteOfDay, daysMask, everyMinutes?)`
   for the water and weigh-in reminders the README promises.
3. `PlanrLinkService` (same Kotlin channel as XPENC). It sends:
   - each scheduled routine as `kind: alarm` (`style: alarm` when `ring`,
     otherwise `reminder`), `group: "FITNS"`, `open: fitns://routine/<id>`,
     `done: fitns://routine/<id>/start`
   - water and weigh-in reminders as `kind: reminder`.
4. When PLANR isn't installed or linked, FITNS falls back to its own local
   notifications, so it still works alone.
5. Deep links `fitns://routine/<id>` and `fitns://routine/<id>/start`.

PLANR extras for FITNS:
- the ring screen shows **Start workout** (the `done` link) as the main button
- a routine trigger "A linked workout starts", so you can for example turn
  on DND and start Focus.

## CONECTR (Flutter, `com.yash.xpenc.conectr`)

### A. Back up all apps
1. PLANR adds **Settings → Backup → Auto-backup to folder**: you pick a SAF
   tree once, and PLANR writes `DDMMYYPLANRBACKUP.json` daily (from the
   accessibility tick or a daily exact alarm), keeping the last 7. FITNS
   does the same with `DDMMYYFITNSBACKUP.json`.
2. CONECTR generalises `BackupFolderService` and `BackupSyncService` from
   "the XPENC folder" to a list of `BackupSource { app, folderUri, filePattern }`.
3. Repo layout: keep `XPENC-BACKUP` for compatibility, and add `planr/` and
   `fitns/` folders in a new `EXOSYNK-BACKUP` repo, or in the same repo.
   Reuse the fast-forward rules and `Connector-*` trailers unchanged.
4. Do client-side encryption before adding more apps. The README already
   flags it; with three apps' data on GitHub it becomes more important.

### B. Friends' shared dues → PLANR
After phases 3–4 of `docs/superpowers/specs/2026-10-24-friends-shared-dues-design.md`,
CONECTR acts as a fourth link source (`source: "conectr"`). It pushes each
open shared due as `kind: due`, `group: "Friends"`, `open: conectr://friend/<id>`.
PLANR stays offline: CONECTR fetches and PLANR only receives.

### C. Status hub
A new CONECTR tab, **ExoSynk apps**:
- XPENC, FITNS and PLANR, each with installed or not, version, and backup
  status
- PLANR link state, from `status`
- a **Link** button that opens PLANR's Connected apps page, or the source
  app's "Show in PLANR" setting.

Needs `<queries>` for all three packages.

---

## Security and privacy checklist

- [ ] Package and certificate allowlist on every call. A debuggable PLANR
      build may skip the certificate check, like CONECTR.
- [ ] Your approval before the first item is stored. Blocking an app wipes its
      items and refuses future calls.
- [ ] Size, count and rate limits; one bad item never drops the snapshot.
- [ ] "Hide amounts in PLANR" per source. Amounts are never shown on the lock
      screen. Linked notifications use `VISIBILITY_PRIVATE`.
- [ ] Linked items are in PLANR's backup only as a re-creatable cache. They
      are left out of the export, because the source re-syncs.
- [ ] Uninstalling a source: PLANR removes its items. On start, check that
      the package still exists; don't rely on broadcasts.
- [ ] PLANR still has **no INTERNET permission**.

## Phases (each ships on its own)

| # | What | Done when |
|---|------|-----------|
| 0 | Stable signing for all four apps; certificate SHA-256s recorded | Two consecutive CI releases update in place on a phone |
| 1 | PLANR: `LinkProvider`, DB v6, Connected apps page, approval notification, read-only linked UI, a debug "Link tester" screen that calls itself with sample JSON | Sample snapshot appears in Today/Reminders/Alarm; re-sending removes deleted items; a wrong certificate is refused |
| 2 | XPENC: card dues + bill reminders → PLANR, deep links | A card's next due shows in PLANR's calendar with a 3-days-before alert; "Mark paid" opens XPENC; after paying it disappears |
| 3 | FITNS: routine schedules + push to PLANR, Start workout deep link | A Mon/Wed/Fri 6:30 workout rings full screen in PLANR and opens FITNS on Start |
| 4 | CONECTR: multi-app backup + status hub | PLANR and FITNS backups round-trip through GitHub; hub shows all link states |
| 5 | CONECTR: friends' shared dues → PLANR | A friend's shared due shows under "Friends" in PLANR |

## Open questions for Yash

1. One ExoSynk signing key for all apps, or one per app? One key is simpler.
   Then a `signature`-level permission becomes possible later, but losing
   the key hits every app.
2. XPENC on F-Droid is signed by F-Droid. Should that build be allowed to
   link? If yes, add F-Droid's certificate for `com.yash.xpenc` to the
   allowlist.
3. Should linked card dues ring **alarm-style** by default, or stay
   notifications?
4. Backup repo: extend `XPENC-BACKUP`, or a new `EXOSYNK-BACKUP`?
