package com.nendo.argosy.ui.common

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntRect
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.memory.MemoryCache
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.Transformation
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A game screenshot or save-state frame filling its slot.
 *
 * A dual-screen frame captured as both screens stacked (DS 2:3 or 3DS 5:6 in shape) shows only
 * its top screen in a landscape slot, and both screens fitted whole in a portrait or square slot.
 * Every other image fills the slot center-cropped.
 */
@Composable
fun ScreenshotImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    onError: ((AsyncImagePainter.State.Error) -> Unit)? = null
) {
    val context = LocalContext.current
    BoxWithConstraints(modifier = modifier) {
        val slotAspect = if (constraints.hasBoundedWidth && constraints.hasBoundedHeight && constraints.maxHeight > 0) {
            constraints.maxWidth.toFloat() / constraints.maxHeight
        } else {
            StackedScreenshot.DEFAULT_SLOT_ASPECT
        }
        val aspectKey = (slotAspect * ASPECT_KEY_PRECISION).roundToInt()
        val request = remember(model, aspectKey) { screenshotRequest(context, model, slotAspect) }
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            onError = onError,
            contentScale = if (slotAspect > 1f) ContentScale.Crop else ContentScale.Fit
        )
    }
}

private const val ASPECT_KEY_PRECISION = 100f

/**
 * Crops a stacked dual-screen screenshot to its top screen for a landscape backdrop. Any other
 * image passes through unchanged. Never call it for a cover: a cover can share a stacked frame's
 * shape.
 */
fun ImageRequest.Builder.stackedScreenshotBackdrop(): ImageRequest.Builder =
    transformations(StackedScreenshotTransformation(StackedScreenshot.DEFAULT_SLOT_ASPECT))

private fun screenshotRequest(context: Context, model: Any?, slotAspect: Float): ImageRequest {
    val transformation = StackedScreenshotTransformation(slotAspect)
    val source = model as? ImageRequest
    val builder = source?.newBuilder() ?: ImageRequest.Builder(context).data(model)
    source?.memoryCacheKey?.let { key ->
        builder.memoryCacheKey(MemoryCache.Key("${key.key}#${transformation.cacheKey}", key.extras))
    }
    return builder.transformations(transformation).build()
}

private class StackedScreenshotTransformation(private val slotAspect: Float) : Transformation {

    override val cacheKey: String =
        "stacked-screenshot:${(slotAspect * ASPECT_KEY_PRECISION).roundToInt()}"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val rect = StackedScreenshot.sourceRect(input.width, input.height, slotAspect) ?: return input
        return Bitmap.createBitmap(input, rect.left, rect.top, rect.width, rect.height)
    }
}

internal object StackedScreenshot {

    const val DEFAULT_SLOT_ASPECT = 16f / 9f
    private const val DS_ASPECT = 256f / 384f
    private const val N3DS_ASPECT = 400f / 480f
    private const val ASPECT_TOLERANCE = 0.02f

    fun isStacked(width: Float, height: Float): Boolean {
        if (width <= 0f || height <= width) return false
        val aspect = width / height
        return abs(aspect - DS_ASPECT) <= ASPECT_TOLERANCE || abs(aspect - N3DS_ASPECT) <= ASPECT_TOLERANCE
    }

    fun topScreenHeight(height: Float): Float = height / 2f

    fun sourceRect(width: Int, height: Int, slotAspect: Float): IntRect? {
        if (width <= 0 || height <= 0) return null
        val stacked = isStacked(width.toFloat(), height.toFloat())
        val landscape = slotAspect > 1f
        return when {
            stacked && landscape -> IntRect(0, 0, width, topScreenHeight(height.toFloat()).roundToInt())
            stacked || landscape -> null
            else -> centerCrop(width, height, slotAspect)
        }
    }

    private fun centerCrop(width: Int, height: Int, slotAspect: Float): IntRect? {
        val imageAspect = width.toFloat() / height
        return when {
            imageAspect > slotAspect -> {
                val cropWidth = (height * slotAspect).roundToInt().coerceIn(1, width)
                val left = (width - cropWidth) / 2
                IntRect(left, 0, left + cropWidth, height)
            }
            imageAspect < slotAspect -> {
                val cropHeight = (width / slotAspect).roundToInt().coerceIn(1, height)
                val top = (height - cropHeight) / 2
                IntRect(0, top, width, top + cropHeight)
            }
            else -> null
        }
    }
}
