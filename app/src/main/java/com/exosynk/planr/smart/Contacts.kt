package com.exosynk.planr.smart

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

data class ContactMatch(val name: String, val number: String, val label: String)

/**
 * Looks up contacts by name, on the device only (PLANR has no internet permission).
 * Used to turn "call parth" into a real contact with a number.
 */
object Contacts {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Contacts whose name matches [query] (prefix of any word ranks first), one row per phone number. */
    fun search(context: Context, query: String, limit: Int = 8): List<ContactMatch> {
        val q = query.trim()
        if (q.length < 2 || !hasPermission(context)) return emptyList()
        val uri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(q))
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL,
        )
        val out = LinkedHashMap<String, ContactMatch>()
        runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                while (c.moveToNext() && out.size < limit * 2) {
                    val name = c.getString(0) ?: continue
                    val number = c.getString(1) ?: continue
                    val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                        context.resources, c.getInt(2), c.getString(3) ?: "",
                    ).toString()
                    val key = name + "|" + number.filter { it.isDigit() }.takeLast(10)
                    out.putIfAbsent(key, ContactMatch(name, number, label))
                }
            }
        }
        return out.values.sortedWith(compareBy({ rank(it.name, q) }, { it.name.length })).take(limit)
    }

    /** 0 = exact name, 1 = a word starts with the query, 2 = anything else. */
    internal fun rank(name: String, query: String): Int {
        val n = name.lowercase()
        val q = query.lowercase()
        return when {
            n == q || n.substringBefore(' ') == q -> 0
            n.split(' ', '-', '.').any { it.startsWith(q) } -> 1
            else -> 2
        }
    }
}
