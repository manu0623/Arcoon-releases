package com.nendo.argosy.util

import android.app.Activity
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window

/**
 * Logs every frame that takes clearly longer than one refresh (a visible hitch), with where the
 * time went, so short stutters show up in the live log. Tag ArcoonTrace, lines start with "jank".
 * Runs in release builds too (it only listens, on its own thread, and writes nothing for smooth
 * frames); turn it off with ENABLED once the app is tuned.
 */
object FrameWatch {
    private const val ENABLED = true
    private const val SLOW_MS = 25.0 // at least one 60 Hz refresh missed
    private val thread by lazy { HandlerThread("ArcoonFrameWatch").apply { start() } }

    fun attach(activity: Activity) {
        if (!ENABLED) return
        val display = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            runCatching { activity.display?.displayId }.getOrNull() ?: -1
        } else {
            -1
        }
        runCatching {
            activity.window.addOnFrameMetricsAvailableListener(
                Window.OnFrameMetricsAvailableListener { _, m, dropped ->
                    val total = m.getMetric(FrameMetrics.TOTAL_DURATION) / 1e6
                    if (total < SLOW_MS) return@OnFrameMetricsAvailableListener
                    fun ms(id: Int) = "%.0f".format(m.getMetric(id) / 1e6)
                    ArcoonTrace.log(
                        "jank display $display: ${"%.0f".format(total)} ms" +
                            " (input ${ms(FrameMetrics.INPUT_HANDLING_DURATION)}," +
                            " animation ${ms(FrameMetrics.ANIMATION_DURATION)}," +
                            " layout ${ms(FrameMetrics.LAYOUT_MEASURE_DURATION)}," +
                            " draw ${ms(FrameMetrics.DRAW_DURATION)}," +
                            " sync/upload ${ms(FrameMetrics.SYNC_DURATION)}," +
                            " gpu-commands ${ms(FrameMetrics.COMMAND_ISSUE_DURATION)}," +
                            " swap ${ms(FrameMetrics.SWAP_BUFFERS_DURATION)}," +
                            " late-start ${ms(FrameMetrics.UNKNOWN_DELAY_DURATION)})" +
                            if (dropped > 0) " dropped-reports $dropped" else ""
                    )
                },
                Handler(thread.looper),
            )
        }
    }
}
