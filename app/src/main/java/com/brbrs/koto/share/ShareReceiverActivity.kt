package com.brbrs.koto.share

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.brbrs.koto.auth.AuthRepository
import com.brbrs.koto.data.TaskRepository
import com.brbrs.koto.di.ApplicationScope
import com.brbrs.koto.ical.VTodo
import com.brbrs.koto.settings.SettingsRepository
import com.brbrs.koto.settings.WebDavClient
import com.brbrs.koto.util.QuickAdd
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

/**
 * "Share to Koto" from any app: text, a link, or one or more files.
 *
 * Headless on purpose. A share sheet that opens a whole app to confirm one line of text is
 * worse than a toast, and the task can be opened and edited straight afterwards.
 *
 * Files are uploaded to `/Koto/attachments/<uid>/` in the user's own Nextcloud and referenced
 * from the task with a standard ATTACH property, so Nextcloud Tasks and other CalDAV clients
 * see the link too. Nothing is sent anywhere but the user's own server.
 */
@AndroidEntryPoint
class ShareReceiverActivity : AppCompatActivity() {

    @Inject lateinit var repository: TaskRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var davClient: WebDavClient
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val subject = intent?.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty().trim()
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty().trim()
        val uris = incomingUris()

        if (subject.isBlank() && text.isBlank() && uris.isEmpty()) {
            toast("Nothing to add")
            finish()
            return
        }

        // Feedback has to be immediate: the activity finishes straight away and the upload
        // continues on the application scope, so a silent share would look like it failed.
        toast(if (uris.isEmpty()) "Adding to Koto…" else "Adding to Koto with ${uris.size} file${plural(uris.size)}…")

        appScope.launch { createTask(subject, text, uris) }
        finish()
    }

    private suspend fun createTask(subject: String, text: String, uris: List<Uri>) {
        val settings = settingsRepository.settings.value
        val lists = repository.lists.first()
        val target = settings.defaultListHref?.takeIf { href -> lists.any { it.href == href && !it.readOnly } }
            ?: lists.firstOrNull { !it.readOnly }?.href
        if (target == null) {
            toastOnMain("Open Koto and sign in first")
            return
        }

        // Work out a sensible title. A shared link usually arrives as subject + URL, and a
        // shared image as a URI with no text at all, in which case the file name is the
        // best title available.
        val fileNames = uris.map { displayName(it) }
        val rawTitle = when {
            subject.isNotBlank() -> subject
            text.isNotBlank() && text.length <= 120 && !text.contains('\n') -> text
            fileNames.isNotEmpty() -> fileNames.first().substringBeforeLast('.')
            else -> text.lineSequence().firstOrNull()?.take(120).orEmpty()
        }.ifBlank { "Shared item" }

        val parsed = QuickAdd.parse(rawTitle, settings.smartQuickAdd)
        val notes = buildList {
            if (text.isNotBlank() && text != subject && text != rawTitle) add(text)
        }.joinToString("\n\n").ifBlank { null }

        val todo = VTodo.newTask(parsed.title).copy(
            description = notes,
            due = parsed.due,
            priority = parsed.priority,
            categories = parsed.tags,
        )

        val href = repository.createTask(target, todo)

        if (uris.isEmpty()) {
            toastOnMain("Added to Koto")
            return
        }

        val session = authRepository.currentSession()
        if (session == null) {
            toastOnMain("Task added, but the files need you to be signed in")
            return
        }

        val uploaded = ArrayList<Pair<String, String>>() // name to url
        runCatching {
            davClient.ensureFolder(session, "Koto")
            davClient.ensureFolder(session, "Koto/attachments")
            davClient.ensureFolder(session, "Koto/attachments/${todo.uid}")
        }
        uris.forEachIndexed { index, uri ->
            runCatching {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Could not read the shared file")
                val mime = contentResolver.getType(uri) ?: "application/octet-stream"
                val name = sanitize(fileNames.getOrNull(index) ?: "attachment-${index + 1}")
                val url = davClient.putBytes(session, "Koto/attachments/${todo.uid}/$name", bytes, mime)
                uploaded.add(name to url)
            }
        }

        if (uploaded.isEmpty()) {
            toastOnMain("Task added, but the files could not be uploaded")
            return
        }

        // ATTACH is the standard way to hang a file off a VTODO, so other clients see it too.
        repository.updateTask(href) { current ->
            current.copy(
                unknownLines = current.unknownLines + uploaded.map { (_, url) -> "ATTACH:$url" },
                description = listOfNotNull(
                    current.description,
                    uploaded.joinToString("\n") { (name, url) -> "$name\n$url" },
                ).joinToString("\n\n"),
            )
        }
        toastOnMain("Added to Koto with ${uploaded.size} file${plural(uploaded.size)}")
    }

    @Suppress("DEPRECATION")
    private fun incomingUris(): List<Uri> {
        val i = intent ?: return emptyList()
        return when (i.action) {
            Intent.ACTION_SEND -> listOfNotNull(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else i.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
            )
            Intent.ACTION_SEND_MULTIPLE ->
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else i.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)).orEmpty().filterNotNull()
            else -> emptyList()
        }
    }

    /** The sending app's display name for the file, falling back to a generated one. */
    private fun displayName(uri: Uri): String {
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) c.getString(idx)?.takeIf { it.isNotBlank() }?.let { return it }
                }
            }
        }
        val ext = contentResolver.getType(uri)?.substringAfterLast('/')?.take(5)
        return "shared-" + UUID.randomUUID().toString().take(8) + if (ext != null) ".$ext" else ""
    }

    /** Keeps the name safe as a WebDAV path segment. */
    private fun sanitize(name: String): String =
        name.replace(Regex("""[/\\?%*:|"<>\x00-\x1F]"""), "_").take(120).ifBlank { "attachment" }

    private fun plural(n: Int) = if (n == 1) "" else "s"

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private suspend fun toastOnMain(message: String) = withContext(Dispatchers.Main) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
    }
}
