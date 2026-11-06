package com.nendo.argosy.libretro

import android.app.Presentation
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.view.Display
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.view.WindowManager
import androidx.compose.runtime.snapshotFlow
import com.nendo.argosy.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * The suspension handle on the console's second screen: a faint home glyph in the corner chosen in
 * Personalization, in a window of its own.
 *
 * It cannot live in the second screen's window, because that window's game surface is drawn with
 * ZOrderOnTop and covers everything else in it. It also does not belong on the top screen: while a
 * two-screen game runs, both displays are picture, and the lower one is the screen the player is
 * looking at when the menu is not up.
 *
 * Where in that screen matters. These consoles put their touch panel in the lower screen, so a
 * handle drawn over the picture takes taps the game needs. When the game's picture leaves a bar
 * free, [freeBand] names that bar and the handle is kept inside it, shrunk to fit when the bar is
 * thinner than the chosen size.
 */
class HomeHandlePresentation(
    context: Context,
    display: Display,
    private val freeBand: (() -> Rect?)? = null,
    private val onTap: () -> Unit,
) : Presentation(context, display) {

    private val geometryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var iconView: ImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The game screen can be the first surface in this process, so read Personalization itself.
        com.nendo.argosy.ui.cocoon.CocoonHomeStore.ensureLoaded(context)
        setCancelable(false)
        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(0f)
            addFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        val icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_home_handle)
            // Nearly invisible on purpose: the game is the picture, and Clarity is the player's
            // call on how nearly.
            imageAlpha = (com.nendo.argosy.ui.cocoon.CocoonHomeStore.handleClarity * 255).toInt()
            contentDescription = context.getString(R.string.ingame_home_handle)
            setOnClickListener { onTap() }
        }
        iconView = icon
        setContentView(
            FrameLayout(context).apply {
                addView(
                    icon,
                    FrameLayout.LayoutParams(
                        dp(com.nendo.argosy.ui.cocoon.cocoonHandleIconDp()),
                        dp(com.nendo.argosy.ui.cocoon.cocoonHandleIconDp()),
                        Gravity.CENTER
                    )
                )
            }
        )
        applyGeometry()
        // Personalization ▸ On Screen Home Menu owns the size and the corner, and it can change
        // while a game runs, so the window follows the store instead of reading it once.
        geometryScope.launch {
            snapshotFlow {
                com.nendo.argosy.ui.cocoon.CocoonHomeStore.handleSize to Triple(
                    com.nendo.argosy.ui.cocoon.CocoonHomeStore.handleX,
                    com.nendo.argosy.ui.cocoon.CocoonHomeStore.handleY,
                    com.nendo.argosy.ui.cocoon.CocoonHomeStore.handleClarity
                )
            }.collect { applyGeometry() }
        }
    }

    /** Re-places the handle, for when the game's picture (and so its bars) changes under it. */
    fun refresh() = applyGeometry()

    private fun applyGeometry() {
        // The system's navigation bar is drawn above this window and swallows the touches that land
        // in its band, so the handle has to sit clear of it. The display reports its real size and
        // the area apps may use; the difference is the band that never reaches this window. (The
        // window's own insets are not reported for a presentation on a secondary display.)
        val realMetrics = android.util.DisplayMetrics().also { display.getRealMetrics(it) }
        val appMetrics = android.util.DisplayMetrics().also { display.getMetrics(it) }
        val navBand = (realMetrics.heightPixels - appMetrics.heightPixels).coerceAtLeast(0)
        val target = dp(com.nendo.argosy.ui.cocoon.cocoonHandleTargetDp())
        val glyph = dp(com.nendo.argosy.ui.cocoon.cocoonHandleIconDp())
        // A bar of the picture, kept above the navigation bar.
        val bar = freeBand?.invoke()
            ?.let { band ->
                Rect(band).apply {
                    right = right.coerceAtMost(realMetrics.widthPixels)
                    bottom = bottom.coerceAtMost(realMetrics.heightPixels - navBand)
                }
            }
            // A sliver is not a bar: below the smallest usable handle the corner is less bad than
            // a handle nobody can press.
            ?.takeIf { it.width() >= dp(HANDLE_MIN_BOX_DP) && it.height() >= dp(HANDLE_MIN_BOX_DP) }
        // Free placement: a point in the area the handle may use, not a corner. When the picture
        // leaves a bar free, the point maps across that bar, so a handle placed at an edge of the
        // bar stays on the bar instead of landing on the game's touch panel.
        val area = bar ?: Rect(
            0,
            0,
            realMetrics.widthPixels,
            (realMetrics.heightPixels - navBand).coerceAtLeast(1)
        )
        // Never bigger than the bar: the picture around it is the game's touch panel.
        val box = minOf(target, area.width(), area.height()).coerceAtLeast(dp(HANDLE_MIN_BOX_DP))
        val boxGlyph = (glyph.toFloat() * box / target).toInt()
            .coerceIn(dp(HANDLE_MIN_GLYPH_DP), glyph)
        val x = area.left + ((area.width() - box) * com.nendo.argosy.ui.cocoon.CocoonHomeStore.handleX).toInt()
        val y = area.top + ((area.height() - box) * com.nendo.argosy.ui.cocoon.CocoonHomeStore.handleY).toInt()
        android.util.Log.i(
            "HomeHandlePresentation",
            "handle geometry: size=${com.nendo.argosy.ui.cocoon.CocoonHomeStore.handleSize}" +
                " at=${com.nendo.argosy.ui.cocoon.CocoonHomeStore.handleX}," +
                "${com.nendo.argosy.ui.cocoon.CocoonHomeStore.handleY}" +
                " clarity=${com.nendo.argosy.ui.cocoon.CocoonHomeStore.handleClarity}" +
                " target=${target}px glyph=${glyph}px navBand=${navBand}px" +
                " bar=$bar box=${box}px at=$x,$y"
        )
        window?.apply {
            val params = attributes
            params.gravity = Gravity.TOP or Gravity.START
            params.width = box
            params.height = box
            params.x = x
            params.y = y
            attributes = params
        }
        iconView?.let { icon ->
            icon.imageAlpha = (com.nendo.argosy.ui.cocoon.CocoonHomeStore.handleClarity * 255).toInt()
            icon.layoutParams = FrameLayout.LayoutParams(boxGlyph, boxGlyph, Gravity.CENTER).also {
                icon.requestLayout()
            }
        }
    }

    override fun onStop() {
        geometryScope.cancel()
        iconView = null
        super.onStop()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val HANDLE_MIN_GLYPH_DP = 12
        const val HANDLE_MIN_BOX_DP = 24
    }
}
