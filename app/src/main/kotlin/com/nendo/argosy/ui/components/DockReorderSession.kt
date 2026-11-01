package com.nendo.argosy.ui.components

import androidx.compose.runtime.Immutable
import java.util.Collections

@Immutable
data class DockReorderSession(
    val token: Long,
    val controller: Boolean,
    val visiblePackages: List<String>,
    val originalOrder: List<String>,
    val heldPackage: String,
    val targetIndex: Int,
    val saving: Boolean = false
) {
    val order: List<String> get() = originalOrder.toMutableList().apply {
        Collections.swap(this, originalOrder.indexOf(heldPackage), originalOrder.indexOf(visiblePackages[targetIndex]))
    }

    fun target(index: Int): DockReorderSession {
        val target = index.coerceIn(0, visiblePackages.lastIndex)
        return if (target != targetIndex) copy(targetIndex = target) else this
    }

    companion object {
        fun lift(
            token: Long,
            controller: Boolean,
            pins: List<String>,
            visible: List<String>,
            packageName: String
        ): DockReorderSession? {
            if (visible.size < 2 || packageName !in visible || !pins.containsAll(visible)) return null
            return DockReorderSession(token, controller, visible, pins, packageName, visible.indexOf(packageName))
        }
    }
}

internal fun dockDragTarget(
    current: Int,
    previousCenter: Float,
    center: Float,
    centers: List<Pair<Int, Float>>
): Int = if (center >= previousCenter) {
    centers.filter { (index, x) -> index > current && x <= center }
        .maxOfOrNull { it.first } ?: current
} else {
    centers.filter { (index, x) -> index < current && x >= center }
        .minOfOrNull { it.first } ?: current
}
