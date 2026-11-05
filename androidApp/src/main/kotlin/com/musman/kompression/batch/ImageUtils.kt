package com.musman.kompression.batch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.format.Formatter
import java.io.ByteArrayOutputStream

fun loadImage(context: Context, uri: Uri): ImageItem? {
    return try {
        val data = context.contentResolver
            .openInputStream(uri)
            ?.use { it.readBytes() }
            ?: return null

        decodeImageItem(context, data)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun decodeImageItem(context: Context, data: ByteArray): ImageItem? {
    val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size) ?: return null

    return ImageItem(
        bitmap = bitmap,
        data = data,
        sizeString = formatBytes(context, data.size.toLong())
    )
}

fun formatBytes(context: Context, bytes: Long): String {
    return Formatter.formatFileSize(context, bytes)
}

fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
    return outputStream.toByteArray()
}
