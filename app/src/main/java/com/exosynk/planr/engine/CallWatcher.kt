package com.exosynk.planr.engine

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor

/**
 * Knows when a call is ringing or in progress, so Off-Grid can step aside:
 * the call screen is never bounced, the touch shield comes down, and (with
 * phone permission) the full-screen call screen is brought up at once.
 *
 * Detection needs no permission: the audio mode switches to RINGTONE /
 * IN_CALL / IN_COMMUNICATION for both regular and VoIP calls.
 */
class CallWatcher(private val context: Context, private val onChange: (ringing: Boolean, inCall: Boolean) -> Unit) {

    private val audio = context.getSystemService(AudioManager::class.java)
    private var modeListener: Any? = null
    @Suppress("DEPRECATION")
    private var phoneListener: PhoneStateListener? = null

    fun start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val l = AudioManager.OnModeChangedListener { notifyNow() }
            audio?.addOnModeChangedListener(Executor { it.run() }, l)
            modeListener = l
        } else {
            // Android 11: call-state updates need no permission there.
            @Suppress("DEPRECATION")
            val l = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) = notifyNow()
            }
            @Suppress("DEPRECATION")
            runCatching { context.getSystemService(TelephonyManager::class.java)?.listen(l, PhoneStateListener.LISTEN_CALL_STATE) }
            phoneListener = l
        }
    }

    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (modeListener as? AudioManager.OnModeChangedListener)?.let { audio?.removeOnModeChangedListener(it) }
        } else {
            @Suppress("DEPRECATION")
            phoneListener?.let { runCatching { context.getSystemService(TelephonyManager::class.java)?.listen(it, PhoneStateListener.LISTEN_NONE) } }
        }
    }

    private fun notifyNow() = onChange(isRinging(context), isInCall(context))

    companion object {
        fun isRinging(context: Context): Boolean =
            context.getSystemService(AudioManager::class.java)?.mode == AudioManager.MODE_RINGTONE

        /** Ringing, or a call (phone or VoIP) in progress. */
        fun isInCall(context: Context): Boolean = when (context.getSystemService(AudioManager::class.java)?.mode) {
            AudioManager.MODE_RINGTONE, AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION,
            AudioManager.MODE_CALL_SCREENING -> true
            else -> false
        }

        fun hasPhonePermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

        /** Brings the system's full-screen call screen to the front (needs READ_PHONE_STATE). */
        @SuppressLint("MissingPermission")
        fun showCallScreen(context: Context) {
            if (!hasPhonePermission(context)) return
            runCatching { context.getSystemService(TelecomManager::class.java)?.showInCallScreen(false) }
        }
    }
}
