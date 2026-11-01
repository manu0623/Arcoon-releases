package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.nendo.argosy.data.emulator.BuiltinCoreResolver
import com.nendo.argosy.data.repository.CoreVersionRepository
import com.nendo.argosy.libretro.LibretroCoreRegistry
import com.nendo.argosy.ui.screens.settings.sections.formatCoreBuildStamp
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject

/**
 * The core the built-in emulator loads for a platform or game, and the buildbot stamp of its
 * downloaded build. [buildStamp] is null when no build of the core was downloaded.
 */
data class BuiltinCoreSummary(
    val coreName: String,
    val buildStamp: String?
) {
    fun formattedBuild(zone: ZoneId, locale: Locale): String? =
        buildStamp?.takeIf { it.isNotBlank() }?.let { raw ->
            formatCoreBuildStamp(raw, zone, locale).takeIf { it != raw }
        }

    fun subtitle(zone: ZoneId, locale: Locale, withBuild: (coreName: String, build: String) -> String): String =
        formattedBuild(zone, locale)?.let { withBuild(coreName, it) } ?: coreName
}

fun builtinCoreSummaryOf(coreId: String, installedBuild: String?): BuiltinCoreSummary =
    BuiltinCoreSummary(
        coreName = LibretroCoreRegistry.getCoreById(coreId)?.displayName ?: coreId,
        buildStamp = installedBuild
    )

class BuiltinCoreSummaryLoader @Inject constructor(
    private val builtinCoreResolver: BuiltinCoreResolver,
    private val coreVersionRepository: CoreVersionRepository
) {
    suspend fun load(gameId: Long?, platformId: Long, platformSlug: String): BuiltinCoreSummary? {
        val coreId = builtinCoreResolver.resolveCoreId(gameId, platformId, platformSlug) ?: return null
        return builtinCoreSummaryOf(coreId, coreVersionRepository.installedBuild(coreId))
    }
}

@Composable
fun builtinCoreSubtitle(summary: BuiltinCoreSummary, @StringRes withBuildRes: Int): String {
    val locale = LocalConfiguration.current.locales[0]
    val context = LocalContext.current
    return remember(summary, locale, withBuildRes) {
        summary.subtitle(ZoneId.systemDefault(), locale) { coreName, build ->
            context.getString(withBuildRes, coreName, build)
        }
    }
}
