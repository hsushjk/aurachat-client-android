package dev.aurachat.client.net

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import java.io.ByteArrayOutputStream
import kotlin.math.max

// 40MB 够放一屏 720px 缩略图，翻回来不用重解；进程死了重来
object ImageCache {
    private val cache = object : LruCache<String, Bitmap>(40 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bmp: Bitmap) {
        cache.put(key, bmp)
    }
}

fun prepareImage(ctx: Context, uri: Uri, maxSide: Int = 1600, quality: Int = 82): Pair<String, String>? {
    val res = ctx.contentResolver
    val mime = res.getType(uri) ?: "image/jpeg"
    val raw = runCatching { res.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: return null

    val probe = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(raw, 0, raw.size, probe)
    var sample = 1
    while (max(probe.outWidth, probe.outHeight) / (sample * 2) >= maxSide) sample *= 2

    val decoded = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return null
    val longSide = max(decoded.width, decoded.height)
    val scaled = if (longSide > maxSide) {
        val k = maxSide.toFloat() / longSide
        Bitmap.createScaledBitmap(decoded, (decoded.width * k).toInt().coerceAtLeast(1), (decoded.height * k).toInt().coerceAtLeast(1), true)
    } else decoded

    val png = mime == "image/png"
    val bos = ByteArrayOutputStream()
    scaled.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, quality, bos)
    if (scaled !== decoded) scaled.recycle()
    return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP) to if (png) "image/png" else "image/jpeg"
}

fun decodeInline(b64: String, maxSide: Int = 1080): Bitmap? {
    val raw = runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull() ?: return null
    return decodeBytes(raw, maxSide)
}

fun decodeBytes(raw: ByteArray, maxSide: Int = 1080): Bitmap? {
    val probe = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(raw, 0, raw.size, probe)
    var sample = 1
    while (max(probe.outWidth, probe.outHeight) / (sample * 2) >= maxSide) sample *= 2
    return runCatching {
        BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}
