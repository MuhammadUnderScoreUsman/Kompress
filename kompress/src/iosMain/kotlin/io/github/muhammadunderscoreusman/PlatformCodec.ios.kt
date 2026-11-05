@file:OptIn(ExperimentalForeignApi::class)

package io.github.muhammadunderscoreusman

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataCreateMutable
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryGetValue
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFNumberGetValue
import platform.CoreFoundation.CFNumberRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringRef
//import platform.CoreFoundation.__CFNumber
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFNumberDoubleType
import platform.CoreFoundation.kCFNumberIntType
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextSetRGBFillColor
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetAlphaInfo
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.ImageIO.CGImageDestinationAddImage
import platform.ImageIO.CGImageDestinationCreateWithData
import platform.ImageIO.CGImageDestinationFinalize
import platform.ImageIO.CGImageSourceCopyPropertiesAtIndex
import platform.ImageIO.CGImageSourceCreateThumbnailAtIndex
import platform.ImageIO.CGImageSourceCreateWithData
import platform.ImageIO.CGImageSourceGetCount
import platform.ImageIO.CGImageSourceRef
import platform.ImageIO.kCGImageDestinationLossyCompressionQuality
import platform.ImageIO.kCGImagePropertyOrientation
import platform.ImageIO.kCGImagePropertyPixelHeight
import platform.ImageIO.kCGImagePropertyPixelWidth
import platform.ImageIO.kCGImageSourceCreateThumbnailFromImageAlways
import platform.ImageIO.kCGImageSourceCreateThumbnailWithTransform
import platform.ImageIO.kCGImageSourceShouldCacheImmediately
import platform.ImageIO.kCGImageSourceThumbnailMaxPixelSize
import platform.posix.memcpy

internal actual fun platformCodec(): ImageCodec = IosImageCodec()

internal class IosImageCodec : ImageCodec {

    override fun decode(data: ByteArray, maxWidth: Int?, maxHeight: Int?): DecodedImage {
        if (data.isEmpty()) throw CompressionError.InvalidImage()

        // One copy of the bytes into a CFData; everything after this works on native memory.
        val cfData = data.usePinned { pinned ->
            CFDataCreate(null, pinned.addressOf(0).reinterpret(), data.size.convert())
        } ?: throw CompressionError.InvalidImage()

        try {
            val source = CGImageSourceCreateWithData(cfData, null) ?: throw CompressionError.InvalidImage()
            try {
                if (CGImageSourceGetCount(source) == 0uL) throw CompressionError.InvalidImage()

                val info = readImageInfo(source) ?: throw CompressionError.InvalidImage()
                val rawWidth = info.width
                val rawHeight = info.height
                val orientation = info.orientation

                // Orientation 5..8 means a quarter turn, so width and height swap.
                val uprightWidth = if (orientation in 5..8) rawHeight else rawWidth
                val uprightHeight = if (orientation in 5..8) rawWidth else rawHeight
                val (targetWidth, targetHeight) = fitInside(uprightWidth, uprightHeight, maxWidth, maxHeight)
                val maxPixel = maxOf(targetWidth, targetHeight)

                // The thumbnail API decodes straight to the reduced size and applies orientation.
                val maxPixelNumber = cfNumber(maxPixel)
                val options = CFDictionaryCreateMutable(
                    null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr,
                )
                try {
                    CFDictionarySetValue(options, kCGImageSourceCreateThumbnailFromImageAlways, kCFBooleanTrue)
                    CFDictionarySetValue(options, kCGImageSourceCreateThumbnailWithTransform, kCFBooleanTrue)
                    CFDictionarySetValue(options, kCGImageSourceShouldCacheImmediately, kCFBooleanTrue)
                    CFDictionarySetValue(options, kCGImageSourceThumbnailMaxPixelSize, maxPixelNumber)
                    val image = CGImageSourceCreateThumbnailAtIndex(source, 0u, options)
                        ?: throw CompressionError.InvalidImage()
                    return IosDecodedImage(image)
                } finally {
                    CFRelease(options)
                    CFRelease(maxPixelNumber)
                }
            } finally {
                CFRelease(source)
            }
        } finally {
            CFRelease(cfData)
        }
    }
}

internal class IosDecodedImage(private val image: CGImageRef) : DecodedImage {
    override val width: Int get() = CGImageGetWidth(image).toInt()
    override val height: Int get() = CGImageGetHeight(image).toInt()

    private var flattened: CGImageRef? = null

    override fun encode(format: OutputFormat, quality: Int): ByteArray {
        val source = if (format == OutputFormat.Jpeg) jpegSource() else image
        val sink = CFDataCreateMutable(null, 0) ?: throw CompressionError.EncodingFailed()
        val typeName = CFStringCreateWithCString(null, format.uti(), kCFStringEncodingUTF8)
        try {
            val destination = CGImageDestinationCreateWithData(sink, typeName, 1u, null)
                ?: throw CompressionError.EncodingFailed()
            try {
                if (format == OutputFormat.Jpeg) {
                    val qualityNumber = cfNumber(quality / 100.0)
                    val properties = CFDictionaryCreateMutable(
                        null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr,
                    )
                    try {
                        CFDictionarySetValue(properties, kCGImageDestinationLossyCompressionQuality, qualityNumber)
                        CGImageDestinationAddImage(destination, source, properties)
                    } finally {
                        CFRelease(properties)
                        CFRelease(qualityNumber)
                    }
                } else {
                    CGImageDestinationAddImage(destination, source, null)
                }
                if (!CGImageDestinationFinalize(destination)) throw CompressionError.EncodingFailed()
            } finally {
                CFRelease(destination)
            }
            return sink.toByteArray()
        } finally {
            CFRelease(typeName)
            CFRelease(sink)
        }
    }

    override fun release() {
        flattened?.let { CGImageRelease(it) }
        flattened = null
        CGImageRelease(image)
    }

    /** JPEG has no alpha; paint transparent pixels onto white instead of letting them turn black. */
    private fun jpegSource(): CGImageRef {
        val info = CGImageGetAlphaInfo(image)
        val opaque = info == CGImageAlphaInfo.kCGImageAlphaNone ||
                info == CGImageAlphaInfo.kCGImageAlphaNoneSkipFirst ||
                info == CGImageAlphaInfo.kCGImageAlphaNoneSkipLast
        if (opaque) return image
        flattened?.let { return it }

        val w = CGImageGetWidth(image)
        val h = CGImageGetHeight(image)
        val colorSpace = CGColorSpaceCreateDeviceRGB()
        val context = CGBitmapContextCreate(
            null, w, h, 8u, 0u, colorSpace, CGImageAlphaInfo.kCGImageAlphaNoneSkipLast.value,
        )
        try {
            val rect = CGRectMake(0.0, 0.0, w.toDouble(), h.toDouble())
            CGContextSetRGBFillColor(context, 1.0, 1.0, 1.0, 1.0)
            CGContextFillRect(context, rect)
            CGContextDrawImage(context, rect, image)
            return CGBitmapContextCreateImage(context)?.also { flattened = it } ?: image
        } finally {
            CGContextRelease(context)
            CGColorSpaceRelease(colorSpace)
        }
    }
}

private class ImageInfo(val width: Int, val height: Int, val orientation: Int)

/** Pixel size and EXIF orientation from the header only; nothing is decoded. */
private fun readImageInfo(source: CGImageSourceRef): ImageInfo? {
    val properties = CGImageSourceCopyPropertiesAtIndex(source, 0u, null) ?: return null
    try {
        val width = properties.intFor(kCGImagePropertyPixelWidth) ?: return null
        val height = properties.intFor(kCGImagePropertyPixelHeight) ?: return null
        if (width <= 0 || height <= 0) return null
        return ImageInfo(width, height, properties.intFor(kCGImagePropertyOrientation) ?: 1)
    } finally {
        CFRelease(properties)
    }
}

private fun OutputFormat.uti(): String = when (this) {
    OutputFormat.Jpeg -> "public.jpeg"
    OutputFormat.Png -> "public.png"
}

private fun cfNumber(value: Int): CFNumberRef = memScoped {
    val slot = alloc<IntVar>().also { it.value = value }
    CFNumberCreate(null, kCFNumberIntType, slot.ptr)!!
}

private fun cfNumber(value: Double): CFNumberRef = memScoped {
    val slot = alloc<DoubleVar>().also { it.value = value }
    CFNumberCreate(null, kCFNumberDoubleType, slot.ptr)!!
}

private fun CFDictionaryRef.intFor(key: CFStringRef?): Int? {
    // 1. CFDictionaryGetValue returns a COpaquePointer? (void*)
    val numberPtr = CFDictionaryGetValue(this, key) ?: return null
    return memScoped {
        val slot = alloc<IntVar>().also { it.value = 0 }
        // 2. Safely cast the pointer to CFNumberRef (which maps to CPointer<__CFNumber>?)
        val numberRef = numberPtr as CFNumberRef
        CFNumberGetValue(numberRef, kCFNumberIntType, slot.ptr)
        slot.value
    }
}


private fun CFDataRef.toByteArray(): ByteArray {
    val length = CFDataGetLength(this).toInt()
    val out = ByteArray(length)
    if (length > 0) {
        out.usePinned { memcpy(it.addressOf(0), CFDataGetBytePtr(this), length.convert()) }
    }
    return out
}
