package com.mynotes.ui.canvas

import android.content.Context
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfDocument
import com.mynotes.data.Note
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

class PdfExporter(private val context: Context) {

    fun exportNote(note: Note, folderPath: String, outputFile: File): Result<File> {
        return try {
            val strokes: List<StrokeData> = try {
                Json.decodeFromString(note.content)
            } catch (e: Exception) {
                emptyList()
            }

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
            FileOutputStream(outputFile).use {
                document.writeTo(it)
            }
            document.close()
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
