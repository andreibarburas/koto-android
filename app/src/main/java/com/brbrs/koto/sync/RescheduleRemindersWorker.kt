package com.brbrs.koto.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.brbrs.koto.data.TaskRepository
import com.brbrs.koto.location.GeofenceManager
import com.brbrs.koto.reminders.ReminderScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class RescheduleRemindersWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: TaskRepository,
    private val reminderScheduler: ReminderScheduler,
    private val geofenceManager: GeofenceManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        rescheduleUpcoming(repository, reminderScheduler)
        // Proximity alerts are lost on reboot in the same way AlarmManager alarms are.
        geofenceManager.refresh(repository.withLocations())
        return Result.success()
    }
}
