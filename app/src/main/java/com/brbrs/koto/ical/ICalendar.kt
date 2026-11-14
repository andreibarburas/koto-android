package com.brbrs.koto.ical

/**
 * A minimal, strict RFC 5545 content-line reader/writer.
 *
 * Koto parses iCalendar by hand rather than pulling in ical4j: ical4j drags in a large
 * dependency tree that needs careful R8 rules on Android (the suite already got bitten by
 * reflective libraries, see Retrofit/Gson), and Koto only needs VTODO. The safety net that
 * makes a hand-rolled parser acceptable is [VTodo.unknownLines]: every property Koto does not
 * understand is kept verbatim and written back out untouched, so a task edited here never
 * loses data another client put there.
 */
object ICalendar {

    /** One content line: NAME;PARAM=VALUE;...:VALUE */
    data class Line(val name: String, val params: Map<String, String>, val value: String) {
        fun param(key: String): String? = params[key.uppercase()]
    }

    /**
     * Unfolds RFC 5545 folded lines. A line break followed by a single space or tab is a
     * continuation, not a new line. Handles CRLF, LF and lone CR, since servers vary.
     */
    fun unfold(raw: String): List<String> {
        val normalized = raw.replace("\r\n", "\n").replace('\r', '\n')
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var first = true
        for (line in normalized.split('\n')) {
            if (!first && (line.startsWith(" ") || line.startsWith("\t"))) {
                sb.append(line.substring(1))
            } else {
                if (!first) out.add(sb.toString())
                sb.setLength(0)
                sb.append(line)
                first = false
            }
        }
        if (!first) out.add(sb.toString())
        return out.filter { it.isNotBlank() }
    }

    /** Folds a content line at 75 octets, continuing with a leading space, as RFC 5545 requires. */
    fun fold(line: String): String {
        if (line.toByteArray(Charsets.UTF_8).size <= 75) return line
        val out = StringBuilder()
        var count = 0
        var lineBytes = 0
        for (ch in line) {
            val chBytes = ch.toString().toByteArray(Charsets.UTF_8).size
            // 74 leaves room for the leading space on the continuation line.
            if (lineBytes + chBytes > (if (count == 0) 75 else 74)) {
                out.append("\r\n ")
                lineBytes = 0
                count++
            }
            out.append(ch)
            lineBytes += chBytes
        }
        return out.toString()
    }

    /** Splits one unfolded content line into name, params and value. */
    fun parseLine(raw: String): Line? {
        // The value starts at the first colon that is not inside a quoted parameter value.
        var colon = -1
        var inQuotes = false
        for (i in raw.indices) {
            val c = raw[i]
            if (c == '"') inQuotes = !inQuotes
            if (c == ':' && !inQuotes) { colon = i; break }
        }
        if (colon < 0) return null
        val head = raw.substring(0, colon)
        val value = raw.substring(colon + 1)

        val headParts = splitUnquoted(head, ';')
        if (headParts.isEmpty()) return null
        val name = headParts[0].uppercase()
        val params = LinkedHashMap<String, String>()
        for (i in 1 until headParts.size) {
            val eq = headParts[i].indexOf('=')
            if (eq <= 0) continue
            val k = headParts[i].substring(0, eq).uppercase()
            val v = headParts[i].substring(eq + 1).trim('"')
            params[k] = v
        }
        return Line(name, params, value)
    }

    private fun splitUnquoted(s: String, delim: Char): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (c in s) {
            when {
                c == '"' -> { inQuotes = !inQuotes; sb.append(c) }
                c == delim && !inQuotes -> { out.add(sb.toString()); sb.setLength(0) }
                else -> sb.append(c)
            }
        }
        out.add(sb.toString())
        return out
    }

    /** Unescapes a TEXT value: \n \, \; \\ as RFC 5545 defines them. */
    fun unescapeText(v: String): String {
        val sb = StringBuilder(v.length)
        var i = 0
        while (i < v.length) {
            val c = v[i]
            if (c == '\\' && i + 1 < v.length) {
                when (val next = v[i + 1]) {
                    'n', 'N' -> sb.append('\n')
                    '\\' -> sb.append('\\')
                    ',' -> sb.append(',')
                    ';' -> sb.append(';')
                    else -> { sb.append(next) }
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    fun escapeText(v: String): String = v
        .replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace(",", "\\,")
        .replace(";", "\\;")

    /** Splits a comma-separated TEXT list (e.g. CATEGORIES), respecting escaped commas. */
    fun splitTextList(v: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < v.length) {
            val c = v[i]
            when {
                c == '\\' && i + 1 < v.length -> { sb.append(c).append(v[i + 1]); i += 2 }
                c == ',' -> { out.add(sb.toString()); sb.setLength(0); i++ }
                else -> { sb.append(c); i++ }
            }
        }
        out.add(sb.toString())
        return out.map { unescapeText(it).trim() }.filter { it.isNotBlank() }
    }
}
