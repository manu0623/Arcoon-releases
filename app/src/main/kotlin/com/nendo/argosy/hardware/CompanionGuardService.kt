package com.nendo.argosy.hardware

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nendo.argosy.ArgosyActivity
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.R
import com.nendo.argosy.core.service.ServiceNotificationIds
import com.nendo.argosy.core.service.startForegroundServiceSafely
import com.nendo.argosy.util.HomeAppComponent
import com.nendo.argosy.util.SecondaryHomeComponent
import java.util.concurrent.atomic.AtomicBoolean

class CompanionGuardService : Service() {

    override fun attachBaseContext(newBase: Context) {
        val tag = com.nendo.argosy.data.preferences.SessionStateStore(newBase).getAppLanguage()
        super.attachBaseContext(com.nendo.argosy.core.locale.LocaleHelper.wrap(newBase, tag))
    }

    companion object {
        private const val TAG = "CompanionGuard"
        private const val ACTION_STOP = "com.nendo.argosy.STOP_COMPANION_GUARD"
        private val awaitingForeground = AtomicBoolean(false)

        fun start(context: Context) {
            awaitingForeground.set(true)
            val started = context.startForegroundServiceSafely(
                Intent(context, CompanionGuardService::class.java)
            )
            if (!started) awaitingForeground.set(false)
        }

        fun stop(context: Context) {
            if (awaitingForeground.get()) {
                val intent = Intent(context, CompanionGuardService::class.java).apply { action = ACTION_STOP }
                if (context.startForegroundServiceSafely(intent)) return
            }
            context.stopService(
                Intent(context, CompanionGuardService::class.java)
            )
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var displayManager: DisplayManager? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {
            val dm = displayManager ?: return
            if (dm.displays.size <= 1) {
                Log.d(TAG, "No secondary display, stopping guard service")
                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        SecondaryDisplayNotificationChannel.create(this)
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayManager?.registerDisplayListener(displayListener, handler)
        startForegroundNotification()
        Log.d(TAG, "Companion guard service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()
        awaitingForeground.set(false)
        if (intent?.action == ACTION_STOP) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!HomeAppComponent.isLauncherActivity(rootIntent?.component?.className)) return
        if (SecondaryHomeComponent.isDefaultHome(this)) return
        val dsm = DualScreenManagerHolder.instance
        val sessionLive = dsm?.hasLiveSession() == true
        if (sessionLive) return
        Log.i(TAG, "Main task removed while not default home, releasing secondary display")
        SecondaryHomeComponent.setEnabled(this, false)
        dsm?.teardownCompanion()
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        displayManager?.unregisterDisplayListener(displayListener)
        Log.d(TAG, "Companion guard service stopped")
        super.onDestroy()
    }

    private fun startForegroundNotification() {
        val notification = NotificationCompat.Builder(
            this, SecondaryDisplayNotificationChannel.CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_helm)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.dual_notification_guard_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(createContentIntent())
            .build()

        val notificationId = ServiceNotificationIds.COMPANION_GUARD
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(notificationId, notification)
        }
    }

    private fun createContentIntent(): PendingIntent {
        val intent = Intent(this, ArgosyActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
