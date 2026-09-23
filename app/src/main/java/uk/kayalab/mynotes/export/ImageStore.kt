package uk.kayalab.mynotes.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Inserted images live as JPEG files under filesDir/images and are referenced by name. */
@Singleton
class ImageStore @Inject constructor(@ApplicationContext private val context: Context) {

    data class Imported(val name: String, val width: Int, val height: Int)

    private val dir: File get() = File(context.filesDir, "images").apply { mkdirs() }

    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun file(name: String): File = File(dir, name)

    /** Decodes with down-sampling so the stored copy is at most [MAX_EDGE] pixels on its long side. */
    suspend fun import(uri: Uri): Result<Imported> = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val stream = context.contentResolver.openInputStream(uri) ?: error("Could not open the image.")
            stream.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("This file is not an image the tablet can decode.")
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: error("Could not decode the image.")
            val name = UUID.randomUUID().toString() + ".jpg"
            file(name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            cache.put(name, bitmap)
            Imported(name, bitmap.width, bitmap.height)
        }.onFailure { Timber.e(it, "Image import failed") }
    }

    /** Cached decode; synchronous so the canvas can draw it directly. */
    fun bitmap(name: String): Bitmap? {
        cache.get(name)?.let { return it }
        val file = file(name)
        if (!file.exists()) return null
        val decoded = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull() ?: return null
        cache.put(name, decoded)
        return decoded
    }

    suspend fun preload(names: Collection<String>) = withContext(Dispatchers.IO) { names.forEach { bitmap(it) } }

    suspend fun copyIn(name: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val target = file(name)
        if (!target.exists()) target.writeBytes(bytes)
    }

    /** Deletes files no stroke references any more. */
    suspend fun prune(referenced: Collection<String>) = withContext(Dispatchers.IO) {
        val keep = referenced.toHashSet()
        dir.listFiles()?.filter { it.name !in keep }?.forEach {
            cache.remove(it.name)
            it.delete()
        }
    }

    private companion object {
        const val MAX_EDGE = 2048
        const val CACHE_BYTES = 48 * 1024 * 1024
    }
}
