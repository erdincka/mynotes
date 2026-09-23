package uk.kayalab.mynotes.ui.canvas

import androidx.compose.ui.geometry.Offset
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicLong

enum class CanvasTool { PEN, BRUSH, HIGHLIGHTER, ERASER, SHAPE, LASSO, TEXT }

object OffsetSerializer : KSerializer<Offset> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Offset", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Offset) {
        encoder.encodeString("${value.x},${value.y}")
    }
    override fun deserialize(decoder: Decoder): Offset {
        val (x, y) = decoder.decodeString().split(",").map { it.toFloat() }
        return Offset(x, y)
    }
}

// Monotonic so two strokes created within the same millisecond never share an id.
private val strokeIdCounter = AtomicLong(System.currentTimeMillis())
fun nextStrokeId(): Long = strokeIdCounter.incrementAndGet()

@Serializable
data class StrokeData(
    val id: Long = nextStrokeId(),
    val points: List<@Serializable(with = OffsetSerializer::class) Offset>,
    val pressures: List<Float> = emptyList(),
    val color: String = "#000000",
    val strokeWidth: Float = 5f,
    val tool: String = "pen",
    val text: String? = null,
    val fontSize: Float = 40f,
    val fontFamily: String = "Default",
    val imageName: String? = null,
    val imageWidth: Float = 0f,
    val imageHeight: Float = 0f
) {
    val isImage: Boolean get() = tool == "image" && imageName != null && points.isNotEmpty()
    val isText: Boolean get() = tool == "text" && text != null && points.isNotEmpty()
}

/**
 * The only way stroke lists are read from or written to storage. Unknown keys are ignored so a
 * newer app version never makes an older note unreadable, and a decode failure is surfaced as a
 * failed Result rather than an empty list so callers can refuse to overwrite the original.
 */
object StrokeCodec {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun decode(content: String): Result<List<StrokeData>> {
        if (content.isBlank()) return Result.success(emptyList())
        return runCatching { json.decodeFromString<List<StrokeData>>(content) }
    }

    fun encode(strokes: List<StrokeData>): String = json.encodeToString(strokes)
}
