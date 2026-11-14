package com.brbrs.koto.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.brbrs.koto.data.TaskRepository
import com.brbrs.koto.reminders.NotificationHelper
import com.brbrs.koto.settings.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * A backstop for the proximity alerts.
 *
 * The AOSP proximity API is noticeably less reliable than Google's geofencing: the OS may
 * delay or drop an alert when the app has been idle, when battery saver is on, or after the
 * process has been killed for a while. Rather than pretend otherwise, Koto also checks the
 * last known location on a schedule and fires anything the alerts missed.
 *
 * This is cheap on purpose. It reads the *last known* fix rather than requesting a new one,
 * so it costs no GPS and no radio: if the phone hasn't located itself recently there is simply
 * nothing to check. A task is notified at most once per arrival, tracked in [GeofenceManager].
 */
@HiltWorker
class LocationCheckWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val repository: TaskRepository,
    private val settingsRepository: SettingsRepository,
    private val notificationHelper: NotificationHelper,
    private val geofenceManager: GeofenceManager,
) : CoroutineWorker(context, params) {

    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        if (!settingsRepository.settings.value.locationRemindersEnabled) return Result.success()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        val manager = context.getSystemService(LocationManager::class.java) ?: return Result.success()
        val fix = lastKnown(manager) ?: return Result.success()

        // A fix older than half an hour says nothing useful about where the person is now.
        if (System.currentTimeMillis() - fix.time > 30 * 60_000L) return Result.success()

        repository.withLocations().forEach { (href, todo) ->
            val lat = todo.latitude ?: return@forEach
            val lon = todo.longitude ?: return@forEach
            val radius = (todo.geoRadius ?: GeofenceManager.DEFAULT_RADIUS_METRES).toFloat()
            val distance = FloatArray(1)
            Location.distanceBetween(fix.latitude, fix.longitude, lat, lon, distance)
            if (distance[0] <= radius) {
                if (geofenceManager.markArrived(href)) {
                    notificationHelper.showReminder(
                        href = href,
                        title = todo.summary.ifBlank { "A task is nearby" },
                        body = todo.location?.takeIf { it.isNotBlank() }?.let { "You're near $it" } ?: "You're nearby",
                    )
                }
            } else if (distance[0] > radius * 2) {
                // Well clear of it: arm it again so a later return notifies.
                geofenceManager.markDeparted(href)
            }
        }
        return Result.success()
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(manager: LocationManager): Location? =
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
}
