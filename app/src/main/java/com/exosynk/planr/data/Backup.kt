package com.exosynk.planr.data

import com.exosynk.planr.data.db.OffGridSession
import com.exosynk.planr.data.db.Schedule
import com.exosynk.planr.data.db.ScheduleMode
import com.exosynk.planr.data.db.ShieldRule
import org.json.JSONArray
import org.json.JSONObject

/** Everything a backup file holds. Usage history stays on the device. */
data class BackupData(
    val shields: List<ShieldRule>,
    val schedules: List<Schedule>,
    val settings: PlanrSettings,
    val progress: ProgressState,
    val sessions: List<OffGridSession>,
)

class BackupFormatException(message: String) : Exception(message)

/** JSON encoding for backup files. Unknown fields are ignored and values are clamped on import. */
object Backup {

    const val FORMAT = "planr-backup"
    const val VERSION = 1

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
        return root.toString(2)
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

        return BackupData(shields, schedules, settings, ProgressState(stats, unlocked), sessions)
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
        return data
    }
}
