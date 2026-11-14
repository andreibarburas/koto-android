package com.brbrs.koto.ical

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * A DATE or DATE-TIME from an iCalendar property.
 *
 * The DATE vs DATE-TIME distinction matters and is the single easiest thing to get wrong:
 * "due Friday" (VALUE=DATE) is not the same as "due Friday 00:00" in the viewer's timezone,
 * and collapsing the two makes all-day tasks jump a day for anyone east or west of UTC. Koto
 * keeps the flag and the originating [tzid] so a task written by another client round-trips
 * byte-identically unless the user actually changes the date.
 *
 * [millis] is always a concrete instant for sorting and alarms. For a date-only value it is
 * local midnight at the start of that day in the device's current zone.
 */
data class DateVal(
    val millis: Long,
    val dateOnly: Boolean,
    val tzid: String? = null,
    /** True when the original value was UTC ("...Z"). Written back as UTC. */
    val utc: Boolean = false,
) {
    fun zone(): ZoneId = when {
        utc -> ZoneOffset.UTC
        tzid != null -> runCatching { ZoneId.of(tzid) }.getOrDefault(ZoneId.systemDefault())
        else -> ZoneId.systemDefault()
    }

    fun localDate(): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

    companion object {
        private val DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val DT_FMT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

        /** Parses a DATE / DATE-TIME property value together with its VALUE and TZID params. */
        fun parse(line: ICalendar.Line): DateVal? = runCatching {
            val raw = line.value.trim()
            val isDateParam = line.param("VALUE").equals("DATE", ignoreCase = true)
            val tzid = line.param("TZID")
            if (isDateParam || (raw.length == 8 && !raw.contains('T'))) {
                val d = LocalDate.parse(raw, DATE_FMT)
                DateVal(
                    millis = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    dateOnly = true,
                )
            } else if (raw.endsWith("Z")) {
                val ldt = LocalDateTime.parse(raw.dropLast(1), DT_FMT)
                DateVal(ldt.toInstant(ZoneOffset.UTC).toEpochMilli(), dateOnly = false, utc = true)
            } else {
                val zone = tzid?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
                val ldt = LocalDateTime.parse(raw, DT_FMT)
                DateVal(ldt.atZone(zone).toInstant().toEpochMilli(), dateOnly = false, tzid = tzid)
            }
        }.getOrNull()

        /** Local midnight on the given day, as an all-day value. */
        fun allDay(date: LocalDate): DateVal = DateVal(
            millis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            dateOnly = true,
        )

        /** A concrete local date and time, written out with the device's TZID. */
        fun dateTime(millis: Long): DateVal =
            DateVal(millis, dateOnly = false, tzid = ZoneId.systemDefault().id)

        fun utcNow(): DateVal = DateVal(System.currentTimeMillis(), dateOnly = false, utc = true)
    }

    /** Serializes back to a full content line, e.g. "DUE;TZID=Europe/Amsterdam:20261012T090000". */
    fun toLine(property: String): String = when {
        dateOnly -> {
            val d = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
            "$property;VALUE=DATE:${d.format(DATE_FMT)}"
        }
        utc -> {
            val dt = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDateTime()
            "$property:${dt.format(DT_FMT)}Z"
        }
        else -> {
            val z = zone()
            val dt = Instant.ofEpochMilli(millis).atZone(z).toLocalDateTime()
            "$property;TZID=${z.id}:${dt.format(DT_FMT)}"
        }
    }
}
