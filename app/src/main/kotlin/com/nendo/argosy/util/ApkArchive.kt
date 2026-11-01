package com.nendo.argosy.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

private const val TAG = "ApkArchive"

fun apkArchivePackageName(context: Context, apkFile: File): String? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageArchiveInfo(
            apkFile.absolutePath,
            PackageManager.PackageInfoFlags.of(0)
        )
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
    }?.packageName
} catch (e: Exception) {
    Logger.warn(TAG, "Could not read package from ${apkFile.name}: ${e.message}")
    null
}
