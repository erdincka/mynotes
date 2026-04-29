package uk.kayalab.mynotes.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import uk.kayalab.mynotes.data.Note
import uk.kayalab.mynotes.data.SettingsRepository
import uk.kayalab.mynotes.ui.canvas.StrokeData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportManager @Inject constructor(
    private val settingsRepository: SettingsRepository
) {

     private val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

      suspend fun exportToPng(
         context: Context,
         bitmap: Bitmap,
         fileName: String = "note_${timestamp.format(Date())}"
     ): Uri? = withContext(Dispatchers.IO) {
         try {
             val exportFolderUriString = settingsRepository.exportFolderUri.first()
             if (exportFolderUriString != null) {
                 val treeUri = exportFolderUriString.toUri()
                 if (treeUri.scheme == "content") {
                     val pickedDir = DocumentFile.fromTreeUri(context, treeUri)
                     val file = pickedDir?.createFile("image/png", fileName)
                     if (file != null) {
                         context.contentResolver.openOutputStream(file.uri)?.use { out ->
                             bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                         }
                         Timber.d("ExportManager: PNG exported to ${file.uri}")
                         return@withContext file.uri
                     }
                 }
             }

             // Fallback to internal storage if no external folder is selected or if it's a file:// URI
             val exportDir = settingsRepository.getExportDirectory()
             val file = File(exportDir, "$fileName.png")
             FileOutputStream(file).use { out ->
                 bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
              }
             Timber.d("ExportManager: PNG exported to ${file.absolutePath}")
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
         } catch (e: Exception) {
            Timber.e(e, "ExportManager: PNG export failed")
           null
         }
      }

      suspend fun exportNoteToPdf(
          context: Context,
          note: Note,
          fileName: String? = null
      ): Uri? = withContext(Dispatchers.IO) {
          val strokes: List<StrokeData> = try {
              Json.decodeFromString(note.content)
          } catch (_: Exception) {
              emptyList()
          }
          val name = fileName ?: note.name
          exportToPdf(context, strokes, name)
      }

      suspend fun exportToPdf(
         context: Context,
         strokes: List<StrokeData>,
         fileName: String = "note_${timestamp.format(Date())}"
     ): Uri? = withContext(Dispatchers.IO) {
         try {
             val exportFolderUriString = settingsRepository.exportFolderUri.first()
             val pdfFileName = if (fileName.endsWith(".pdf")) fileName else "$fileName.pdf"

             if (exportFolderUriString != null) {
                 val treeUri = exportFolderUriString.toUri()
                 if (treeUri.scheme == "content") {
                     val pickedDir = DocumentFile.fromTreeUri(context, treeUri)
                     val file = pickedDir?.createFile("application/pdf", pdfFileName)
                     if (file != null) {
                         context.contentResolver.openOutputStream(file.uri)?.use { out ->
                             generatePdf(strokes, out)
                         }
                         Timber.d("ExportManager: PDF exported to ${file.uri}")
                         return@withContext file.uri
                     }
                 }
             }
             
             // Fallback to internal storage
             val exportDir = settingsRepository.getExportDirectory()
             val file = File(exportDir, pdfFileName)
             FileOutputStream(file).use { out ->
                 generatePdf(strokes, out)
             }
             Timber.d("ExportManager: PDF exported to ${file.absolutePath}")
             FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
         } catch (e: Exception) {
             Timber.e(e, "ExportManager: PDF export failed")
             null
         }
      }

      private fun generatePdf(strokes: List<StrokeData>, outputStream: java.io.OutputStream) {
          val document = PdfDocument()
          // Standard A4 size is 595 x 842 points
          val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
          val page = document.startPage(pageInfo)
          val canvas = page.canvas

          strokes.forEach { stroke ->
              if (stroke.tool == "text" && stroke.text != null && stroke.points.isNotEmpty()) {
                  val paint = Paint().apply {
                      color = android.graphics.Color.parseColor(stroke.color)
                      textSize = stroke.fontSize
                      isAntiAlias = true
                      typeface = when (stroke.fontFamily) {
                          "Serif" -> android.graphics.Typeface.SERIF
                          "SansSerif" -> android.graphics.Typeface.SANS_SERIF
                          "Monospace" -> android.graphics.Typeface.MONOSPACE
                          else -> android.graphics.Typeface.DEFAULT
                      }
                  }
                  canvas.drawText(
                      stroke.text,
                      stroke.points[0].x,
                      stroke.points[0].y + (stroke.fontSize * 0.8f),
                      paint
                  )
              } else if (stroke.points.isNotEmpty()) {
                  val paint = Paint().apply {
                      color = android.graphics.Color.parseColor(stroke.color)
                      strokeWidth = stroke.strokeWidth
                      style = Paint.Style.STROKE
                      strokeCap = Paint.Cap.ROUND
                      strokeJoin = Paint.Join.ROUND
                      isAntiAlias = true
                  }
                  val path = Path().apply {
                      moveTo(stroke.points[0].x, stroke.points[0].y)
                      for (i in 1 until stroke.points.size) {
                          lineTo(stroke.points[i].x, stroke.points[i].y)
                      }
                  }
                  canvas.drawPath(path, paint)
              }
          }

          document.finishPage(page)
          document.writeTo(outputStream)
          document.close()
      }
}
