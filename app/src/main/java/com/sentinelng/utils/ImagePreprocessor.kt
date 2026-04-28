package com.sentinelng.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.content.Context
import androidx.exifinterface.media.ExifInterface
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object ImagePreprocessor {

    private const val INPUT_SIZE = 224
    private const val PIXEL_SIZE = 3  // RGB
    private const val BYTES_PER_CHANNEL = 4  // float32

    /**
     * Load a Bitmap from a content URI, auto-rotating based on EXIF data.
     */
    fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream: InputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            // Fix rotation
            val exifStream = context.contentResolver.openInputStream(uri) ?: return bitmap
            val exif = ExifInterface(exifStream)
            exifStream.close()

            val rotation = when (exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90  -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

            if (rotation != 0f) {
                val matrix = Matrix().apply { postRotate(rotation) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Resize bitmap to 224×224.
     */
    fun resize(bitmap: Bitmap): Bitmap =
        Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)

    /**
     * Convert 224×224 bitmap to a ByteBuffer scaled to [-1, 1] (MobileNet / EfficientNet style).
     * Layout: [1, 224, 224, 3] float32
     */
    fun toByteBuffer(bitmap: Bitmap): ByteBuffer {
        val resized = if (bitmap.width != INPUT_SIZE || bitmap.height != INPUT_SIZE)
            resize(bitmap) else bitmap

        val byteBuffer = ByteBuffer.allocateDirect(
            1 * INPUT_SIZE * INPUT_SIZE * PIXEL_SIZE * BYTES_PER_CHANNEL
        ).apply { order(ByteOrder.nativeOrder()) }

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF).toFloat()
            val g = ((pixel shr 8) and 0xFF).toFloat()
            val b = (pixel and 0xFF).toFloat()
            // Scale [0,255] → [-1,1]
            byteBuffer.putFloat((r - 127.5f) / 127.5f)
            byteBuffer.putFloat((g - 127.5f) / 127.5f)
            byteBuffer.putFloat((b - 127.5f) / 127.5f)
        }

        byteBuffer.rewind()
        return byteBuffer
    }
}
