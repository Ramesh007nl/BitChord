package com.music.bitchord.car

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import java.util.LinkedHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** Loads car artwork without allowing a slow or broken cover to block a template. */
class CarArtworkLoader(
    private val context: Context,
    private val imageLoader: ImageLoader? = null,
    private val decodeTimeoutMs: Long = DEFAULT_DECODE_TIMEOUT_MS,
) {
    private val cache = object : LinkedHashMap<String, CarIcon>(
        MAX_CACHE_ENTRIES,
        LOAD_FACTOR,
        true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CarIcon>): Boolean =
            size > MAX_CACHE_ENTRIES
    }

    /**
     * Returns a decoded bitmap icon or the supplied vector fallback.
     *
     * [withTimeoutOrNull] handles only this loader's own timeout. Cancellation from the
     * owning screen still escapes so stale artwork cannot be published after disposal.
     */
    suspend fun load(artworkUri: Uri?, @DrawableRes fallbackRes: Int): CarIcon {
        val fallback = resourceIcon(fallbackRes)
        if (artworkUri == null) return fallback

        cached(artworkUri)?.let { return it }

        val loaded = try {
            withTimeoutOrNull(decodeTimeoutMs) {
                decode(artworkUri)
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            null
        } ?: return fallback

        synchronized(cache) {
            cache[artworkUri.toString()] = loaded
        }
        return loaded
    }

    /** Returns already-decoded artwork without doing I/O on the car template thread. */
    fun cachedOrFallback(artworkUri: Uri?, @DrawableRes fallbackRes: Int): CarIcon =
        artworkUri?.let(::cached) ?: resourceIcon(fallbackRes)

    private suspend fun decode(artworkUri: Uri): CarIcon? {
        val request = ImageRequest.Builder(context)
            .data(artworkUri)
            .size(MAX_ARTWORK_PX, MAX_ARTWORK_PX)
            .allowHardware(false)
            .build()
        val result = (imageLoader ?: SingletonImageLoader.get(context)).execute(request)
            as? SuccessResult ?: return null
        val drawable = result.image.asDrawable(context.resources) as? BitmapDrawable ?: return null
        return CarIcon.Builder(IconCompat.createWithBitmap(drawable.bitmap)).build()
    }

    private fun cached(artworkUri: Uri): CarIcon? = synchronized(cache) {
        cache[artworkUri.toString()]
    }

    private fun resourceIcon(@DrawableRes resource: Int): CarIcon =
        CarIcon.Builder(IconCompat.createWithResource(context, resource)).build()

    private companion object {
        const val MAX_ARTWORK_PX = 256
        const val MAX_CACHE_ENTRIES = 40
        const val DEFAULT_DECODE_TIMEOUT_MS = 3_000L
        const val LOAD_FACTOR = 0.75f
    }
}
