# Changelog

All notable changes to Koto are documented here.

## [1.0.0] - unreleased

First release.

### Fixed (during first-device testing)
- Tapping a linked task from Vinci (or another suite app) said "This task is no longer here" -- the `koto://task/{id}` deep link only ever matched a task's real (mutable) href, but cross-app links point at the task's stable CalDAV uid instead, since href can change (e.g. on rename). The task screen now tries the href first and falls back to a uid lookup, so both the in-app/reminder case and the cross-app deep-link case resolve correctly
- Whole lists downloaded no tasks at all: on the first sync each list adopted the sync token the server reports during discovery, before anything had been fetched, so the first incremental sync asked for "changes since now" and got nothing. Lists whose server reports a token stayed permanently empty while the rest worked. A list with a token but an empty cache now re-downloads itself, so existing installs recover without clearing data
- Tasks with longer content never appeared: the CalDAV response reader took only the first chunk of each `calendar-data` element, truncating the iCalendar mid-task so it failed to parse and was dropped
- The logo in the top bar sat in the middle instead of on the left
- Tapping a list on the Lists screen went to the task list without actually scoping it to that list
- Opening a list left the Today filter on, so the list looked almost empty; it now widens to All
- A subtask whose list differs from its parent's disappeared when scoping to either list; it is now shown in its own list as a row of its own
- The counts on the filter chips ignored subtasks, so they read lower than the number of tasks actually in a list

### Added (seventh round)
- Linked items: link a task to a Vinci contact, a Nóta note, a Tavla card, or any other pasted link -- mirrors Tavla's own "Linked items"
- "Send to Nóta" and "Send to Tavla" from a task's menu, sending its title and description over as a new note or card

### Changed (seventh round)
- The "link a contact" picker, and a task's own "Linked contacts" list, now show each contact's actual photo instead of a generic icon
- When a task's contact link fails to sync to Nextcloud, that's now shown directly under "Linked contacts" instead of failing silently

### Fixed (seventh round)
- Linking a contact to a task could silently never reach Nextcloud: the push used an ETag-conditional PUT to guard against another device overwriting a conflicting edit, but there was never anything to actually merge on a conflict (Room is the sole source of truth for these links either way), and on some Nextcloud setups the retry kept 412'ing too, abandoning the push every time. The write is now unconditional, since the conflict handling was never protecting anything real

### Added (sixth round)
- Linked contacts: link a task directly to a Vinci contact from your address book -- it then shows up on that contact's profile in Vinci

### Added (fifth round)
- Tag management: a Tags screen from the Lists tab, showing every tag across every list with how many tasks carry it. Rename one and Koto rewrites each task that uses it; remove one and it comes off everything, leaving the tasks alone. Tags can be given a color, which shows on their chips throughout the app
- Recurrence now covers "every weekday", "the last Friday of the month", "the second Monday", and "the last weekday of the month" — ordinal BYDAY and BYSETPOS, on top of the simple frequencies
- A periodic location check as a backstop for the proximity alerts, which the platform delivers less reliably than Google's geofencing does. It reads the last known position rather than requesting a fix, so it costs no GPS, and an arrival is only ever notified once however it is noticed
- Background location is now requested properly as a separate, second grant, which is the only way Android 11 and later will give it

### Changed (fifth round)
- A new list icon set, in the Rounded style and chosen for personal task lists rather than reused from Tavla's work boards, so the two apps no longer look like the same object
- Attachment links are mirrored into a task's notes in a block Koto owns and regenerates. Nextcloud Tasks on the web ignores ATTACH, so without this a file attached in Koto was invisible there
- The database no longer falls back to a destructive migration. Offline edits waiting to sync exist nowhere else, so every future schema change needs a real migration; the schema JSON is now exported for migrations to be written and tested against

### Changed (fourth round)
- The task lists moved from a row of scope pills into a left-hand drawer, opened from the header. Each list shows its icon, color and open count, and the drawer scrolls, so it still works past half a dozen lists
- The Lists tab now uses the same row design as the boards on Tavla's front page: a colored tile, the list name, and its counts underneath. Long-press a list for its menu, which keeps the row itself clean
- Lists can be given an icon from the same curated Material set Tavla uses, with a few task-flavoured additions. The choice is stored in the Nextcloud sidecar keyed by collection, so it follows you between devices and merges rather than overwriting when two devices disagree

### Added (third round)
- The header — logo, search, theme toggle and Settings — is now on every tab instead of only the first. Search is scoped to whatever the tab shows: tasks, the agenda, or list names
- An Everything filter that shows the literal contents of a list, finished tasks included, alongside All which stays open-only
- Manual ordering. Choose Manual under Settings → Tasks, then long-press and drag. The position is written as X-APPLE-SORT-ORDER on the task itself, which is the property Nextcloud Tasks uses for its own drag-and-drop, so the order syncs rather than living on one device
- Location reminders. Give a task coordinates and a radius and Koto notifies you on arrival. Built on the AOSP LocationManager rather than Google Play Services geofencing, so the app stays free of Google dependencies and eligible for F-Droid. Off by default, capped at 40 watched places, and re-registered after a reboot
- Attachments can be added from a task's detail screen, not just by sharing into Koto. Files go to /Koto/attachments in your own Nextcloud and are linked with a standard ATTACH property
- GEO, ATTACH and X-APPLE-SORT-ORDER are now parsed and written as first-class properties rather than only preserved verbatim

### Changed (second round of device testing)
- Dates are shown as YYYY/MM/DD everywhere. Today, Tomorrow and Yesterday keep their words, since on a task list those read better than a number
- Agenda is now the first tab and the screen the app opens on
- The add button sits clear of the bottom bar and has a shadow, so it no longer disappears into the list behind it
- Share to Koto now accepts images, PDFs, audio, video and text files as well as plain text. Files are uploaded to `/Koto/attachments/<task uid>/` on your own Nextcloud and attached to the task with a standard ATTACH property, so other CalDAV clients see them too. Several files can be shared at once

### Fixed (second round of device testing)
- The checkbox on a task row sat higher than the task's title
- Tapping a subtask did nothing; subtasks now open their own detail screen, both from the task list and from a parent's detail screen

### Added
- Nextcloud Login Flow v2 sign-in, with the poll URL rewritten to the address you typed so it works behind Tailscale and custom subdomains, and a check that the account has at least one task list
- CalDAV sync against Nextcloud Tasks: collection discovery, incremental sync-collection with sync tokens, and a full fetch fallback when a token is rejected
- An iCalendar layer that keeps every property it does not model, so a task edited in Koto round-trips without losing data another client wrote
- Main task list grouped into Overdue, Today, Tomorrow, This week, Later and No date, with Today, Upcoming, All and Done filters and per-filter counts
- Agenda: a flat, dated timeline across every list
- Subtasks via RELATED-TO, shown inline under their parent with a done count; completing a parent closes its open children
- Due and start dates with an explicit all-day switch, so DATE and DATE-TIME never get confused
- Priority on the standard 1-9 scale, color-coded on the task checkbox
- Tags, notes, location, and a progress percentage that closes the task at 100
- Recurring tasks (daily, weekly with specific weekdays, monthly, yearly) with intervals, COUNT and UNTIL; completing one rolls the dates forward rather than splitting the series. A rule Koto cannot parse is shown as read-only and left untouched
- Local reminders from a task's own VALARM, or from global offsets in Settings when it has none; all-day tasks fire at 09:00 rather than midnight. A Complete action on the notification ticks the task off without opening the app
- Smart quick add: recognizes "tomorrow", "friday 17:30", "!2" and "#tag" out of what you type
- Search across every task's title, notes and tags
- Create, rename, recolor and delete task lists; hide a list from the combined views without changing anything on the server; pick a default list for quick add
- Offline copy of every list in a local database; edits made offline are queued and pushed on the next sync, and a conflicting edit on the server is merged rather than overwritten
- Share text or a link from any app straight into a task
- Background sync on a configurable interval (15 minutes to 3 hours)
- Optional biometric app lock
- Theme (System, Light, Dark, OLED Black), text size, and the DM Serif Display + Inter Tight font pairing
- Settings, hidden lists and the default list live in a small JSON file in your own Nextcloud (`/Koto/koto-settings.json`), so they follow you to a new device
- Plain HTTP support for private networks
- `koto://task/{href}` deep links, so other BarburasLab apps can open a specific task
