package uk.kayalab.mynotes.export

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import uk.kayalab.mynotes.data.Folder
import uk.kayalab.mynotes.data.FolderRepository
import uk.kayalab.mynotes.data.Note
import uk.kayalab.mynotes.data.NoteRepository
import uk.kayalab.mynotes.ui.canvas.StrokeCodec
import uk.kayalab.mynotes.ui.canvas.StrokeData
import uk.kayalab.mynotes.ui.canvas.nextStrokeId
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class BackupNote(val id: Long, val name: String, val folderId: Long, val createdAt: Long, val updatedAt: Long, val template: String = "grid")

@Serializable
private data class BackupManifest(
    val version: Int = 1,
    val exportedAt: Long,
    val folders: List<Folder>,
    val notes: List<BackupNote>
)

/**
 * A zip with a manifest plus one JSON file of strokes per note. Restore adds everything under
 * fresh ids and never deletes, so restoring twice simply duplicates.
 */
@Singleton
class BackupService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val noteRepository: NoteRepository,
    private val folderRepository: FolderRepository,
    private val imageStore: ImageStore
) {
    private val json = Json { ignoreUnknownKeys = true }

    data class RestoreSummary(val folders: Int, val notes: Int)

    fun suggestedFileName(): String =
        "mynotes-backup-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.UK).format(Date()) + ".zip"

    suspend fun writeBackup(target: Uri, allFolders: List<Folder>): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val notes = noteRepository.getAllPlain()
            val manifest = BackupManifest(
                exportedAt = System.currentTimeMillis(),
                folders = allFolders,
                notes = notes.map { BackupNote(it.id, it.name, it.folderId, it.createdAt, it.updatedAt, it.template) }
            )
            val stream = context.contentResolver.openOutputStream(target, "wt")
                ?: error("Could not open the chosen location for writing.")
            ZipOutputStream(BufferedOutputStream(stream)).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(json.encodeToString(manifest).toByteArray())
                zip.closeEntry()
                val imageNames = LinkedHashSet<String>()
                for (note in notes) {
                    val strokes = noteRepository.loadStrokes(note.id)
                    strokes.mapNotNullTo(imageNames) { it.imageName }
                    zip.putNextEntry(ZipEntry("notes/${note.id}.json"))
                    zip.write(StrokeCodec.encode(strokes).toByteArray())
                    zip.closeEntry()
                }
                for (name in imageNames) {
                    val file = imageStore.file(name)
                    if (!file.exists()) continue
                    zip.putNextEntry(ZipEntry("images/$name"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            notes.size
        }.onFailure { Timber.e(it, "Backup failed") }
    }

    suspend fun restoreBackup(source: Uri): Result<RestoreSummary> = withContext(Dispatchers.IO) {
        runCatching {
            var manifest: BackupManifest? = null
            val inkByOldId = HashMap<Long, List<StrokeData>>()
            val stream = context.contentResolver.openInputStream(source)
                ?: error("Could not open the backup file.")
            ZipInputStream(BufferedInputStream(stream)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val bytes = zip.readBytes()
                    when {
                        entry.name == "manifest.json" -> manifest = json.decodeFromString<BackupManifest>(bytes.decodeToString())
                        entry.name.startsWith("images/") && !entry.name.substringAfter("images/").contains('/') -> {
                            val name = entry.name.substringAfter("images/")
                            if (name.matches(Regex("[A-Za-z0-9-]+\\.jpg"))) imageStore.copyIn(name, bytes)
                        }
                        entry.name.startsWith("notes/") && entry.name.endsWith(".json") -> {
                            val oldId = entry.name.removePrefix("notes/").removeSuffix(".json").toLongOrNull()
                            if (oldId != null) {
                                inkByOldId[oldId] = StrokeCodec.decode(bytes.decodeToString())
                                    .getOrElse { error("Note $oldId in the backup is unreadable.") }
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            val data = manifest ?: error("This file is not a MyNotes backup: manifest.json is missing.")

            val folderIdMap = HashMap<Long?, Long?>().apply { put(null, null) }
            var remaining = data.folders
            while (remaining.isNotEmpty()) {
                val (ready, waiting) = remaining.partition { it.parentId in folderIdMap }
                if (ready.isEmpty()) {
                    // Parents missing from the backup: attach the rest at the top level.
                    for (folder in waiting) folderIdMap[folder.id] = folderRepository.create(folder.name, null)
                    break
                }
                for (folder in ready) folderIdMap[folder.id] = folderRepository.create(folder.name, folderIdMap[folder.parentId])
                remaining = waiting
            }

            var restoredNotes = 0
            for (note in data.notes) {
                val strokes = (inkByOldId[note.id] ?: emptyList()).map { it.copy(id = nextStrokeId()) }
                val folderId = if (note.folderId == 0L) 0L else folderIdMap[note.folderId] ?: 0L
                val thumbnail = if (strokes.isEmpty()) null else NoteThumbnailRenderer.render(strokes, imageStore::bitmap)
                noteRepository.insertWithStrokes(
                    Note(name = note.name, folderId = folderId, createdAt = note.createdAt, updatedAt = note.updatedAt, template = note.template),
                    strokes,
                    thumbnail
                )
                restoredNotes++
            }
            RestoreSummary(folders = data.folders.size, notes = restoredNotes)
        }.onFailure { Timber.e(it, "Restore failed") }
    }
}
