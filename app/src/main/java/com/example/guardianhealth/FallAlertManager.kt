package com.example.guardianhealth

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.guardianhealth.data.local.ContactDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles the response when a fall is detected:
 *  1. Posts a high-priority notification with alarm sound + vibration
 *  2. Sends SMS to every saved emergency contact
 *
 * Testable via BLEManager.simulateFall() or by sending 0x01 on the
 * Fall Detection characteristic from nRF Connect.
 */
@Singleton
class FallAlertManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contactDao: ContactDao
) {
    companion object {
        private const val TAG = "FallAlertManager"
        private const val CHANNEL_ID = "fall_alerts"
        private const val NOTIFICATION_ID = 1001
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        createNotificationChannel()
    }

    // ── Notification Channel (Android 8+) ─────────────────────────
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Fall Detection Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical alerts when a fall is detected"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                )
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    // ── Public API ────────────────────────────────────────────────
    fun onFallDetected() {
        Log.w(TAG, "FALL DETECTED — triggering alerts")
        postNotification()
        sendSmsToContacts()
    }

    fun dismissAlert() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
    }

    // ── Notification ──────────────────────────────────────────────
    private fun postNotification() {
        // Check POST_NOTIFICATIONS permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "Missing POST_NOTIFICATIONS permission — skipping notification")
                return
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Fall Detected!")
            .setContentText("A fall has been detected. Emergency contacts are being notified.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    // ── SMS ───────────────────────────────────────────────────────
    private fun sendSmsToContacts() {
        scope.launch {
            // Check SEND_SMS permission
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "Missing SEND_SMS permission — skipping SMS alerts")
                return@launch
            }

            try {
                val contacts = contactDao.getAllContactsList()
                if (contacts.isEmpty()) {
                    Log.w(TAG, "No emergency contacts configured")
                    return@launch
                }

                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }

                val message = "FALL ALERT from Guardian Health: " +
                        "A possible fall has been detected. " +
                        "Please check on your loved one immediately. " +
                        "This is an automated message."

                contacts.forEach { contact ->
                    try {
                        smsManager.sendTextMessage(
                            contact.phoneNumber,
                            null,
                            message,
                            null,
                            null
                        )
                        Log.d(TAG, "SMS sent to ${contact.name} (${contact.phoneNumber})")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to send SMS to ${contact.name}: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in SMS alert flow: ${e.message}")
            }
        }
    }
}
