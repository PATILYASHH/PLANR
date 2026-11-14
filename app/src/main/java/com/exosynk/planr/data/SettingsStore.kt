package com.exosynk.planr.data

import android.content.Context
import androidx.core.content.edit
import com.exosynk.planr.data.db.Schedule
import com.exosynk.planr.data.db.isActiveAt
import com.exosynk.planr.data.db.windowEnd
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime

enum class ThemeMode(val title: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

data class PlanrSettings(
    // Appearance
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Material You: colours from the wallpaper (Android 12+). */
    val dynamicColor: Boolean = false,

    /** Show the phone's calendar events on Today (needs READ_CALENDAR). */
    val deviceCalendar: Boolean = false,

    // Daily auto-backup to a folder the user picked (SAF tree URI; null = off)
    val autoBackupFolder: String? = null,

    // Mindful Gateway: pause before every app that isn't whitelisted
    val gatewayEnabled: Boolean = false,
    val gatewayWhitelist: Set<String> = emptySet(),
    val gatewayPauseSeconds: Int = 5,
    val gatewayPassMinutes: Int = 10,

    // Emergency passes: limited daily overrides for blocked apps
    val emergencyQuota: Int = 2,
    val emergencyMinutes: Int = 5,

    // Lockdown: rules can't be loosened during these hours
    val lockdownEnabled: Boolean = false,
    val lockdownStart: Int = 9 * 60,
    val lockdownEnd: Int = 17 * 60,
    val lockdownDays: Int = 0b0011111,
    val lockdownBlocksSettings: Boolean = false,

    // Wind Down: reminder at bedtime, optional Do-Not-Disturb until morning
    val windDownEnabled: Boolean = false,
    val windDownStart: Int = 22 * 60 + 30,
    val windDownEnd: Int = 7 * 60,
    val windDownDnd: Boolean = false,

    // Eye Care 20-20-20
    val eyeCareEnabled: Boolean = false,
) {
    /** Lockdown expressed as a schedule so it shares the tested window logic. */
    val lockdownWindow: Schedule
        get() = Schedule(
            name = "Lockdown",
            startMinute = lockdownStart,
            endMinute = lockdownEnd,
            daysMask = lockdownDays,
            enabled = lockdownEnabled,
        )

    val windDownWindow: Schedule
        get() = Schedule(
            name = "Wind Down",
            startMinute = windDownStart,
            endMinute = windDownEnd,
            enabled = windDownEnabled,
        )

    fun isLockdown(now: LocalDateTime = LocalDateTime.now()): Boolean =
        lockdownEnabled && lockdownStart != lockdownEnd && lockdownWindow.isActiveAt(now)

    /** End of the running lockdown window, or null if none is active. */
    fun lockdownEndsAt(now: LocalDateTime = LocalDateTime.now()): LocalDateTime? =
        if (isLockdown(now)) lockdownWindow.windowEnd(now) else null

    /** Normalizes values from the UI or a backup file into safe ranges. */
    fun sanitized(): PlanrSettings = copy(
        gatewayPauseSeconds = gatewayPauseSeconds.coerceIn(3, 30),
        gatewayPassMinutes = gatewayPassMinutes.coerceIn(1, 60),
        emergencyQuota = emergencyQuota.coerceIn(0, MAX_EMERGENCY_QUOTA),
        emergencyMinutes = emergencyMinutes.coerceIn(1, 15),
        lockdownStart = lockdownStart.coerceIn(0, 1439),
        lockdownEnd = lockdownEnd.coerceIn(0, 1439),
        lockdownDays = lockdownDays and 0b1111111,
        windDownStart = windDownStart.coerceIn(0, 1439),
        windDownEnd = windDownEnd.coerceIn(0, 1439),
    )

    /**
     * Changes allowed while Lockdown is active: anything that makes PLANR stricter
     * (or doesn't affect blocking). Loosening rules has to wait until it ends.
     */
    fun isAllowedDuringLockdown(next: PlanrSettings): Boolean =
        next.lockdownEnabled == lockdownEnabled &&
            next.lockdownStart == lockdownStart &&
            next.lockdownEnd == lockdownEnd &&
            next.lockdownDays == lockdownDays &&
            (next.lockdownBlocksSettings || !lockdownBlocksSettings) &&
            (next.gatewayEnabled || !gatewayEnabled) &&
            (next.gatewayWhitelist - gatewayWhitelist).isEmpty() &&
            next.gatewayPauseSeconds >= gatewayPauseSeconds &&
            next.gatewayPassMinutes <= gatewayPassMinutes &&
            next.emergencyQuota <= emergencyQuota &&
            next.emergencyMinutes <= emergencyMinutes

    companion object {
        const val MAX_EMERGENCY_QUOTA = 5
    }
}

class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<PlanrSettings> = _state.asStateFlow()

    val current: PlanrSettings get() = _state.value

    fun isLockdown(): Boolean = current.isLockdown()

    /**
     * Saves [next] unless it would loosen rules during Lockdown.
     * Returns false (and changes nothing) in that case.
     */
    fun update(next: PlanrSettings, force: Boolean = false): Boolean {
        val clean = next.sanitized()
        val cur = current
        if (!force && cur.isLockdown() && !cur.isAllowedDuringLockdown(clean)) return false
        write(clean)
        return true
    }

    inline fun edit(transform: (PlanrSettings) -> PlanrSettings): Boolean = update(transform(current))

    private fun write(s: PlanrSettings) {
        prefs.edit(commit = true) {
            putBoolean("gw", s.gatewayEnabled)
            putStringSet("gwList", s.gatewayWhitelist)
            putInt("gwPause", s.gatewayPauseSeconds)
            putInt("gwPass", s.gatewayPassMinutes)
            putInt("emQuota", s.emergencyQuota)
            putInt("emMin", s.emergencyMinutes)
            putBoolean("ld", s.lockdownEnabled)
            putInt("ldStart", s.lockdownStart)
            putInt("ldEnd", s.lockdownEnd)
            putInt("ldDays", s.lockdownDays)
            putBoolean("ldSettings", s.lockdownBlocksSettings)
            putBoolean("wd", s.windDownEnabled)
            putInt("wdStart", s.windDownStart)
            putInt("wdEnd", s.windDownEnd)
            putBoolean("wdDnd", s.windDownDnd)
            putBoolean("eye", s.eyeCareEnabled)
            putString("theme", s.themeMode.name)
            putBoolean("dynamic", s.dynamicColor)
            putString("backupFolder", s.autoBackupFolder)
            putBoolean("deviceCal", s.deviceCalendar)
        }
        _state.value = s
    }

    private fun read(): PlanrSettings {
        val d = PlanrSettings()
        return PlanrSettings(
            gatewayEnabled = prefs.getBoolean("gw", d.gatewayEnabled),
            gatewayWhitelist = prefs.getStringSet("gwList", null)?.toSet() ?: d.gatewayWhitelist,
            gatewayPauseSeconds = prefs.getInt("gwPause", d.gatewayPauseSeconds),
            gatewayPassMinutes = prefs.getInt("gwPass", d.gatewayPassMinutes),
            emergencyQuota = prefs.getInt("emQuota", d.emergencyQuota),
            emergencyMinutes = prefs.getInt("emMin", d.emergencyMinutes),
            lockdownEnabled = prefs.getBoolean("ld", d.lockdownEnabled),
            lockdownStart = prefs.getInt("ldStart", d.lockdownStart),
            lockdownEnd = prefs.getInt("ldEnd", d.lockdownEnd),
            lockdownDays = prefs.getInt("ldDays", d.lockdownDays),
            lockdownBlocksSettings = prefs.getBoolean("ldSettings", d.lockdownBlocksSettings),
            windDownEnabled = prefs.getBoolean("wd", d.windDownEnabled),
            windDownStart = prefs.getInt("wdStart", d.windDownStart),
            windDownEnd = prefs.getInt("wdEnd", d.windDownEnd),
            windDownDnd = prefs.getBoolean("wdDnd", d.windDownDnd),
            eyeCareEnabled = prefs.getBoolean("eye", d.eyeCareEnabled),
            themeMode = runCatching { ThemeMode.valueOf(prefs.getString("theme", null) ?: "") }.getOrDefault(d.themeMode),
            dynamicColor = prefs.getBoolean("dynamic", d.dynamicColor),
            autoBackupFolder = prefs.getString("backupFolder", null),
            deviceCalendar = prefs.getBoolean("deviceCal", d.deviceCalendar),
        )
    }
}
