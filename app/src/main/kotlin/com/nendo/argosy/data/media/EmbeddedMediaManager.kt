package com.nendo.argosy.data.media

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "EmbeddedMediaManager"

@Singleton
class EmbeddedMediaManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val containerReader: RomContainerReader,
    private val iconExtractor: IconExtractor
) {

    data class ExtractionResult(
        val success: Boolean,
        val iconBitmap: Bitmap? = null,
        val message: String = "",
        val title: String? = null,
        val publisher: String? = null,
        val bannerCgfx: ByteArray? = null,
        /** The banner's shared scene when [bannerCgfx] is a language overlay on it. */
        val bannerCommonCgfx: ByteArray? = null,
        val bannerJingleWav: ByteArray? = null,
        val bannerBitmap: Bitmap? = null,
        val bannerGif: ByteArray? = null,
        val bannerFailureReason: String? = null,
        /** True when this run drew the banner; false leaves any banner already cached alone. */
        val bannerRendered: Boolean = false
    )

    /**
     * Reads the ROM's icon and title, and with [renderBanner] also draws the 3DS banner. Banner
     * drawing stops as soon as this coroutine is cancelled or the banner setting is turned off,
     * and then this throws [kotlinx.coroutines.CancellationException] so nothing half-done is saved.
     */
    suspend fun extractForGame(romPath: String, renderBanner: Boolean = true): ExtractionResult = withContext(Dispatchers.IO) {
        val job = coroutineContext[kotlinx.coroutines.Job]
        val shouldStop = { job?.isActive == false || !ThreeDsBannerWork.enabled }
        val file = File(romPath)
        if (!file.isFile) {
            return@withContext ExtractionResult(false, message = "ROM file not found: $romPath")
        }

        val inspection = containerReader.inspect(file)
        if (!inspection.isValid) {
            return@withContext ExtractionResult(
                false,
                message = inspection.failureReason ?: "Unsupported or invalid ROM container"
            )
        }

        when (inspection.platform) {
            RomContainerReader.RomPlatform.NDS -> {
                val bitmap = iconExtractor.extractNdsIcon(file, inspection.bannerOffset)
                if (bitmap != null) {
                    ExtractionResult(true, iconBitmap = bitmap, message = "Successfully extracted NDS embedded icon")
                } else {
                    ExtractionResult(false, message = "Failed to parse NDS banner icon")
                }
            }
            RomContainerReader.RomPlatform.THREEDS -> {
                when (val parsed = containerReader.readThreeDsSmdh(file, inspection)) {
                    is SmdhParseResult.Success -> {
                        val media = parsed.media
                        val bannerResult = if (renderBanner) {
                            containerReader.readThreeDsBannerCgfx(file, inspection)
                        } else {
                            null
                        }
                        val bannerCgfx = (bannerResult as? ThreeDsBannerResult.Success)?.cgfx
                        val bannerCommon = (bannerResult as? ThreeDsBannerResult.Success)?.common
                        val bannerRenderResult = bannerCgfx?.let { ThreeDsBannerRenderer.render(it, bannerCommon, shouldStop) }
                        if (renderBanner && shouldStop()) throw kotlinx.coroutines.CancellationException("3DS banner work stopped")
                        val bannerGif = bannerCgfx?.let { ThreeDsBannerRenderer.renderAnimation(it, bannerCommon, shouldStop) }
                        if (renderBanner && shouldStop()) throw kotlinx.coroutines.CancellationException("3DS banner work stopped")
                        Log.i(
                            "ArcoonTrace",
                            "3DS banner ${file.name}: cgfx=${bannerCgfx?.size} common=${bannerCommon?.size} animation=${bannerGif?.size}"
                        )
                        val bannerBitmap = (bannerRenderResult as? ThreeDsBannerRenderResult.Success)?.let {
                            Bitmap.createBitmap(it.argb, it.width, it.height, Bitmap.Config.ARGB_8888)
                        }
                        val bannerJingle = containerReader.readThreeDsBannerJingle(file, inspection)
                        val bannerJingleWav = (bannerJingle as? BcwavResult.Success)?.wav
                        if (bannerJingle is BcwavResult.Failure) {
                            Log.w(TAG, "Could not decode 3DS banner jingle from ${file.name}: ${bannerJingle.reason}")
                        }
                        val bannerFailure = when {
                            bannerResult is ThreeDsBannerResult.Failure -> bannerResult.reason
                            bannerRenderResult is ThreeDsBannerRenderResult.Failure -> bannerRenderResult.reason
                            !renderBanner -> null
                            else -> null
                        }
                        if (bannerFailure != null) {
                            Log.w(TAG, "Could not render 3DS banner from ${file.name}: $bannerFailure")
                            Log.w("ArcoonTrace", "3DS banner ${file.name}: $bannerFailure")
                        }
                        ExtractionResult(
                            success = true,
                            iconBitmap = Bitmap.createBitmap(
                                media.iconArgb,
                                media.iconWidth,
                                media.iconHeight,
                                Bitmap.Config.ARGB_8888
                            ),
                            message = "Successfully extracted 3DS SMDH icon",
                            title = media.title,
                            publisher = media.publisher,
                            bannerCgfx = bannerCgfx,
                            bannerCommonCgfx = bannerCommon,
                            bannerJingleWav = bannerJingleWav,
                            bannerBitmap = bannerBitmap,
                            bannerGif = bannerGif,
                            bannerFailureReason = bannerFailure,
                            bannerRendered = renderBanner
                        )
                    }
                    is SmdhParseResult.Failure -> ExtractionResult(false, message = parsed.reason)
                }
            }
            RomContainerReader.RomPlatform.UNKNOWN -> {
                ExtractionResult(false, message = "Unknown ROM platform format")
            }
        }
    }
}
