package com.nendo.argosy.ui.cocoon.theme

import android.content.Context
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.cocoon.CocoonMenuPanel
import com.nendo.argosy.ui.cocoon.CocoonMenuRow
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.primitives.CocoonCursorHost
import com.nendo.argosy.ui.primitives.cocoonBrackets
import com.nendo.argosy.ui.theme.CocoonSurface
import com.nendo.argosy.ui.util.clickableNoFocus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Arcoon's theme store, laid out like Cocoon's Silk. It reads catalogues from public GitHub repos
 * you add (a themes.json listing each theme's name, author, version, preview and zip), plus any
 * theme ZIPs already in your Download folder. Cocoon's own Silk server isn't used.
 */
object CocoonThemeCatalog {
    data class Item(
        val name: String,
        val author: String?,
        val version: String?,
        val description: String?,
        val preview: String?,
        /** A URL, or a local path for ZIPs found on the device. */
        val zip: String,
        val downloads: Long,
        val added: String?,
        val source: String,
    )

    private const val PREFS = "cocoon_theme"
    private val cache = java.util.concurrent.ConcurrentHashMap<String, List<Item>>()

    fun repos(context: Context): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet("store_repos", emptySet()).orEmpty().sorted()

    fun addRepo(context: Context, repo: String) {
        val clean = repo.trim().removePrefix("https://github.com/").trim('/')
        if (!clean.contains('/')) return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit().putStringSet("store_repos", p.getStringSet("store_repos", emptySet()).orEmpty() + clean).apply()
    }

    fun removeRepo(context: Context, repo: String) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit().putStringSet("store_repos", p.getStringSet("store_repos", emptySet()).orEmpty() - repo).apply()
        cache.remove(repo)
    }

    private fun get(url: String): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 60_000
        connection.instanceFollowRedirects = true
        try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun resolve(repo: String, ref: String?): String? = when {
        ref.isNullOrBlank() -> null
        ref.startsWith("http") -> ref
        else -> "https://raw.githubusercontent.com/$repo/HEAD/" + ref.trimStart('/')
    }

    suspend fun online(context: Context): List<Item> = withContext(Dispatchers.IO) {
        repos(context).flatMap { repo ->
            cache[repo] ?: runCatching {
                val json = JSONObject(String(get("https://raw.githubusercontent.com/$repo/HEAD/themes.json")))
                val list = json.optJSONArray("themes") ?: return@runCatching emptyList<Item>()
                (0 until list.length()).mapNotNull { i ->
                    val o = list.optJSONObject(i) ?: return@mapNotNull null
                    val zip = resolve(repo, o.optString("zip")) ?: return@mapNotNull null
                    Item(
                        name = o.optString("name").ifBlank { "Theme" },
                        author = o.optString("author").ifBlank { null },
                        version = o.optString("version").ifBlank { null },
                        description = o.optString("description").ifBlank { null },
                        preview = resolve(repo, o.optString("preview")),
                        zip = zip,
                        downloads = o.optLong("downloads", 0),
                        added = o.optString("added").ifBlank { null },
                        source = repo,
                    )
                }
            }.getOrDefault(emptyList()).also { cache[repo] = it }
        }
    }

    /** Theme / asset-pack ZIPs sitting in Download (and Download/Arcoon). */
    suspend fun onDevice(): List<Item> = withContext(Dispatchers.IO) {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        listOf(downloads, File(downloads, "Arcoon"), File(downloads, "Cocoon"))
            .filter { it.isDirectory }
            .flatMap { dir -> dir.listFiles().orEmpty().filter { it.isFile && it.extension.equals("zip", true) } }
            .sortedByDescending { it.lastModified() }
            .map { Item(it.nameWithoutExtension, null, null, null, null, it.absolutePath, 0, null, "Download") }
    }

    /** Downloads (if needed) and installs; returns the message to show. */
    suspend fun install(context: Context, item: Item): String = withContext(Dispatchers.IO) {
        val path = if (item.zip.startsWith("http")) {
            val out = File(context.cacheDir, "store_${item.name.hashCode()}.zip")
            out.writeBytes(get(item.zip))
            out.absolutePath
        } else item.zip
        val result = CocoonThemeEngine.importZip(path)
        result.fold(
            onSuccess = { context.getString(R.string.cocoon_themes_imported, it.name) },
            onFailure = {
                val message = it.message.orEmpty()
                if (message.startsWith("PACK:")) context.getString(R.string.cocoon_themes_imported, message.removePrefix("PACK:"))
                else context.getString(R.string.cocoon_themes_import_failed, message)
            }
        )
    }

    /** Installed themes that a catalogue has a newer version of. */
    fun updates(installed: List<CocoonTheme>, online: List<Item>): List<Item> = online.filter { item ->
        val mine = installed.firstOrNull { it.name.equals(item.name, ignoreCase = true) } ?: return@filter false
        item.version != null && item.version != mine.version
    }
}

private enum class StoreSection(val labelRes: Int, val subRes: Int, val icon: ImageVector) {
    POPULAR(R.string.cocoon_store_popular, R.string.cocoon_store_popular_sub, Icons.Filled.WorkspacePremium),
    NEW(R.string.cocoon_store_new, R.string.cocoon_store_new_sub, Icons.Filled.FiberNew),
    BROWSE(R.string.cocoon_store_browse, R.string.cocoon_store_browse_sub, Icons.Filled.ViewModule),
    DEVICE(R.string.cocoon_store_device, R.string.cocoon_store_device_sub, Icons.Filled.FolderOpen),
    UPDATES(R.string.cocoon_store_updates, R.string.cocoon_store_updates_sub, Icons.Filled.SystemUpdateAlt),
    CATALOGS(R.string.cocoon_store_catalogs, R.string.cocoon_store_catalogs_sub, Icons.Filled.Storefront),
}

/** The store: a menu of sections, then a carousel of themes (A installs, B back). */
@Composable
fun CocoonThemeStoreScreen(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var section by remember { mutableStateOf<StoreSection?>(null) }
    var menuFocus by remember { mutableIntStateOf(0) }
    var catalog by remember { mutableStateOf<List<CocoonThemeCatalog.Item>>(emptyList()) }
    var focus by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }
    var managing by remember { mutableStateOf(false) }
    val loading = stringResource(R.string.cocoon_store_loading)
    val empty = stringResource(R.string.cocoon_store_empty)

    LaunchedEffect(section) {
        val s = section ?: return@LaunchedEffect
        if (s == StoreSection.CATALOGS) { managing = true; section = null; return@LaunchedEffect }
        focus = 0
        catalog = emptyList()
        status = loading
        val list = when (s) {
            StoreSection.DEVICE -> CocoonThemeCatalog.onDevice()
            StoreSection.UPDATES -> CocoonThemeCatalog.updates(CocoonThemeEngine.installed.value, CocoonThemeCatalog.online(context))
            StoreSection.POPULAR -> CocoonThemeCatalog.online(context).sortedByDescending { it.downloads }
            StoreSection.NEW -> CocoonThemeCatalog.online(context).sortedByDescending { it.added.orEmpty() }
            else -> CocoonThemeCatalog.online(context).sortedBy { it.name.lowercase() }
        }
        catalog = list
        status = if (list.isEmpty()) empty else null
    }

    val itemsNow by rememberUpdatedState(catalog)
    val dismiss by rememberUpdatedState(onDismiss)
    val handler = remember {
        object : InputHandler {
            override fun onUp(): InputResult {
                if (section == null) { if (menuFocus > 0) menuFocus-- else return InputResult.handled(SoundType.BOUNDARY) }
                return InputResult.HANDLED
            }
            override fun onDown(): InputResult {
                if (section == null) { if (menuFocus < StoreSection.entries.lastIndex) menuFocus++ else return InputResult.handled(SoundType.BOUNDARY) }
                return InputResult.HANDLED
            }
            override fun onLeft(): InputResult {
                if (section != null && focus > 0) focus-- else return InputResult.handled(SoundType.BOUNDARY)
                return InputResult.HANDLED
            }
            override fun onRight(): InputResult {
                if (section != null && focus < itemsNow.lastIndex) focus++ else return InputResult.handled(SoundType.BOUNDARY)
                return InputResult.HANDLED
            }
            override fun onConfirm(): InputResult {
                if (section == null) {
                    section = StoreSection.entries[menuFocus]
                } else {
                    val item = itemsNow.getOrNull(focus) ?: return InputResult.HANDLED
                    status = context.getString(R.string.cocoon_themes_importing)
                    scope.launch { status = runCatching { CocoonThemeCatalog.install(context, item) }.getOrElse { it.message } }
                }
                return InputResult.handled(SoundType.SELECT)
            }
            override fun onBack(): InputResult {
                if (section != null) { section = null; status = null } else dismiss()
                return InputResult.HANDLED
            }
            override fun onMenu(): InputResult = InputResult.handled(SoundType.SILENT)
            override fun onSecondaryAction(): InputResult = InputResult.HANDLED
            override fun onContextMenu(): InputResult = InputResult.HANDLED
        }
    }
    ModalInputEffect(active = !managing, handler = handler)

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0B0B0D)).clickableNoFocus { }) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Storefront, null, tint = CocoonSurface.TextPrimary, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    section?.let { stringResource(it.labelRes) } ?: stringResource(R.string.cocoon_store_title),
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = CocoonSurface.TextPrimary
                )
                Spacer(Modifier.weight(1f))
                status?.let { Text(it, color = CocoonSurface.TextSecondary, maxLines = 1, style = MaterialTheme.typography.bodyMedium) }
            }
            Spacer(Modifier.height(14.dp))
            val current = section
            if (current == null) {
                CocoonCursorHost(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp)) {
                        StoreSection.entries.forEachIndexed { index, s ->
                            CocoonMenuRow(
                                icon = s.icon,
                                label = stringResource(s.labelRes) + "  ·  " + stringResource(s.subRes),
                                focused = index == menuFocus,
                                isFirst = index == 0,
                                isLast = index == StoreSection.entries.lastIndex,
                                onClick = { menuFocus = index; section = s },
                            )
                        }
                    }
                }
            } else {
                // Silk's carousel: the focused theme large in the middle, its neighbours either side.
                val row = rememberLazyListState()
                LaunchedEffect(focus) { if (catalog.isNotEmpty()) runCatching { row.animateScrollToItem((focus - 1).coerceAtLeast(0)) } }
                CocoonCursorHost(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    LazyRow(
                        state = row,
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        contentPadding = PaddingValues(horizontal = 40.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(catalog.size) { index ->
                            val item = catalog[index]
                            val focused = index == focus
                            Column(
                                modifier = Modifier
                                    .width(420.dp)
                                    .graphicsLayer { val s = if (focused) 1f else 0.86f; scaleX = s; scaleY = s; alpha = if (focused) 1f else 0.6f },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                        .cocoonBrackets(focused = focused, outset = 4.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(Color(0xFF1A1A1E))
                                        .clickableNoFocus { focus = index },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (item.preview != null) {
                                        coil.compose.AsyncImage(model = item.preview, contentDescription = null,
                                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    } else {
                                        Icon(Icons.Filled.Download, null, tint = CocoonSurface.TextSecondary, modifier = Modifier.size(48.dp))
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                Column(
                                    modifier = Modifier.fillMaxWidth().background(CocoonMenuPanel, RoundedCornerShape(16.dp)).padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(item.name, color = CocoonSurface.TextPrimary, style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.SemiBold, maxLines = 1, textAlign = TextAlign.Center)
                                    Text(
                                        listOfNotNull(item.author, item.version?.let { "v$it" }, item.source).joinToString(" · "),
                                        color = CocoonSurface.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
                Text(stringResource(R.string.cocoon_store_hints), color = CocoonSurface.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (managing) {
        CocoonCatalogManager(onDismiss = { managing = false })
    }
}

@Composable
private fun CocoonCatalogManager(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val repos = remember(version) { CocoonThemeCatalog.repos(context) }
    var focus by remember { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    val reposNow by rememberUpdatedState(repos)
    val dismiss by rememberUpdatedState(onDismiss)
    val handler = remember {
        object : InputHandler {
            override fun onUp(): InputResult { if (focus > 0) focus--; return InputResult.HANDLED }
            override fun onDown(): InputResult { if (focus < reposNow.size) focus++; return InputResult.HANDLED }
            override fun onLeft(): InputResult = InputResult.HANDLED
            override fun onRight(): InputResult = InputResult.HANDLED
            override fun onConfirm(): InputResult { if (focus == 0) adding = true; return InputResult.HANDLED }
            override fun onContextMenu(): InputResult {
                reposNow.getOrNull(focus - 1)?.let { CocoonThemeCatalog.removeRepo(context, it); version++ }
                focus = focus.coerceAtMost(reposNow.size - 1).coerceAtLeast(0)
                return InputResult.HANDLED
            }
            override fun onBack(): InputResult { dismiss(); return InputResult.HANDLED }
            override fun onMenu(): InputResult = InputResult.handled(SoundType.SILENT)
            override fun onSecondaryAction(): InputResult = InputResult.HANDLED
        }
    }
    ModalInputEffect(active = !adding, handler = handler)
    Box(modifier = Modifier.fillMaxSize().background(Color.Black).clickableNoFocus { }) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp)) {
            Text(stringResource(R.string.cocoon_store_catalogs), style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold, color = CocoonSurface.TextPrimary)
            Spacer(Modifier.height(14.dp))
            CocoonCursorHost(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf(stringResource(R.string.cocoon_store_catalog_add)) + repos).forEachIndexed { index, label ->
                        Text(
                            label,
                            style = MaterialTheme.typography.titleMedium,
                            color = CocoonSurface.TextPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .cocoonBrackets(focused = index == focus, outset = 2.dp)
                                .background(CocoonMenuPanel, RoundedCornerShape(14.dp))
                                .clickableNoFocus { focus = index; if (index == 0) adding = true }
                                .padding(horizontal = 18.dp, vertical = 14.dp)
                        )
                    }
                }
            }
            Text(stringResource(R.string.cocoon_jingle_repo_hint), color = CocoonSurface.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (adding) {
        com.nendo.argosy.ui.screens.collections.dialogs.CollectionNameDialog(
            title = stringResource(R.string.cocoon_store_catalog_add),
            label = "owner/repository",
            confirmLabel = stringResource(R.string.cocoon_themes_apply),
            initialName = "",
            gamepadInput = true,
            onDismiss = { adding = false },
            onSubmit = { name ->
                adding = false
                CocoonThemeCatalog.addRepo(context, name)
                version++
            }
        )
    }
}
