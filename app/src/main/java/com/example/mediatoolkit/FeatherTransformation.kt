package com.example.mediatoolkit

import android.graphics.*
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.security.MessageDigest

class FeatherTransformation(private val featherSize: Int = 20) : BitmapTransformation() {

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update("feather_transformation".toByteArray(Charsets.UTF_8))
    }

    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        val width = toTransform.width
        val height = toTransform.height

        val result = pool.get(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Tegn originalt billede
        canvas.drawBitmap(toTransform, 0f, 0f, null)

        // Ændring af radial gradienten til at starte fra højre hjørne
        val radius = (width.coerceAtMost(height) / 1f) + featherSize // Øg radius

        // Sætter udgangspunktet for gradienten i øverste højre hjørne
        val gradient = RadialGradient(
            width.toFloat(), 0.3f, radius,
            intArrayOf(
                Color.argb(255, 0, 0, 0),  // 100% opaque
                Color.argb(0, 0, 0, 0)     // 0% opaque
            ),
            null,
            Shader.TileMode.CLAMP
        )

        paint.shader = gradient
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)

        // Tegn effekten over billedet; juster rektanglet for at øge den fuldfarvede del
        canvas.drawRect(0f, 0f, (width + featherSize).toFloat(), (height + featherSize).toFloat(), paint)

        return result
    }
}

