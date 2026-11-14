package com.brbrs.koto.contactlinks

import android.content.Context
import android.provider.ContactsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** A device contact suitable for the "link a contact" picker. */
data class PickableContact(
    val contactId: Long,
    val displayName: String,
    val cardavUid: String,
    val photoUri: String? = null,
)

/**
 * Reads Android's ContactsContract directly -- the same source Vinci reads from -- just to
 * resolve a contact's stable CardDAV UID (cardavUid) for linking. Koto does not otherwise
 * manage contacts; this is a one-purpose reader for the picker dialog on a task.
 *
 * The UID-resolution logic mirrors Vinci's ContactsRepository.syncFromDevice() (and Tavla's
 * copy of the same) so all three apps agree on what a contact's cardavUid is.
 */
@Singleton
class DeviceContactsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Contacts matching [query] by display name, resolved to cardavUid (contacts with no Nextcloud-synced CardDAV identity are left out -- nothing to link against). */
    suspend fun search(query: String): List<PickableContact> = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        val uidMap = resolveUidMap(cr)
        if (uidMap.isEmpty()) return@withContext emptyList()

        val results = mutableListOf<PickableContact>()
        val selection = if (query.isBlank()) null else
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?"
        val args = if (query.isBlank()) null else arrayOf("%$query%")

        cr.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.PHOTO_THUMBNAIL_URI),
            selection,
            args,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndex(ContactsContract.Contacts._ID)
            val nameCol = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            val photoCol = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uid = uidMap[id] ?: continue
                results.add(
                    PickableContact(
                        contactId = id,
                        displayName = cursor.getString(nameCol) ?: "",
                        cardavUid = uid,
                        photoUri = cursor.getString(photoCol),
                    )
                )
            }
        }
        results
    }

    /** Resolves display name + photo for a set of cardavUids (e.g. for rendering linked-contact rows), by uid. */
    suspend fun detailsFor(uids: Set<String>): Map<String, Pair<String, String?>> = withContext(Dispatchers.IO) {
        if (uids.isEmpty()) return@withContext emptyMap()
        val cr = context.contentResolver
        val uidMap = resolveUidMap(cr)
        val idsForUids = uidMap.filterValues { it in uids }.keys
        if (idsForUids.isEmpty()) return@withContext emptyMap()

        val result = mutableMapOf<String, Pair<String, String?>>()
        cr.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.PHOTO_THUMBNAIL_URI),
            null, null, null,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndex(ContactsContract.Contacts._ID)
            val nameCol = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            val photoCol = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                if (id !in idsForUids) continue
                val uid = uidMap[id] ?: continue
                result[uid] = (cursor.getString(nameCol) ?: uid) to cursor.getString(photoCol)
            }
        }
        result
    }

    /** contactId -> cardavUid, for every device contact that has a usable CardDAV identity. */
    private fun resolveUidMap(cr: android.content.ContentResolver): Map<Long, String> {
        val uidMap = mutableMapOf<Long, String>()
        val davx5Ids = mutableSetOf<Long>()
        cr.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(
                ContactsContract.RawContacts.CONTACT_ID,
                ContactsContract.RawContacts.SYNC1,
                ContactsContract.RawContacts.SOURCE_ID,
                ContactsContract.RawContacts.ACCOUNT_TYPE,
            ),
            "${ContactsContract.RawContacts.DELETED} = 0",
            null, null,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndex(ContactsContract.RawContacts.CONTACT_ID)
            val sync1Col = cursor.getColumnIndex(ContactsContract.RawContacts.SYNC1)
            val sourceIdCol = cursor.getColumnIndex(ContactsContract.RawContacts.SOURCE_ID)
            val accountCol = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE)
            while (cursor.moveToNext()) {
                val sync1 = cursor.getString(sync1Col) ?: ""
                val sourceId = cursor.getString(sourceIdCol) ?: ""
                val accountType = cursor.getString(accountCol) ?: ""
                val contactId = cursor.getLong(idCol)
                val isDavx5 = accountType.contains("bitfire", ignoreCase = true) ||
                    accountType.contains("davdroid", ignoreCase = true)

                if (isDavx5) {
                    davx5Ids.add(contactId)
                    val filenameUid = if (sourceId.contains(".vcf", ignoreCase = true)) {
                        sourceId.substringAfterLast("/").removeSuffix(".vcf").removeSuffix(".VCF")
                    } else ""
                    val uid = filenameUid.ifBlank { sync1 }
                    if (uid.isNotBlank()) uidMap[contactId] = uid
                } else {
                    val uid = sync1.ifBlank { sourceId }
                    if (uid.isNotBlank() && contactId !in davx5Ids) {
                        uidMap[contactId] = uid
                    }
                }
            }
            davx5Ids.forEach { contactId ->
                val uid = uidMap[contactId] ?: return@forEach
                if ("@s.whatsapp.net" in uid || "@g.us" in uid || "@c.us" in uid) {
                    uidMap.remove(contactId)
                }
            }
        }
        return uidMap
    }
}
