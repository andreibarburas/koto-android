package com.brbrs.koto.links.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Persistent store for a task's links to things in other BarburasLab apps (#12, mirroring
 * Tavla's "Linked items"). Separate database from koto.db and from contact-links.db so a
 * future schema change to any of the three never has to touch the others' migrations.
 */
@Entity(tableName = "task_item_link")
data class TaskLinkEntity(
    @PrimaryKey val id: String,
    /** The VTODO UID -- stable across href changes (a task moving list, a rename, etc). */
    val taskUid: String,
    val appPackage: String,
    val appLabel: String,
    val label: String,
    val uri: String,
)

@Dao
interface TaskLinksDao {
    @Query("SELECT * FROM task_item_link WHERE taskUid = :taskUid")
    fun observeForTask(taskUid: String): Flow<List<TaskLinkEntity>>

    @Query("SELECT * FROM task_item_link")
    suspend fun getAll(): List<TaskLinkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(link: TaskLinkEntity)

    @Query("DELETE FROM task_item_link WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM task_item_link WHERE taskUid = :taskUid")
    suspend fun deleteAllForTask(taskUid: String)
}

@Database(entities = [TaskLinkEntity::class], version = 1)
abstract class TaskLinksDatabase : RoomDatabase() {
    abstract fun taskLinksDao(): TaskLinksDao
}
