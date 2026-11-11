package app.mindblow.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.roundToInt

class Pic(val bytes: ByteArray, val mime: String, val w: Int, val h: Int) {
    companion object {
        private const val SIDE = 2048
        private val KEEP = setOf("image/png", "image/jpeg", "image/webp")

        /**
         * Igual a web/src/lib/images/shrink.ts: lado maior de até 2048px em WebP 85%.
         * GIF vai como está, e se a versão reduzida sair maior, vai o original.
         * O ImageDecoder já aplica a rotação do EXIF e lê HEIC.
         */
        fun read(c: Context, u: Uri): Pic? {
            val r = c.contentResolver
            val type = r.getType(u)?.takeIf { it.startsWith("image/") } ?: return null
            val raw = r.openInputStream(u)?.use { it.readBytes() } ?: return null
            val gif = type == "image/gif"
            var ow = 0
            var oh = 0
            val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(raw))) { d, info, _ ->
                ow = info.size.width
                oh = info.size.height
                val k = if (gif) 1f else minOf(1f, SIDE.toFloat() / max(ow, oh))
                d.setTargetSize((ow * k).roundToInt(), (oh * k).roundToInt())
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            if (gif) {
                bmp.recycle()
                return Pic(raw, type, ow, oh)
            }
            val w = bmp.width
            val h = bmp.height
            val out = ByteArrayOutputStream()
            @Suppress("DEPRECATION")
            val webp = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
            bmp.compress(webp, 85, out)
            bmp.recycle()
            val small = out.toByteArray()
            return if (type in KEEP && raw.size <= small.size) Pic(raw, type, ow, oh) else Pic(small, "image/webp", w, h)
        }
    }
}
