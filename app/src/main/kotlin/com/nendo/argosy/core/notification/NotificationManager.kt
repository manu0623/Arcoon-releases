package com.nendo.argosy.core.notification

import android.util.Log
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "NotificationManager"
private const val DEBOUNCE_DELAY_MS = 500L

@Singleton
class NotificationManager @Inject constructor() {

    private val scope = SafeCoroutineScope(Dispatchers.Main, "NotificationManager")
    private val pendingByKey = mutableMapOf<String, Job>()

    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    val notifications: StateFlow<List<Notification>> = _notifications.asStateFlow()

    private val _persistentNotifications = MutableStateFlow<List<Notification>>(emptyList())
    val persistentNotifications: StateFlow<List<Notification>> = _persistentNotifications.asStateFlow()

    fun show(
        title: NotificationText,
        subtitle: NotificationText? = null,
        type: NotificationType = NotificationType.INFO,
        imagePath: String? = null,
        platformSlug: String? = null,
        duration: NotificationDuration = NotificationDuration.SHORT,
        key: String? = null,
        immediate: Boolean = false,
        accentColor: Int? = null
    ): String {
        val notification = Notification(
            key = key,
            type = type,
            title = title,
            subtitle = subtitle,
            imagePath = imagePath,
            platformSlug = platformSlug,
            duration = duration,
            immediate = immediate,
            accentColor = accentColor
        )

        if (key == null) {
            addNotification(notification)
            return notification.id
        }

        scope.launch {
            pendingByKey.remove(key)?.cancel()
            if (immediate) {
                removeByKey(key)
                addNotification(notification)
            } else {
                pendingByKey[key] = scope.launch {
                    delay(DEBOUNCE_DELAY_MS)
                    pendingByKey.remove(key)
                    removeByKey(key)
                    addNotification(notification)
                }
            }
        }

        return notification.id
    }

    private fun addNotification(notification: Notification) {
        _notifications.update { current ->
            current + notification
        }
    }

    private fun removeByKey(key: String) {
        _notifications.update { current ->
            current.filterNot { it.key == key }
        }
    }

    fun dismiss(id: String) {
        _notifications.update { current ->
            current.filterNot { it.id == id }
        }
    }

    fun dismissByKey(key: String) {
        scope.launch { pendingByKey.remove(key)?.cancel() }
        removeByKey(key)
        removePersistent(key)
    }

    fun clear() {
        scope.launch {
            pendingByKey.values.forEach { it.cancel() }
            pendingByKey.clear()
        }
        _notifications.value = emptyList()
        _persistentNotifications.value = emptyList()
    }

    fun showPersistent(
        title: NotificationText,
        subtitle: NotificationText? = null,
        key: String,
        progress: NotificationProgress? = null,
        platformSlug: String? = null
    ) {
        val notification = Notification(
            key = key,
            type = NotificationType.INFO,
            title = title,
            subtitle = subtitle,
            progress = progress,
            platformSlug = platformSlug
        )
        _persistentNotifications.update { current ->
            current.filterNot { it.key == key } + notification
        }
    }

    fun updatePersistent(
        key: String,
        title: NotificationText? = null,
        subtitle: NotificationText? = null,
        progress: NotificationProgress? = null,
        platformSlug: String? = null
    ) {
        _persistentNotifications.update { current ->
            current.map { existing ->
                if (existing.key != key) {
                    existing
                } else {
                    existing.copy(
                        title = title ?: existing.title,
                        subtitle = subtitle ?: existing.subtitle,
                        progress = progress ?: existing.progress,
                        platformSlug = platformSlug ?: existing.platformSlug
                    )
                }
            }
        }
    }

    fun completePersistent(
        key: String,
        title: NotificationText,
        subtitle: NotificationText? = null,
        type: NotificationType,
        platformSlug: String? = null
    ) {
        removePersistent(key)
        show(
            title = title,
            subtitle = subtitle,
            type = type,
            platformSlug = platformSlug,
            duration = NotificationDuration.SHORT,
            immediate = true
        )
    }

    private fun removePersistent(key: String) {
        _persistentNotifications.update { current ->
            current.filterNot { it.key == key }
        }
    }
}
