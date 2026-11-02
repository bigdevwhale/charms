package app.charms.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

/**
 * The grimoire's engravings, in the app's own files. Camera pictures never stay: each becomes an
 * engraving and the original is deleted at once.
 */
class Vault(private val context: Context) {
    private val dir = File(context.filesDir, "engravings").apply { mkdirs() }
    private val smalls = File(dir, "small").apply { mkdirs() }
    private val cache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun file(name: String) = File(dir, name)
    private fun smallFile(name: String) = File(smalls, name)

    fun put(bmp: Bitmap): String {
        val name = UUID.randomUUID().toString() + ".jpg"
        FileOutputStream(file(name)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return name
    }

    /** A file for the phone's camera to write one picture into. */
    fun capture(): File = File(context.cacheDir, "capture").apply { mkdirs() }.let { d ->
        d.listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() > 6 * 3_600_000L) it.delete() }
        File(d, "shot.jpg")
    }

    fun small(name: String): Bitmap? {
        if (name.isEmpty()) return null
        cache.get(name)?.let { return it }
        val f = smallFile(name)
        if (!f.exists()) {
            val b = decodeUpright(file(name), SMALL) ?: return null
            FileOutputStream(f).use { b.compress(Bitmap.CompressFormat.JPEG, 86, it) }
        }
        val b = BitmapFactory.decodeFile(f.path) ?: return null
        cache.put(name, b)
        return b
    }

    fun full(name: String): Bitmap? = decodeUpright(file(name), 2000)

    fun delete(name: String) {
        if (name.isEmpty()) return
        file(name).delete()
        smallFile(name).delete()
        cache.remove(name)
    }

    companion object {
        const val SMALL = 420

        fun scaleDown(b: Bitmap, max: Int): Bitmap {
            val m = max(b.width, b.height)
            if (m <= max) return b
            val k = max.toFloat() / m
            return Bitmap.createScaledBitmap(b, (b.width * k).toInt().coerceAtLeast(1), (b.height * k).toInt().coerceAtLeast(1), true)
        }

        /** Decodes with subsampling and turns it the way EXIF says. */
        fun decodeUpright(f: File, max: Int): Bitmap? {
            if (!f.exists()) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            if (bounds.outWidth <= 0) return null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= max) sample *= 2
            val raw = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            val rot = runCatching { ExifInterface(f.path).rotationDegrees }.getOrDefault(0)
            val scaled = scaleDown(raw, max)
            if (scaled !== raw) raw.recycle()
            if (rot == 0) return scaled
            val m = Matrix().apply { postRotate(rot.toFloat()) }
            val r = Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, m, true)
            if (r !== scaled) scaled.recycle()
            return r
        }
    }
}
