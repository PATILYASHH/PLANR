package com.exosynk.planr.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
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

    /** Writes today's backup if it isn't done yet (or [force]). Returns null on success, else an error. */
    suspend fun runIfDue(context: Context, force: Boolean = false): String? {
        val app = PlanrApp.instance
        val folder = app.settings.current.autoBackupFolder ?: return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now()
        if (!force && prefs.getString(K_LAST_DAY, null) == today.toString()) return null
        val error = runCatching {
            val tree = Uri.parse(folder)
            val json = BackupManager.export(app)
            write(context, tree, fileName(today), json.toByteArray())
            prune(context, tree)
        }.exceptionOrNull()?.let { it.message ?: it::class.java.simpleName }
        prefs.edit {
            if (error == null) {
                putString(K_LAST_DAY, today.toString())
                remove(K_LAST_ERROR)
            } else {
                putString(K_LAST_ERROR, error)
            }
        }
        return error
    }

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
