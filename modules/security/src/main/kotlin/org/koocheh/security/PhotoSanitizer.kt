package org.koocheh.security

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

/**
 * Photos are off by default; this runs only when a photo feature is enabled.
 *
 * sanitize: decodes to pixels and re-encodes as JPEG, so EXIF/XMP/ICC metadata
 * from the source container is not carried over. blurred: heavy downscale then
 * upscale, used as the placeholder until Tier 3.
 *
 * [UNVERIFIED] Not run on a device. Re-encoding is expected to drop metadata,
 * but that has not been checked against real files, and an image that fails to
 * decode raises IllegalArgumentException rather than passing through.
 */
class PhotoSanitizer {

    fun sanitize(image: ByteArray): ByteArray {
        val bitmap = decode(image)
        try {
            return encode(bitmap, SANITIZE_QUALITY)
        } finally {
            bitmap.recycle()
        }
    }

    fun blurred(sanitized: ByteArray): ByteArray {
        val source = decode(sanitized)
        val small = Bitmap.createScaledBitmap(
            source,
            maxOf(1, source.width / BLUR_FACTOR),
            maxOf(1, source.height / BLUR_FACTOR),
            true
        )
        val restored = Bitmap.createScaledBitmap(small, source.width, source.height, true)
        try {
            return encode(restored, BLUR_QUALITY)
        } finally {
            if (small !== restored) small.recycle()
            restored.recycle()
            source.recycle()
        }
    }

    private fun decode(bytes: ByteArray): Bitmap =
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalArgumentException("undecodable image")

    private fun encode(bitmap: Bitmap, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }

    private companion object {
        const val SANITIZE_QUALITY = 85
        const val BLUR_QUALITY = 60
        const val BLUR_FACTOR = 24
    }
}
