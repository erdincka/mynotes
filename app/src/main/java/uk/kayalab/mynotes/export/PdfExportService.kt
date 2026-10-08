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
import uk.kayalab.mynotes.data.PageTemplate
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
    @ApplicationContext private val context: Context,
    private val imageStore: ImageStore
) {
    sealed interface Outcome {
        data class Saved(val uri: Uri, val displayPath: String) : Outcome
        data class Failed(val message: String) : Outcome
    }

    /** One note ready to render: its name, ink and paper. */
    data class Document(val name: String, val strokes: List<StrokeData>, val template: PageTemplate = PageTemplate.PLAIN)

    private val referenceWidth: Float
        get() = context.resources.displayMetrics.widthPixels.toFloat()

    fun fileNameFor(noteName: String): String =
        pdfFileNameFor(noteName, SimpleDateFormat("yyyyMMdd", Locale.UK).format(Date()))

    suspend fun exportToFolder(noteName: String, strokes: List<StrokeData>, treeUri: String?, template: PageTemplate = PageTemplate.PLAIN): Outcome =
        withContext(Dispatchers.IO) {
            val fileName = fileNameFor(noteName)
            runCatching {
                if (treeUri != null) writeToTree(Uri.parse(treeUri), fileName, strokes, template)
                else writeToAppStorage(fileName, strokes, template)
            }.getOrElse { Outcome.Failed(it.message ?: it.javaClass.simpleName) }
        }

    /** Renders to the cache directory and opens the system share sheet. */
    suspend fun share(noteName: String, strokes: List<StrokeData>, template: PageTemplate = PageTemplate.PLAIN): Outcome =
        shareAll(listOf(Document(noteName, strokes, template)))

    /**
     * Renders every document to the cache directory and opens one share sheet for all of them, so
     * a cloud drive asks for the destination folder once. A single document is sent with
     * ACTION_SEND, which every target accepts; several use ACTION_SEND_MULTIPLE.
     */
    suspend fun shareAll(documents: List<Document>): Outcome = withContext(Dispatchers.IO) {
        if (documents.isEmpty()) return@withContext Outcome.Failed("There is nothing to send.")
        runCatching {
            val dir = File(context.cacheDir, "shared_pdfs").apply { mkdirs() }
            dir.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - ONE_DAY_MS }
                ?.forEach { it.delete() }
            val names = uniquePdfFileNames(documents.map { fileNameFor(it.name) })
            val uris = documents.zip(names).map { (document, name) ->
                val file = File(dir, name)
                file.outputStream().use { PdfRenderer.render(document.strokes, referenceWidth, it, document.template, imageStore::bitmap) }
                FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            }
            val send = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_STREAM, uris.single())
                    putExtra(Intent.EXTRA_SUBJECT, documents.single().name)
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                    putExtra(Intent.EXTRA_SUBJECT, "${uris.size} notes")
                }
            }
            send.type = "application/pdf"
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val title = if (uris.size == 1) "Send \"${documents.single().name}\" as PDF" else "Send ${uris.size} notes as PDF"
            context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Outcome.Saved(uris.first(), names.joinToString())
        }.getOrElse { Outcome.Failed(it.message ?: it.javaClass.simpleName) }
    }

    private fun writeToTree(treeUri: Uri, fileName: String, strokes: List<StrokeData>, template: PageTemplate): Outcome {
        val dir = DocumentFile.fromTreeUri(context, treeUri)
            ?: return Outcome.Failed("The export folder is no longer available. Choose it again in Settings.")
        dir.findFile(fileName)?.delete()
        val doc = dir.createFile("application/pdf", fileName)
            ?: return Outcome.Failed("Could not create a file in the export folder.")
        val stream = context.contentResolver.openOutputStream(doc.uri)
            ?: return Outcome.Failed("Could not open the export folder for writing.")
        stream.use { PdfRenderer.render(strokes, referenceWidth, it, template, imageStore::bitmap) }
        return Outcome.Saved(doc.uri, "${treeUri.toReadablePath()}/$fileName")
    }

    private fun writeToAppStorage(fileName: String, strokes: List<StrokeData>, template: PageTemplate): Outcome {
        val dir = File(context.getExternalFilesDir(null), "Exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.outputStream().use { PdfRenderer.render(strokes, referenceWidth, it, template, imageStore::bitmap) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        return Outcome.Saved(uri, "App storage/Exports/$fileName")
    }

    private companion object {
        const val ONE_DAY_MS = 24L * 60 * 60 * 1000
    }
}

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
