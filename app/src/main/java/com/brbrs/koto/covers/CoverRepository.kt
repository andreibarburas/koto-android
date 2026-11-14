package com.brbrs.koto.covers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.brbrs.koto.auth.AuthRepository
import com.brbrs.koto.di.ApplicationScope
import com.brbrs.koto.settings.SettingsRepository
import com.brbrs.koto.settings.WebDavClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages per-list cover images.
 *
 * Architecture:
 *  - A "cover token" is a short random UUID stored in koto-settings.json under `listCovers`.
 *    It is not the image; it is just the key that names the file on Nextcloud and in local cache.
 *  - Remote path: `Koto/covers/<token>.jpg`
 *  - Local cache: `<filesDir>/covers/<token>.jpg`
 *  - Replacing a cover generates a new token, so the old cached file is bypassed automatically
 *    on other devices; stale local files are left until the device is low on space (they are
 *    small and rare, and a periodic sweep adds complexity for almost no benefit).
 *
 * All network calls are best-effort and happen in the background. The caller sees the image
 * the moment it is written to the local cache, whether or not the upload has completed yet.
 */
@Singleton
class CoverRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val davClient: WebDavClient,
    private val settingsRepository: SettingsRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    companion object {
        private const val TAG = "CoverRepository"
        private const val REMOTE_FOLDER = "Koto/covers"
        private const val MAX_EDGE = 1080
        private const val JPEG_QUALITY = 85
    }

    private val coverDir: File
        get() = File(context.filesDir, "covers").also { it.mkdirs() }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Returns the local [File] for the given [token], or null if not yet cached.
     * Use this directly in Coil's `model` parameter; Coil handles null gracefully.
     */
    fun localFile(token: String?): File? {
        if (token.isNullOrBlank()) return null
        val f = File(coverDir, "$token.jpg")
        return if (f.exists()) f else null
    }

    /**
     * Pick a new cover image from [uri]:
     *  1. Downscale to max 1080px longest edge, re-encode as JPEG 85, apply EXIF rotation.
     *  2. Write to local cache immediately so the UI reacts before the upload finishes.
     *  3. Upload in the background; store the token in settings on success.
     *  4. On upload failure the image is still shown locally; the reconciler retries on next sync.
     *
     * Existing cover (if any) is removed from Nextcloud after the new one lands.
     */
    fun pickCover(listHref: String, uri: Uri) {
        appScope.launch {
            try {
                val bytes = processImage(uri) ?: return@launch
                val token = UUID.randomUUID().toString().replace("-", "").take(16)
                val localFile = File(coverDir, "$token.jpg")
                withContext(Dispatchers.IO) { localFile.writeBytes(bytes) }

                // Surface the new cover immediately (the token is in settings, local file exists).
                val oldToken = settingsRepository.settings.value.listCovers[listHref]
                settingsRepository.setCover(listHref, token)

                // Upload in the background.
                uploadCover(token, bytes)

                // Clean up the old cover from Nextcloud (fire-and-forget; missing file is fine).
                if (oldToken != null) {
                    deleteRemoteCover(oldToken)
                }
            } catch (e: Exception) {
                Log.w(TAG, "pickCover failed for $listHref: ${e.message}")
            }
        }
    }

    /**
     * Removes the cover for [listHref]:
     *  - Clears the token from settings.
     *  - Deletes the local cached file.
     *  - Deletes the remote file from Nextcloud.
     */
    fun removeCover(listHref: String) {
        appScope.launch {
            val token = settingsRepository.settings.value.listCovers[listHref] ?: return@launch
            settingsRepository.setCover(listHref, null)
            withContext(Dispatchers.IO) { File(coverDir, "$token.jpg").delete() }
            deleteRemoteCover(token)
        }
    }

    /**
     * Background reconciler: called by [com.brbrs.koto.sync.SyncWorker] after each sync.
     * Downloads any cover token that is referenced in settings but not yet cached locally.
     * This is how covers made on another device appear here without the user doing anything.
     */
    suspend fun reconcile() = withContext(Dispatchers.IO) {
        val session = authRepository.currentSession() ?: return@withContext
        val covers = settingsRepository.settings.value.listCovers
        for ((_, token) in covers) {
            val local = File(coverDir, "$token.jpg")
            if (local.exists()) continue
            try {
                val path = "$REMOTE_FOLDER/$token.jpg"
                val bytes = davClient.getBytes(session, path) ?: continue
                local.writeBytes(bytes)
                Log.d(TAG, "Downloaded cover $token")
            } catch (e: Exception) {
                Log.w(TAG, "Could not download cover $token: ${e.message}")
            }
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private suspend fun processImage(uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        try {
            // Read EXIF rotation before decoding (ExifInterface needs a fresh stream).
            val rotation = context.contentResolver.openInputStream(uri)?.use { exifRotation(it) } ?: 0

            val raw: Bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return@withContext null

            // Apply EXIF rotation.
            val upright = if (rotation != 0) {
                val m = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true).also {
                    if (it !== raw) raw.recycle()
                }
            } else raw

            // Downscale to MAX_EDGE on the longest side.
            val w = upright.width; val h = upright.height
            val scale = if (maxOf(w, h) > MAX_EDGE) MAX_EDGE.toFloat() / maxOf(w, h) else 1f
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(upright, (w * scale).toInt(), (h * scale).toInt(), true)
                    .also { if (it !== upright) upright.recycle() }
            } else upright

            ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                scaled.recycle()
                out.toByteArray()
            }
        } catch (e: Exception) {
            Log.w(TAG, "processImage failed: ${e.message}")
            null
        }
    }

    private fun exifRotation(stream: InputStream): Int {
        return try {
            val exif = ExifInterface(stream)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (_: Exception) { 0 }
    }

    private suspend fun uploadCover(token: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val session = authRepository.currentSession() ?: return@withContext
        try {
            davClient.ensureFolder(session, REMOTE_FOLDER)
            davClient.putBytes(session, "$REMOTE_FOLDER/$token.jpg", bytes, "image/jpeg")
            Log.d(TAG, "Uploaded cover $token")
        } catch (e: Exception) {
            Log.w(TAG, "uploadCover failed for $token: ${e.message}")
            // Not fatal: the local file is there; next reconcile will retry the upload.
        }
    }

    private suspend fun deleteRemoteCover(token: String) = withContext(Dispatchers.IO) {
        val session = authRepository.currentSession() ?: return@withContext
        try {
            davClient.deleteFile(session, "$REMOTE_FOLDER/$token.jpg")
        } catch (e: Exception) {
            Log.w(TAG, "deleteRemoteCover failed for $token: ${e.message}")
        }
    }
}
