package com.brbrs.koto.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.brbrs.koto.data.TaskRepository
import com.brbrs.koto.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The notification's Complete action. Runs on the application scope rather than a
 * goAsync() window, since a CalDAV PUT can easily outlive a broadcast receiver's budget;
 * the local write lands immediately either way and the push is retried by SyncWorker
 * if the process is killed first.
 */
@AndroidEntryPoint
class CompleteActionReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: TaskRepository
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var reminderScheduler: ReminderScheduler
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    companion object {
        const val EXTRA_HREF = "href"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val href = intent.getStringExtra(EXTRA_HREF) ?: return
        notificationHelper.dismiss(href)
        // Cancel any remaining scheduled alarms for this task (e.g. a "1 day before" alarm
        // fired but a "1 hour before" alarm was still pending).
        reminderScheduler.cancel(href)
        appScope.launch { runCatching { repository.setCompleted(href, true) } }
    }
}
