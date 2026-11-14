package com.brbrs.koto.ical

import java.util.UUID

/** A VALARM, kept simple: a relative or absolute trigger with an optional description. */
data class VAlarm(
    /** Minutes before the reference time. Negative means after. Null for an absolute trigger. */
    val minutesBefore: Int?,
    /** Absolute trigger in epoch millis, when [minutesBefore] is null. */
    val absoluteMillis: Long? = null,
    val description: String? = null,
) {
    fun toLines(): List<String> {
        val trigger = when {
            minutesBefore != null -> {
                val m = minutesBefore
                val abs = kotlin.math.abs(m)
                val sign = if (m >= 0) "-" else ""
                val dur = when {
                    abs == 0 -> "PT0S"
                    abs % (24 * 60) == 0 -> "P${abs / (24 * 60)}D"
                    abs % 60 == 0 -> "PT${abs / 60}H"
                    else -> "PT${abs}M"
                }
                "TRIGGER;RELATED=END:$sign$dur"
            }
            absoluteMillis != null -> DateVal(absoluteMillis, dateOnly = false, utc = true).toLine("TRIGGER")
            else -> "TRIGGER;RELATED=END:-PT0S"
        }
        return listOf(
            "BEGIN:VALARM",
            "ACTION:DISPLAY",
            trigger,
            "DESCRIPTION:${ICalendar.escapeText(description ?: "Reminder")}",
            "END:VALARM",
        )
    }

    companion object {
        /** Parses "-PT1H", "-P1D", "PT30M", "-PT0S" and absolute UTC triggers. */
        fun parseTrigger(line: ICalendar.Line): VAlarm? {
            val v = line.value.trim()
            if (line.param("VALUE").equals("DATE-TIME", true) || v.endsWith("Z")) {
                val dv = DateVal.parse(line) ?: return null
                return VAlarm(minutesBefore = null, absoluteMillis = dv.millis)
            }
            val negative = v.startsWith("-")
            val body = v.trimStart('-', '+')
            val re = Regex("""P(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?""")
            val m = re.matchEntire(body) ?: return null
            val weeks = m.groupValues[1].toIntOrNull() ?: 0
            val days = m.groupValues[2].toIntOrNull() ?: 0
            val hours = m.groupValues[3].toIntOrNull() ?: 0
            val mins = m.groupValues[4].toIntOrNull() ?: 0
            val total = weeks * 7 * 24 * 60 + days * 24 * 60 + hours * 60 + mins
            return VAlarm(minutesBefore = if (negative) total else -total)
        }
    }
}

/** RFC 5545 STATUS values a VTODO may carry. */
object TodoStatus {
    const val NEEDS_ACTION = "NEEDS-ACTION"
    const val COMPLETED = "COMPLETED"
    const val IN_PROCESS = "IN-PROCESS"
    const val CANCELLED = "CANCELLED"
}

/**
 * One task, as stored on the server.
 *
 * [unknownLines] holds every property inside the VTODO that Koto does not model, in the order
 * it was read, and [preamble]/[trailer] hold everything outside the VTODO (VTIMEZONE blocks,
 * X- properties on the VCALENDAR). Writing a task back reassembles all of it, so Koto never
 * silently drops something Tasks.org, Nextcloud Tasks or Thunderbird put there.
 */
data class VTodo(
    val uid: String,
    val summary: String = "",
    val description: String? = null,
    val due: DateVal? = null,
    val start: DateVal? = null,
    val completed: Long? = null,
    val status: String? = null,
    val percentComplete: Int? = null,
    /** 0 = unset, 1 highest .. 9 lowest, matching Nextcloud Tasks' own scale. */
    val priority: Int = 0,
    val categories: List<String> = emptyList(),
    /** UID of the parent task, from RELATED-TO;RELTYPE=PARENT. */
    val parentUid: String? = null,
    val rrule: String? = null,
    val location: String? = null,
    /** Coordinates from GEO, used for location reminders. */
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** Trigger radius in metres; null means Koto's default. */
    val geoRadius: Int? = null,
    /** URLs from ATTACH properties. Koto uploads files to the user's own Nextcloud. */
    val attachments: List<String> = emptyList(),
    /** Manual position within its list, from X-APPLE-SORT-ORDER. */
    val sortOrder: Int? = null,
    val created: Long? = null,
    val lastModified: Long? = null,
    val sequence: Int = 0,
    val alarms: List<VAlarm> = emptyList(),
    val unknownLines: List<String> = emptyList(),
    val preamble: List<String> = emptyList(),
    val trailer: List<String> = emptyList(),
) {
    val hasLocation: Boolean get() = latitude != null && longitude != null
    val isCompleted: Boolean get() = status == TodoStatus.COMPLETED || completed != null || percentComplete == 100
    val isCancelled: Boolean get() = status == TodoStatus.CANCELLED

    /** The date Koto sorts and groups by: DUE if set, otherwise DTSTART. */
    val effectiveDate: DateVal? get() = due ?: start

    companion object {
        private const val ATTACH_BLOCK_START = "-- Koto attachments --"

        /**
         * Rewrites the attachment list into the end of the description.
         *
         * Nextcloud Tasks on the web shows DESCRIPTION but ignores ATTACH, so a file attached
         * in Koto would be invisible there. Mirroring the links into the notes makes them
         * clickable on the web as well. The block is delimited and fully regenerated each
         * time, so repeated edits don't stack up copies of it.
         */
        fun withMirroredAttachments(todo: VTodo): VTodo {
            val body = todo.description
                ?.substringBefore(ATTACH_BLOCK_START)
                ?.trimEnd()
                ?.ifBlank { null }
            if (todo.attachments.isEmpty()) return todo.copy(description = body)
            val block = buildString {
                append(ATTACH_BLOCK_START)
                todo.attachments.forEach { url ->
                    append("\n")
                    append(url.substringAfterLast('/'))
                    append("\n")
                    append(url)
                }
            }
            return todo.copy(description = listOfNotNull(body, block).joinToString("\n\n"))
        }

        /** Properties VTodo models itself. Anything else is preserved verbatim. */
        private val KNOWN = setOf(
            "UID", "SUMMARY", "DESCRIPTION", "DUE", "DTSTART", "COMPLETED", "STATUS",
            "PERCENT-COMPLETE", "PRIORITY", "CATEGORIES", "RELATED-TO", "RRULE",
            "LOCATION", "CREATED", "LAST-MODIFIED", "SEQUENCE", "DTSTAMP",
            "GEO", "ATTACH", "X-APPLE-SORT-ORDER", RADIUS_PROP,
        )

        /**
         * Manual ordering lives on the task, not in Koto's sidecar.
         *
         * X-APPLE-SORT-ORDER is what Nextcloud Tasks itself uses for drag-and-drop, so an order
         * set here shows up on the web and vice versa. It also sidesteps the merge problem a
         * per-device position field would create: the order syncs through CalDAV like any other
         * property, with the same conflict handling.
         */
        const val SORT_PROP = "X-APPLE-SORT-ORDER"

        /** Radius in metres for a location reminder. No standard property covers this. */
        const val RADIUS_PROP = "X-KOTO-GEO-RADIUS"

        fun newTask(summary: String): VTodo = VTodo(
            uid = "koto-" + UUID.randomUUID().toString(),
            summary = summary,
            status = TodoStatus.NEEDS_ACTION,
            created = System.currentTimeMillis(),
        )

        /**
         * Parses the first VTODO out of an iCalendar object. Returns null when the resource holds
         * no VTODO (a VEVENT shared into a tasks collection, say) rather than inventing one.
         */
        fun parse(ics: String): VTodo? {
            val lines = ICalendar.unfold(ics)
            val todoStart = lines.indexOfFirst { it.equals("BEGIN:VTODO", true) }
            if (todoStart < 0) return null
            val todoEnd = lines.indexOfFirst { it.equals("END:VTODO", true) }
            if (todoEnd < todoStart) return null

            val preamble = lines.subList(0, todoStart).filterNot { it.equals("BEGIN:VCALENDAR", true) }
            val trailer = lines.subList(todoEnd + 1, lines.size).filterNot { it.equals("END:VCALENDAR", true) }
            val body = lines.subList(todoStart + 1, todoEnd)

            var uid: String? = null
            var summary = ""
            var description: String? = null
            var due: DateVal? = null
            var start: DateVal? = null
            var completed: Long? = null
            var status: String? = null
            var percent: Int? = null
            var priority = 0
            var categories = emptyList<String>()
            var parentUid: String? = null
            var rrule: String? = null
            var location: String? = null
            var latitude: Double? = null
            var longitude: Double? = null
            var geoRadius: Int? = null
            val attachments = ArrayList<String>()
            var sortOrder: Int? = null
            var created: Long? = null
            var lastModified: Long? = null
            var sequence = 0
            val alarms = ArrayList<VAlarm>()
            val unknown = ArrayList<String>()

            var i = 0
            while (i < body.size) {
                val raw = body[i]
                if (raw.equals("BEGIN:VALARM", true)) {
                    val end = body.subList(i, body.size).indexOfFirst { it.equals("END:VALARM", true) }
                    if (end < 0) { i = body.size; continue }
                    val block = body.subList(i, i + end + 1)
                    block.mapNotNull { ICalendar.parseLine(it) }
                        .firstOrNull { it.name == "TRIGGER" }
                        ?.let { VAlarm.parseTrigger(it) }
                        ?.let { alarms.add(it) }
                    i += end + 1
                    continue
                }
                val line = ICalendar.parseLine(raw)
                if (line == null) { i++; continue }
                when (line.name) {
                    "UID" -> uid = line.value.trim()
                    "SUMMARY" -> summary = ICalendar.unescapeText(line.value)
                    "DESCRIPTION" -> description = ICalendar.unescapeText(line.value).ifBlank { null }
                    "DUE" -> due = DateVal.parse(line)
                    "DTSTART" -> start = DateVal.parse(line)
                    "COMPLETED" -> completed = DateVal.parse(line)?.millis
                    "STATUS" -> status = line.value.trim().uppercase()
                    "PERCENT-COMPLETE" -> percent = line.value.trim().toIntOrNull()
                    "PRIORITY" -> priority = line.value.trim().toIntOrNull()?.coerceIn(0, 9) ?: 0
                    "CATEGORIES" -> categories = categories + ICalendar.splitTextList(line.value)
                    "RELATED-TO" -> {
                        // No RELTYPE means PARENT, per RFC 5545.
                        val relType = line.param("RELTYPE")?.uppercase() ?: "PARENT"
                        if (relType == "PARENT") parentUid = line.value.trim()
                        else unknown.add(raw)
                    }
                    "RRULE" -> rrule = line.value.trim()
                    "LOCATION" -> location = ICalendar.unescapeText(line.value).ifBlank { null }
                    "GEO" -> {
                        // RFC 5545 writes GEO as "lat;lon". Some clients use a comma instead.
                        val parts = line.value.trim().split(';', ',')
                        if (parts.size >= 2) {
                            latitude = parts[0].trim().toDoubleOrNull()
                            longitude = parts[1].trim().toDoubleOrNull()
                        }
                    }
                    RADIUS_PROP -> geoRadius = line.value.trim().toIntOrNull()
                    "ATTACH" -> line.value.trim().takeIf { it.isNotBlank() }?.let { attachments.add(it) }
                    "X-APPLE-SORT-ORDER" -> sortOrder = line.value.trim().toIntOrNull()
                    "CREATED" -> created = DateVal.parse(line)?.millis
                    "LAST-MODIFIED" -> lastModified = DateVal.parse(line)?.millis
                    "SEQUENCE" -> sequence = line.value.trim().toIntOrNull() ?: 0
                    "DTSTAMP" -> Unit // always regenerated on write
                    else -> if (line.name !in KNOWN) unknown.add(raw)
                }
                i++
            }

            val resolvedUid = uid ?: return null
            return VTodo(
                uid = resolvedUid,
                summary = summary,
                description = description,
                due = due,
                start = start,
                completed = completed,
                status = status,
                percentComplete = percent,
                priority = priority,
                categories = categories.distinct(),
                parentUid = parentUid,
                rrule = rrule,
                location = location,
                latitude = latitude,
                longitude = longitude,
                geoRadius = geoRadius,
                attachments = attachments.distinct(),
                sortOrder = sortOrder,
                created = created,
                lastModified = lastModified,
                sequence = sequence,
                alarms = alarms,
                unknownLines = unknown,
                preamble = preamble,
                trailer = trailer,
            )
        }
    }

    /** Serializes back to a complete iCalendar object, ready to PUT. */
    fun toIcs(): String {
        val out = ArrayList<String>()
        out.add("BEGIN:VCALENDAR")
        if (preamble.none { it.startsWith("VERSION", true) }) out.add("VERSION:2.0")
        if (preamble.none { it.startsWith("PRODID", true) }) out.add("PRODID:-//BARBURAS//Koto//EN")
        out.addAll(preamble)
        out.add("BEGIN:VTODO")
        out.add("UID:$uid")
        out.add(DateVal.utcNow().toLine("DTSTAMP"))
        created?.let { out.add(DateVal(it, dateOnly = false, utc = true).toLine("CREATED")) }
        out.add(DateVal.utcNow().toLine("LAST-MODIFIED"))
        out.add("SEQUENCE:$sequence")
        out.add("SUMMARY:${ICalendar.escapeText(summary)}")
        description?.takeIf { it.isNotBlank() }?.let { out.add("DESCRIPTION:${ICalendar.escapeText(it)}") }
        start?.let { out.add(it.toLine("DTSTART")) }
        due?.let { out.add(it.toLine("DUE")) }
        status?.let { out.add("STATUS:$it") }
        completed?.let { out.add(DateVal(it, dateOnly = false, utc = true).toLine("COMPLETED")) }
        percentComplete?.let { out.add("PERCENT-COMPLETE:$it") }
        if (priority > 0) out.add("PRIORITY:$priority")
        if (categories.isNotEmpty()) out.add("CATEGORIES:${categories.joinToString(",") { ICalendar.escapeText(it) }}")
        parentUid?.let { out.add("RELATED-TO;RELTYPE=PARENT:$it") }
        rrule?.takeIf { it.isNotBlank() }?.let { out.add("RRULE:$it") }
        location?.takeIf { it.isNotBlank() }?.let { out.add("LOCATION:${ICalendar.escapeText(it)}") }
        if (latitude != null && longitude != null) out.add("GEO:$latitude;$longitude")
        geoRadius?.let { out.add("$RADIUS_PROP:$it") }
        sortOrder?.let { out.add("$SORT_PROP:$it") }
        attachments.forEach { out.add("ATTACH:$it") }
        out.addAll(unknownLines)
        alarms.forEach { out.addAll(it.toLines()) }
        out.add("END:VTODO")
        out.addAll(trailer)
        out.add("END:VCALENDAR")
        return out.joinToString("\r\n") { ICalendar.fold(it) } + "\r\n"
    }

    /** Marks done. Recurring tasks are rolled forward instead -- see [Recurrence.completeRecurring]. */
    fun markCompleted(now: Long = System.currentTimeMillis()): VTodo = copy(
        status = TodoStatus.COMPLETED,
        completed = now,
        percentComplete = 100,
        sequence = sequence + 1,
        lastModified = now,
    )

    fun markNotCompleted(now: Long = System.currentTimeMillis()): VTodo = copy(
        status = TodoStatus.NEEDS_ACTION,
        completed = null,
        percentComplete = null,
        sequence = sequence + 1,
        lastModified = now,
    )
}
