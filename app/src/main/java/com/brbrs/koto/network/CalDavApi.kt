package com.brbrs.koto.network

import android.util.Xml
import com.brbrs.koto.auth.KotoSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

class CalDavException(val code: Int, message: String) : Exception(message)

/** A VTODO collection on the server. */
data class RemoteTaskList(
    val href: String,
    val displayName: String,
    val color: String?,
    val syncToken: String?,
    val readOnly: Boolean,
)

/** One task resource as it exists on the server. */
data class RemoteTask(val href: String, val etag: String?, val ics: String)

/** The result of a sync-collection REPORT. */
data class SyncResult(
    val changed: List<RemoteTask>,
    val removedHrefs: List<String>,
    val newSyncToken: String?,
    /** True when the server rejected the token (507/403 valid-sync-token) and a full resync is needed. */
    val tokenInvalid: Boolean = false,
)

/**
 * CalDAV against Nextcloud, over OkHttp with XmlPullParser for the multistatus bodies.
 *
 * Nextcloud's DAV root is predictable (`/remote.php/dav/calendars/{user}/`), so Koto goes
 * straight there rather than walking current-user-principal first; that saves two round trips
 * on every cold start and is the same shortcut the official clients take.
 */
@Singleton
class CalDavApi @Inject constructor(
    private val httpClient: OkHttpClient,
) {
    private val xmlMedia = "application/xml; charset=utf-8".toMediaType()
    private val icsMedia = "text/calendar; charset=utf-8".toMediaType()

    private fun calendarHome(s: KotoSession) =
        "${s.serverUrl.trimEnd('/')}/remote.php/dav/calendars/${s.username}/"

    /** Turns a server-returned href (usually a path) into an absolute URL on the user's server. */
    fun absolute(s: KotoSession, href: String): String {
        if (href.startsWith("http://") || href.startsWith("https://")) {
            // The server may report its internal host. Keep the path, use the address the user typed.
            val path = runCatching { URI(href).rawPath }.getOrNull() ?: return href
            return s.serverUrl.trimEnd('/') + path
        }
        return s.serverUrl.trimEnd('/') + "/" + href.trimStart('/')
    }

    private fun Request.Builder.auth(s: KotoSession) = header("Authorization", s.basicAuthHeader())

    private fun Response.require(): Response {
        if (!isSuccessful && code != 207) throw CalDavException(code, "HTTP $code for ${request.url}")
        return this
    }

    // ── Discovery ────────────────────────────────────────────────────────────────

    suspend fun listTaskLists(s: KotoSession): List<RemoteTaskList> = withContext(Dispatchers.IO) {
        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:" xmlns:cs="http://calendarserver.org/ns/"
                        xmlns:c="urn:ietf:params:xml:ns:caldav"
                        xmlns:ic="http://apple.com/ns/ical/">
              <d:prop>
                <d:displayname />
                <d:resourcetype />
                <d:sync-token />
                <d:current-user-privilege-set />
                <c:supported-calendar-component-set />
                <ic:calendar-color />
              </d:prop>
            </d:propfind>
        """.trimIndent()
        val request = Request.Builder()
            .url(calendarHome(s))
            .auth(s)
            .header("Depth", "1")
            .method("PROPFIND", body.toRequestBody(xmlMedia))
            .build()
        httpClient.newCall(request).execute().use { response ->
            response.require()
            parseCalendarList(response.body!!.string())
        }
    }

    private fun parseCalendarList(xml: String): List<RemoteTaskList> {
        val out = ArrayList<RemoteTaskList>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(StringReader(xml))

        var href: String? = null
        var displayName: String? = null
        var color: String? = null
        var syncToken: String? = null
        var isCalendar = false
        var supportsTodo = false
        var sawCompSet = false
        var sawPrivileges = false
        var canWrite = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name
                    val ns = parser.namespace
                    when {
                        ns == NS_DAV && name == "response" -> {
                            href = null; displayName = null; color = null; syncToken = null
                            isCalendar = false; supportsTodo = false; sawCompSet = false
                            sawPrivileges = false; canWrite = false
                        }
                        ns == NS_DAV && name == "href" && href == null -> href = safeText(parser)?.trim()
                        ns == NS_DAV && name == "displayname" -> displayName = safeText(parser)
                        ns == NS_DAV && name == "sync-token" -> syncToken = safeText(parser)
                        ns == NS_ICAL && name == "calendar-color" -> color = safeText(parser)
                        ns == NS_CALDAV && name == "calendar" -> isCalendar = true
                        ns == NS_CALDAV && name == "supported-calendar-component-set" -> sawCompSet = true
                        ns == NS_CALDAV && name == "comp" -> {
                            if (parser.getAttributeValue(null, "name").equals("VTODO", true)) supportsTodo = true
                        }
                        ns == NS_DAV && name == "current-user-privilege-set" -> sawPrivileges = true
                        ns == NS_DAV && name == "write" -> canWrite = true
                        ns == NS_DAV && name == "write-content" -> canWrite = true
                        ns == NS_DAV && name == "all" -> canWrite = true
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.namespace == NS_DAV && parser.name == "response") {
                        val h = href
                        // A collection with no supported-calendar-component-set accepts everything,
                        // per RFC 4791; only exclude one that explicitly lists components without VTODO.
                        val takesTodos = supportsTodo || !sawCompSet
                        if (h != null && isCalendar && takesTodos && !h.trimEnd('/').endsWith("inbox") &&
                            !h.trimEnd('/').endsWith("outbox")
                        ) {
                            out.add(
                                RemoteTaskList(
                                    href = h,
                                    displayName = displayName?.takeIf { it.isNotBlank() }
                                        ?: h.trimEnd('/').substringAfterLast('/'),
                                    color = color?.removePrefix("#")?.take(6)?.uppercase(),
                                    syncToken = syncToken,
                                    // A server that doesn't report privileges is assumed writable.
                                    // Defaulting the other way would silently make every list
                                    // read-only and the app useless on that server.
                                    readOnly = sawPrivileges && !canWrite,
                                )
                            )
                        }
                    }
                }
            }
            event = parser.next()
        }
        return out
    }

    // ── Reading tasks ────────────────────────────────────────────────────────────

    /** Full download of every VTODO in a collection. Used on first sync and after a token reset. */
    suspend fun fetchAllTasks(s: KotoSession, listHref: String): List<RemoteTask> = withContext(Dispatchers.IO) {
        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <c:calendar-query xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
              <d:prop>
                <d:getetag />
                <c:calendar-data />
              </d:prop>
              <c:filter>
                <c:comp-filter name="VCALENDAR">
                  <c:comp-filter name="VTODO" />
                </c:comp-filter>
              </c:filter>
            </c:calendar-query>
        """.trimIndent()
        val request = Request.Builder()
            .url(absolute(s, listHref))
            .auth(s)
            .header("Depth", "1")
            .method("REPORT", body.toRequestBody(xmlMedia))
            .build()
        httpClient.newCall(request).execute().use { response ->
            response.require()
            parseMultistatus(response.body!!.string()).first
        }
    }

    /** Incremental sync. Returns [SyncResult.tokenInvalid] when the caller must fall back to a full fetch. */
    suspend fun syncCollection(s: KotoSession, listHref: String, syncToken: String?): SyncResult =
        withContext(Dispatchers.IO) {
            val body = """
                <?xml version="1.0" encoding="utf-8"?>
                <d:sync-collection xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
                  <d:sync-token>${syncToken.orEmpty()}</d:sync-token>
                  <d:sync-level>1</d:sync-level>
                  <d:prop>
                    <d:getetag />
                    <c:calendar-data />
                  </d:prop>
                </d:sync-collection>
            """.trimIndent()
            val request = Request.Builder()
                .url(absolute(s, listHref))
                .auth(s)
                .method("REPORT", body.toRequestBody(xmlMedia))
                .build()
            httpClient.newCall(request).execute().use { response ->
                // 403 with valid-sync-token, or 409, means the token is too old to honour.
                if (response.code == 403 || response.code == 409) {
                    return@use SyncResult(emptyList(), emptyList(), null, tokenInvalid = true)
                }
                response.require()
                val (changed, removed, token) = parseSyncReport(response.body!!.string())
                SyncResult(changed, removed, token)
            }
        }

    // ── Writing ──────────────────────────────────────────────────────────────────

    /**
     * Creates or updates a task resource. Pass [ifMatchEtag] for an update (so a concurrent edit
     * from another client is rejected rather than clobbered) or set [isNew] to use If-None-Match
     * on a create. Returns the new ETag, which Nextcloud may omit -- the caller then re-reads.
     */
    suspend fun putTask(
        s: KotoSession,
        href: String,
        ics: String,
        ifMatchEtag: String? = null,
        isNew: Boolean = false,
    ): String? = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(absolute(s, href))
            .auth(s)
            .put(ics.toRequestBody(icsMedia))
        when {
            isNew -> builder.header("If-None-Match", "*")
            ifMatchEtag != null -> builder.header("If-Match", ifMatchEtag)
        }
        httpClient.newCall(builder.build()).execute().use { response ->
            if (response.code == 412) throw CalDavException(412, "The task changed on the server")
            response.require()
            response.header("ETag")
        }
    }

    suspend fun getTask(s: KotoSession, href: String): RemoteTask? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(absolute(s, href)).auth(s).get().build()
        httpClient.newCall(request).execute().use { response ->
            when {
                response.code == 404 -> null
                else -> {
                    response.require()
                    RemoteTask(href, response.header("ETag"), response.body?.string().orEmpty())
                }
            }
        }
    }

    suspend fun deleteTask(s: KotoSession, href: String, etag: String?): Unit = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(absolute(s, href)).auth(s).delete()
        if (etag != null) builder.header("If-Match", etag)
        httpClient.newCall(builder.build()).execute().use { response ->
            // Already gone is a success as far as Koto is concerned.
            if (response.code == 404 || response.code == 410) return@use
            response.require()
        }
    }

    /** Creates a new task list (RFC 4791 MKCALENDAR, restricted to VTODO). */
    suspend fun createTaskList(s: KotoSession, name: String, colorHex: String): String =
        withContext(Dispatchers.IO) {
            val slug = "koto-" + java.util.UUID.randomUUID().toString().take(8)
            val url = calendarHome(s) + slug + "/"
            val body = """
                <?xml version="1.0" encoding="utf-8"?>
                <c:mkcalendar xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav"
                              xmlns:ic="http://apple.com/ns/ical/">
                  <d:set>
                    <d:prop>
                      <d:displayname>${escapeXml(name)}</d:displayname>
                      <ic:calendar-color>#${colorHex.removePrefix("#").uppercase()}</ic:calendar-color>
                      <c:supported-calendar-component-set>
                        <c:comp name="VTODO" />
                      </c:supported-calendar-component-set>
                    </d:prop>
                  </d:set>
                </c:mkcalendar>
            """.trimIndent()
            val request = Request.Builder()
                .url(url).auth(s)
                .method("MKCALENDAR", body.toRequestBody(xmlMedia))
                .build()
            httpClient.newCall(request).execute().use { it.require() }
            URI(url).rawPath
        }

    suspend fun renameTaskList(s: KotoSession, listHref: String, name: String, colorHex: String?): Unit =
        withContext(Dispatchers.IO) {
            val colorProp = colorHex?.let {
                "<ic:calendar-color>#${it.removePrefix("#").uppercase()}</ic:calendar-color>"
            }.orEmpty()
            val body = """
                <?xml version="1.0" encoding="utf-8"?>
                <d:propertyupdate xmlns:d="DAV:" xmlns:ic="http://apple.com/ns/ical/">
                  <d:set>
                    <d:prop>
                      <d:displayname>${escapeXml(name)}</d:displayname>
                      $colorProp
                    </d:prop>
                  </d:set>
                </d:propertyupdate>
            """.trimIndent()
            val request = Request.Builder()
                .url(absolute(s, listHref)).auth(s)
                .method("PROPPATCH", body.toRequestBody(xmlMedia))
                .build()
            httpClient.newCall(request).execute().use { it.require() }
        }

    suspend fun deleteTaskList(s: KotoSession, listHref: String): Unit = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(absolute(s, listHref)).auth(s).delete().build()
        httpClient.newCall(request).execute().use { response ->
            if (response.code == 404) return@use
            response.require()
        }
    }

    // ── Multistatus parsing ──────────────────────────────────────────────────────

    private fun parseMultistatus(xml: String): Pair<List<RemoteTask>, List<String>> {
        val (changed, removed, _) = parseSyncReport(xml)
        return changed to removed
    }

    private fun parseSyncReport(xml: String): Triple<List<RemoteTask>, List<String>, String?> {
        val changed = ArrayList<RemoteTask>()
        val removed = ArrayList<String>()
        var newToken: String? = null

        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(StringReader(xml))

        var inResponse = false
        var href: String? = null
        var etag: String? = null
        var data: String? = null
        var statusCode = 0

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name
                    val ns = parser.namespace
                    when {
                        ns == NS_DAV && name == "response" -> {
                            inResponse = true; href = null; etag = null; data = null; statusCode = 0
                        }
                        ns == NS_DAV && name == "href" && inResponse && href == null -> href = safeText(parser)
                        ns == NS_DAV && name == "getetag" -> etag = safeText(parser)?.trim()
                        ns == NS_CALDAV && name == "calendar-data" -> data = safeText(parser)
                        ns == NS_DAV && name == "status" && inResponse -> {
                            val text = safeText(parser).orEmpty()
                            // Only a response-level status (not a propstat one) marks a deletion.
                            val code = Regex("""\b(\d{3})\b""").find(text)?.groupValues?.get(1)?.toIntOrNull()
                            if (code != null && statusCode == 0) statusCode = code
                        }
                        ns == NS_DAV && name == "sync-token" && !inResponse -> newToken = safeText(parser)?.trim()
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.namespace == NS_DAV && parser.name == "response") {
                        val h = href
                        when {
                            h == null -> Unit
                            statusCode == 404 || statusCode == 410 -> removed.add(h)
                            // A collection's own href comes back in the report with no calendar-data; skip it.
                            data != null && data!!.contains("BEGIN:VCALENDAR", ignoreCase = true) ->
                                changed.add(RemoteTask(h, etag, data!!))
                        }
                        inResponse = false
                    }
                }
            }
            event = parser.next()
        }
        return Triple(changed, removed, newToken)
    }

    /**
     * Reads an element's full text content.
     *
     * XmlPullParser is free to split character data across several TEXT events, and it does so
     * routinely for anything large -- which a <calendar-data> body with a real task in it always
     * is. Reading only the first chunk truncates the iCalendar mid-VTODO, so it no longer parses
     * and the task silently never appears. Accumulate until the closing tag instead.
     *
     * Returns null for an empty element rather than throwing, which nextText() does not. A parse
     * error mid-element keeps whatever was read, since a partial body is still worth trying.
     */
    private fun safeText(parser: XmlPullParser): String? {
        val sb = StringBuilder()
        var depth = 0
        try {
            loop@ while (true) {
                when (parser.next()) {
                    XmlPullParser.TEXT, XmlPullParser.CDSECT -> sb.append(parser.text)
                    // Defensive: some servers wrap calendar-data in a child element.
                    XmlPullParser.START_TAG -> depth++
                    XmlPullParser.END_TAG -> {
                        if (depth == 0) break@loop
                        depth--
                    }
                    XmlPullParser.END_DOCUMENT -> break@loop
                }
            }
        } catch (e: Exception) {
            // Keep what was read; the caller treats an unparseable body as a skipped task.
        }
        return sb.toString().takeIf { it.isNotEmpty() }
    }

    private fun escapeXml(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    companion object {
        private const val NS_DAV = "DAV:"
        private const val NS_CALDAV = "urn:ietf:params:xml:ns:caldav"
        private const val NS_ICAL = "http://apple.com/ns/ical/"
    }
}
