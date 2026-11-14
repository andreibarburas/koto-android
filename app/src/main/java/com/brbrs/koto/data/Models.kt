package com.brbrs.koto.data

import androidx.compose.runtime.Immutable
import com.brbrs.koto.ical.DateVal
import com.brbrs.koto.ical.VTodo

@Immutable
data class TaskList(
    val href: String,
    val displayName: String,
    val color: String,
    val readOnly: Boolean,
    val sortOrder: Int,
)

/** A task plus the bits of UI state Koto derives from the rest of the list. */
@Immutable
data class Task(
    val href: String,
    val listHref: String,
    val todo: VTodo,
    val etag: String?,
    /** True when a local change hasn't reached the server yet. */
    val pending: Boolean,
) {
    val uid get() = todo.uid
    val title get() = todo.summary
    val due: DateVal? get() = todo.due
    val isCompleted get() = todo.isCompleted
}

/** A task with its direct children, which is how the list screen renders a parent. */
@Immutable
data class TaskNode(
    val task: Task,
    val children: List<Task>,
    val listName: String,
    val listColor: String,
) {
    val doneChildren: Int get() = children.count { it.isCompleted }
}

/** How the main list is grouped. */
enum class TaskBucket { Overdue, Today, Tomorrow, ThisWeek, Later, NoDate, Completed, Everything }

/**
 * [All] means every open task; [Everything] adds the finished ones in the same list, for when
 * you want the literal contents of a list rather than a working view of it.
 */
enum class TaskFilter { Today, Upcoming, All, Completed, Everything }

enum class SortMode { DueDate, Priority, Alphabetical, Created, Manual }
