package io.github.muhammadunderscoreusman

import android.graphics.Bitmap
import android.graphics.Bitmap.createBitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

internal actual fun platformCodec(): ImageCodec = AndroidImageCodec()

internal class AndroidImageCodec : ImageCodec {

    override fun decode(data: ByteArray, maxWidth: Int?, maxHeight: Int?): DecodedImage {
        try {
            // 1. Read only the header: no pixels are allocated.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw CompressionError.InvalidImage()

            // 2. Work out the final upright size.
            val orientation = readOrientation(data)
            val quarterTurn = orientation in 5..8 // 90/270 rotations swap width and height
            val uprightWidth = if (quarterTurn) bounds.outHeight else bounds.outWidth
            val uprightHeight = if (quarterTurn) bounds.outWidth else bounds.outHeight
            val (targetWidth, targetHeight) = fitInside(uprightWidth, uprightHeight, maxWidth, maxHeight)

            // 3. Decode at a power-of-two reduction that is still >= the target (cheap, saves memory).
            val rawTargetWidth = if (quarterTurn) targetHeight else targetWidth
            val rawTargetHeight = if (quarterTurn) targetWidth else targetHeight
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, rawTargetWidth, rawTargetHeight)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeByteArray(data, 0, data.size, options)
                ?: throw CompressionError.InvalidImage()

            // 4. Apply orientation and the exact resize in one pass.
            return AndroidDecodedImage(upright(decoded, orientation, targetWidth))
        } catch (e: OutOfMemoryError) {
            throw CompressionError.OutOfMemory(e)
        }
    }

    private fun readOrientation(data: ByteArray): Int = try {
        ExifInterface(ByteArrayInputStream(data))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } catch (_: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }

    private fun sampleSize(rawWidth: Int, rawHeight: Int, neededWidth: Int, neededHeight: Int): Int {
        var sample = 1
        while (rawWidth / (sample * 2) >= neededWidth && rawHeight / (sample * 2) >= neededHeight) sample *= 2
        return sample
    }

    private fun upright(bitmap: Bitmap, orientation: Int, targetWidth: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f); matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f); matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f); matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        }
        val uprightWidth = if (orientation in 5..8) bitmap.height else bitmap.width
        val scale = targetWidth.toFloat() / uprightWidth
        if (scale < 1f) matrix.postScale(scale, scale)

        if (matrix.isIdentity) return bitmap
        val out = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (out !== bitmap) bitmap.recycle()
        return out
    }
}

internal class AndroidDecodedImage(private val bitmap: Bitmap) : DecodedImage {
    override val width: Int get() = bitmap.width
    override val height: Int get() = bitmap.height

    // JPEG has no alpha: transparent pixels become black unless we paint them on white first.
    // Built once on first JPEG encode, then reused by every step of the target-size search.
    private var flattened: Bitmap? = null

    private fun jpegSource(): Bitmap {
        if (!bitmap.hasAlpha()) return bitmap
        return flattened ?: flattenOnWhite(bitmap).also { flattened = it }
    }

    override fun encode(format: OutputFormat, quality: Int): ByteArray {
        val source = if (format == OutputFormat.Jpeg) jpegSource() else bitmap
        val compressFormat = when (format) {
            OutputFormat.Jpeg -> Bitmap.CompressFormat.JPEG
            OutputFormat.Png -> Bitmap.CompressFormat.PNG
        }
        val out = ByteArrayOutputStream(source.byteCount / 8)
        try {
            if (!source.compress(compressFormat, quality, out)) throw CompressionError.EncodingFailed()
        } catch (e: OutOfMemoryError) {
            throw CompressionError.OutOfMemory(e)
        }
        return out.toByteArray()
    }

    override fun release() {
        flattened?.recycle()
        flattened = null
        bitmap.recycle()
    }

    private fun flattenOnWhite(src: Bitmap): Bitmap {
        val out = createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(src, 0f, 0f, null)
        }
        return out
    }
}
