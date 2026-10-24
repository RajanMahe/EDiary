package com.aburv.kurippidu.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.UUID
import kotlin.math.max

/**
 * Copies a picked or captured photo into the app's private attachments folder,
 * upright and no larger than [maxSize] pixels on its long side (JPEG).
 */
class ImageImporter(private val context: Context, private val dir: File) {

    /** @return the new file's name inside [dir] */
    fun import(uri: Uri, maxSize: Int = 2048): String {
        val resolver = context.contentResolver

        // Bounds only: decodeStream returns null here by design and fills in outWidth/outHeight
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val stream = resolver.openInputStream(uri) ?: error("Can't open the photo")
        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Not an image" }

        // Decode at roughly the target size to keep memory low
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSize) sample *= 2
        var bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Can't read the photo")

        // Cameras often store photos sideways with a rotation tag
        val rotation = resolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f

        val scale = minOf(1f, maxSize.toFloat() / max(bitmap.width, bitmap.height))
        if (scale < 1f || rotation != 0f) {
            val matrix = Matrix().apply {
                postScale(scale, scale)
                postRotate(rotation)
            }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        dir.mkdirs()
        val name = "${UUID.randomUUID()}.jpg"
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        return name
    }

    companion object {
        fun attachmentsDir(context: Context) = File(context.filesDir, "attachments")

        /** Small version of an attachment for thumbnails. */
        fun thumbnail(file: File, sizePx: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0) return null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= sizePx) sample *= 2
            return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
}
