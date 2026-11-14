package com.brbrs.koto.ical

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * RRULE handling, deliberately limited to what one person's task list actually uses:
 * FREQ=DAILY|WEEKLY|MONTHLY|YEARLY with INTERVAL, BYDAY (weekly), BYMONTHDAY (monthly),
 * and the COUNT / UNTIL terminators.
 *
 * Completing a recurring task rolls its dates forward rather than writing a COMPLETED instance
 * and spawning a sibling, which is what Nextcloud Tasks and Tasks.org both do; a split would
 * read as two tasks in every other client. When the series has run out (COUNT exhausted or
 * UNTIL passed) the task is completed normally and the RRULE is dropped.
 *
 * Anything Koto can't parse is left untouched on the task and the series simply doesn't advance,
 * which is a visible, recoverable failure rather than a silently wrong date.
 */
object Recurrence {

    /**
     * One BYDAY entry. [ordinal] is the "-1" in "-1FR" (last Friday) or the "2" in "2MO"
     * (second Monday); null means every occurrence of that weekday.
     */
    data class WeekDay(val day: DayOfWeek, val ordinal: Int? = null)

    data class Rule(
        val freq: String,
        val interval: Int = 1,
        val byDay: List<WeekDay> = emptyList(),
        val byMonthDay: List<Int> = emptyList(),
        /** BYSETPOS, used with BYDAY for "the last weekday of the month". */
        val bySetPos: List<Int> = emptyList(),
        val count: Int? = null,
        val untilMillis: Long? = null,
    ) {
        val plainDays: List<DayOfWeek> get() = byDay.map { it.day }
    }

    private val DAY_MAP = mapOf(
        "MO" to DayOfWeek.MONDAY, "TU" to DayOfWeek.TUESDAY, "WE" to DayOfWeek.WEDNESDAY,
        "TH" to DayOfWeek.THURSDAY, "FR" to DayOfWeek.FRIDAY, "SA" to DayOfWeek.SATURDAY,
        "SU" to DayOfWeek.SUNDAY,
    )

    fun parse(rrule: String?): Rule? {
        if (rrule.isNullOrBlank()) return null
        val parts = rrule.split(';').mapNotNull {
            val eq = it.indexOf('='); if (eq <= 0) null else it.substring(0, eq).uppercase() to it.substring(eq + 1)
        }.toMap()
        val freq = parts["FREQ"]?.uppercase() ?: return null
        if (freq !in setOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY")) return null
        val until = parts["UNTIL"]?.let { raw ->
            runCatching {
                when {
                    raw.endsWith("Z") -> LocalDateTime.parse(raw.dropLast(1), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
                        .toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
                    raw.length == 8 -> LocalDate.parse(raw, DateTimeFormatter.ofPattern("yyyyMMdd"))
                        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    else -> LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }
            }.getOrNull()
        }
        return Rule(
            freq = freq,
            interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
            byDay = parts["BYDAY"]?.split(',')?.mapNotNull { parseWeekDay(it.trim()) } ?: emptyList(),
            byMonthDay = parts["BYMONTHDAY"]?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList(),
            bySetPos = parts["BYSETPOS"]?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList(),
            count = parts["COUNT"]?.toIntOrNull(),
            untilMillis = until,
        )
    }

    /** "FR", "-1FR", "2MO" -> a [WeekDay]. */
    private fun parseWeekDay(token: String): WeekDay? {
        if (token.length < 2) return null
        val day = DAY_MAP[token.takeLast(2).uppercase()] ?: return null
        val prefix = token.dropLast(2)
        val ordinal = if (prefix.isBlank()) null else prefix.toIntOrNull() ?: return null
        return WeekDay(day, ordinal?.takeIf { it != 0 })
    }

    /** Human-readable summary for the task detail screen, e.g. "Every 2 weeks on Mon, Thu". */
    fun describe(rrule: String?): String? {
        val r = parse(rrule) ?: return rrule?.takeIf { it.isNotBlank() }?.let { "Repeats (custom rule)" }
        val unit = when (r.freq) {
            "DAILY" -> if (r.interval == 1) "day" else "days"
            "WEEKLY" -> if (r.interval == 1) "week" else "weeks"
            "MONTHLY" -> if (r.interval == 1) "month" else "months"
            else -> if (r.interval == 1) "year" else "years"
        }
        val base = if (r.interval == 1) "Every $unit" else "Every ${r.interval} $unit"
        // Name the common shapes outright rather than reciting the rule back at the reader.
        if (r.freq == "WEEKLY" && r.interval == 1 && r.plainDays.toSet() == WEEKDAYS) return "Every weekday"
        if (r.freq == "WEEKLY" && r.interval == 1 && r.plainDays.toSet() == WEEKEND) return "Every weekend day"

        val days = when {
            r.byDay.isEmpty() -> ""
            else -> " on " + r.byDay.joinToString(", ") { wd ->
                val name = wd.day.name.lowercase().replaceFirstChar(Char::uppercase).take(3)
                when (wd.ordinal) {
                    null -> name
                    -1 -> "the last $name"
                    1 -> "the first $name"
                    2 -> "the second $name"
                    3 -> "the third $name"
                    4 -> "the fourth $name"
                    else -> "$name #${wd.ordinal}"
                }
            }
        }
        val monthDays = if (r.byMonthDay.isNotEmpty()) " on day " + r.byMonthDay.joinToString(", ") else ""
        val ends = when {
            r.count != null -> ", ${r.count} times"
            r.untilMillis != null -> ", until " + Instant.ofEpochMilli(r.untilMillis)
                .atZone(ZoneId.systemDefault()).toLocalDate()
                .format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
            else -> ""
        }
        return base + days + monthDays + ends
    }

    /** The next occurrence strictly after [from], or null when the series has ended. */
    fun nextAfter(rule: Rule, from: DateVal): DateVal? {
        val zone = ZoneId.systemDefault()
        val startDt = Instant.ofEpochMilli(from.millis).atZone(zone)
        var next: LocalDate = when (rule.freq) {
            "DAILY" -> startDt.toLocalDate().plusDays(rule.interval.toLong())
            "WEEKLY" -> nextWeekly(startDt.toLocalDate(), rule)
            "MONTHLY" -> nextMonthly(startDt.toLocalDate(), rule)
            else -> startDt.toLocalDate().plusYears(rule.interval.toLong())
        }
        // Guard against a rule that fails to advance (e.g. BYMONTHDAY=31 in February).
        var guard = 0
        while (!next.isAfter(startDt.toLocalDate()) && guard < 60) {
            next = next.plusDays(1)
            guard++
        }

        val candidate = if (from.dateOnly) {
            DateVal.allDay(next)
        } else {
            val time = startDt.toLocalTime()
            DateVal(
                millis = next.atTime(time).atZone(from.zone()).toInstant().toEpochMilli(),
                dateOnly = false,
                tzid = from.tzid,
                utc = from.utc,
            )
        }
        if (rule.untilMillis != null && candidate.millis > rule.untilMillis) return null
        return candidate
    }

    private fun nextWeekly(from: LocalDate, rule: Rule): LocalDate {
        if (rule.byDay.isEmpty()) return from.plusWeeks(rule.interval.toLong())
        val sorted = rule.plainDays.distinct().sortedBy { it.value }
        // A later day in the same week comes first; otherwise jump INTERVAL weeks to the earliest day.
        val laterThisWeek = sorted.firstOrNull { it.value > from.dayOfWeek.value }
        return if (laterThisWeek != null) {
            from.with(TemporalAdjusters.next(laterThisWeek))
        } else {
            from.plusWeeks(rule.interval.toLong()).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .with(TemporalAdjusters.nextOrSame(sorted.first()))
        }
    }

    private fun nextMonthly(from: LocalDate, rule: Rule): LocalDate {
        // "The last Friday", "the second Monday": BYDAY carries an ordinal inside the month.
        val ordinalDays = rule.byDay.filter { it.ordinal != null }
        if (ordinalDays.isNotEmpty()) {
            val thisMonth = ordinalDays.mapNotNull { nthWeekdayOf(from, it) }.filter { it.isAfter(from) }.minOrNull()
            if (thisMonth != null) return thisMonth
            val target = from.plusMonths(rule.interval.toLong()).withDayOfMonth(1)
            return ordinalDays.mapNotNull { nthWeekdayOf(target, it) }.minOrNull()
                ?: target.withDayOfMonth(minOf(from.dayOfMonth, target.lengthOfMonth()))
        }
        // BYDAY with no ordinal plus BYSETPOS: "the last weekday of the month".
        if (rule.byDay.isNotEmpty() && rule.bySetPos.isNotEmpty()) {
            val thisMonth = setPosDays(from, rule).filter { it.isAfter(from) }.minOrNull()
            if (thisMonth != null) return thisMonth
            val target = from.plusMonths(rule.interval.toLong()).withDayOfMonth(1)
            return setPosDays(target, rule).minOrNull()
                ?: target.withDayOfMonth(minOf(from.dayOfMonth, target.lengthOfMonth()))
        }
        if (rule.byMonthDay.isEmpty()) {
            val target = from.plusMonths(rule.interval.toLong())
            // Clamp "the 31st" to the last day of a shorter month rather than overflowing.
            return target.withDayOfMonth(minOf(from.dayOfMonth, target.lengthOfMonth()))
        }
        val days = rule.byMonthDay.filter { it in 1..31 }.sorted()
        val laterThisMonth = days.firstOrNull { it > from.dayOfMonth && it <= from.lengthOfMonth() }
        if (laterThisMonth != null) return from.withDayOfMonth(laterThisMonth)
        val target = from.plusMonths(rule.interval.toLong())
        return target.withDayOfMonth(minOf(days.first(), target.lengthOfMonth()))
    }

    /** The nth (or last) given weekday within [inMonth]'s month, or null if it doesn't exist. */
    private fun nthWeekdayOf(inMonth: LocalDate, wd: WeekDay): LocalDate? {
        val ordinal = wd.ordinal ?: return null
        val first = inMonth.withDayOfMonth(1)
        return if (ordinal > 0) {
            val candidate = first.with(TemporalAdjusters.dayOfWeekInMonth(ordinal, wd.day))
            candidate.takeIf { it.month == inMonth.month }
        } else {
            first.with(TemporalAdjusters.lastInMonth(wd.day)).let { last ->
                // -1 is the last, -2 the one before it, and so on.
                val shifted = last.minusWeeks((-ordinal - 1).toLong())
                shifted.takeIf { it.month == inMonth.month }
            }
        }
    }

    /** Every day in the month matching BYDAY, picked out by BYSETPOS (1-based, -1 = last). */
    private fun setPosDays(inMonth: LocalDate, rule: Rule): List<LocalDate> {
        val days = rule.plainDays.toSet()
        if (days.isEmpty()) return emptyList()
        val first = inMonth.withDayOfMonth(1)
        val all = (0 until inMonth.lengthOfMonth()).map { first.plusDays(it.toLong()) }
            .filter { it.dayOfWeek in days }
        return rule.bySetPos.mapNotNull { pos ->
            when {
                pos > 0 -> all.getOrNull(pos - 1)
                pos < 0 -> all.getOrNull(all.size + pos)
                else -> null
            }
        }
    }

    /**
     * Completing a recurring task: rolls DUE and DTSTART forward by one occurrence and keeps the
     * task open. Returns the completed task instead when the series has run out, with the RRULE
     * removed so it doesn't look like it should still repeat.
     */
    fun completeRecurring(task: VTodo, now: Long = System.currentTimeMillis()): VTodo {
        val rule = parse(task.rrule) ?: return task.markCompleted(now)
        val anchor = task.due ?: task.start ?: return task.markCompleted(now)

        // COUNT is tracked with an X- property, since rolling the dates forward means there is
        // no series of instances on the server to count. Other clients ignore it harmlessly.
        val doneKey = "X-KOTO-OCCURRENCES-DONE"
        val already = task.unknownLines
            .firstOrNull { it.startsWith(doneKey, ignoreCase = true) }
            ?.substringAfter(':')?.trim()?.toIntOrNull() ?: 0
        val newDone = already + 1
        if (rule.count != null && newDone >= rule.count) {
            return task.markCompleted(now).copy(
                rrule = null,
                unknownLines = task.unknownLines.filterNot { it.startsWith(doneKey, ignoreCase = true) },
            )
        }

        val nextDue = nextAfter(rule, anchor) ?: return task.markCompleted(now).copy(rrule = null)
        val shift = nextDue.millis - anchor.millis
        val nextStart = task.start?.let { s ->
            if (task.due == null) nextDue else s.copy(millis = s.millis + shift)
        }

        val lines = task.unknownLines.filterNot { it.startsWith(doneKey, ignoreCase = true) } +
            "$doneKey:$newDone"

        return task.copy(
            due = if (task.due != null) nextDue else null,
            start = nextStart,
            status = TodoStatus.NEEDS_ACTION,
            completed = null,
            percentComplete = null,
            sequence = task.sequence + 1,
            lastModified = now,
            unknownLines = lines,
        )
    }

    private val WEEKDAYS = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )
    private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    private fun code(day: DayOfWeek) = DAY_MAP.entries.first { it.value == day }.key

    /** Builds an RRULE from the presets the detail screen offers. */
    fun buildRule(
        freq: String,
        interval: Int,
        byDay: List<DayOfWeek> = emptyList(),
        /** Ordinal within the month for a monthly rule: -1 = last, 1 = first. */
        monthlyOrdinal: Int? = null,
    ): String {
        val sb = StringBuilder("FREQ=$freq")
        if (interval > 1) sb.append(";INTERVAL=$interval")
        if (byDay.isNotEmpty()) {
            val prefix = if (freq == "MONTHLY" && monthlyOrdinal != null) monthlyOrdinal.toString() else ""
            sb.append(";BYDAY=" + byDay.joinToString(",") { prefix + code(it) })
        }
        return sb.toString()
    }

    /** Every weekday, Monday to Friday. */
    fun weekdaysRule(): String = "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"

    /** The last weekday of each month, e.g. for a month-end chore. */
    fun lastWeekdayOfMonthRule(): String = "FREQ=MONTHLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=-1"
}
