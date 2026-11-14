package com.brbrs.koto.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.brbrs.koto.location.LocationCheckWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val SYNC_WORK_NAME = "koto_periodic_sync"
        private const val PUSH_WORK_NAME = "koto_push_pending"
        private const val RESCHEDULE_WORK_NAME = "koto_reschedule_reminders"
        private const val LOCATION_WORK_NAME = "koto_location_check"

        /** WorkManager refuses periodic intervals under 15 minutes. */
        const val MIN_SYNC_INTERVAL_MINUTES = 15
    }

    fun scheduleSync(intervalMinutes: Int) {
        val interval = intervalMinutes.coerceAtLeast(MIN_SYNC_INTERVAL_MINUTES)
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(interval.toLong(), TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(SYNC_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancelSync() {
        WorkManager.getInstance(context).cancelUniqueWork(SYNC_WORK_NAME)
    }

    /**
     * Kicks a one-off sync. enqueueUniqueWork (not plain enqueue) is deliberate: two workers
     * writing to the same CalDAV collection at once produce 423 Locked on Nextcloud.
     */
    fun syncNow() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(PUSH_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    /** Called by [com.brbrs.koto.reminders.BootReceiver]; alarms don't survive a reboot. */
    /**
     * The periodic location check. Only runs while location reminders are on; switching them
     * off cancels it, so the app does no location work at all by default.
     */
    fun scheduleLocationChecks(enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(LOCATION_WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<LocationCheckWorker>(20, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        manager.enqueueUniquePeriodicWork(LOCATION_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun rescheduleRemindersAfterBoot() {
        val request = OneTimeWorkRequestBuilder<RescheduleRemindersWorker>().build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(RESCHEDULE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}
