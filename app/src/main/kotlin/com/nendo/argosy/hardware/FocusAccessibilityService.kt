package com.nendo.argosy.hardware

import android.annotation.SuppressLint
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.ui.quickpanel.QuickPanelOverlay
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

@AndroidEntryPoint
class FocusAccessibilityService : AccessibilityService() {

    @Inject lateinit var playSessionTracker: PlaySessionTracker
    @Inject lateinit var preferencesRepository: UserPreferencesRepository
    @Inject lateinit var quickPanelOverlay: QuickPanelOverlay

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var quickPanelEnabled = false
    private var foregroundPackage: String? = null
    private val backGate = BackHoldGate(
        filtering = ::filtersBack,
        scheduleHold = { handler.postDelayed(it, BACK_HOLD_MS) },
        cancelHold = handler::removeCallbacks,
        onTap = { performGlobalAction(GLOBAL_ACTION_BACK) },
        onHold = { quickPanelOverlay.show(gameDisplayId(), resultOwner = null) }
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val source = event.packageName?.toString() ?: return
        if (source != packageName) foregroundPackage = source
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        scope.launch {
            preferencesRepository.userPreferences
                .map { it.quickPanelInEmulators }
                .distinctUntilChanged()
                .collect { quickPanelEnabled = it }
        }
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onDestroy() {
        instance = null
        backGate.reset()
        scope.cancel()
        Log.d(TAG, "Accessibility service destroyed")
        super.onDestroy()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_BACK) return false
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> backGate.onDown(isRepeat = event.repeatCount > 0)
            KeyEvent.ACTION_UP -> backGate.onUp()
            else -> false
        }
    }

    private fun filtersBack(): Boolean {
        if (!quickPanelEnabled || quickPanelOverlay.isOpen.value) return false
        val session = playSessionTracker.activeSession.value ?: return false
        if (session.emulatorPackage == EmulatorRegistry.BUILTIN_PACKAGE) return false
        return foregroundPackage == session.emulatorPackage
    }

    private fun gameDisplayId(): Int =
        DualScreenManagerHolder.instance?.emulatorDisplayId ?: Display.DEFAULT_DISPLAY

    @SuppressLint("NewApi")
    fun tapOnDisplay(displayId: Int) {
        val path = Path().apply { moveTo(1f, 1f) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 1)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .setDisplayId(displayId)
            .build()
        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Focus tap completed on display $displayId")
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Focus tap cancelled on display $displayId")
            }
        }, null)
        Log.d(TAG, "Focus tap dispatched=$dispatched on display $displayId")
    }

    /**
     * A silent capture of [displayId], with no flash, sound or consent prompt, as a software
     * bitmap; null before Android 11 or when the system refuses, such as within its rate limit.
     */
    suspend fun captureDisplay(displayId: Int): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return suspendCancellableCoroutine { continuation ->
            takeScreenshot(displayId, Dispatchers.IO.asExecutor(), object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val bitmap = runCatching {
                        screenshot.hardwareBuffer.use { buffer ->
                            Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)?.let { wrapped ->
                                wrapped.copy(Bitmap.Config.ARGB_8888, false).also { wrapped.recycle() }
                            }
                        }
                    }.onFailure { Log.w(TAG, "Screenshot of display $displayId unreadable", it) }.getOrNull()
                    continuation.resume(bitmap) { bitmap?.recycle() }
                }

                override fun onFailure(errorCode: Int) {
                    Log.w(TAG, "Screenshot of display $displayId refused: $errorCode")
                    continuation.resume(null)
                }
            })
        }
    }

    companion object {
        private const val TAG = "FocusA11y"
        private const val BACK_HOLD_MS = 500L

        var instance: FocusAccessibilityService? = null
            private set

        /**
         * Whether Argosy's accessibility service is switched on in the system accessibility
         * settings, whether or not the system has bound it yet.
         */
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val component = ComponentName(context, FocusAccessibilityService::class.java)
            return enabled.split(':').any { entry ->
                ComponentName.unflattenFromString(entry) == component
            }
        }
    }
}
