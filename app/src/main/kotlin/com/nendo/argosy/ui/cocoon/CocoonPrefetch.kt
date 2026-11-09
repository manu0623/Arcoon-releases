package com.nendo.argosy.ui.cocoon

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Where pictures prepared ahead of time (covers of the folder under the cursor, top-screen art,
 * folder logos) are read and decoded: two background threads at the lowest priority. Pictures the
 * screen is waiting for still load on the image loader's normal threads; these only fill in
 * behind, so preparing never takes the CPU away from drawing.
 */
object CocoonPrefetch {
    private val count = AtomicInteger()

    val dispatcher: CoroutineDispatcher by lazy {
        Executors.newFixedThreadPool(2) { work ->
            Thread {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_LOWEST)
                work.run()
            }.apply {
                name = "ArcoonPrefetch-${count.incrementAndGet()}"
                isDaemon = true
            }
        }.asCoroutineDispatcher()
    }
}
