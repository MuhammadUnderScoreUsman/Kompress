package com.musman.kompression.batch

import android.graphics.Bitmap

data class ImageItem(
    val bitmap: Bitmap,
    val data: ByteArray,
    val sizeString: String
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ImageItem) return false
        return bitmap == other.bitmap &&
                data.contentEquals(other.data) &&
                sizeString == other.sizeString
    }

    override fun hashCode(): Int {
        var result = bitmap.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + sizeString.hashCode()
        return result
    }
}
