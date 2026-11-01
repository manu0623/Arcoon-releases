package com.nendo.argosy.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import com.nendo.argosy.util.StorageAccess
import com.nendo.argosy.util.openStorageAccessSettings

@Composable
fun rememberStorageAccessRequest(onResult: () -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val activity = context.findActivity()
        val denied = results.isNotEmpty() && !results.values.all { it }
        val promptSuppressed = activity != null && StorageAccess.LEGACY_PERMISSIONS.none {
            ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
        }
        if (denied && promptSuppressed) {
            context.openStorageAccessSettings()
        }
        currentOnResult()
    }
    return remember(context, launcher) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.openStorageAccessSettings()
            } else {
                launcher.launch(StorageAccess.LEGACY_PERMISSIONS)
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
