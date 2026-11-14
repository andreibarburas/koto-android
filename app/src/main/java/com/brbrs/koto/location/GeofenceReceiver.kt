package com.brbrs.koto.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import com.brbrs.koto.reminders.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Fires when the device enters (or leaves) the radius around a task's GEO coordinates.
 *
 * Only arrival is notified. Leaving a place is almost never what a task reminder means, and
 * notifying on both doubles the interruptions for no gain.
 */
@AndroidEntryPoint
class GeofenceReceiver : BroadcastReceiver() {

    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var geofenceManager: GeofenceManager

    override fun onReceive(context: Context, intent: Intent) {
        val href = intent.getStringExtra(GeofenceManager.EXTRA_HREF) ?: return
        val entering = intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, false)
        if (!entering) {
            // Leaving isn't notified, but it does re-arm the task for the next arrival.
            geofenceManager.markDeparted(href)
            return
        }
        // The backstop worker may have got there first.
        if (!geofenceManager.markArrived(href)) return
        val title = intent.getStringExtra(GeofenceManager.EXTRA_TITLE).orEmpty().ifBlank { "A task is nearby" }
        val place = intent.getStringExtra(GeofenceManager.EXTRA_PLACE)
        notificationHelper.showReminder(
            href = href,
            title = title,
            body = if (place.isNullOrBlank()) "You're nearby" else "You're near $place",
        )
    }
}
