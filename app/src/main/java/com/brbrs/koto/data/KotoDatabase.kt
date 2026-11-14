package com.brbrs.koto.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import kotlinx.coroutines.flow.Flow

/**
 * The local cache. [TaskEntity.rawIcs] holds the full iCalendar object exactly as the server
 * sent it: every parsed column is a denormalized copy for querying and sorting, and the raw
 * text is what gets re-parsed and re-serialized on edit. That way a property Koto doesn't
 * model can still survive a round trip through this device.
 */
@Entity(tableName = "task_lists")
data class TaskListEntity(
    @PrimaryKey val href: String,
    val displayName: String,
    val color: String,
    val syncToken: String?,
    val readOnly: Boolean,
    val sortOrder: Int,
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val href: String,
    val listHref: String,
    val uid: String,
    val etag: String?,
    val rawIcs: String,
    // Denormalized for querying:
    val summary: String,
    val dueMillis: Long?,
    val dueDateOnly: Boolean,
    val startMillis: Long?,
    val completedMillis: Long?,
    val status: String?,
    val priority: Int,
    val percentComplete: Int?,
    val parentUid: String?,
    val hasRrule: Boolean,
    val categories: String,
    val createdMillis: Long?,
    val lastModified: Long?,
    /** Local edit not yet accepted by the server. */
    val pending: Boolean = false,
    /** Deleted locally, waiting for the server to confirm. */
    val deletedLocally: Boolean = false,
)

@Dao
abstract class TaskDao {

    // ── Lists
    @Query("SELECT * FROM task_lists ORDER BY sortOrder ASC, displayName COLLATE NOCASE ASC")
    abstract fun observeLists(): Flow<List<TaskListEntity>>

    @Query("SELECT * FROM task_lists")
    abstract suspend fun getLists(): List<TaskListEntity>

    @Query("SELECT * FROM task_lists WHERE href = :href")
    abstract suspend fun getList(href: String): TaskListEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertLists(lists: List<TaskListEntity>)

    @Query("UPDATE task_lists SET syncToken = :token WHERE href = :href")
    abstract suspend fun setSyncToken(href: String, token: String?)

    @Query("DELETE FROM task_lists WHERE href NOT IN (:keep)")
    abstract suspend fun deleteListsNotIn(keep: List<String>)

    @Query("DELETE FROM task_lists WHERE href = :href")
    abstract suspend fun deleteList(href: String)

    @Query("DELETE FROM task_lists")
    abstract suspend fun deleteAllLists()

    /**
     * Replaces the known collections. Tasks belonging to a list that vanished go too --
     * done as explicit deletes rather than ON DELETE CASCADE, because REPLACE on a parent row
     * would fire the cascade and silently wipe children that are still valid.
     */
    @Transaction
    open suspend fun replaceLists(lists: List<TaskListEntity>) {
        if (lists.isEmpty()) {
            deleteAllLists()
            deleteAllTasks()
            return
        }
        val hrefs = lists.map { it.href }
        deleteListsNotIn(hrefs)
        deleteTasksForListsNotIn(hrefs)
        // Preserve the sync token already stored for a list we've seen before: the discovery
        // PROPFIND may report an older token than the one our last sync-collection returned.
        val existing = getLists().associateBy { it.href }
        upsertLists(lists.map { l -> l.copy(syncToken = l.syncToken ?: existing[l.href]?.syncToken) })
    }

    // ── Tasks
    @Query("SELECT * FROM tasks WHERE deletedLocally = 0")
    abstract fun observeAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE listHref = :listHref AND deletedLocally = 0")
    abstract fun observeTasksForList(listHref: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE href = :href")
    abstract fun observeTask(href: String): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE href = :href")
    abstract suspend fun getTask(href: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE uid = :uid LIMIT 1")
    abstract suspend fun getTaskByUid(uid: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE deletedLocally = 0 AND completedMillis IS NULL")
    abstract suspend fun getAllForGeofencing(): List<TaskEntity>

    @Query("SELECT COUNT(*) FROM tasks WHERE listHref = :listHref")
    abstract suspend fun countTasksForList(listHref: String): Int

    @Query("SELECT * FROM tasks WHERE pending = 1 OR deletedLocally = 1")
    abstract suspend fun getUnsynced(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE parentUid = :parentUid AND deletedLocally = 0")
    abstract suspend fun getChildren(parentUid: String): List<TaskEntity>

    @Query(
        """
        SELECT * FROM tasks
        WHERE deletedLocally = 0 AND completedMillis IS NULL
          AND (status IS NULL OR status != 'COMPLETED')
          AND dueMillis IS NOT NULL AND dueMillis <= :until
        ORDER BY dueMillis ASC
        """
    )
    abstract suspend fun getDueBefore(until: Long): List<TaskEntity>

    /**
     * Completed or cancelled tasks that had a due date inside the alarm window.
     * Used after sync to cancel any stale alarms that were scheduled before this device
     * learned the task was completed/cancelled on another device.
     */
    @Query(
        """
        SELECT * FROM tasks
        WHERE deletedLocally = 0
          AND (completedMillis IS NOT NULL OR status = 'COMPLETED' OR status = 'CANCELLED')
          AND dueMillis IS NOT NULL AND dueMillis <= :until
        """
    )
    abstract suspend fun getCompletedWithDueBefore(until: Long): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertTasks(tasks: List<TaskEntity>)

    @Query("DELETE FROM tasks WHERE href = :href")
    abstract suspend fun deleteTask(href: String)

    @Query("DELETE FROM tasks WHERE href IN (:hrefs)")
    abstract suspend fun deleteTasks(hrefs: List<String>)

    @Query("DELETE FROM tasks WHERE listHref NOT IN (:keep)")
    abstract suspend fun deleteTasksForListsNotIn(keep: List<String>)

    @Query("DELETE FROM tasks WHERE listHref = :listHref")
    abstract suspend fun deleteTasksForList(listHref: String)

    @Query("DELETE FROM tasks")
    abstract suspend fun deleteAllTasks()

    /**
     * Applies a full re-download of one list. Locally pending rows are kept: a resync must not
     * throw away an edit that hasn't reached the server yet.
     */
    @Transaction
    open suspend fun replaceListContent(listHref: String, tasks: List<TaskEntity>) {
        val unsynced = getUnsynced().filter { it.listHref == listHref }.associateBy { it.href }
        deleteTasksForList(listHref)
        upsertTasks(tasks.filterNot { it.href in unsynced })
        upsertTasks(unsynced.values.toList())
    }

    @Transaction
    open suspend fun clearAll() {
        deleteAllTasks()
        deleteAllLists()
    }

    @Query("UPDATE task_lists SET syncToken = NULL")
    abstract suspend fun clearSyncTokens()
}

/**
 * Version 1.
 *
 * The cache itself is disposable -- it can always be re-downloaded -- but the `pending` and
 * `deletedLocally` rows are not: those are edits made offline that have never reached the
 * server. A destructive migration would throw them away silently, so there isn't one. Every
 * schema bump from here needs a real migration in [KotoMigrations], and Room will refuse to
 * open the database rather than lose someone's unsynced work.
 *
 * exportSchema is on so the generated JSON can be committed and migrations tested against it.
 */
@Database(entities = [TaskListEntity::class, TaskEntity::class], version = 1, exportSchema = true)
abstract class KotoDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
}

/**
 * Schema migrations, oldest first.
 *
 * Rules for adding one:
 *  - bump `version` on [KotoDatabase] and append the migration here in the same commit;
 *  - a new column needs a DEFAULT, since existing rows have no value for it;
 *  - never drop or rewrite the `tasks` table wholesale -- `rawIcs`, `pending` and
 *    `deletedLocally` carry work that exists nowhere else yet.
 */
object KotoMigrations {

    // Example of the shape a future migration takes; delete the comment, not the pattern.
    // val MIGRATION_1_2 = object : Migration(1, 2) {
    //     override fun migrate(db: SupportSQLiteDatabase) {
    //         db.execSQL("ALTER TABLE tasks ADD COLUMN newThing TEXT NOT NULL DEFAULT ''")
    //     }
    // }

    val ALL: Array<Migration> = arrayOf()
}
