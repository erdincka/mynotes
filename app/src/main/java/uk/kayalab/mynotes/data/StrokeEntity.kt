package uk.kayalab.mynotes.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import uk.kayalab.mynotes.ui.canvas.StrokeData

/** One row per stroke so a note of any length never hits SQLite's 2 MB row window. */
@Entity(
    tableName = "strokes",
    foreignKeys = [ForeignKey(entity = Note::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("noteId")]
)
class StrokeEntity(
    @PrimaryKey val id: Long,
    val noteId: Long,
    val ordinal: Int,
    val tool: String,
    val color: String,
    val strokeWidth: Float,
    val points: ByteArray,
    val pressures: ByteArray,
    val text: String?,
    val fontSize: Float,
    val fontFamily: String,
    val imageName: String? = null,
    @ColumnInfo(defaultValue = "0")
    val imageWidth: Float = 0f,
    @ColumnInfo(defaultValue = "0")
    val imageHeight: Float = 0f
) {
    fun toStrokeData(): StrokeData = StrokeData(
        id = id,
        points = StrokePacking.unpackPoints(points),
        pressures = StrokePacking.unpackFloats(pressures),
        color = color,
        strokeWidth = strokeWidth,
        tool = tool,
        text = text,
        fontSize = fontSize,
        fontFamily = fontFamily,
        imageName = imageName,
        imageWidth = imageWidth,
        imageHeight = imageHeight
    )

    companion object {
        fun from(stroke: StrokeData, noteId: Long, ordinal: Int): StrokeEntity = StrokeEntity(
            id = stroke.id,
            noteId = noteId,
            ordinal = ordinal,
            tool = stroke.tool,
            color = stroke.color,
            strokeWidth = stroke.strokeWidth,
            points = StrokePacking.packPoints(stroke.points),
            pressures = StrokePacking.packFloats(stroke.pressures),
            text = stroke.text,
            fontSize = stroke.fontSize,
            fontFamily = stroke.fontFamily,
            imageName = stroke.imageName,
            imageWidth = stroke.imageWidth,
            imageHeight = stroke.imageHeight
        )
    }
}
