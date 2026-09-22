package uk.kayalab.mynotes.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.kayalab.mynotes.ui.canvas.StrokeData
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Writes note PDFs to the chosen export folder, app storage, or a share sheet. */
@Singleton
class PdfExportService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    sealed interface Outcome {
        data class Saved(val uri: Uri, val displayPath: String) : Outcome
        data class Failed(val message: String) : Outcome
    }

    private val referenceWidth: Float
        get() = context.resources.displayMetrics.widthPixels.toFloat()

    fun fileNameFor(noteName: String): String {
        val date = SimpleDateFormat("yyyyMMdd", Locale.UK).format(Date())
        return "$date-${noteName.toSafeFileName()}.pdf"
    }

    suspend fun exportToFolder(noteName: String, strokes: List<StrokeData>, treeUri: String?): Outcome =
        withContext(Dispatchers.IO) {
            val fileName = fileNameFor(noteName)
            runCatching {
                if (treeUri != null) writeToTree(Uri.parse(treeUri), fileName, strokes)
                else writeToAppStorage(fileName, strokes)
            }.getOrElse { Outcome.Failed(it.message ?: it.javaClass.simpleName) }
        }

    /** Renders to the cache directory and opens the system share sheet. */
    suspend fun share(noteName: String, strokes: List<StrokeData>): Outcome =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "shared_pdfs").apply { mkdirs() }
                dir.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - ONE_DAY_MS }
                    ?.forEach { it.delete() }
                val file = File(dir, fileNameFor(noteName))
                file.outputStream().use { PdfRenderer.render(strokes, referenceWidth, it) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, noteName)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(send, "Send \"$noteName\" as PDF")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
                Outcome.Saved(uri, file.name)
            }.getOrElse { Outcome.Failed(it.message ?: it.javaClass.simpleName) }
        }

    private fun writeToTree(treeUri: Uri, fileName: String, strokes: List<StrokeData>): Outcome {
        val dir = DocumentFile.fromTreeUri(context, treeUri)
            ?: return Outcome.Failed("The export folder is no longer available. Choose it again in Settings.")
        dir.findFile(fileName)?.delete()
        val doc = dir.createFile("application/pdf", fileName)
            ?: return Outcome.Failed("Could not create a file in the export folder.")
        val stream = context.contentResolver.openOutputStream(doc.uri)
            ?: return Outcome.Failed("Could not open the export folder for writing.")
        stream.use { PdfRenderer.render(strokes, referenceWidth, it) }
        return Outcome.Saved(doc.uri, "${treeUri.toReadablePath()}/$fileName")
    }

    private fun writeToAppStorage(fileName: String, strokes: List<StrokeData>): Outcome {
        val dir = File(context.getExternalFilesDir(null), "Exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.outputStream().use { PdfRenderer.render(strokes, referenceWidth, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        return Outcome.Saved(uri, "App storage/Exports/$fileName")
    }

    private companion object {
        const val ONE_DAY_MS = 24L * 60 * 60 * 1000
    }
}

private fun String.toSafeFileName() = replace(Regex("[/\\\\:*?\"<>|]"), "_").trim().ifEmpty { "note" }

/** Converts a storage-access tree URI to something like "Internal storage/Documents/Notes". */
fun Uri.toReadablePath(): String = runCatching {
    val docId = DocumentsContract.getTreeDocumentId(this) ?: ""
    val colon = docId.indexOf(':')
    if (colon < 0) return@runCatching pathSegments.lastOrNull() ?: toString()
    val volume = docId.substring(0, colon)
    val path = docId.substring(colon + 1)
    val root = if (volume == "primary") "Internal storage" else volume
    if (path.isEmpty()) root else "$root/$path"
}.getOrDefault(pathSegments.lastOrNull() ?: toString())
