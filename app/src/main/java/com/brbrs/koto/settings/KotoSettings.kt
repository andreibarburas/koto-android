package com.brbrs.koto.settings

import org.json.JSONArray
import org.json.JSONObject

/**
 * Everything Koto needs that a VTODO has no field for.
 *
 * This is the sidecar: one small JSON file in the user's own Nextcloud (`/Koto/koto-settings.json`)
 * holding app preferences and per-list display state, so they follow the person to a new device.
 *
 * It is deliberately a cache with defaults, never a source of truth. Koto must render correctly
 * with this file entirely absent, because a fresh install or a second device will hit exactly
 * that. Nothing here is keyed by task UID: a per-task sidecar would need an orphan sweep every
 * sync (complete a task on the web, it's deleted, its sidecar entry is stranded), and that
 * complexity is not worth buying before the CalDAV layer has been lived with.
 */
data class KotoSettings(
    val themeMode: String = "system",
    val textSize: String = "default",
    val useCustomFont: Boolean = true,
    val syncIntervalMinutes: Int = 60,
    val remindersEnabled: Boolean = true,
    /** Notify on arrival at a task's GEO coordinates. Off by default: it needs location access. */
    val locationRemindersEnabled: Boolean = false,
    /** Minutes before the due date to notify. */
    val reminderOffsetsMinutes: List<Int> = listOf(0, 1440),
    /** "due", "priority", "alpha" or "created". */
    val sortMode: String = "due",
    val showCompleted: Boolean = false,
    /** Keep finished tasks visible for this many days in the Completed filter. */
    val completedRetentionDays: Int = 30,
    val groupByList: Boolean = false,
    /** Collection hrefs hidden from the combined views. */
    val hiddenLists: List<String> = emptyList(),
    /** Collection href -> position, so list order survives a reinstall. */
    val listOrder: Map<String, Int> = emptyMap(),
    /** Collection href -> Material icon name from [com.brbrs.koto.icons.IconCatalog]. */
    val listIcons: Map<String, String> = emptyMap(),
    /** Tag -> hex color. CATEGORIES is a bare string in iCalendar, so this can only live here. */
    val tagColors: Map<String, String> = emptyMap(),
    /** Collection href -> cover token (short UUID fragment). Token names the file in Nextcloud and local cache. */
    val listCovers: Map<String, String> = emptyMap(),
    /** Where a quick-added task goes when no list is chosen. */
    val defaultListHref: String? = null,
    /** Parse "tomorrow", "!2", "#tag" out of the quick-add box. */
    val smartQuickAdd: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("themeMode", themeMode)
        put("textSize", textSize)
        put("useCustomFont", useCustomFont)
        put("syncIntervalMinutes", syncIntervalMinutes)
        put("remindersEnabled", remindersEnabled)
        put("locationRemindersEnabled", locationRemindersEnabled)
        put("reminderOffsetsMinutes", JSONArray(reminderOffsetsMinutes))
        put("sortMode", sortMode)
        put("showCompleted", showCompleted)
        put("completedRetentionDays", completedRetentionDays)
        put("groupByList", groupByList)
        put("hiddenLists", JSONArray(hiddenLists))
        put("listOrder", JSONObject().apply { listOrder.forEach { (k, v) -> put(k, v) } })
        put("listIcons", JSONObject().apply { listIcons.forEach { (k, v) -> put(k, v) } })
        put("tagColors", JSONObject().apply { tagColors.forEach { (k, v) -> put(k, v) } })
        put("listCovers", JSONObject().apply { listCovers.forEach { (k, v) -> put(k, v) } })
        defaultListHref?.let { put("defaultListHref", it) }
        put("smartQuickAdd", smartQuickAdd)
    }

    companion object {
        fun fromJson(json: JSONObject): KotoSettings {
            val offsets = json.optJSONArray("reminderOffsetsMinutes")?.let { arr ->
                (0 until arr.length()).map { arr.optInt(it) }
            } ?: listOf(0, 1440)
            val hidden = json.optJSONArray("hiddenLists")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
            } ?: emptyList()
            val icons = mutableMapOf<String, String>()
            json.optJSONObject("listIcons")?.let { obj ->
                obj.keys().forEach { k -> obj.optString(k).takeIf { it.isNotBlank() }?.let { icons[k] = it } }
            }
            val tagColors = mutableMapOf<String, String>()
            json.optJSONObject("tagColors")?.let { obj ->
                obj.keys().forEach { k -> obj.optString(k).takeIf { it.isNotBlank() }?.let { tagColors[k] = it } }
            }
            val order = mutableMapOf<String, Int>()
            json.optJSONObject("listOrder")?.let { obj ->
                obj.keys().forEach { k -> order[k] = obj.optInt(k) }
            }
            val listCovers = mutableMapOf<String, String>()
            json.optJSONObject("listCovers")?.let { obj ->
                obj.keys().forEach { k -> obj.optString(k).takeIf { it.isNotBlank() }?.let { listCovers[k] = it } }
            }
            return KotoSettings(
                themeMode = json.optString("themeMode", "system"),
                textSize = json.optString("textSize", "default"),
                useCustomFont = json.optBoolean("useCustomFont", true),
                syncIntervalMinutes = json.optInt("syncIntervalMinutes", 60),
                remindersEnabled = json.optBoolean("remindersEnabled", true),
                locationRemindersEnabled = json.optBoolean("locationRemindersEnabled", false),
                reminderOffsetsMinutes = offsets,
                sortMode = json.optString("sortMode", "due"),
                showCompleted = json.optBoolean("showCompleted", false),
                completedRetentionDays = json.optInt("completedRetentionDays", 30),
                groupByList = json.optBoolean("groupByList", false),
                hiddenLists = hidden,
                listOrder = order,
                listIcons = icons,
                tagColors = tagColors,
                listCovers = listCovers,
                defaultListHref = json.optString("defaultListHref").takeIf { it.isNotBlank() },
                smartQuickAdd = json.optBoolean("smartQuickAdd", true),
            )
        }
    }
}
