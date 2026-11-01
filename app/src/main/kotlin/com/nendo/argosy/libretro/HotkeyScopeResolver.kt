package com.nendo.argosy.libretro

import com.nendo.argosy.data.local.entity.HotkeyAction
import com.nendo.argosy.data.local.entity.HotkeyEntity
import com.nendo.argosy.data.local.entity.HotkeyScopeType

sealed interface HotkeyBindWarning {
    data class OverridesLowerTier(val shadowedAction: HotkeyAction, val scopeLabel: String) : HotkeyBindWarning
    data class ShadowedByHigherTier(val scopeLabel: String) : HotkeyBindWarning
    data class SameTierConflict(val action: HotkeyAction) : HotkeyBindWarning
}

object HotkeyScopeResolver {

    private fun tierRank(type: HotkeyScopeType): Int = when (type) {
        HotkeyScopeType.GLOBAL -> 0
        HotkeyScopeType.PLATFORM -> 1
        HotkeyScopeType.CORE -> 2
    }

    /**
     * Actions one scope can hold several binds of at once, so they group by combo, not by action.
     */
    private val MULTI_BIND_ACTIONS = setOf(
        HotkeyAction.CYCLE_CORE_OPTION,
        HotkeyAction.SEND_CORE_INPUT
    )

    /**
     * GLOBAL + PLATFORM(slug) + CORE(coreId), highest tier winning. A higher tier displaces a
     * lower one that shares its combo, so one combo never drives two actions, and one that shares
     * its action, so rebinding in a scope replaces the bind rather than running alongside it. A
     * disabled row still displaces, which is how a scope unbinds an action the tier below holds;
     * callers pass disabled rows in for that and drop them after resolving.
     */
    fun resolve(
        all: List<HotkeyEntity>,
        platformSlug: String?,
        coreId: String?,
        parseCombo: (HotkeyEntity) -> List<Int>
    ): List<HotkeyEntity> {
        val applicable = all.filter { isApplicable(it, platformSlug, coreId) }
        val (multiBind, singleBind) = applicable.partition { it.action in MULTI_BIND_ACTIONS }

        val resolvedMulti = multiBind
            .groupBy { HotkeyManager.canonicalizeCombo(parseCombo(it)) to it.controllerId }
            .flatMap { (_, group) -> topTierOf(group) }

        val resolvedSingle = singleBind.filter { entity ->
            val combo = HotkeyManager.canonicalizeCombo(parseCombo(entity))
            val rank = tierRank(entity.scopeType)
            singleBind.none { other ->
                other !== entity &&
                    other.controllerId == entity.controllerId &&
                    tierRank(other.scopeType) > rank &&
                    (other.action == entity.action ||
                        HotkeyManager.canonicalizeCombo(parseCombo(other)) == combo)
            }
        }

        return resolvedMulti + resolvedSingle
    }

    private fun topTierOf(group: List<HotkeyEntity>): List<HotkeyEntity> {
        val topTier = group.maxOf { tierRank(it.scopeType) }
        return group.filter { tierRank(it.scopeType) == topTier }
    }

    private fun isApplicable(entity: HotkeyEntity, platformSlug: String?, coreId: String?): Boolean =
        when (entity.scopeType) {
            HotkeyScopeType.GLOBAL -> true
            HotkeyScopeType.PLATFORM -> entity.scopeKey != null && entity.scopeKey == platformSlug
            HotkeyScopeType.CORE -> entity.scopeKey != null && entity.scopeKey == coreId
        }

    /** Whether two scopes can both apply to one game (different cores never do). */
    fun scopesOverlap(a: HotkeyEntity, b: HotkeyEntity): Boolean {
        val at = a.scopeType
        val bt = b.scopeType
        return when {
            at == HotkeyScopeType.GLOBAL || bt == HotkeyScopeType.GLOBAL -> true
            at == HotkeyScopeType.PLATFORM && bt == HotkeyScopeType.PLATFORM -> a.scopeKey == b.scopeKey
            at == HotkeyScopeType.CORE && bt == HotkeyScopeType.CORE -> a.scopeKey == b.scopeKey
            at == HotkeyScopeType.PLATFORM && bt == HotkeyScopeType.CORE -> coreRunsOnPlatform(b.scopeKey, a.scopeKey)
            at == HotkeyScopeType.CORE && bt == HotkeyScopeType.PLATFORM -> coreRunsOnPlatform(a.scopeKey, b.scopeKey)
            else -> false
        }
    }

    private fun coreRunsOnPlatform(coreId: String?, platformSlug: String?): Boolean {
        if (coreId == null || platformSlug == null) return false
        return LibretroCoreRegistry.getCoreById(coreId)?.platforms?.contains(platformSlug) == true
    }

    /** Warnings for a candidate bind vs overlapping binds sharing its combo+controller. */
    fun evaluateOnSave(
        candidate: HotkeyEntity,
        existing: List<HotkeyEntity>,
        parseCombo: (HotkeyEntity) -> List<Int>
    ): List<HotkeyBindWarning> {
        val candidateCombo = HotkeyManager.canonicalizeCombo(parseCombo(candidate))
        if (candidateCombo.isEmpty()) return emptyList()
        val candidateRank = tierRank(candidate.scopeType)

        return existing.asSequence()
            .filter { it.id != candidate.id }
            .filter { it.controllerId == candidate.controllerId }
            .filter { HotkeyManager.canonicalizeCombo(parseCombo(it)) == candidateCombo }
            .filter { scopesOverlap(candidate, it) }
            .mapNotNull { other ->
                val otherRank = tierRank(other.scopeType)
                when {
                    candidateRank > otherRank ->
                        HotkeyBindWarning.OverridesLowerTier(other.action, scopeLabel(other))
                    candidateRank < otherRank ->
                        HotkeyBindWarning.ShadowedByHigherTier(scopeLabel(other))
                    other.action != candidate.action ->
                        HotkeyBindWarning.SameTierConflict(other.action)
                    else -> null
                }
            }
            .toList()
    }

    fun scopeLabel(entity: HotkeyEntity): String = when (entity.scopeType) {
        HotkeyScopeType.GLOBAL -> "Global"
        HotkeyScopeType.PLATFORM -> "Platform"
        HotkeyScopeType.CORE -> "Core"
    }
}
