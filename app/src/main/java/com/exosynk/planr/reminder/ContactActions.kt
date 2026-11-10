package com.exosynk.planr.reminder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import java.util.Locale

/** Intents for a reminder's contact button. None need a permission (the user confirms in the dialer/app). */
object ContactActions {
    fun intent(context: Context, c: ReminderContact): Intent? {
        val digits = c.number.filter { it.isDigit() || it == '+' }
        if (digits.isBlank()) return null
        return when (c.action) {
            ContactAction.CALL -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits"))
            ContactAction.MESSAGE -> Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$digits"))
            ContactAction.WHATSAPP -> {
                // wa.me needs the full international number (e.g. 9198… for India).
                val country = context.getSystemService(TelephonyManager::class.java)?.simCountryIso
                    ?.takeIf { it.isNotBlank() }?.uppercase() ?: Locale.getDefault().country
                val e164 = PhoneNumberUtils.formatNumberToE164(digits, country) ?: digits
                Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${e164.removePrefix("+")}"))
            }
        }
    }
}
