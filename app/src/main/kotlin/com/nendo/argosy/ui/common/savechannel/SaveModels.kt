package com.nendo.argosy.ui.common.savechannel

enum class SaveFocusColumn { SLOTS, HISTORY }

sealed interface SaveDeleteTarget {
    data class Channel(val channelName: String) : SaveDeleteTarget
    data class Version(val cacheId: Long, val timestamp: Long, val onServer: Boolean) : SaveDeleteTarget
    data class Archived(val cacheIds: List<Long>, val allOnServer: Boolean) : SaveDeleteTarget
}

data class SaveSlotItem(
    val channelName: String?,
    val displayName: String,
    val isActive: Boolean,
    val saveCount: Int,
    val latestTimestamp: Long?,
    val isCreateAction: Boolean = false,
    val isMigrationCandidate: Boolean = false,
    val isArchivedBucket: Boolean = false
) {
    val slotKey: String get() = channelName ?: when {
        isCreateAction -> "__new_slot__"
        isArchivedBucket -> "__archived__"
        else -> "__none__"
    }
}

data class SlotPickerItem(
    val channelName: String?,
    val displayName: String,
    val isNewSave: Boolean = false,
    val isBlocked: Boolean = false
) {
    val pickerKey: String get() = if (isNewSave) "__new_save__" else channelName ?: "__none__"
}

data class SaveHistoryItem(
    val cacheId: Long,
    val timestamp: Long,
    val size: Long,
    val channelName: String?,
    val isLocal: Boolean,
    val isSynced: Boolean,
    val isActiveRestorePoint: Boolean,
    val isLatest: Boolean,
    val isHardcore: Boolean,
    val isRollback: Boolean,
    val isArchival: Boolean = false,
    val serverSaveId: Long? = null
) {
    val historyKey: String get() =
        if (cacheId >= 0) "c$cacheId" else "s${serverSaveId ?: timestamp}"

    val isDeletable: Boolean get() =
        cacheId >= 0 && !isActiveRestorePoint && (isArchival || !isLatest)
}
