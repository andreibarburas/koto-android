package com.brbrs.koto.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.brbrs.koto.auth.AuthRepository
import com.brbrs.koto.covers.CoverRepository
import com.brbrs.koto.data.TaskRepository
import com.brbrs.koto.location.GeofenceManager
import com.brbrs.koto.reminders.ReminderScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Pushes anything queued locally, pulls every list, then re-arms reminders -- so a due date
 * changed on another device or on the web still produces a local notification here.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val authRepository: AuthRepository,
    private val coverRepository: CoverRepository,
    private val repository: TaskRepository,
    private val reminderScheduler: ReminderScheduler,
    private val geofenceManager: GeofenceManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (authRepository.currentSession() == null) return Result.success()
        return repository.refreshAll().fold(
            onSuccess = {
                rescheduleUpcoming(repository, reminderScheduler)
                // A task's location, due date or completion can all change what's worth
                // watching, so the geofence set is rebuilt after every sync.
                geofenceManager.refresh(repository.withLocations())
                coverRepository.reconcile()
                Result.success()
            },
            onFailure = { Result.retry() },
        )
    }
}

/**
 * Re-arms alarms for everything due in the next fortnight, and cancels any stale alarms
 * for tasks that are now completed or cancelled (e.g. marked done on another device).
 * Shared with the boot worker.
 */
internal suspend fun rescheduleUpcoming(
    repository: TaskRepository,
    scheduler: ReminderScheduler,
) {
    val until = System.currentTimeMillis() + 14L * 24 * 60 * 60 * 1000
    repository.dueBefore(until).forEach { (href, todo) -> scheduler.reschedule(href, todo) }
    // Cancel any alarms that were scheduled before this device learned the task
    // was completed or cancelled (e.g. synced from another device or the web UI).
    repository.completedHrefsWithDueBefore(until).forEach { href -> scheduler.cancel(href) }
}
