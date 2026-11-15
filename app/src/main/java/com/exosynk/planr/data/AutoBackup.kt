package com.exosynk.planr.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
import kotlinx.coroutines.sync.withLock
import com.exosynk.planr.PlanrApp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Daily backup into a folder the user picked (Storage Access Framework), named
 * like XPENC's: `DDMMYYPLANRBACKUP.json`, so CONECTR can later sync it the same
 * way. Keeps the newest [KEEP] files. Runs at most once a day, from the app and
 * the accessibility service's hourly tick; nothing leaves the phone.
 */
object AutoBackup {

    const val KEEP = 7
    private const val PREFS = "auto_backup"
    private const val K_LAST_DAY = "lastDay"
    private const val K_LAST_ERROR = "lastError"
    private val NAME_FMT = DateTimeFormatter.ofPattern("ddMMyy")
    private val NAME = Regex("^(\\d{6})PLANRBACKUP(?: \\(\\d+\\))?\\.json$")

    fun fileName(day: LocalDate): String = day.format(NAME_FMT) + "PLANRBACKUP.json"

    fun lastBackupDay(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(K_LAST_DAY, null)

    fun lastError(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(K_LAST_ERROR, null)

    private const val K_DEVICE = "device"
    private const val K_DIRTY = "dirty"
    private const val K_LAST_WRITE = "lastWriteAt"
    private const val K_HANDLED_REMOTE = "handledRemoteAt"

    /** Don't rewrite the file more often than this while changes keep coming. */
    private const val MIN_WRITE_GAP_MS = 2 * 60_000L

    /** A newer backup in the folder, written by another phone. */
    data class Remote(val exportedAt: Long, val deviceName: String, val json: String, val localChangesPending: Boolean)

    private val _remote = kotlinx.coroutines.flow.MutableStateFlow<Remote?>(null)
    /** Set while another phone's newer backup is waiting for the user's decision. */
    val remote: kotlinx.coroutines.flow.StateFlow<Remote?> = _remote

    private val mutex = kotlinx.coroutines.sync.Mutex()

    /** Random id for this install, so PLANR can tell its own backups from another phone's. */
    fun deviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(K_DEVICE, null) ?: java.util.UUID.randomUUID().toString().also {
            prefs.edit { putString(K_DEVICE, it) }
        }
    }

    fun lastWriteAt(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(K_LAST_WRITE, 0L)

    /** Something the user saved changed; the next [runIfDue] writes a fresh backup. */
    fun markDirty(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(K_DIRTY, false)) prefs.edit { putBoolean(K_DIRTY, true) }
    }

    /**
     * One sync pass: first look for a newer backup from another phone (and hold our own
     * writes while one is waiting, so we never overwrite it unseen); otherwise write
     * ours if something changed, it's a new day, or [force]. Returns null on success.
     */
    suspend fun runIfDue(context: Context, force: Boolean = false): String? = mutex.withLock {
        val app = PlanrApp.instance
        val folder = app.settings.current.autoBackupFolder ?: return@withLock null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now()
        val tree = Uri.parse(folder)
        val now = System.currentTimeMillis()

        runCatching { findRemote(context, tree, prefs.getLong(K_HANDLED_REMOTE, 0L), prefs.getBoolean(K_DIRTY, false)) }
            .getOrNull()?.let { _remote.value = it }
        if (_remote.value != null) return@withLock null // wait for Restore / Keep mine

        val dirty = prefs.getBoolean(K_DIRTY, false)
        val newDay = prefs.getString(K_LAST_DAY, null) != today.toString()
        val due = newDay || (dirty && now - prefs.getLong(K_LAST_WRITE, 0L) >= MIN_WRITE_GAP_MS)
        if (!force && !due) return@withLock null

        val error = runCatching {
            val json = BackupManager.export(app)
            write(context, tree, fileName(today), json.toByteArray())
            prune(context, tree)
        }.exceptionOrNull()?.let { it.message ?: it::class.java.simpleName }
        prefs.edit {
            if (error == null) {
                putString(K_LAST_DAY, today.toString())
                putLong(K_LAST_WRITE, System.currentTimeMillis())
                putBoolean(K_DIRTY, false)
                remove(K_LAST_ERROR)
            } else {
                putString(K_LAST_ERROR, error)
            }
        }
        error
    }

    /** Take the other phone's backup: replaces this phone's data. Returns an error message or null. */
    suspend fun acceptRemote(context: Context): String? {
        val r = _remote.value ?: return null
        val error = runCatching { BackupManager.restore(PlanrApp.instance, r.json) }.exceptionOrNull()?.message
        if (error == null) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                putLong(K_HANDLED_REMOTE, r.exportedAt)
                putBoolean(K_DIRTY, false)
            }
            _remote.value = null
        }
        return error
    }

    /** Keep this phone's data: ignore that backup and write ours over it. */
    suspend fun keepMine(context: Context) {
        val r = _remote.value ?: return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putLong(K_HANDLED_REMOTE, r.exportedAt) }
        _remote.value = null
        runIfDue(context, force = true)
    }

    private fun findRemote(context: Context, tree: Uri, handledAt: Long, dirty: Boolean): Remote? {
        val mine = deviceId(context)
        val newest = children(context, tree).mapNotNull { c ->
            val m = NAME.matchEntire(c.name) ?: return@mapNotNull null
            runCatching { LocalDate.parse(m.groupValues[1], NAME_FMT) }.getOrNull()?.let { it to c }
        }.sortedByDescending { it.first }.take(2)
        var best: Remote? = null
        for ((_, c) in newest) {
            val uri = DocumentsContract.buildDocumentUriUsingTree(tree, c.documentId)
            val json = context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                if (bytes.size > 4 * 1024 * 1024) null else String(bytes)
            } ?: continue
            val h = Backup.header(json) ?: continue
            if (isRemoteNewer(h, mine, handledAt, lastWriteAt(context)) && h.exportedAt > (best?.exportedAt ?: 0L)) {
                best = Remote(h.exportedAt, h.deviceName.ifBlank { "another phone" }, json, dirty)
            }
        }
        return best
    }

    /** Pure rule (unit-tested): another device's backup we haven't handled and that is newer than our last write. */
    fun isRemoteNewer(h: Backup.Header, myDevice: String, handledAt: Long, myLastWriteAt: Long): Boolean =
        h.device.isNotEmpty() && h.device != myDevice && h.exportedAt > handledAt && h.exportedAt > myLastWriteAt

    private data class Child(val documentId: String, val name: String)

    private fun children(context: Context, tree: Uri): List<Child> {
        val parentId = DocumentsContract.getTreeDocumentId(tree)
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val out = mutableListOf<Child>()
        context.contentResolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { c -> while (c.moveToNext()) out += Child(c.getString(0), c.getString(1) ?: "") }
        return out
    }

    private fun write(context: Context, tree: Uri, name: String, bytes: ByteArray) {
        val existing = children(context, tree).firstOrNull { it.name == name }
        val target = if (existing != null) {
            DocumentsContract.buildDocumentUriUsingTree(tree, existing.documentId)
        } else {
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            DocumentsContract.createDocument(context.contentResolver, parent, "application/json", name)
                ?: error("Couldn't create $name in the backup folder")
        }
        context.contentResolver.openOutputStream(target, "wt")?.use { it.write(bytes) }
            ?: error("Couldn't write $name")
    }

    /** Deletes all but the newest [KEEP] PLANR backups (other files are never touched). */
    private fun prune(context: Context, tree: Uri) {
        val backups = children(context, tree).mapNotNull { c ->
            val m = NAME.matchEntire(c.name) ?: return@mapNotNull null
            val day = runCatching { LocalDate.parse(m.groupValues[1], NAME_FMT) }.getOrNull() ?: return@mapNotNull null
            day to c
        }.sortedByDescending { it.first }
        backups.drop(KEEP).forEach { (_, c) ->
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, c.documentId)) }
        }
    }

    /** Pure: which names to delete, newest-first retention (unit-tested). */
    fun namesToPrune(names: List<String>, keep: Int = KEEP): List<String> =
        names.mapNotNull { n ->
            val m = NAME.matchEntire(n) ?: return@mapNotNull null
            runCatching { LocalDate.parse(m.groupValues[1], NAME_FMT) }.getOrNull()?.let { it to n }
        }.sortedByDescending { it.first }.drop(keep).map { it.second }
}
