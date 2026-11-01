package com.nendo.argosy.ui.util

import android.content.ClipboardManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * The text of the clipboard's first item, or null when the clipboard is empty or that item holds no text.
 */
class ClipboardTextReader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun read(): String? {
        val manager = context.getSystemService(ClipboardManager::class.java) ?: return null
        val clip = manager.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).text?.toString()
    }
}
