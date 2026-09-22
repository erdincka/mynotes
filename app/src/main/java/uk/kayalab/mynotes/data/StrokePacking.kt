package uk.kayalab.mynotes.data

import androidx.compose.ui.geometry.Offset
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Little-endian float32 packing: 8 bytes per point instead of ~20 as JSON text. */
object StrokePacking {

    fun packPoints(points: List<Offset>): ByteArray {
        val buffer = ByteBuffer.allocate(points.size * 8).order(ByteOrder.LITTLE_ENDIAN)
        for (p in points) {
            buffer.putFloat(p.x)
            buffer.putFloat(p.y)
        }
        return buffer.array()
    }

    fun unpackPoints(bytes: ByteArray): List<Offset> {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val count = bytes.size / 8
        val out = ArrayList<Offset>(count)
        repeat(count) { out.add(Offset(buffer.getFloat(), buffer.getFloat())) }
        return out
    }

    fun packFloats(values: List<Float>): ByteArray {
        val buffer = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (v in values) buffer.putFloat(v)
        return buffer.array()
    }

    fun unpackFloats(bytes: ByteArray): List<Float> {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val count = bytes.size / 4
        val out = ArrayList<Float>(count)
        repeat(count) { out.add(buffer.getFloat()) }
        return out
    }
}
