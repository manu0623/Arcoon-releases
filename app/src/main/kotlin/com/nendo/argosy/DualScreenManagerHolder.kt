package com.nendo.argosy

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DualScreenManagerHolder {
    private val current = MutableStateFlow<DualScreenManager?>(null)

    val instanceFlow: StateFlow<DualScreenManager?> = current.asStateFlow()

    var instance: DualScreenManager?
        get() = current.value
        set(value) {
            current.value = value
        }

    private var attachedActivities = 0

    @Synchronized
    fun attach() {
        attachedActivities++
    }

    @Synchronized
    fun detachAndCountRemaining(): Int {
        attachedActivities = (attachedActivities - 1).coerceAtLeast(0)
        return attachedActivities
    }
}
