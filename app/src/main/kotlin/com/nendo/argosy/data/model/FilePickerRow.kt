package com.nendo.argosy.data.model

data class FilePickerRow(
    val isHeader: Boolean,
    val groupKey: String,
    val label: String,
    val rommFileId: Long? = null,
    val sizeBytes: Long = 0,
    val isDownloaded: Boolean = false,
    val isLocked: Boolean = false
)

fun List<FilePickerRow>.visibleWithCollapsed(collapsed: Set<String>): List<FilePickerRow> =
    filter { it.isHeader || it.groupKey !in collapsed }

private fun List<FilePickerRow>.selectableRows(): List<FilePickerRow> =
    filter { !it.isHeader && !it.isLocked }

fun List<FilePickerRow>.allSelectableSelected(selectedFileIds: Set<Long>): Boolean {
    val rows = selectableRows()
    return rows.isNotEmpty() && rows.all { it.rommFileId in selectedFileIds }
}

fun List<FilePickerRow>.selectableFileIds(): Set<Long> =
    selectableRows().mapNotNull { it.rommFileId }.toSet()
