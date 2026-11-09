package com.nendo.argosy.data.media

import com.nendo.argosy.data.media.cgfx.CgfxFile
import com.nendo.argosy.data.media.cgfx.CgfxParser
import com.nendo.argosy.data.media.cgfx.CgfxSceneRenderer
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * A 3DS banner drawn as the Home Menu draws it: the CGFX scene read in full (models, skeleton,
 * materials, lights, textures, animations) and rendered through the banner's own camera by
 * [CgfxSceneRenderer].
 *
 * The Home Menu shows banners at 400x240; here they are drawn at twice that with 2x2 supersampling
 * (so 16 samples per original pixel), then cropped to what the animation actually covers so the
 * logo fills the space it is shown in.
 */
internal object ThreeDsBannerScene {

    private const val CANVAS_WIDTH = 800
    private const val CANVAS_HEIGHT = 480
    private const val SUPERSAMPLE = 2

    /** The Home Menu advances a banner's animation once per 60 Hz refresh. */
    private const val BANNER_FRAMES_PER_SECOND = 60.0
    /** Output frame rate: every second banner frame, 30 fps. */
    private const val OUTPUT_FRAMES_PER_SECOND = 30.0
    private const val MAX_ANIMATION_FRAMES = 300

    /** Frames looked at to find the area the animation covers. */
    private const val BOUNDS_SAMPLES = 24
    private const val BOUNDS_PADDING = 0.03
    private const val MIN_COVERAGE_FRACTION = 0.002

    private const val MAX_THREADS = 4
    /** Share of the progress bar for finding the banner's area, before its frames are drawn. */
    private const val BOUNDS_SHARE = 0.05f

    /** Encodes one ARGB frame as a still WebP. */
    fun interface FrameEncoder {
        fun encode(argb: IntArray, width: Int, height: Int): ByteArray?
    }

    class Crop(val x: Int, val y: Int, val width: Int, val height: Int)

    private fun parse(cgfx: ByteArray, common: ByteArray?): CgfxFile? {
        val local = CgfxParser.parse(cgfx) ?: return null
        val shared = common?.let { CgfxParser.parse(it) } ?: return local
        return CgfxFile.overlay(shared, local)
    }

    private fun open(file: CgfxFile): CgfxSceneRenderer = CgfxSceneRenderer(file) { texture ->
        CgfxTextureDecoder.decodePixels(texture.data, texture.width, texture.height, texture.format)
    }

    private fun open(cgfx: ByteArray, common: ByteArray?): CgfxSceneRenderer? {
        val file = parse(cgfx, common) ?: return null
        if (file.models.none { model -> model.meshes.isNotEmpty() }) return null
        return open(file)
    }

    /** Whether the full renderer can read this container at all. */
    fun isReadable(cgfx: ByteArray, common: ByteArray? = null): Boolean =
        runCatching { open(cgfx, common) != null }.getOrDefault(false)

    /**
     * The part of the canvas the banner ever draws into, from frames spread over its loop, with a
     * little margin; null when nothing is drawn at all.
     */
    private fun bounds(renderer: CgfxSceneRenderer, shouldStop: () -> Boolean): Crop? {
        val length = renderer.animator.frameCount
        val w = CANVAS_WIDTH / 2
        val h = CANVAS_HEIGHT / 2
        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        val samples = if (length < 2f) 1 else BOUNDS_SAMPLES
        for (s in 0 until samples) {
            if (shouldStop()) return null
            val argb = renderer.render(length * s / samples, w, h, supersample = 1)
            for (y in 0 until h) for (x in 0 until w) {
                if ((argb[y * w + x] ushr 24) < 8) continue
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        if (maxX < 0) return null
        val scale = CANVAS_WIDTH / w
        val pad = (max(maxX - minX, maxY - minY) * scale * BOUNDS_PADDING).toInt() + 2 * scale
        val x0 = max(0, minX * scale - pad)
        val y0 = max(0, minY * scale - pad)
        val x1 = min(CANVAS_WIDTH, (maxX + 1) * scale + pad)
        val y1 = min(CANVAS_HEIGHT, (maxY + 1) * scale + pad)
        // Even sizes keep video-style encoders happy.
        return Crop(x0, y0, (x1 - x0) and 1.inv(), (y1 - y0) and 1.inv())
    }

    private fun crop(argb: IntArray, c: Crop): IntArray {
        val out = IntArray(c.width * c.height)
        for (y in 0 until c.height) {
            System.arraycopy(argb, (c.y + y) * CANVAS_WIDTH + c.x, out, y * c.width, c.width)
        }
        return out
    }

    private fun coverage(argb: IntArray): Int = argb.count { (it ushr 24) != 0 }

    fun renderStill(
        cgfx: ByteArray,
        common: ByteArray? = null,
        shouldStop: () -> Boolean = { false },
    ): ThreeDsBannerRenderResult.Success? = runCatching {
        val renderer = open(cgfx, common) ?: return null
        val area = bounds(renderer, shouldStop) ?: return null.also { trace("still: nothing drawn or stopped") }
        if (shouldStop()) return null
        val length = renderer.animator.frameCount
        // An intro can start empty (a logo flying in): show the frame with the most on screen.
        val frame = if (length <= 1f) 0f else {
            (0 until 8).map { length * it / 8 }
                .maxByOrNull { coverage(renderer.render(it, CANVAS_WIDTH / 4, CANVAS_HEIGHT / 4, supersample = 1)) }
                ?: 0f
        }
        val full = renderer.render(frame, CANVAS_WIDTH, CANVAS_HEIGHT, SUPERSAMPLE)
        if (coverage(full) < full.size * MIN_COVERAGE_FRACTION) return null
        trace("still: frame $frame, ${area.width}x${area.height}")
        ThreeDsBannerRenderResult.Success(area.width, area.height, crop(full, area))
    }.onFailure { trace("still failed: $it") }.getOrNull()

    /**
     * One loop of the banner's own animation as an animated WebP, timed like the Home Menu (one
     * banner frame per 60 Hz refresh, shown here at 30 fps). Frames are drawn in parallel, each
     * worker with its own renderer. Null for a banner that does not move.
     */
    fun renderAnimation(
        cgfx: ByteArray,
        common: ByteArray?,
        encoder: FrameEncoder,
        shouldStop: () -> Boolean = { false },
    ): ByteArray? = runCatching {
        val started = System.currentTimeMillis()
        val file = parse(cgfx, common) ?: return null.also { trace("animation: container not readable") }
        if (file.models.none { it.meshes.isNotEmpty() }) return null
        val first = open(file)
        val length = first.animator.frameCount
        trace("animation: loop is $length banner frames")
        if (length < 2f) return null
        val area = bounds(first, shouldStop) ?: return null.also { trace("animation: frames empty or stopped") }
        ThreeDsBannerWork.report(BOUNDS_SHARE)

        val seconds = length / BANNER_FRAMES_PER_SECOND
        val count = min(MAX_ANIMATION_FRAMES, max(2, ceil(seconds * OUTPUT_FRAMES_PER_SECOND - 1e-6).toInt()))
        val step = length / count
        // Durations in whole milliseconds that still add up to the loop's exact length.
        val totalMs = seconds * 1000.0
        val durations = IntArray(count) { i ->
            (Math.round(totalMs * (i + 1) / count) - Math.round(totalMs * i / count)).toInt().coerceAtLeast(1)
        }

        val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, MAX_THREADS)
        val encoded = arrayOfNulls<ByteArray>(count)
        val framesDone = java.util.concurrent.atomic.AtomicInteger(0)
        // Lowest priority: the screens keep the processor whenever they need it, so drawing banners
        // in the background never makes scrolling or animations stutter.
        val pool = Executors.newFixedThreadPool(threads) { task ->
            Thread(task, "BannerRender").apply {
                priority = Thread.MIN_PRIORITY
                isDaemon = true
            }
        }
        try {
            val jobs = (0 until threads).map { worker ->
                pool.submit(Callable {
                    val renderer = if (worker == 0) first else open(file)
                    var i = worker
                    // Checked every frame, so turning banners off stops the work within a frame.
                    while (i < count && !shouldStop()) {
                        val full = renderer.render(i * step, CANVAS_WIDTH, CANVAS_HEIGHT, SUPERSAMPLE)
                        encoded[i] = encoder.encode(crop(full, area), area.width, area.height)
                        ThreeDsBannerWork.report(BOUNDS_SHARE + (1f - BOUNDS_SHARE) * framesDone.incrementAndGet() / count)
                        i += threads
                    }
                })
            }
            jobs.forEach { it.get() }
        } finally {
            pool.shutdown()
            ThreeDsBannerWork.sceneDone()
        }

        if (shouldStop()) return null.also { trace("animation: stopped") }
        val muxer = WebpAnimationMuxer(area.width, area.height)
        for (i in 0 until count) {
            val frame = encoded[i] ?: return null.also { trace("animation: frame $i failed to encode") }
            if (!muxer.addFrame(frame, durations[i])) return null.also { trace("animation: frame $i unreadable") }
        }
        val webp = muxer.build()
        trace(
            "animation: $count frames (${"%.2f".format(seconds)}s loop) ${area.width}x${area.height}, " +
                "${webp?.size} bytes in ${System.currentTimeMillis() - started} ms on $threads threads"
        )
        webp
    }.onFailure { trace("animation failed: $it") }.getOrNull()

    private fun trace(message: String) {
        android.util.Log.i("ArcoonTrace", "3DS banner $message")
    }
}
