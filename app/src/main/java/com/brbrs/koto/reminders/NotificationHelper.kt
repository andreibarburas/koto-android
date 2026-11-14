package com.brbrs.koto.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.brbrs.koto.MainActivity
import com.brbrs.koto.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_REMINDERS = "koto_reminders"
    }

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Task reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A reminder before a task is due"
            },
        )
    }

    fun canNotify(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * Shows a reminder with a Complete action, so the common case (tick it off) never needs the
     * app opened. The notification id is derived from the href so a later reminder for the same
     * task replaces the earlier one instead of stacking.
     */
    fun showReminder(href: String, title: String, body: String) {
        if (!canNotify()) return
        val id = notificationId(href)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("koto://task/${Uri.encode(href)}")
        }
        val openPending = PendingIntent.getActivity(
            context, id, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val completeIntent = Intent(context, CompleteActionReceiver::class.java).apply {
            putExtra(CompleteActionReceiver.EXTRA_HREF, href)
        }
        val completePending = PendingIntent.getBroadcast(
            context, id + 1, completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_koto)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .addAction(0, "Complete", completePending)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    fun dismiss(href: String) {
        runCatching { NotificationManagerCompat.from(context).cancel(notificationId(href)) }
    }

    /** Stable, positive and even, leaving the odd neighbour free for the action PendingIntent. */
    fun notificationId(href: String): Int = ((href.hashCode() and 0x3FFFFFFF) shl 1)
}
