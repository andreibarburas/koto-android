package com.brbrs.koto.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.brbrs.koto.ical.VTodo
import com.brbrs.koto.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reminders are plain [AlarmManager] alarms scheduled on-device: no push server, nothing leaves
 * the phone. Two sources feed in -- the global offsets from Settings, and any VALARM the task
 * itself carries (set in Koto, Tasks.org, Thunderbird or anywhere else). Per-task alarms win:
 * if a task has its own VALARM, the global offsets are not also applied to it, or a task with
 * a deliberate "on the day" alarm would also fire a day early from the global default.
 *
 * Alarms don't survive a reboot, so [BootReceiver] re-schedules everything open on startup.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val alarmManager get() = context.getSystemService(AlarmManager::class.java)

    /** Offsets Koto may ever have scheduled, so a removed one still gets cancelled. */
    private val cancellableOffsets = listOf(0, 15, 30, 60, 120, 180, 480, 1440, 2880, 10080)

    private fun requestCode(href: String, offsetMinutes: Int): Int =
        ((href.hashCode() and 0x00FFFFFF) * 31 + offsetMinutes) and 0x7FFFFFFF

    private fun pendingIntent(href: String, title: String, offsetMinutes: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_HREF, href)
            putExtra(ReminderReceiver.EXTRA_TITLE, title)
            putExtra(ReminderReceiver.EXTRA_OFFSET_MINUTES, offsetMinutes)
        }
        return PendingIntent.getBroadcast(
            context, requestCode(href, offsetMinutes), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun reschedule(href: String, task: VTodo) {
        cancel(href)
        val settings = settingsRepository.settings.value
        if (!settings.remindersEnabled || task.isCompleted || task.isCancelled) return
        val due = task.due ?: task.start ?: return

        // An all-day task fires at 09:00 local rather than at midnight, which nobody wants.
        val reference = if (due.dateOnly) due.millis + 9 * 60 * 60_000L else due.millis

        val perTask = task.alarms.mapNotNull { alarm ->
            when {
                alarm.absoluteMillis != null -> alarm.absoluteMillis to 0
                alarm.minutesBefore != null -> (reference - alarm.minutesBefore * 60_000L) to alarm.minutesBefore
                else -> null
            }
        }
        val triggers = perTask.ifEmpty {
            settings.reminderOffsetsMinutes.map { (reference - it * 60_000L) to it }
        }

        val now = System.currentTimeMillis()
        triggers.forEach { (triggerAt, offset) ->
            if (triggerAt <= now) return@forEach
            val pending = pendingIntent(href, task.summary, offset)
            runCatching {
                val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    alarmManager?.canScheduleExactAlarms() == true
                if (canExact) alarmManager?.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                else alarmManager?.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        }
    }

    fun cancel(href: String) {
        val offsets = (settingsRepository.settings.value.reminderOffsetsMinutes + cancellableOffsets).distinct()
        offsets.forEach { alarmManager?.cancel(pendingIntent(href, "", it)) }
    }

    /** True from Android 12 onward when the exact-alarm permission still needs granting. */
    fun needsExactAlarmPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager?.canScheduleExactAlarms() == false
}
