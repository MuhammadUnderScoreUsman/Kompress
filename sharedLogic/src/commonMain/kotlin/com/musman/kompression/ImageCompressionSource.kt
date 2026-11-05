package com.musman.kompression

import io.github.muhammadunderscoreusman.CompressionConfig
import io.github.muhammadunderscoreusman.ImageCompressor
import io.github.muhammadunderscoreusman.compress

class ImageCompressionSource {

    private val compressor = ImageCompressor()

    suspend fun compressImage(original: ByteArray, targetKb: Float): CompressState {
        val config = CompressionConfig {
            maxWidth = 1080
            maxHeight = 1080
            quality = 85
            targetSizeBytes = targetKb.toLong() * 1024
        }

        return compressor.compress(bytes = original, config  = config).fold(
            onSuccess = { out ->
                CompressState.Done(
                    summary = "Original: ${original.size / 1024} KB\n" +
                            "Compressed: ${out.sizeBytes / 1024} KB (${out.width} x ${out.height})\n" +
                            "Saved: ${100 - out.sizeBytes * 100L / original.size}% ",
                    preview = out.bytes,
                )
            },
            onFailure = { CompressState.Failed(it.message ?: "Compression failed") },
        )
    }

}