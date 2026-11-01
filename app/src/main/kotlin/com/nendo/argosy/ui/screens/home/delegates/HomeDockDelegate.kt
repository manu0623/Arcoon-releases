package com.nendo.argosy.ui.screens.home.delegates

import com.nendo.argosy.DualScreenManager
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.ui.components.DockReorderInputHandler
import com.nendo.argosy.ui.components.DockReorderSession
import com.nendo.argosy.ui.input.InputHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject

private class DockSnapshot(val apps: List<String>?, val session: DockReorderSession?)

/**
 * The home dock on the presentation screen: its app order and the controller-driven reorder
 * session. The dual-screen manager is resolved on every call and followed through
 * [DualScreenManagerHolder.instanceFlow], so a manager created or replaced after this delegate
 * still drives the dock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeDockDelegate @Inject constructor() {
    private var controllerToken: Long? = null

    private val _reorder = MutableStateFlow(DualScreenManagerHolder.instance?.dockReorder?.value)
    val reorder: StateFlow<DockReorderSession?> = _reorder.asStateFlow()

    private val manager: DualScreenManager?
        get() = DualScreenManagerHolder.instance

    fun observe(scope: CoroutineScope, onApps: (List<String>) -> Unit) {
        DualScreenManagerHolder.instanceFlow
            .flatMapLatest { it?.let(::snapshotsOf) ?: flowOf(DockSnapshot(apps = null, session = null)) }
            .onEach { snapshot ->
                if (snapshot.session?.token != controllerToken) controllerToken = null
                _reorder.update { snapshot.session }
                snapshot.apps?.let(onApps)
            }
            .launchIn(scope)
    }

    fun isReordering(): Boolean = manager?.dockReorder?.value != null

    fun createInputHandler(isRtl: Boolean): InputHandler =
        DockReorderInputHandler(
            isRtl = isRtl,
            onMove = { delta -> controllerToken?.let { manager?.moveDockReorder(it, delta) } },
            onSave = { controllerToken?.let { manager?.saveDockReorder(it) } },
            onCancel = ::cancelReorder
        )

    fun startReorder(packageName: String): Boolean {
        val token = manager?.beginDockReorder(packageName, controller = true) ?: return false
        controllerToken = token
        return true
    }

    fun cancelReorder() {
        val token = controllerToken ?: return
        manager?.cancelDockReorder(token)
        controllerToken = null
    }

    private fun snapshotsOf(manager: DualScreenManager): Flow<DockSnapshot> =
        combine(manager.homeAppsFlow, manager.dockReorder) { apps, session -> DockSnapshot(apps, session) }
}
