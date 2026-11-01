package com.nendo.argosy.data.iconpack

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.Xml
import androidx.core.content.res.ResourcesCompat
import com.nendo.argosy.data.model.IconPack
import com.nendo.argosy.data.preferences.DisplayPreferencesRepository
import com.nendo.argosy.data.repository.AppsRepository
import com.nendo.argosy.util.SafeCoroutineScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "IconPackRepository"
private const val APPFILTER = "appfilter"
private const val ITEM_TAG = "item"
private const val COMPONENT_ATTR = "component"
private const val DRAWABLE_ATTR = "drawable"

@Singleton
class IconPackRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val displayPreferences: DisplayPreferencesRepository,
    appsRepository: AppsRepository
) {
    private val packageManager: PackageManager = context.packageManager
    private val scope = SafeCoroutineScope(Dispatchers.IO, TAG)
    private val lock = Mutex()
    private var loaded: LoadedPack? = null

    init {
        scope.launch {
            appsRepository.packageChanges.collect { changed -> invalidate(changed) }
        }
    }

    suspend fun installedPacks(): List<IconPack> = withContext(Dispatchers.IO) {
        DISCOVERY_INTENTS.asSequence()
            .flatMap { intent -> packageManager.queryIntentActivities(intent, 0).asSequence() }
            .mapNotNull { it.activityInfo?.applicationInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { IconPack(it.packageName, packageManager.getApplicationLabel(it).toString()) }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /**
     * The selected pack's icon for [packageName], or null when no pack is selected, the pack is
     * gone, or the pack has no icon for the app.
     */
    suspend fun iconFor(packageName: String): Drawable? = withContext(Dispatchers.IO) {
        val packPackage = displayPreferences.iconPack.first() ?: return@withContext null
        try {
            val pack = packFor(packPackage)
            val className = launchComponentOf(packageName)?.className
            pack.drawableFor(packageName, className)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Icon pack $packPackage failed for $packageName", e)
            null
        }
    }

    private suspend fun packFor(packPackage: String): LoadedPack = lock.withLock {
        loaded?.takeIf { it.packageName == packPackage }
            ?: load(packPackage).also { loaded = it }
    }

    private suspend fun invalidate(changedPackage: String) {
        lock.withLock {
            loaded = loaded?.takeUnless { it.packageName == changedPackage }
        }
    }

    private fun load(packPackage: String): LoadedPack {
        val resources = try {
            packageManager.getResourcesForApplication(packPackage)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Icon pack $packPackage is not installed")
            return LoadedPack(packPackage, null, AppFilter.EMPTY)
        }
        val filter = AppFilter.fromEntries(readAppFilter(resources, packPackage))
        if (filter.isEmpty) Log.w(TAG, "Icon pack $packPackage has no usable appfilter entries")
        return LoadedPack(packPackage, resources, filter)
    }

    private fun readAppFilter(resources: Resources, packPackage: String): List<AppFilterEntry> {
        val xmlId = resources.getIdentifier(APPFILTER, "xml", packPackage)
        if (xmlId != 0) {
            val parser = resources.getXml(xmlId)
            return try {
                itemEntries(parser)
            } finally {
                parser.close()
            }
        }
        return try {
            resources.assets.open("$APPFILTER.xml").use { stream ->
                val parser = Xml.newPullParser().apply { setInput(stream, null) }
                itemEntries(parser)
            }
        } catch (e: IOException) {
            Log.w(TAG, "Icon pack $packPackage has no appfilter")
            emptyList()
        }
    }

    private fun itemEntries(parser: XmlPullParser): List<AppFilterEntry> {
        val entries = mutableListOf<AppFilterEntry>()
        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == ITEM_TAG) {
                    entries += AppFilterEntry(
                        component = parser.getAttributeValue(null, COMPONENT_ATTR),
                        drawable = parser.getAttributeValue(null, DRAWABLE_ATTR)
                    )
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Appfilter stopped parsing after ${entries.size} entries", e)
        }
        return entries
    }

    private fun launchComponentOf(packageName: String): ComponentName? =
        packageManager.getLaunchIntentForPackage(packageName)?.component
            ?: packageManager.getLeanbackLaunchIntentForPackage(packageName)?.component

    private class LoadedPack(
        val packageName: String,
        private val resources: Resources?,
        private val filter: AppFilter
    ) {
        fun drawableFor(appPackage: String, className: String?): Drawable? {
            val res = resources ?: return null
            val id = filter.resolve(appPackage, className) { name ->
                res.getIdentifier(name, "drawable", packageName)
            } ?: return null
            return ResourcesCompat.getDrawable(res, id, null)
        }
    }

    private companion object {
        val DISCOVERY_INTENTS = listOf(
            Intent("org.adw.launcher.THEMES"),
            Intent("com.novalauncher.THEME"),
            Intent("org.adw.launcher.icons.ACTION_PICK_ICON"),
            Intent("com.dlto.atom.launcher.THEME"),
            Intent(Intent.ACTION_MAIN).addCategory("com.anddoes.launcher.THEME")
        )
    }
}
