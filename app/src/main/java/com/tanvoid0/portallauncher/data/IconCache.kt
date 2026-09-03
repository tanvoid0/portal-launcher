package com.tanvoid0.portallauncher.data

import android.app.ActivityManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.withClip
import com.tanvoid0.portallauncher.ui.kit.IconShape
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Rasterised app icons, kept between screens.
 *
 * Rendering an adaptive icon is the expensive part of drawing a cell, and the app
 * drawer asks for the same icons every time it opens. Without this, scrolling the
 * drawer re-rendered every drawable that came back into view.
 *
 * Application-scoped on purpose: a ViewModel-scoped cache is thrown away on every
 * navigation, which is exactly when the drawer is about to be reopened.
 */
class IconCache(
    context: Context,
    private val appRepository: AppRepository,
    scope: CoroutineScope
) {

    // Needed to wrap a masked bitmap back into a Drawable for badging — see [icon].
    private val resources = context.resources

    /**
     * Sized against the app's own heap limit rather than a fixed entry count: a 48dp
     * icon is ~4x the bytes at 3x density that it is at 1.5x, so counting entries
     * would mean very different memory on different devices.
     *
     * An eighth of the heap is the conventional share for an image cache. At 96x96
     * ARGB_8888 (~37 KB) that is several hundred icons on a 64 MB heap, which is more
     * than any drawer holds.
     */
    private val cache: LruCache<String, ImageBitmap> = run {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val heapBytes = activityManager.memoryClass * 1024 * 1024
        object : LruCache<String, ImageBitmap>(heapBytes / 8) {
            override fun sizeOf(key: String, value: ImageBitmap): Int =
                value.asAndroidBitmap().byteCount
        }
    }

    init {
        // The app list re-emits precisely when a package is installed, removed or
        // updated — which is also the only time an icon changes. Clearing wholesale is
        // cruder than evicting one package, and it is also correct without having to
        // track which packages a change touched; the cache refills from visible cells.
        scope.launch {
            var seenFirst = false
            appRepository.apps.collect {
                if (seenFirst) cache.evictAll() else seenFirst = true
            }
        }
    }

    /**
     * The icon for [app] at [sizePx] masked to [shape], rasterising on
     * [Dispatchers.Default] if it is not already cached. Returns null when the app has
     * no resolvable icon.
     */
    suspend fun icon(app: LaunchableApp, sizePx: Int, shape: IconShape = IconShape.System): ImageBitmap? {
        val key = "${app.key}@$sizePx@${shape.name}"
        cache.get(key)?.let { return it }
        val rendered = withContext(Dispatchers.Default) {
            val icon = appRepository.loadIcon(app) ?: return@withContext null
            // Mask before badging: getUserBadgedIcon returns a plain wrapper drawable,
            // not an AdaptiveIconDrawable, so masking that instead of the raw icon
            // would drop the shape for every work-profile app. See AppRepository.badge.
            val masked = icon.toImageBitmap(sizePx, shape).asAndroidBitmap()
            appRepository.badge(masked.toDrawable(resources), app).toImageBitmap(sizePx, IconShape.System)
        } ?: return null
        cache.put(key, rendered)
        return rendered
    }
}

/**
 * Rasterises to a [size]x[size] bitmap. An [AdaptiveIconDrawable] with a non-[IconShape.System]
 * [shape] is clipped to that shape's path and its layers redrawn oversized and centred —
 * they are 108dp for a 72dp visible icon, so a `-size * extraInsetFraction` inset on
 * every side lines the crop up through our mask the way it would through the OS's own.
 * Anything else (including an already-masked, already-badged bitmap wrapper) draws
 * unchanged.
 */
internal fun Drawable.toImageBitmap(size: Int, shape: IconShape): ImageBitmap {
    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)
    if (this is AdaptiveIconDrawable && shape != IconShape.System) {
        val inset = (size * AdaptiveIconDrawable.getExtraInsetFraction()).toInt()
        val bounds = Rect(-inset, -inset, size + inset, size + inset)
        canvas.withClip(shape.path(size.toFloat())) {
            background?.apply { this.bounds = bounds; draw(this@withClip) }
            foreground?.apply { this.bounds = bounds; draw(this@withClip) }
        }
    } else {
        setBounds(0, 0, size, size)
        draw(canvas)
    }
    return bitmap.asImageBitmap()
}
