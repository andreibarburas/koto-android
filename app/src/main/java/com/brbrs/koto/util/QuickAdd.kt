package com.brbrs.koto.util

import com.brbrs.koto.ical.DateVal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Light natural-language parsing for the quick-add box, the one Tasks.org habit worth keeping
 * verbatim. Deliberately conservative: only unambiguous keywords are consumed, and whatever
 * isn't recognized stays in the title rather than being silently eaten.
 */
object QuickAdd {

    data class Parsed(
        val title: String,
        val due: DateVal?,
        val priority: Int,
        val tags: List<String>,
    )

    private val WEEKDAYS = mapOf(
        "monday" to DayOfWeek.MONDAY, "mon" to DayOfWeek.MONDAY,
        "tuesday" to DayOfWeek.TUESDAY, "tue" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY, "wed" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "thu" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "fri" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY, "sat" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY, "sun" to DayOfWeek.SUNDAY,
    )

    fun parse(input: String, enabled: Boolean): Parsed {
        if (!enabled) return Parsed(input.trim(), null, 0, emptyList())

        var text = input
        val tags = ArrayList<String>()
        var priority = 0
        var due: LocalDate? = null
        var time: Pair<Int, Int>? = null

        // #tag
        Regex("""(?<=^|\s)#([\p{L}\p{N}_-]+)""").findAll(text).forEach { tags.add(it.groupValues[1]) }
        text = text.replace(Regex("""(?<=^|\s)#[\p{L}\p{N}_-]+"""), " ")

        // !1 .. !9 priority
        Regex("""(?<=^|\s)!([1-9])(?=\s|$)""").find(text)?.let {
            priority = it.groupValues[1].toInt()
            text = text.replaceRange(it.range, " ")
        }

        // explicit time, "at 17:30" or bare "17:30"
        Regex("""(?<=^|\s)(?:at\s+)?([01]?\d|2[0-3]):([0-5]\d)(?=\s|$)""").find(text)?.let {
            time = it.groupValues[1].toInt() to it.groupValues[2].toInt()
            text = text.replaceRange(it.range, " ")
        }

        val today = LocalDate.now()
        val lower = text.lowercase()
        when {
            Regex("""(?<=^|\s)today(?=\s|$)""").containsMatchIn(lower) -> {
                due = today; text = text.replace(Regex("""(?i)(?<=^|\s)today(?=\s|$)"""), " ")
            }
            Regex("""(?<=^|\s)tomorrow(?=\s|$)""").containsMatchIn(lower) -> {
                due = today.plusDays(1); text = text.replace(Regex("""(?i)(?<=^|\s)tomorrow(?=\s|$)"""), " ")
            }
            Regex("""(?<=^|\s)next week(?=\s|$)""").containsMatchIn(lower) -> {
                due = today.plusWeeks(1); text = text.replace(Regex("""(?i)(?<=^|\s)next week(?=\s|$)"""), " ")
            }
            else -> {
                for ((word, day) in WEEKDAYS) {
                    val re = Regex("""(?i)(?<=^|\s)(?:next\s+)?$word(?=\s|$)""")
                    val m = re.find(text) ?: continue
                    due = today.with(TemporalAdjusters.next(day))
                    text = text.replaceRange(m.range, " ")
                    break
                }
            }
        }

        val dueVal = due?.let { d ->
            val t = time
            if (t == null) DateVal.allDay(d) else composeDue(d, t.first, t.second)
        }

        val title = text.replace(Regex("""\s{2,}"""), " ").trim()
        return Parsed(
            title = title.ifBlank { input.trim() },
            due = dueVal,
            priority = priority,
            tags = tags.distinct(),
        )
    }

    /** A short description of what was recognized, shown under the quick-add field. */
    fun hint(p: Parsed): String? {
        val bits = buildList {
            p.due?.let { add(formatDue(it)) }
            if (p.priority > 0) add("priority ${p.priority}")
            if (p.tags.isNotEmpty()) add(p.tags.joinToString(", ") { "#$it" })
        }
        return bits.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
}
