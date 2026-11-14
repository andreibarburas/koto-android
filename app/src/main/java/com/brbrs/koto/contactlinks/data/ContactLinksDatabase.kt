package com.brbrs.koto.contactlinks.data

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
 * Persistent store for task <-> Vinci-contact links, part of the suite's cross-app
 * integration (see cross-app-linking notes). Separate database from koto.db so a future
 * schema change to either never has to touch the other's migrations.
 */
@Entity(tableName = "task_contact_link", primaryKeys = ["taskUid", "contactUid"])
data class TaskContactLinkEntity(
    /** The VTODO UID -- stable across href changes (a task moving list, a rename, etc). */
    val taskUid: String,
    /** Vinci's cardavUid -- the Nextcloud address book's CardDAV UID, stable across devices. */
    val contactUid: String,
)

@Dao
interface ContactLinksDao {
    @Query("SELECT contactUid FROM task_contact_link WHERE taskUid = :taskUid")
    fun observeForTask(taskUid: String): Flow<List<String>>

    @Query("SELECT contactUid FROM task_contact_link WHERE taskUid = :taskUid")
    suspend fun getForTask(taskUid: String): List<String>

    @Query("SELECT * FROM task_contact_link")
    suspend fun getAll(): List<TaskContactLinkEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(link: TaskContactLinkEntity)

    @Query("DELETE FROM task_contact_link WHERE taskUid = :taskUid AND contactUid = :contactUid")
    suspend fun delete(taskUid: String, contactUid: String)

    @Query("DELETE FROM task_contact_link WHERE taskUid = :taskUid")
    suspend fun deleteAllForTask(taskUid: String)
}

@Database(entities = [TaskContactLinkEntity::class], version = 1)
abstract class ContactLinksDatabase : RoomDatabase() {
    abstract fun contactLinksDao(): ContactLinksDao
}
