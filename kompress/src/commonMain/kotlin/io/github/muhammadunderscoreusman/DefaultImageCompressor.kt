package io.github.muhammadunderscoreusman

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

private const val SCALE_STEP_PERCENT = 85
private const val MAX_SCALE_STEPS = 4

internal class DefaultImageCompressor(private val codec: ImageCodec) : ImageCompressor {

    override suspend fun compress(source: ImageSource, config: CompressionConfig): Result<CompressedImage> =
        withContext(Dispatchers.Default) {
            try {
                Result.success(execute(source, config))
            } catch (e: CancellationException) {
                throw e
            } catch (e: CompressionError) {
                Result.failure(e)
            } catch (e: Throwable) {
                Result.failure(CompressionError.Unknown(e))
            }
        }

    /**
     * Pipeline: decode small + upright -> encode at the requested quality.
     * With a target size: binary-search quality downward, and if the minimum quality still
     * overshoots, re-decode at 85% of the size (up to 4 times) and search again.
     */
    private suspend fun execute(source: ImageSource, config: CompressionConfig): CompressedImage {
        val data = when (source) {
            is ImageSource.Bytes -> source.data
        }
        if (data.isEmpty()) throw CompressionError.InvalidImage()

        val target = config.targetSizeBytes
        var boxWidth = config.maxWidth
        var boxHeight = config.maxHeight
        var smallest = Long.MAX_VALUE
        var scaleSteps = 0

        while (true) {
            currentCoroutineContext().ensureActive()
            val image = codec.decode(data, boxWidth, boxHeight)
            try {
                val first = image.encode(config.format, config.quality)
                smallest = minOf(smallest, first.size.toLong())
                if (target == null || first.size <= target) return image.result(first, config.format)

                if (config.format.isLossy && config.quality > config.minQuality) {
                    val search = searchQuality(image, config, target)
                    smallest = minOf(smallest, search.smallest)
                    search.fit?.let { return image.result(it, config.format) }
                }

                if (scaleSteps >= MAX_SCALE_STEPS) {
                    throw CompressionError.TargetSizeNotReachable(smallest, target)
                }
                // Integer math keeps the result identical on every platform.
                boxWidth = maxOf(1, image.width * SCALE_STEP_PERCENT / 100)
                boxHeight = maxOf(1, image.height * SCALE_STEP_PERCENT / 100)
                scaleSteps++
            } finally {
                image.release()
            }
        }
    }

    private class QualitySearch(val fit: ByteArray?, val smallest: Long)

    /** Highest quality in [minQuality, quality-1] whose output fits [target]; at most ~6 encodes. */
    private suspend fun searchQuality(image: DecodedImage, config: CompressionConfig, target: Long): QualitySearch {
        var low = config.minQuality
        var high = config.quality - 1
        var fit: ByteArray? = null
        var smallest = Long.MAX_VALUE
        while (low <= high) {
            currentCoroutineContext().ensureActive()
            val mid = (low + high) / 2
            val out = image.encode(config.format, mid)
            smallest = minOf(smallest, out.size.toLong())
            if (out.size <= target) {
                fit = out
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return QualitySearch(fit, smallest)
    }

    private fun DecodedImage.result(bytes: ByteArray, format: OutputFormat) =
        CompressedImage(bytes, width, height, format)
}
