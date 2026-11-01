package com.nendo.argosy.domain.model

import org.json.JSONObject

enum class ScreenRole {
    PRIMARY,
    PRESENTATION,
    APP_TARGET,
    OFF;

    companion object {
        fun fromString(value: String?): ScreenRole = entries.find { it.name == value } ?: PRESENTATION
    }
}

data class ScreenSpec(
    val key: String,
    val widthPx: Int,
    val heightPx: Int,
    val builtIn: Boolean
) {
    val areaPx: Long get() = widthPx.toLong() * heightPx.toLong()
}

data class ScreenLayout(val roles: Map<String, ScreenRole> = emptyMap()) {

    fun roleFor(screenKey: String): ScreenRole? = roles[screenKey]

    /**
     * Assigns [role] to [screenKey], handing the screen that held [role] the role [screenKey] is
     * giving up. Every role stays held by at most one screen through any assignment, including a
     * promotion out of APP_TARGET or OFF. Assigning OFF takes no role from another screen.
     */
    fun withRole(screenKey: String, role: ScreenRole): ScreenLayout {
        val previous = roles[screenKey] ?: ScreenRole.PRESENTATION
        val holder = roles.entries.find { it.value == role && it.key != screenKey }?.key
        val next = roles.toMutableMap()
        next[screenKey] = role
        if (holder != null && role != ScreenRole.OFF) next[holder] = previous
        return ScreenLayout(next)
    }

    val primaryKey: String? get() = roles.entries.find { it.value == ScreenRole.PRIMARY }?.key

    val presentationKey: String? get() = roles.entries.find { it.value == ScreenRole.PRESENTATION }?.key

    val appTargetKey: String? get() = roles.entries.find { it.value == ScreenRole.APP_TARGET }?.key

    val isSingleDisplay: Boolean get() = presentationKey == null

    fun toJson(): String = JSONObject().apply {
        roles.forEach { (key, role) -> put(key, role.name) }
    }.toString()

    companion object {
        fun fromJson(json: String?): ScreenLayout {
            if (json.isNullOrBlank()) return ScreenLayout()
            return try {
                val obj = JSONObject(json)
                val roles = obj.keys().asSequence().associateWith { key ->
                    ScreenRole.fromString(obj.optString(key))
                }
                ScreenLayout(roles)
            } catch (_: Exception) {
                ScreenLayout()
            }
        }

        /**
         * The layout a screen set holds before anyone assigns a role. The smallest internal panel
         * takes PRIMARY, remaining internals then externals take [FILL_ORDER] in turn, and
         * [invertInternalOrder] reverses the internal ordering for a model that needs it.
         * Externals keep the order they arrive in.
         */
        fun defaultFor(
            screens: List<ScreenSpec>,
            invertInternalOrder: Boolean = false
        ): ScreenLayout {
            if (screens.isEmpty()) return ScreenLayout()
            val internals = screens.filter { it.builtIn }
                .sortedWith(compareByDescending<ScreenSpec> { it.areaPx }.thenBy { screens.indexOf(it) })
                .let { if (invertInternalOrder) it.asReversed() else it }
            val externals = screens.filterNot { it.builtIn }
            val primary = internals.lastOrNull() ?: externals.firstOrNull() ?: return ScreenLayout()
            val rest = (internals + externals).filterNot { it.key == primary.key }
            val roles = mutableMapOf(primary.key to ScreenRole.PRIMARY)
            rest.forEachIndexed { index, screen ->
                roles[screen.key] = FILL_ORDER.getOrNull(index) ?: ScreenRole.OFF
            }
            return ScreenLayout(roles)
        }

        private val FILL_ORDER = listOf(ScreenRole.PRESENTATION, ScreenRole.APP_TARGET)
    }
}

data class ScreenLayouts(val bySet: Map<String, ScreenLayout> = emptyMap()) {

    fun layoutFor(setKey: String): ScreenLayout? = bySet[setKey]

    fun with(setKey: String, layout: ScreenLayout): ScreenLayouts =
        ScreenLayouts(bySet + (setKey to layout))

    fun toJson(): String = JSONObject().apply {
        bySet.forEach { (setKey, layout) -> put(setKey, JSONObject(layout.toJson())) }
    }.toString()

    companion object {
        fun fromJson(json: String?): ScreenLayouts {
            if (json.isNullOrBlank()) return ScreenLayouts()
            return try {
                val obj = JSONObject(json)
                val sets = obj.keys().asSequence().associateWith { setKey ->
                    ScreenLayout.fromJson(obj.optJSONObject(setKey)?.toString())
                }
                ScreenLayouts(sets)
            } catch (_: Exception) {
                ScreenLayouts()
            }
        }

        fun setKeyOf(screenKeys: List<String>): String = screenKeys.sorted().joinToString("|")
    }
}
