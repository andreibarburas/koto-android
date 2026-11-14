package com.brbrs.koto.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.brbrs.koto.ical.VTodo
import com.brbrs.koto.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Location reminders, built on the AOSP [LocationManager] proximity alert rather than Google
 * Play Services' GeofencingClient.
 *
 * The GMS geofencing API is smarter about battery, but depending on it would pull Google Play
 * Services into the app and make Koto ineligible for F-Droid, which is where the rest of the
 * suite lives. The platform API does the same job with no Google dependency; the trade-off is
 * that the OS is less clever about batching, so Koto keeps the number of active alerts small.
 *
 * Only tasks that are open, have GEO coordinates, and are due within the next month get an
 * alert, capped at [MAX_ACTIVE]. Watching hundreds of points would be the battery problem
 * people rightly worry about.
 */
@Singleton
class GeofenceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    companion object {
        /** Platform proximity alerts are not free; keep the working set small. */
        const val MAX_ACTIVE = 40
        const val DEFAULT_RADIUS_METRES = 200
        const val EXTRA_HREF = "href"
        const val EXTRA_TITLE = "title"
        const val EXTRA_PLACE = "place"
    }

    private val locationManager get() = context.getSystemService(LocationManager::class.java)

    private val registered = LinkedHashMap<String, PendingIntent>()

    /**
     * Tasks already notified for the current arrival.
     *
     * Two things can notice an arrival -- the platform proximity alert and
     * [LocationCheckWorker] -- and without this they would both fire for the same one.
     * Cleared for a task once the device is well clear of it again.
     */
    private val arrived = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /** Returns true if this is a fresh arrival and the caller should notify. */
    fun markArrived(href: String): Boolean = arrived.add(href)

    fun markDeparted(href: String) { arrived.remove(href) }

    fun hasForegroundPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Background location is a separate grant from Android 10 on, and proximity alerts only
     * fire with the app closed if it has been given.
     */
    fun hasBackgroundPermission(): Boolean =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) hasForegroundPermission()
        else ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun pendingIntent(href: String, title: String, place: String?): PendingIntent {
        val intent = Intent(context, GeofenceReceiver::class.java).apply {
            // A distinct action per task keeps PendingIntents from being treated as equal,
            // which would otherwise collapse every alert into one.
            action = "com.brbrs.koto.GEOFENCE." + href.hashCode()
            putExtra(EXTRA_HREF, href)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_PLACE, place)
        }
        return PendingIntent.getBroadcast(
            context, href.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Replaces the whole set of watched places. Called after every sync, since a task's
     * location, due date or completion can all change what should be watched.
     */
    @SuppressLint("MissingPermission")
    fun refresh(tasks: List<Pair<String, VTodo>>) {
        clearAll()
        if (!settingsRepository.settings.value.locationRemindersEnabled) return
        if (!hasForegroundPermission()) return
        val manager = locationManager ?: return

        tasks.asSequence()
            .filter { (_, todo) -> todo.hasLocation && !todo.isCompleted && !todo.isCancelled }
            .take(MAX_ACTIVE)
            .forEach { (href, todo) ->
                val lat = todo.latitude ?: return@forEach
                val lon = todo.longitude ?: return@forEach
                val radius = (todo.geoRadius ?: DEFAULT_RADIUS_METRES).coerceIn(50, 10_000).toFloat()
                val pending = pendingIntent(href, todo.summary, todo.location)
                runCatching {
                    @Suppress("DEPRECATION")
                    manager.addProximityAlert(lat, lon, radius, -1L, pending)
                    registered[href] = pending
                }
            }
    }

    @SuppressLint("MissingPermission")
    fun clearAll() {
        arrived.clear()
        val manager = locationManager ?: return
        registered.values.forEach { pending ->
            runCatching {
                @Suppress("DEPRECATION")
                manager.removeProximityAlert(pending)
            }
        }
        registered.clear()
    }

    fun cancel(href: String) {
        val manager = locationManager ?: return
        registered.remove(href)?.let { pending ->
            runCatching {
                @Suppress("DEPRECATION")
                manager.removeProximityAlert(pending)
            }
        }
    }
}
