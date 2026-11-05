package io.github.muhammadunderscoreusman

/**
 * Compresses images. Create one with [ImageCompressor]; it is stateless and thread-safe.
 *
 * Failures are returned as [Result.failure] holding a [CompressionError]; only coroutine
 * cancellation is rethrown.
 */
public interface ImageCompressor {
    public suspend fun compress(
        source: ImageSource,
        config: CompressionConfig = CompressionConfig(),
    ): Result<CompressedImage>
}

/** Convenience for the common case of having the image as bytes already. */
public suspend fun ImageCompressor.compress(
    bytes: ByteArray,
    config: CompressionConfig = CompressionConfig(),
): Result<CompressedImage> = compress(ImageSource.Bytes(bytes), config)

/** Where the input image comes from. More sources (file, uri) can be added without breaking callers. */
public sealed interface ImageSource {
    public class Bytes(public val data: ByteArray) : ImageSource
}

public enum class OutputFormat {
    /** Lossy; honours quality and target-size search. */
    Jpeg,

    /** Lossless; quality is ignored, so a target size is reached by scaling down only. */
    Png;

    internal val isLossy: Boolean get() = this == Jpeg
}

/**
 * Immutable settings, created with the DSL:
 * ```
 * val config = CompressionConfig {
 *     maxWidth = 1080
 *     maxHeight = 1080
 *     quality = 85
 *     targetSizeBytes = 300 * 1024L
 * }
 * ```
 * A builder (instead of a data class) lets later versions add options without breaking binary compatibility.
 * Metadata (EXIF, GPS) is always stripped in this version.
 */
public class CompressionConfig private constructor(
    /** Longest width allowed after orientation is applied. Never upscales. `null` = no limit. */
    public val maxWidth: Int?,
    /** Longest height allowed after orientation is applied. Never upscales. `null` = no limit. */
    public val maxHeight: Int?,
    /** Encoder quality, 0..100. Ignored for lossless formats. */
    public val quality: Int,
    public val format: OutputFormat,
    /** If set, the library lowers quality and then scales down until the output fits. */
    public val targetSizeBytes: Long?,
    /** Lowest quality the target-size search may use. Capped to [quality]. */
    public val minQuality: Int,
) {
    public class Builder internal constructor() {
        public var maxWidth: Int? = null
        public var maxHeight: Int? = null
        public var quality: Int = 80
        public var format: OutputFormat = OutputFormat.Jpeg
        public var targetSizeBytes: Long? = null
        public var minQuality: Int = 40

        internal fun build(): CompressionConfig {
            require(quality in 0..100) { "quality must be in 0..100 but was $quality" }
            require(minQuality in 0..100) { "minQuality must be in 0..100 but was $minQuality" }
            require(maxWidth == null || maxWidth!! > 0) { "maxWidth must be positive" }
            require(maxHeight == null || maxHeight!! > 0) { "maxHeight must be positive" }
            require(targetSizeBytes == null || targetSizeBytes!! > 0) { "targetSizeBytes must be positive" }
            return CompressionConfig(
                maxWidth = maxWidth,
                maxHeight = maxHeight,
                quality = quality,
                format = format,
                targetSizeBytes = targetSizeBytes,
                minQuality = minOf(minQuality, quality),
            )
        }
    }

    override fun toString(): String =
        "CompressionConfig(maxWidth=$maxWidth, maxHeight=$maxHeight, quality=$quality, " +
            "format=$format, targetSizeBytes=$targetSizeBytes, minQuality=$minQuality)"
}

/** Builds a [CompressionConfig]. Invalid values throw [IllegalArgumentException] immediately. */
public fun CompressionConfig(block: CompressionConfig.Builder.() -> Unit = {}): CompressionConfig =
    CompressionConfig.Builder().apply(block).build()

/** The result: encoded bytes plus the final pixel size, so callers need not decode again. */
public class CompressedImage internal constructor(
    public val bytes: ByteArray,
    public val width: Int,
    public val height: Int,
    public val format: OutputFormat,
) {
    public val sizeBytes: Int get() = bytes.size
    override fun toString(): String = "CompressedImage(${width}x$height, $format, $sizeBytes bytes)"
}

public sealed class CompressionError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** The bytes are empty, corrupt, or not an image the platform can decode. */
    public class InvalidImage(cause: Throwable? = null) :
        CompressionError("Input is not a decodable image", cause)

    public class OutOfMemory(cause: Throwable? = null) :
        CompressionError("Not enough memory to process this image", cause)

    public class EncodingFailed(cause: Throwable? = null) :
        CompressionError("The platform encoder failed", cause)

    /** Even the lowest quality and the maximum scale-down stayed above the target. */
    public class TargetSizeNotReachable(
        public val bestSizeBytes: Long,
        public val targetSizeBytes: Long,
    ) : CompressionError("Smallest result was $bestSizeBytes bytes, target was $targetSizeBytes bytes")

    public class Unknown(cause: Throwable) : CompressionError(cause.message ?: "Unexpected failure", cause)
}
