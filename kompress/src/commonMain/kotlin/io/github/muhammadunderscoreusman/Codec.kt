package io.github.muhammadunderscoreusman

import kotlin.math.roundToInt

/**
 * The only thing each platform has to implement. Everything else (config, search, errors,
 * threading) is shared, so a fix in shared code reaches Android and iOS together.
 */
internal interface ImageCodec {
    /**
     * Decodes [data] already reduced and oriented:
     *  - EXIF orientation is applied to the pixels (the result is upright),
     *  - the result fits inside maxWidth x maxHeight, keeps the aspect ratio, and is never upscaled,
     *  - large images are decoded at reduced size, never fully into memory first.
     *
     * Throws [CompressionError.InvalidImage] / [CompressionError.OutOfMemory] on failure.
     */
    fun decode(data: ByteArray, maxWidth: Int?, maxHeight: Int?): DecodedImage
}

internal interface DecodedImage {
    val width: Int
    val height: Int

    /** Encodes without metadata. Can be called many times (target-size search). */
    fun encode(format: OutputFormat, quality: Int): ByteArray

    /** Frees native memory. Always called, even on failure. */
    fun release()
}

/** Largest size that fits the box, keeps the aspect ratio and never upscales. */
internal fun fitInside(width: Int, height: Int, maxWidth: Int?, maxHeight: Int?): Pair<Int, Int> {
    require(width > 0 && height > 0) { "image size must be positive" }
    val scaleW = if (maxWidth != null) maxWidth.toDouble() / width else 1.0
    val scaleH = if (maxHeight != null) maxHeight.toDouble() / height else 1.0
    val scale = minOf(scaleW, scaleH, 1.0)
    if (scale >= 1.0) return width to height
    return maxOf(1, (width * scale).roundToInt()) to maxOf(1, (height * scale).roundToInt())
}
