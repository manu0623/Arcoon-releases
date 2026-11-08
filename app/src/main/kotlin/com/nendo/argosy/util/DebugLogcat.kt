package com.nendo.argosy.util

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Full device log, written continuously while Argosy's file logging is on (Settings ▸ logging),
 * into the same folder as the app's own logs: logcat-*.txt, rotated so it never grows past
 * about 8 files of 2 MB. It runs through the device's system access (the Thor's), so it sees
 * everything — windows, displays, activities, crashes — not just Argosy's own lines.
 *
 * Turning file logging off stops it. Very chatty system parts (audio, input polling, SELinux
 * audits) are left out so the files hold what matters for debugging.
 */
object DebugLogcat {
    private const val TAG = "DebugLogcat"
    private const val FILE_NAME = "logcat.txt"
    private const val FILE_KB = 2048
    private const val FILE_COUNT = 8

    /** Tags that flood the log without helping: silenced. */
    private val QUIET = listOf(
        "MediaDimRamp", "GamepadInput", "AmbientAudio", "AudioTrack", "AAudio", "AAudioStream",
        "AudioStreamBuilder", "AudioStreamTrack", "OboeAudio", "libretrodroid", "chatty",
        "TrafficStats", "OkHttp", "InputDispatcher", "InputReader", "audit", "SELinux",
        "BufferQueueProducer", "BLASTBufferQueue", "SurfaceComposerClient",
        "qdgralloc", "AdrenoGLES-0", "Gralloc4", "ion", "CCodec", "MediaCodec", "BatteryService"
    )

    @Volatile private var runningFor: String? = null

    fun apply(context: Context, enabled: Boolean, directory: String?) {
        val target = directory?.takeIf { enabled }
        if (target == runningFor) return
        val app = context.applicationContext
        Thread {
            runCatching {
                stopAll(app)
                if (target != null) start(app, target)
                runningFor = target
            }.onFailure { Log.w(TAG, "Could not switch the full log", it) }
        }.apply { name = "DebugLogcat"; isDaemon = true }.start()
    }

    private fun start(app: Context, directory: String) {
        if (!RootShell.isAvailable) {
            Log.i(TAG, "No system access: full log unavailable, only Argosy's own log is kept")
            return
        }
        val dir = File(directory).apply { mkdirs() }
        val out = File(dir, FILE_NAME).absolutePath
        val filters = (listOf("*:D") + QUIET.map { "$it:S" }).joinToString(" ")
        // started detached, so it keeps writing while the app is in the background or a game runs
        val cmd = "(setsid logcat -v threadtime -b main,system,crash,events -r $FILE_KB -n $FILE_COUNT " +
            "-f ${RootShell.quote(out)} $filters > /dev/null 2>&1 &)"
        RootShell.run(app, cmd)
        Log.i(TAG, "Full log writing to $out")
    }

    private fun stopAll(app: Context) {
        if (!RootShell.isAvailable) return
        RootShell.run(app, "pkill -f 'logcat -v threadtime -b main,system,crash,events' || true")
    }
}

/** One-line markers ("ArcoonTrace") at the moments that matter, so the full log is easy to read. */
object ArcoonTrace {
    fun log(message: String) {
        Log.i("ArcoonTrace", message)
    }
}
