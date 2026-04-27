package com.mynotes.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.mynotes.ui.canvas.StrokeData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportManager @Inject constructor() {

     private val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

      suspend fun exportToPng(
         context: Context,
         bitmap: Bitmap,
         fileName: String = "note_${timestamp.format(Date())}"
     ): Uri? = withContext(Dispatchers.IO) {
         try {
             val filesDir = context.filesDir
             val file = File(filesDir, "$fileName.png")
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

      suspend fun exportToPdf(
         context: Context,
         strokes: List<StrokeData>,
         fileName: String = "note_${timestamp.format(Date())}"
     ): Uri? = withContext(Dispatchers.IO) {
         // PDF export implementation placeholder using context for file location
         val filesDir = context.filesDir
         Timber.w("ExportManager: PDF export not yet implemented. FilesDir: ${filesDir.absolutePath}")
         null
      }
}
