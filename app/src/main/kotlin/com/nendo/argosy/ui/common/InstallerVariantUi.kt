package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.data.emulator.ApkAssetMatcher

data class VariantLabel(@StringRes val labelRes: Int?, val abi: String?)

fun variantLabelFor(variant: String?): VariantLabel = when (variant) {
    "arm64-v8a", "arm64", "aarch64", "64bit", "a64" -> VariantLabel(null, "ARM64")
    "armeabi-v7a", "armeabi", "arm32", "armv7", "32bit", "a32" -> VariantLabel(null, "ARM32")
    "x86_64", "x86-64", "x64" -> VariantLabel(null, "x86_64")
    "x86", "i686", "i386" -> VariantLabel(null, "x86")
    "universal", "all", "fat", "multi" ->
        VariantLabel(R.string.settings_installers_variant_universal, null)
    null -> VariantLabel(R.string.settings_installers_variant_default, null)
    else -> VariantLabel(null, variant.replaceFirstChar { it.uppercase() })
}

@Composable
fun variantLabelText(variant: String?): String {
    val label = variantLabelFor(variant)
    return label.labelRes?.let { stringResource(it) } ?: label.abi.orEmpty()
}

@Composable
fun installerVariantLabel(assetName: String): String = stringResource(
    R.string.settings_installers_variant_entry,
    variantLabelText(ApkAssetMatcher.extractVariantFromAssetName(assetName)),
    assetName
)
