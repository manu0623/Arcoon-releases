package com.nendo.argosy.ui.dualscreen

import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.AppsRepository
import com.nendo.argosy.ui.components.DockReorderSession
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DockReorderCoordinator(
    private val scope: CoroutineScope,
    private val preferences: UserPreferencesRepository,
    private val packages: AppsRepository,
    private val onSaveError: () -> Unit
) {
    private val pins = MutableStateFlow<List<String>?>(null)
    private val installed = MutableStateFlow<Set<String>?>(null)
    private val editing = MutableStateFlow<DockReorderSession?>(null)
    val session: StateFlow<DockReorderSession?> = editing
    private var nextToken = 0L

    val apps: StateFlow<List<String>> = combine(pins, installed, editing) { saved, available, edit ->
        (edit?.order ?: saved.orEmpty()).filter { available == null || it in available }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        scope.launch {
            preferences.userPreferences.map { it.secondaryHomeApps }.distinctUntilChanged().collect { order ->
                if (order != pins.value) editing.value = null
                pins.value = order
            }
        }
        scope.launch {
            packages.packageChanges.collect {
                val available = try {
                    packages.getInstalledApps(includeSystemApps = true).map { it.packageName }.toSet()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Logger.error("DockReorder", "Package refresh failed", error)
                    return@collect
                }
                if (available != installed.value) editing.value = null
                installed.value = available
            }
        }
    }

    fun begin(packageName: String, controller: Boolean): Long? {
        val saved = pins.value ?: return null
        val available = installed.value ?: return null
        if (editing.value != null) return null
        val visible = saved.filter { it in available }
        val edit = DockReorderSession.lift(++nextToken, controller, saved, visible, packageName) ?: return null
        editing.value = edit
        return edit.token
    }

    fun target(token: Long, index: Int) {
        editing.update { edit -> if (edit?.token == token && !edit.saving) edit.target(index) else edit }
    }

    fun move(token: Long, delta: Int) {
        editing.value?.takeIf { it.token == token }?.let { target(token, it.targetIndex + delta) }
    }

    fun cancel(token: Long) {
        editing.update { if (it?.token == token && !it.saving) null else it }
    }

    fun save(token: Long) {
        val edit = editing.value?.takeIf { it.token == token && !it.saving } ?: return
        val order = edit.order
        if (order == edit.originalOrder) {
            cancel(token)
            return
        }
        editing.value = edit.copy(saving = true)
        scope.launch {
            val saved = try {
                preferences.reorderDockPins(edit.originalOrder, order)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Logger.error("DockReorder", "Persistence failed", error)
                false
            }
            if (!saved) {
                editing.update { if (it?.token == token) null else it }
                onSaveError()
            }
        }
    }
}
