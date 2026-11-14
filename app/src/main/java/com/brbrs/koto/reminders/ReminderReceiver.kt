package com.brbrs.koto.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var notificationHelper: NotificationHelper

    companion object {
        const val EXTRA_HREF = "href"
        const val EXTRA_TITLE = "title"
        const val EXTRA_OFFSET_MINUTES = "offset_minutes"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val href = intent.getStringExtra(EXTRA_HREF) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "A task is due" }
        val offset = intent.getIntExtra(EXTRA_OFFSET_MINUTES, 0)
        val body = when {
            offset <= 0 -> "Due now"
            offset < 60 -> "Due in $offset minutes"
            offset < 24 * 60 && offset % 60 == 0 -> "Due in ${offset / 60} hour${if (offset / 60 == 1) "" else "s"}"
            offset % (24 * 60) == 0 -> "Due in ${offset / (24 * 60)} day${if (offset / (24 * 60) == 1) "" else "s"}"
            else -> "Due soon"
        }
        notificationHelper.showReminder(href, title, body)
    }
}
