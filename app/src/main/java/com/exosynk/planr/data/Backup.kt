package com.exosynk.planr.data

import com.exosynk.planr.data.db.OffGridSession
import com.exosynk.planr.data.db.Schedule
import com.exosynk.planr.data.db.ScheduleMode
import com.exosynk.planr.data.db.ShieldRule
import com.exosynk.planr.alarm.Alarm
import com.exosynk.planr.alarm.AlarmGroup
import com.exosynk.planr.notes.Note
import com.exosynk.planr.reminder.Reminder
import com.exosynk.planr.reminder.ReminderCategory
import com.exosynk.planr.routine.Routine
import com.exosynk.planr.routine.RoutineCodec
import org.json.JSONArray
import org.json.JSONObject

/** Planner modules (v0.4+). Absent in v1 backups, which then leave them untouched on restore. */
data class PlannerData(
    val alarmGroups: List<AlarmGroup>,
    val alarms: List<Alarm>,
    val categories: List<ReminderCategory>,
    val reminders: List<Reminder>,
    val notes: List<Note>,
    val routines: List<Routine>,
)

/** Everything a backup file holds. Usage history stays on the device. */
data class BackupData(
    val shields: List<ShieldRule>,
    val schedules: List<Schedule>,
    val settings: PlanrSettings,
    val progress: ProgressState,
    val sessions: List<OffGridSession>,
    val planner: PlannerData? = null,
)

class BackupFormatException(message: String) : Exception(message)

/** JSON encoding for backup files. Unknown fields are ignored and values are clamped on import. */
object Backup {

    const val FORMAT = "planr-backup"
    const val VERSION = 2

    fun encode(data: BackupData, exportedAt: Long, appVersion: String): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", VERSION)
        root.put("appVersion", appVersion)
        root.put("exportedAt", exportedAt)

        root.put("shields", JSONArray().apply {
            data.shields.forEach { r ->
                put(JSONObject().apply {
                    put("packageName", r.packageName)
                    put("appLabel", r.appLabel)
                    put("pauseSeconds", r.pauseSeconds)
                    put("delaySeconds", r.delaySeconds)
                    put("dailyLimitMinutes", r.dailyLimitMinutes)
                    put("passMinutes", r.passMinutes)
                    put("enabled", r.enabled)
                    put("dailyOpenLimit", r.dailyOpenLimit)
                })
            }
        })

        root.put("schedules", JSONArray().apply {
            data.schedules.forEach { s ->
                put(JSONObject().apply {
                    put("name", s.name)
                    put("startMinute", s.startMinute)
                    put("endMinute", s.endMinute)
                    put("daysMask", s.daysMask)
                    put("mode", s.mode.name)
                    put("packages", s.packages)
                    put("enabled", s.enabled)
                    put("challenge", s.challenge)
                })
            }
        })

        val st = data.settings
        root.put("settings", JSONObject().apply {
            put("gatewayEnabled", st.gatewayEnabled)
            put("gatewayWhitelist", JSONArray(st.gatewayWhitelist.sorted()))
            put("gatewayPauseSeconds", st.gatewayPauseSeconds)
            put("gatewayPassMinutes", st.gatewayPassMinutes)
            put("emergencyQuota", st.emergencyQuota)
            put("emergencyMinutes", st.emergencyMinutes)
            put("lockdownEnabled", st.lockdownEnabled)
            put("lockdownStart", st.lockdownStart)
            put("lockdownEnd", st.lockdownEnd)
            put("lockdownDays", st.lockdownDays)
            put("lockdownBlocksSettings", st.lockdownBlocksSettings)
            put("windDownEnabled", st.windDownEnabled)
            put("windDownStart", st.windDownStart)
            put("windDownEnd", st.windDownEnd)
            put("windDownDnd", st.windDownDnd)
            put("eyeCareEnabled", st.eyeCareEnabled)
        })

        val p = data.progress.stats
        root.put("progress", JSONObject().apply {
            put("xp", p.xp)
            put("offGridCompleted", p.offGridCompleted)
            put("offGridMinutes", p.offGridMinutes)
            put("longestOffGridMinutes", p.longestOffGridMinutes)
            put("scheduledOffGridCompleted", p.scheduledOffGridCompleted)
            put("currentStreak", p.currentStreak)
            put("bestStreak", p.bestStreak)
            put("focusBlocks", p.focusBlocks)
            put("focusMinutes", p.focusMinutes)
            put("resists", p.resists)
            put("remindersCompleted", p.remindersCompleted)
            put("wakeUpsNoSnooze", p.wakeUpsNoSnooze)
            put("achievements", JSONObject().apply {
                data.progress.unlocked.forEach { (k, v) -> put(k, v) }
            })
        })

        root.put("offGridSessions", JSONArray().apply {
            data.sessions.forEach { s ->
                put(JSONObject().apply {
                    put("startedAt", s.startedAt)
                    put("plannedEndAt", s.plannedEndAt)
                    s.endedAt?.let { put("endedAt", it) }
                    put("endedEarly", s.endedEarly)
                    put("scheduled", s.scheduleId != null)
                })
            }
        })
        data.planner?.let { encodePlanner(root, it) }
        return root.toString(2)
    }

    private fun encodePlanner(root: JSONObject, p: PlannerData) {
        root.put("alarmGroups", JSONArray().apply {
            p.alarmGroups.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("enabled", it.enabled)) }
        })
        root.put("alarms", JSONArray().apply {
            p.alarms.forEach { a ->
                put(JSONObject().apply {
                    put("id", a.id); put("label", a.label); put("hour", a.hour); put("minute", a.minute)
                    put("daysMask", a.daysMask); a.date?.let { put("date", it) }; put("enabled", a.enabled)
                    a.groupId?.let { put("groupId", it) }; a.sound?.let { put("sound", it) }
                    put("volume", a.volume); put("gradualVolume", a.gradualVolume); put("vibrate", a.vibrate)
                    put("vibrationPattern", a.vibrationPattern); put("snoozeEnabled", a.snoozeEnabled)
                    put("snoozeMinutes", a.snoozeMinutes); put("snoozeRepeat", a.snoozeRepeat)
                    put("readAloud", a.readAloud); put("dismissChallenge", a.dismissChallenge)
                    put("morningBrief", a.morningBrief)
                })
            }
        })
        root.put("reminderCategories", JSONArray().apply {
            p.categories.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("color", it.color)) }
        })
        root.put("reminders", JSONArray().apply {
            p.reminders.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id); put("title", r.title); put("notes", r.notes)
                    r.dueAt?.let { put("dueAt", it) }; put("allDay", r.allDay); put("repeat", r.repeat)
                    put("earlyMinutes", r.earlyMinutes); r.categoryId?.let { put("categoryId", it) }
                    put("pinned", r.pinned); put("alertStyle", r.alertStyle); put("completed", r.completed)
                    r.completedAt?.let { put("completedAt", it) }; r.noteId?.let { put("noteId", it) }
                    put("createdAt", r.createdAt)
                })
            }
        })
        root.put("notes", JSONArray().apply {
            p.notes.forEach { n ->
                put(JSONObject().apply {
                    put("id", n.id); put("title", n.title); put("body", n.body); put("checklist", n.checklist)
                    put("isChecklist", n.isChecklist); put("color", n.color); put("pinned", n.pinned)
                    put("archived", n.archived); put("createdAt", n.createdAt); put("updatedAt", n.updatedAt)
                })
            }
        })
        root.put("routines", JSONArray().apply {
            p.routines.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id); put("name", r.name); put("enabled", r.enabled)
                    put("trigger", JSONObject(r.trigger)); put("actions", JSONArray(r.actions)); put("undoOnEnd", r.undoOnEnd)
                })
            }
        })
    }

    private fun decodePlanner(root: JSONObject): PlannerData? {
        if (!root.has("alarms") && !root.has("reminders") && !root.has("notes") && !root.has("routines")) return null
        val groups = root.optJSONArray("alarmGroups").objects().mapNotNull { o ->
            val id = o.optLong("id", 0L).takeIf { it > 0 } ?: return@mapNotNull null
            AlarmGroup(id, o.optString("name", "Group").take(30).ifBlank { "Group" }, o.optBoolean("enabled", true))
        }.distinctBy { it.id }
        val groupIds = groups.map { it.id }.toSet()
        val alarms = root.optJSONArray("alarms").objects().mapNotNull { o ->
            val id = o.optLong("id", 0L).takeIf { it > 0 } ?: return@mapNotNull null
            val d = Alarm()
            Alarm(
                id = id,
                label = o.optString("label", "").take(40),
                hour = o.optInt("hour", 7).coerceIn(0, 23),
                minute = o.optInt("minute", 0).coerceIn(0, 59),
                daysMask = o.optInt("daysMask", 0) and 0b1111111,
                date = o.optString("date", "").takeIf { it.isNotBlank() && runCatching { java.time.LocalDate.parse(it) }.isSuccess },
                enabled = o.optBoolean("enabled", true),
                groupId = if (o.has("groupId")) o.optLong("groupId").takeIf { it in groupIds } else null,
                sound = o.optString("sound", "").takeIf { it.isNotBlank() },
                volume = o.optInt("volume", d.volume).coerceIn(5, 100),
                gradualVolume = o.optBoolean("gradualVolume", d.gradualVolume),
                vibrate = o.optBoolean("vibrate", d.vibrate),
                vibrationPattern = o.optString("vibrationPattern", d.vibrationPattern).let { v ->
                    if (com.exosynk.planr.alarm.VibrationPattern.entries.any { it.name == v }) v else d.vibrationPattern
                },
                snoozeEnabled = o.optBoolean("snoozeEnabled", d.snoozeEnabled),
                snoozeMinutes = o.optInt("snoozeMinutes", d.snoozeMinutes).coerceIn(1, 60),
                snoozeRepeat = o.optInt("snoozeRepeat", d.snoozeRepeat).coerceIn(0, 10),
                readAloud = o.optBoolean("readAloud", false),
                dismissChallenge = o.optString("dismissChallenge", d.dismissChallenge).let { v ->
                    if (com.exosynk.planr.alarm.DismissChallenge.entries.any { it.name == v }) v else d.dismissChallenge
                },
                morningBrief = o.optBoolean("morningBrief", false),
            )
        }.distinctBy { it.id }
        val categories = root.optJSONArray("reminderCategories").objects().mapNotNull { o ->
            val id = o.optLong("id", 0L).takeIf { it > 0 } ?: return@mapNotNull null
            ReminderCategory(id, o.optString("name", "List").take(24).ifBlank { "List" }, o.optInt("color", 0xFF7C9CFF.toInt()))
        }.distinctBy { it.id }
        val categoryIds = categories.map { it.id }.toSet()
        val notes = root.optJSONArray("notes").objects().mapNotNull { o ->
            val id = o.optLong("id", 0L).takeIf { it > 0 } ?: return@mapNotNull null
            Note(
                id = id,
                title = o.optString("title", "").take(120),
                body = o.optString("body", "").take(20_000),
                checklist = o.optString("checklist", ""),
                isChecklist = o.optBoolean("isChecklist", false),
                color = o.optInt("color", 0).coerceIn(0, 7),
                pinned = o.optBoolean("pinned", false),
                archived = o.optBoolean("archived", false),
                createdAt = o.optLong("createdAt", 0L),
                updatedAt = o.optLong("updatedAt", 0L),
            )
        }.distinctBy { it.id }
        val noteIds = notes.map { it.id }.toSet()
        val reminders = root.optJSONArray("reminders").objects().mapNotNull { o ->
            val id = o.optLong("id", 0L).takeIf { it > 0 } ?: return@mapNotNull null
            val title = o.optString("title", "").take(120).ifBlank { return@mapNotNull null }
            Reminder(
                id = id,
                title = title,
                notes = o.optString("notes", "").take(1000),
                dueAt = if (o.has("dueAt")) o.optLong("dueAt") else null,
                allDay = o.optBoolean("allDay", false),
                repeat = o.optString("repeat", "NONE").let { v ->
                    if (com.exosynk.planr.reminder.Repeat.entries.any { it.name == v }) v else "NONE"
                },
                earlyMinutes = o.optInt("earlyMinutes", 0).coerceIn(0, 7 * 24 * 60),
                categoryId = if (o.has("categoryId")) o.optLong("categoryId").takeIf { it in categoryIds } else null,
                pinned = o.optBoolean("pinned", false),
                alertStyle = o.optString("alertStyle", "NOTIFICATION").let { v ->
                    if (com.exosynk.planr.reminder.AlertStyle.entries.any { it.name == v }) v else "NOTIFICATION"
                },
                completed = o.optBoolean("completed", false),
                completedAt = if (o.has("completedAt")) o.optLong("completedAt") else null,
                noteId = if (o.has("noteId")) o.optLong("noteId").takeIf { it in noteIds } else null,
                createdAt = o.optLong("createdAt", 0L),
            )
        }.distinctBy { it.id }
        val routines = root.optJSONArray("routines").objects().mapNotNull { o ->
            val id = o.optLong("id", 0L).takeIf { it > 0 } ?: return@mapNotNull null
            val trigger = o.optJSONObject("trigger")?.toString()?.let { RoutineCodec.decodeTrigger(it) } ?: return@mapNotNull null
            val actions = RoutineCodec.decodeActions(o.optJSONArray("actions")?.toString() ?: "[]")
            Routine.of(o.optString("name", "Routine").take(40).ifBlank { "Routine" }, trigger, actions,
                o.optBoolean("undoOnEnd", true), id = id, enabled = o.optBoolean("enabled", true))
        }.distinctBy { it.id }
        return PlannerData(groups, alarms, categories, reminders, notes, routines)
    }

    /** Parses a backup file. Throws [BackupFormatException] if it isn't one. */
    fun decode(json: String): BackupData {
        val root = runCatching { JSONObject(json) }.getOrElse {
            throw BackupFormatException("This file isn't valid JSON.")
        }
        if (root.optString("format") != FORMAT) {
            throw BackupFormatException("This isn't a PLANR backup file.")
        }
        if (root.optInt("version", 0) > VERSION) {
            throw BackupFormatException("This backup is from a newer PLANR. Update the app first.")
        }

        val shields = root.optJSONArray("shields").objects().mapNotNull { o ->
            val pkg = o.optString("packageName").trim()
            if (pkg.isEmpty()) return@mapNotNull null
            ShieldRule(
                packageName = pkg,
                appLabel = o.optString("appLabel", pkg).ifBlank { pkg },
                pauseSeconds = o.optInt("pauseSeconds", 5).coerceIn(3, 30),
                delaySeconds = o.optInt("delaySeconds", 0).coerceIn(0, 60),
                dailyLimitMinutes = o.optInt("dailyLimitMinutes", 0).coerceIn(0, 24 * 60),
                passMinutes = o.optInt("passMinutes", 5).coerceIn(1, 60),
                enabled = o.optBoolean("enabled", true),
                dailyOpenLimit = o.optInt("dailyOpenLimit", 0).coerceIn(0, 100),
            )
        }.distinctBy { it.packageName }

        val schedules = root.optJSONArray("schedules").objects().map { o ->
            Schedule(
                name = o.optString("name", "Schedule").take(30).ifBlank { "Schedule" },
                startMinute = o.optInt("startMinute", 9 * 60).coerceIn(0, 1439),
                endMinute = o.optInt("endMinute", 17 * 60).coerceIn(0, 1439),
                daysMask = (o.optInt("daysMask", 0b1111111) and 0b1111111).takeIf { it != 0 } ?: 0b1111111,
                mode = runCatching { ScheduleMode.valueOf(o.optString("mode")) }.getOrDefault(ScheduleMode.BLOCK),
                packages = o.optString("packages", ""),
                enabled = o.optBoolean("enabled", true),
                challenge = runCatching { UnlockChallenge.valueOf(o.optString("challenge")).name }
                    .getOrDefault(UnlockChallenge.TYPING.name),
            )
        }

        val d = PlanrSettings()
        val so = root.optJSONObject("settings") ?: JSONObject()
        val settings = PlanrSettings(
            gatewayEnabled = so.optBoolean("gatewayEnabled", d.gatewayEnabled),
            gatewayWhitelist = so.optJSONArray("gatewayWhitelist").strings().toSet(),
            gatewayPauseSeconds = so.optInt("gatewayPauseSeconds", d.gatewayPauseSeconds),
            gatewayPassMinutes = so.optInt("gatewayPassMinutes", d.gatewayPassMinutes),
            emergencyQuota = so.optInt("emergencyQuota", d.emergencyQuota),
            emergencyMinutes = so.optInt("emergencyMinutes", d.emergencyMinutes),
            lockdownEnabled = so.optBoolean("lockdownEnabled", d.lockdownEnabled),
            lockdownStart = so.optInt("lockdownStart", d.lockdownStart),
            lockdownEnd = so.optInt("lockdownEnd", d.lockdownEnd),
            lockdownDays = so.optInt("lockdownDays", d.lockdownDays),
            lockdownBlocksSettings = so.optBoolean("lockdownBlocksSettings", d.lockdownBlocksSettings),
            windDownEnabled = so.optBoolean("windDownEnabled", d.windDownEnabled),
            windDownStart = so.optInt("windDownStart", d.windDownStart),
            windDownEnd = so.optInt("windDownEnd", d.windDownEnd),
            windDownDnd = so.optBoolean("windDownDnd", d.windDownDnd),
            eyeCareEnabled = so.optBoolean("eyeCareEnabled", d.eyeCareEnabled),
        ).sanitized()

        val po = root.optJSONObject("progress") ?: JSONObject()
        fun n(key: String) = po.optInt(key, 0).coerceAtLeast(0)
        val stats = ProgressStats(
            xp = n("xp"),
            offGridCompleted = n("offGridCompleted"),
            offGridMinutes = n("offGridMinutes"),
            longestOffGridMinutes = n("longestOffGridMinutes"),
            scheduledOffGridCompleted = n("scheduledOffGridCompleted"),
            currentStreak = n("currentStreak"),
            bestStreak = n("bestStreak"),
            focusBlocks = n("focusBlocks"),
            focusMinutes = n("focusMinutes"),
            resists = n("resists"),
            remindersCompleted = n("remindersCompleted"),
            wakeUpsNoSnooze = n("wakeUpsNoSnooze"),
        )
        val ach = po.optJSONObject("achievements")
        val unlocked = buildMap {
            ach?.keys()?.forEach { k ->
                if (Achievement.entries.any { it.name == k }) put(k, ach.optLong(k, 0L))
            }
        }

        val sessions = root.optJSONArray("offGridSessions").objects().mapNotNull { o ->
            val start = o.optLong("startedAt", 0L)
            val planned = o.optLong("plannedEndAt", 0L)
            if (start <= 0L || planned < start) return@mapNotNull null
            OffGridSession(
                startedAt = start,
                plannedEndAt = planned,
                endedAt = if (o.has("endedAt")) o.optLong("endedAt") else planned,
                endedEarly = o.optBoolean("endedEarly", false),
                // The original schedule id has no meaning on another install; keep only the fact.
                scheduleId = if (o.optBoolean("scheduled", false)) -1L else null,
            )
        }

        return BackupData(shields, schedules, settings, ProgressState(stats, unlocked), sessions, decodePlanner(root))
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }
}

/** Reads and writes backup files against the live stores. */
object BackupManager {

    suspend fun export(app: com.exosynk.planr.PlanrApp): String {
        val data = BackupData(
            shields = app.db.shieldDao().getAll(),
            schedules = app.db.scheduleDao().getAll(),
            settings = app.settings.current,
            progress = app.progress.state.value,
            sessions = app.db.offGridDao().getAll(),
            planner = PlannerData(
                alarmGroups = app.db.alarmGroupDao().getAll(),
                alarms = app.db.alarmDao().getAll(),
                categories = app.db.reminderCategoryDao().getAll(),
                reminders = app.db.reminderDao().getAll(),
                notes = app.db.noteDao().getAll(),
                routines = app.db.routineDao().getAll(),
            ),
        )
        val version = runCatching {
            app.packageManager.getPackageInfo(app.packageName, 0).versionName
        }.getOrNull() ?: ""
        return Backup.encode(data, System.currentTimeMillis(), version)
    }

    /**
     * Replaces rules, settings and progress with the backup's. Refused during
     * Lockdown or Off-Grid, since a backup could loosen the rules.
     */
    suspend fun restore(app: com.exosynk.planr.PlanrApp, json: String): BackupData {
        if (app.settings.isLockdown()) throw BackupFormatException("Restore isn't available during Lockdown.")
        if (app.offGrid.isLocked()) throw BackupFormatException("Restore isn't available during Off-Grid.")
        val data = Backup.decode(json)
        app.db.shieldDao().replaceAll(data.shields)
        app.db.scheduleDao().replaceAll(data.schedules)
        app.db.offGridDao().replaceAll(data.sessions)
        app.settings.update(data.settings, force = true)
        app.progress.restore(data.progress)
        data.planner?.let { p ->
            // Cancel what's registered for the alarms/reminders being replaced, then register the new set.
            app.db.alarmDao().getAll().forEach { com.exosynk.planr.alarm.AlarmScheduler.cancelAll(app, it.id) }
            app.db.reminderDao().getAll().forEach { com.exosynk.planr.reminder.Reminders.cancel(app, it.id) }
            app.db.routineDao().getAll().forEach { com.exosynk.planr.routine.RoutineScheduler.cancel(app, it.id) }
            app.db.alarmGroupDao().replaceAll(p.alarmGroups)
            app.db.alarmDao().replaceAll(p.alarms)
            app.db.reminderCategoryDao().replaceAll(p.categories)
            app.db.noteDao().replaceAll(p.notes)
            app.db.reminderDao().replaceAll(p.reminders)
            app.db.routineDao().replaceAll(p.routines)
            com.exosynk.planr.PlanrApp.rescheduleEverything(app)
        }
        return data
    }
}
