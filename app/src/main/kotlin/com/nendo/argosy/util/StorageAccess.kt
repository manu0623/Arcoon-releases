package com.nendo.argosy.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Whether Argosy may read and write shared storage: All files access on Android 11 and later,
 * the legacy read and write runtime permissions before it.
 */
object StorageAccess {

    val LEGACY_PERMISSIONS = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    fun isGranted(context: Context): Boolean = isGranted(
        sdkInt = Build.VERSION.SDK_INT,
        isExternalStorageManager = {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()
        },
        isPermissionGranted = { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    )

    /**
     * The system screen that grants [isGranted]: All files access on Android 11 and later, the
     * app's settings page before it.
     */
    fun settingsIntent(context: Context): Intent {
        val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
        } else {
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        }
        return Intent(action, Uri.parse("package:${context.packageName}"))
    }

    fun isGranted(
        sdkInt: Int,
        isExternalStorageManager: () -> Boolean,
        isPermissionGranted: (String) -> Boolean
    ): Boolean = if (sdkInt >= Build.VERSION_CODES.R) {
        isExternalStorageManager()
    } else {
        LEGACY_PERMISSIONS.all(isPermissionGranted)
    }
}
