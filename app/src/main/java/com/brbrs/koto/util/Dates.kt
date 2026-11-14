package com.brbrs.koto.util

import com.brbrs.koto.data.TaskBucket
import com.brbrs.koto.ical.DateVal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class DueState { Overdue, Today, Soon, Later }

/**
 * An all-day task is overdue only once the day itself is past, not at 00:00 that morning.
 * Comparing a date-only value against the clock is the classic way to show everything due
 * today as already late.
 */
fun dueState(due: DateVal, now: Long = System.currentTimeMillis()): DueState {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val dueDay = Instant.ofEpochMilli(due.millis).atZone(zone).toLocalDate()
    return when {
        dueDay.isBefore(today) -> DueState.Overdue
        dueDay.isEqual(today) -> if (!due.dateOnly && due.millis < now) DueState.Overdue else DueState.Today
        ChronoUnit.DAYS.between(today, dueDay) <= 7L -> DueState.Soon
        else -> DueState.Later
    }
}

fun bucketFor(due: DateVal?, now: Long = System.currentTimeMillis()): TaskBucket {
    if (due == null) return TaskBucket.NoDate
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val dueDay = Instant.ofEpochMilli(due.millis).atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(today, dueDay)
    return when {
        dueDay.isBefore(today) -> TaskBucket.Overdue
        days == 0L -> if (!due.dateOnly && due.millis < now) TaskBucket.Overdue else TaskBucket.Today
        days == 1L -> TaskBucket.Tomorrow
        days <= 7L -> TaskBucket.ThisWeek
        else -> TaskBucket.Later
    }
}

fun bucketLabel(bucket: TaskBucket): String = when (bucket) {
    TaskBucket.Overdue -> "Overdue"
    TaskBucket.Today -> "Today"
    TaskBucket.Tomorrow -> "Tomorrow"
    TaskBucket.ThisWeek -> "This week"
    TaskBucket.Later -> "Later"
    TaskBucket.NoDate -> "No date"
    TaskBucket.Completed -> "Completed"
    TaskBucket.Everything -> "Everything"
}

/**
 * Absolute dates are YYYY/MM/DD throughout the app.
 *
 * Today, Tomorrow and Yesterday keep their words, because on a task list those three carry the
 * meaning a reader actually wants and a bare date makes them do arithmetic. Everything else is
 * the numeric form. To make it numeric everywhere, delete the three relative branches below.
 */
val DATE_PATTERN = "yyyy/MM/dd"
private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern(DATE_PATTERN, Locale.getDefault())
private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

fun formatDue(due: DateVal): String {
    val zone = ZoneId.systemDefault()
    val dt = Instant.ofEpochMilli(due.millis).atZone(zone)
    val today = LocalDate.now(zone)
    val days = ChronoUnit.DAYS.between(today, dt.toLocalDate())
    val time = if (due.dateOnly) "" else " " + dt.format(TIME_FMT)
    return when (days) {
        0L -> "Today$time"
        1L -> "Tomorrow$time"
        -1L -> "Yesterday$time"
        else -> dt.format(DATE_FMT) + time
    }
}

/** The plain numeric form, with no relative words. Used where a row already says "Today". */
fun formatDateNumeric(due: DateVal): String =
    Instant.ofEpochMilli(due.millis).atZone(ZoneId.systemDefault()).format(DATE_FMT)

fun formatDate(date: LocalDate): String = date.format(DATE_FMT)

fun formatTimestamp(ms: Long): String {
    val zone = ZoneId.systemDefault()
    val dt = Instant.ofEpochMilli(ms).atZone(zone)
    return if (dt.toLocalDate() == LocalDate.now(zone)) dt.format(TIME_FMT)
    else dt.format(DATE_FMT) + " " + dt.format(TIME_FMT)
}

/** Agenda section headers: "2026/10/09 · Today", "2026/10/11 · Sunday". */
fun formatDayHeader(date: LocalDate): String {
    val numeric = date.format(DATE_FMT)
    val weekday = date.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))
    return when (ChronoUnit.DAYS.between(LocalDate.now(), date)) {
        0L -> "$numeric · Today"
        1L -> "$numeric · Tomorrow"
        else -> "$numeric · $weekday"
    }
}

/** Combines a chosen day with a chosen time into a DATE-TIME; null time means all-day. */
fun composeDue(date: LocalDate, hour: Int?, minute: Int?): DateVal =
    if (hour == null || minute == null) DateVal.allDay(date)
    else DateVal.dateTime(
        LocalDateTime.of(date, java.time.LocalTime.of(hour, minute))
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    )
