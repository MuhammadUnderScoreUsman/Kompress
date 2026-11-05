package io.github.muhammadunderscoreusman

/** Each platform supplies its native codec: BitmapFactory on Android, ImageIO on iOS. */
internal expect fun platformCodec(): ImageCodec

/** Creates a compressor backed by the platform's native codecs. */
public fun ImageCompressor(): ImageCompressor = DefaultImageCompressor(platformCodec())
