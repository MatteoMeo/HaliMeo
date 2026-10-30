package it.halimeo.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Small process-wide bitmap cache for diary/background images. Keys include the requested
 * maximum dimension so callers never receive an image decoded at an unsuitable resolution.
 */
private val scaledBitmapCache = object : LruCache<String, Bitmap>(
    (Runtime.getRuntime().maxMemory() / 1024L / 16L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
) {
    override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
}

/**
 * Decode user images away from the UI thread, downsample before display, and reuse recent
 * decodes to avoid repeated content-provider IO and bitmap allocations when cards are reopened.
 */
internal fun trimScaledBitmapCache() {
    scaledBitmapCache.evictAll()
}

internal suspend fun decodeScaledBitmap(
    context: Context,
    uriString: String,
    maxDimension: Int
): Bitmap? = withContext(Dispatchers.IO) {
    val cacheKey = "$uriString#$maxDimension"
    scaledBitmapCache.get(cacheKey)?.let { return@withContext it }
    val uri = Uri.parse(uriString)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching {
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        var sampleSize = 1
        while (maxOf(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > maxDimension) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }.getOrNull()?.also { decoded ->
        scaledBitmapCache.put(cacheKey, decoded)
    }
}
