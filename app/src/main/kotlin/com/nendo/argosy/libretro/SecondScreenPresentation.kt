package com.nendo.argosy.libretro

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.util.hideSystemBars

/**
 * The console's other screen on a second display. It hands its surface out and withdraws it
 * through [onSurfaceChanged] with the window's lifecycle, is not cancelable and never takes key
 * focus. Built with [arrivesCovered], it opens black and fades the game in on [reveal] over
 * [ComponentDefaults.LaunchOverlay.arrivalMs]; built without it, it shows the game at once.
 */
class SecondScreenPresentation(
    context: Context,
    display: Display,
    private val arrivesCovered: Boolean,
    private val pictureHeightPx: Int?,
    private val onSurfaceChanged: (Surface?) -> Unit,
    private val onTouch: (x: Float, y: Float, width: Int, height: Int) -> Unit,
    private val onTouchReleased: () -> Unit
) : Presentation(context, display) {

    private lateinit var surfaceView: SurfaceView
    private var arrival: ValueAnimator? = null
    private var cover: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCancelable(false)
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        window?.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
        if (arrivesCovered) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setWindowAlpha(0f, dimAmount = 0f)
        }

        surfaceView = SurfaceView(context).apply {
            setZOrderOnTop(true)
        }
        setContentView(
            FrameLayout(context).apply {
                addView(
                    surfaceView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        pictureHeightPx ?: ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.TOP
                    )
                )
                if (pictureHeightPx != null) {
                    addView(
                        View(context).apply { setBackgroundColor(Color.BLACK) },
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        ).apply { topMargin = pictureHeightPx }
                    )
                }
            }
        )
        window?.hideSystemBars()

        surfaceView.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                    onTouch(event.x, event.y, view.width, view.height)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onTouchReleased()
                else -> Unit
            }
            true
        }

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                onSurfaceChanged(holder.surface)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                onSurfaceChanged(holder.surface)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                onSurfaceChanged(null)
            }
        })
    }

    override fun onStart() {
        super.onStart()
        if (!arrivesCovered || cover != null || arrival != null) return
        cover = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ComponentDefaults.LaunchOverlay.veilMs.toLong()
            interpolator = LinearInterpolator()
            addUpdateListener { setDimAmount(it.animatedValue as Float) }
            start()
        }
    }

    fun reveal() {
        if (!arrivesCovered || arrival != null || !isShowing) return
        arrival = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ComponentDefaults.LaunchOverlay.arrivalMs.toLong()
            interpolator = LinearInterpolator()
            addUpdateListener { setWindowAlpha(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    cover?.cancel()
                    window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                    setWindowAlpha(1f)
                }
            })
            start()
        }
    }

    private fun setDimAmount(dimAmount: Float) {
        val window = window ?: return
        window.attributes = window.attributes.also { params -> params.dimAmount = dimAmount }
    }

    private fun setWindowAlpha(alpha: Float, dimAmount: Float? = null) {
        val window = window ?: return
        window.attributes = window.attributes.also { params ->
            params.alpha = alpha
            if (dimAmount != null) params.dimAmount = dimAmount
        }
    }

    override fun onStop() {
        cover?.cancel()
        arrival?.cancel()
        onSurfaceChanged(null)
        super.onStop()
    }
}
