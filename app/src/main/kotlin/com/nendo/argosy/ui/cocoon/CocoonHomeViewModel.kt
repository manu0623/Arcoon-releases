package com.nendo.argosy.ui.cocoon

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.R
import com.nendo.argosy.data.local.entity.CollectionType
import com.nendo.argosy.data.repository.CollectionRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.home.delegates.HomeSyncDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CocoonHomeViewModel @Inject constructor(
    private val platformRepository: PlatformRepository,
    private val collectionRepository: CollectionRepository,
    private val syncDelegate: HomeSyncDelegate,
    private val appsRepository: com.nendo.argosy.data.repository.AppsRepository,
    private val preferencesRepository: com.nendo.argosy.data.preferences.UserPreferencesRepository,
    private val displayAffinityHelper: com.nendo.argosy.util.DisplayAffinityHelper,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val romMRepository: com.nendo.argosy.data.remote.romm.RomMRepository,
    private val gameDao: com.nendo.argosy.data.local.dao.GameDao,
    private val imageCacheManager: com.nendo.argosy.data.cache.ImageCacheManager,
    private val playSessionDao: com.nendo.argosy.data.local.dao.PlaySessionDao,
) : ViewModel() {

    private var coverPrefetch: kotlinx.coroutines.Job? = null
    private val coverRequests = mutableListOf<coil.request.Disposable>()

    /**
     * While a platform folder sits under the cursor, its first covers are decoded into the image
     * cache, so opening the folder shows them at once instead of blank tiles filling in.
     */
    private var prefetchedKey: String? = null

    private fun prefetchCovers(tile: CocoonTile?) {
        if (tile?.key == prefetchedKey) return
        prefetchedKey = tile?.key
        coverPrefetch?.cancel()
        val platformId = (tile?.target as? CocoonTileTarget.Platform)?.platformId ?: return
        coverPrefetch = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.delay(60)
            val games = runCatching { gameDao.getByPlatformTitleOrdered(platformId, null, 30, 0) }
                .getOrNull() ?: return@launch
            val loader = coil.Coil.imageLoader(context)
            synchronized(coverRequests) {
                coverRequests.forEach { it.dispose() }
                coverRequests.clear()
            }
            games.forEach { game ->
                val path = (imageCacheManager.artFor(game.id) ?: runCatching { imageCacheManager.loadArt(game.id) }.getOrNull())
                    ?.coverPath ?: return@forEach
                val data: Any = CocoonThumbs.model(context, path) ?: return@forEach
                val request = coil.request.ImageRequest.Builder(context).data(data).size(COCOON_COVER_DECODE_PX).build()
                synchronized(coverRequests) { coverRequests.add(loader.enqueue(request)) }
            }
        }
    }

    private val sessionStateStore by lazy { com.nendo.argosy.data.preferences.SessionStateStore(context) }

    private val _events = kotlinx.coroutines.flow.MutableSharedFlow<CocoonHomeEvent>(extraBufferCapacity = 4)
    val events: kotlinx.coroutines.flow.SharedFlow<CocoonHomeEvent> = _events

    private val _state = MutableStateFlow(
        CocoonBoardCache.tiles.let { cached ->
            if (cached.isEmpty()) CocoonHomeState()
            else CocoonHomeState(
                tiles = cached,
                focusedIndex = cached.indexOfFirst { it.key == CocoonHomeStore.lastFocusKey }.coerceAtLeast(0),
                isLoading = false
            )
        }
    )

    /*
     * The board's tiles come from four places that finish loading at different moments. Showing
     * each stage as it arrived made the folders jump around two or three times whenever the board
     * was rebuilt; now the board waits until all four have arrived (or a short time passes).
     */
    private var platformsLoaded = false
    private var collectionsLoaded = false
    private var homeItemsLoaded = false
    private var loadWaitOver = false
    private val allLoaded: Boolean get() = loadWaitOver || (platformsLoaded && collectionsLoaded && homeItemsLoaded)
    val state: StateFlow<CocoonHomeState> = _state.asStateFlow()

    /** The board order the user set with Edit Grid, as tile keys; new tiles go at the end. */
    private val boardPrefs by lazy { context.getSharedPreferences("cocoon_board", android.content.Context.MODE_PRIVATE) }
    private fun savedOrder(): List<String> =
        boardPrefs.getString("order", null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
    private fun saveOrder(tiles: List<CocoonTile>) {
        val keys = tiles.filter { it.isReal() }.map { it.key }
        val group = groupPath.lastOrNull()
        if (group != null) setMembers(group, keys)
        else boardPrefs.edit().putString("order", keys.joinToString("\n")).apply()
    }

    // ---- Home folders (folders inside folders) ----

    /** The Home folders we're inside, outermost first (ids). */
    private val groupPath = mutableListOf<String>()

    private fun members(groupId: String): List<String> =
        boardPrefs.getString("members_$groupId", null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

    private fun setMembers(groupId: String, keys: List<String>) {
        boardPrefs.edit().putString("members_$groupId", keys.joinToString("\n")).apply()
    }

    private fun allGroupMembers(): Set<String> =
        loadHomeItems().filter { it.type == "group" }.flatMap { members(it.id) }.toSet()

    private fun groupTitles(): List<String> {
        val names = loadHomeItems().filter { it.type == "group" }.associate { it.id to (it.value ?: "") }
        return groupPath.mapNotNull { names[it] }
    }

    fun openGroup(groupId: String) {
        groupPath += groupId
        _state.update { it.copy(focusedIndex = 0, inDock = false) }
        publish()
        _state.update { it.copy(focusedIndex = 0) }
    }

    /** B inside a Home folder: back up one level, onto the folder we came out of. */
    fun leaveGroup(): Boolean {
        val left = groupPath.removeLastOrNull() ?: return false
        publish()
        val index = _state.value.tiles.indexOfFirst { it.key == "home-$left" }
        if (index >= 0) _state.update { it.copy(focusedIndex = index) }
        return true
    }

    fun closeSearch() { _state.update { it.copy(searchOpen = false) } }

    fun openNameGroup() { _state.update { it.copy(namingGroup = true, startMenuOpen = false) } }
    fun closeNameGroup() { _state.update { it.copy(namingGroup = false) } }
    fun createGroup(name: String) {
        closeNameGroup()
        addHomeItem("group", name.trim().ifBlank { "Folder" })
    }

    /** Y ▸ Move to Folder: where the tile can go (null = Home itself). */
    fun moveTargets(): List<Pair<String?, String>> {
        val moving = _state.value.movingKey
        val groups = loadHomeItems().filter { it.type == "group" && "home-${it.id}" != moving }
        val here = groupPath.lastOrNull()
        return buildList {
            if (here != null) add(null to "Home")
            groups.filter { it.id != here && !isInside(it.id, moving) }.forEach { add(it.id to (it.value ?: "")) }
        }
    }

    /** True when group [id] sits (at any depth) inside the group tile [key]. */
    private fun isInside(id: String, key: String?): Boolean {
        val owner = key?.removePrefix("home-") ?: return false
        if (!key.startsWith("home-")) return false
        val stack = ArrayDeque(members(owner))
        val seen = HashSet<String>()
        while (stack.isNotEmpty()) {
            val k = stack.removeFirst()
            if (!seen.add(k)) continue
            if (k == "home-$id") return true
            if (k.startsWith("home-")) stack.addAll(members(k.removePrefix("home-")))
        }
        return false
    }

    fun startMove(tile: CocoonTile) {
        _state.update { it.copy(movingKey = tile.key, tileMenuOpen = false) }
        openChoice(CocoonChoice.MOVE)
    }

    private fun moveInto(target: String?) {
        val key = _state.value.movingKey ?: return
        _state.update { it.copy(movingKey = null) }
        // out of every folder first, then into the chosen one
        loadHomeItems().filter { it.type == "group" }.forEach { group ->
            val list = members(group.id)
            if (key in list) setMembers(group.id, list - key)
        }
        if (target != null) setMembers(target, members(target) + key)
        publish()
    }

    /** Tiles that are saved in the order (not slots a big widget covers, not alignment holes). */
    private fun CocoonTile.isReal() = !key.startsWith("cover:") && !key.startsWith("hole:")

    /**
     * Lays the ordered tiles onto the column-major board. A widget bigger than one slot takes the
     * next place it fits whole (it never runs off the bottom), and the slots it spreads over are
     * marked as covered so the cursor treats them as part of it.
     */
    private fun layout(ordered: List<CocoonTile>): List<CocoonTile> {
        val rows = COCOON_BOARD_ROWS
        val cells = HashMap<Int, CocoonTile>()
        var cursor = 0
        ordered.forEach { tile ->
            val c = tile.spanCols.coerceAtLeast(1)
            val r = tile.spanRows.coerceIn(1, rows)
            var i = cursor
            while (true) {
                val fits = i % rows + r <= rows && (0 until c).all { dc ->
                    (0 until r).all { dr -> !cells.containsKey(i + dc * rows + dr) }
                }
                if (fits) break
                i++
            }
            for (dc in 0 until c) for (dr in 0 until r) {
                val at = i + dc * rows + dr
                cells[at] = if (dc == 0 && dr == 0) tile else tile.copy(
                    key = "cover:${tile.key}:$at",
                    target = CocoonTileTarget.Covered(tile.key),
                    spanCols = 1,
                    spanRows = 1,
                )
            }
            while (cells.containsKey(cursor)) cursor++
        }
        val last = cells.keys.maxOrNull() ?: return emptyList()
        return (0..last).map { at -> cells[at] ?: gapTile("hole:$at") }
    }

    /** Y ▸ Size on a widget: 1×1 → 2×1 → 1×2 → 2×2. */
    fun cycleWidgetSize(tile: CocoonTile) {
        val id = (tile.target as? CocoonTileTarget.Widget)?.id ?: return
        val now = COCOON_WIDGET_SIZES.indexOf(tile.spanCols to tile.spanRows).coerceAtLeast(0)
        val (c, r) = COCOON_WIDGET_SIZES[(now + 1) % COCOON_WIDGET_SIZES.size]
        boardPrefs.edit().putString("wsize_$id", "${c}x$r").apply()
        rebuildHomeTiles {
            val index = _state.value.tiles.indexOfFirst { it.key == tile.key }
            if (index >= 0) _state.update { it.copy(focusedIndex = index) }
        }
    }

    private fun widgetSpan(id: String): Pair<Int, Int> =
        boardPrefs.getString("wsize_$id", null)?.split("x")?.let { parts ->
            (parts.getOrNull(0)?.toIntOrNull() ?: 1) to (parts.getOrNull(1)?.toIntOrNull() ?: 1)
        } ?: (1 to 1)

    private var platformTiles: List<CocoonTile> = emptyList()
    private var collectionTiles: List<CocoonTile> = emptyList()
    private var homeTiles: List<CocoonTile> = emptyList()

    // ---- Home shortcuts (Start ▸ New): apps, Pods and widgets, saved as JSON ----

    private fun loadHomeItems(): List<CocoonHomeItem> = runCatching {
        val array = org.json.JSONArray(boardPrefs.getString("home_items", "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            CocoonHomeItem(o.getString("id"), o.getString("type"), o.optString("value").takeIf { it.isNotBlank() })
        }
    }.getOrDefault(emptyList())

    private fun saveHomeItems(items: List<CocoonHomeItem>) {
        val array = org.json.JSONArray()
        items.forEach { item ->
            array.put(org.json.JSONObject().put("id", item.id).put("type", item.type).put("value", item.value ?: ""))
        }
        boardPrefs.edit().putString("home_items", array.toString()).apply()
    }

    private fun rebuildHomeTiles(after: (() -> Unit)? = null) {
        viewModelScope.launch {
            homeTiles = buildHomeTiles()
            homeItemsLoaded = true
            publish()
            after?.invoke()
        }
    }

    private suspend fun buildHomeTiles(): List<CocoonTile> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        loadHomeItems().mapNotNull { item ->
            when (item.type) {
                "app" -> {
                    val pkg = item.value ?: return@mapNotNull null
                    CocoonTile(
                        key = "home-${item.id}",
                        target = CocoonTileTarget.App(pkg),
                        title = CocoonAppStore.name(pkg) ?: appsRepository.getAppLabel(pkg) ?: pkg,
                        titleRes = null,
                        kindRes = R.string.cocoon_kind_app,
                        glyph = CocoonGlyph.APP,
                        platformSlug = pkg,
                        colorIndex = CocoonFolderPalette.APPS,
                    )
                }
                "game" -> {
                    val id = item.value?.toLongOrNull() ?: return@mapNotNull null
                    val title = runCatching { gameDao.cocoonTitleOf(id) }.getOrNull() ?: return@mapNotNull null
                    val cover = (imageCacheManager.artFor(id) ?: runCatching { imageCacheManager.loadArt(id) }.getOrNull())?.coverPath
                    CocoonTile(
                        key = "home-${item.id}",
                        target = CocoonTileTarget.Game(id),
                        title = title,
                        titleRes = null,
                        kindRes = R.string.cocoon_kind_game,
                        glyph = CocoonGlyph.GAME,
                        platformSlug = "$id|${cover ?: ""}",
                        colorIndex = 0,
                    )
                }
                "smart" -> {
                    val kind = CocoonSmartKind.entries.firstOrNull { it.name == item.value } ?: return@mapNotNull null
                    CocoonTile(
                        key = "home-${item.id}",
                        target = CocoonTileTarget.Smart(kind),
                        title = null,
                        titleRes = kind.labelRes,
                        kindRes = R.string.cocoon_kind_smart,
                        glyph = CocoonGlyph.SMART,
                        platformSlug = kind.name,
                        colorIndex = CocoonFolderPalette.indexFor("smart-" + kind.name),
                    )
                }
                "group" -> CocoonTile(
                    key = "home-${item.id}",
                    target = CocoonTileTarget.Group(item.id, item.value ?: ""),
                    title = item.value,
                    titleRes = null,
                    kindRes = R.string.cocoon_kind_folder,
                    glyph = CocoonGlyph.FOLDER,
                    platformSlug = null,
                    colorIndex = CocoonFolderPalette.indexFor("group-" + item.id),
                )
                "pod" -> {
                    val kind = CocoonPodKind.entries.firstOrNull { it.name == item.value } ?: return@mapNotNull null
                    CocoonTile(
                        key = "home-${item.id}",
                        target = CocoonTileTarget.Pod(kind),
                        title = null,
                        titleRes = kind.labelRes,
                        kindRes = R.string.cocoon_kind_pod,
                        glyph = CocoonGlyph.POD,
                        platformSlug = kind.name,
                        colorIndex = 0,
                    )
                }
                else -> {
                    val kind = CocoonWidgetKind.entries.firstOrNull { "widget:" + it.name == item.type } ?: return@mapNotNull null
                    CocoonTile(
                        key = "home-${item.id}",
                        target = CocoonTileTarget.Widget(item.id, kind, item.value),
                        title = null,
                        titleRes = kind.labelRes,
                        kindRes = R.string.cocoon_kind_widget,
                        glyph = CocoonGlyph.WIDGET,
                        platformSlug = cocoonWidgetPayload(kind, item.value),
                        colorIndex = 0,
                        spanCols = widgetSpan(item.id).first,
                        spanRows = widgetSpan(item.id).second,
                    )
                }
            }
        }
    }

    /** Puts a shortcut on Home and moves the cursor onto it. */
    fun addHomeItem(type: String, value: String?) {
        val item = CocoonHomeItem(java.util.UUID.randomUUID().toString().take(8), type, value)
        val slot = _state.value.pendingSlot
        _state.update { it.copy(pendingSlot = null) }
        saveHomeItems(loadHomeItems() + item)
        groupPath.lastOrNull()?.let { group -> setMembers(group, members(group) + "home-${item.id}") }
        rebuildHomeTiles {
            val key = "home-${item.id}"
            if (slot != null) placeAt(key, slot)
            val index = _state.value.tiles.indexOfFirst { it.key == key }
            if (index >= 0) setFocus(index)
        }
        if (type.startsWith("widget:")) refreshWidgets()
    }

    /** Remove from Home: only the shortcut goes; the app stays installed. */
    private fun removeHomeTile(tile: CocoonTile) {
        val id = tile.key.removePrefix("home-")
        if (tile.target is CocoonTileTarget.Group) {
            // its contents go back to where the folder was
            val inside = members(id)
            boardPrefs.edit().remove("members_$id").apply()
            groupPath.lastOrNull()?.let { parent -> setMembers(parent, members(parent) - tile.key + inside) }
        }
        loadHomeItems().filter { it.type == "group" }.forEach { group ->
            val list = members(group.id)
            if (tile.key in list) setMembers(group.id, list - tile.key)
        }
        saveHomeItems(loadHomeItems().filterNot { it.id == id })
        rebuildHomeTiles()
    }

    /** Fills the widgets' data: recent games, a random pick, this week's playtime. */
    fun refreshWidgets() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            suspend fun game(id: Long): CocoonWidgetGame {
                val cover = (imageCacheManager.artFor(id) ?: runCatching { imageCacheManager.loadArt(id) }.getOrNull())?.coverPath
                return CocoonWidgetGame(id, runCatching { gameDao.cocoonTitleOf(id) }.getOrNull(), cover)
            }
            val recent = runCatching { gameDao.cocoonRecentlyPlayedIds(6) }.getOrDefault(emptyList()).map { game(it) }
            CocoonWidgetData.recent.value = recent
            runCatching { gameDao.cocoonRandomGameId() }.getOrNull()?.let { CocoonWidgetData.random.value = game(it) }
            val since = java.time.Instant.now().minus(java.time.Duration.ofDays(7))
            val minutes = runCatching { playSessionDao.getSessionsSince(since) }.getOrDefault(emptyList())
                .sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes().coerceAtLeast(0) }
            CocoonWidgetData.weeklyMinutes.value = minutes +
                CocoonAppStore.sessionsSince(since.toEpochMilli()).sumOf { it.minutes }
        }
    }

    init {
        CocoonHomeStore.load(context)
        CocoonDockSlots.attach(context)
        rebuildHomeTiles()
        refreshWidgets()
        // The board replaces Argosy's home, so it also takes over the home's start-up duty of
        // connecting to RomM and running the weekly library sync.
        syncDelegate.initializeRomM(
            viewModelScope,
            onSyncComplete = { refreshCollections() },
            onFavoritesRefreshed = { }
        )
        viewModelScope.launch {
            platformRepository.observePlatformsWithGames().collect { platforms ->
                platformTiles = platforms
                    .filter { it.isVisible }
                    .sortedWith(compareBy({ it.sortOrder }, { it.name }))
                    .map { platform ->
                        CocoonTile(
                            key = "platform-${platform.id}",
                            target = CocoonTileTarget.Platform(platform.id),
                            title = platform.name,
                            titleRes = null,
                            kindRes = R.string.cocoon_kind_folder,
                            glyph = CocoonGlyph.PLATFORM,
                            platformSlug = platform.slug,
                            colorIndex = CocoonFolderPalette.indexFor(platform.slug),
                        )
                    }
                platformsLoaded = true
                publish()
            }
        }
        refreshCollections()
        viewModelScope.launch {
            kotlinx.coroutines.delay(BOARD_LOAD_WAIT_MS)
            loadWaitOver = true
            publish()
        }
        viewModelScope.launch {
            // The dock is the same app list Argosy's home dock uses, owned by the dual-screen manager.
            com.nendo.argosy.DualScreenManagerHolder.instanceFlow.collectLatest { manager ->
                manager?.homeAppsFlow?.collect { apps ->
                    _state.update { current ->
                        current.copy(
                            dockApps = apps,
                            dockIndex = current.dockIndex.coerceIn(0, COCOON_DOCK_SIZE - 1)
                        )
                    }
                }
            }
        }
    }

    fun launchDockApp(index: Int) {
        val packageName = CocoonDockSlots.arrange(_state.value.dockApps).getOrNull(index) ?: return
        launchPackage(packageName)
    }

    fun launchPackage(packageName: String) {
        val intent = appsRepository.getLaunchIntent(packageName) ?: return
        CocoonAppStore.onLaunched(packageName)
        viewModelScope.launch {
            val prefs = preferencesRepository.preferences.first()
            val options = displayAffinityHelper.getAppLaunchOptions(
                preferredScreenKey = prefs.appDisplayTargets[packageName],
                rolesSwapped = sessionStateStore.isRolesSwapped(),
                occupiedDisplayId = com.nendo.argosy.DualScreenManagerHolder.instance?.emulatorDisplayId,
                defaultTarget = prefs.appsDisplayTarget
            )
            _events.emit(CocoonHomeEvent.LaunchApp(intent, options))
        }
    }

    fun focusDock(index: Int) {
        _state.update { it.copy(inDock = true, dockIndex = index.coerceIn(0, COCOON_DOCK_SIZE - 1)) }
    }

    fun closeDockMenu() { _state.update { it.copy(dockMenuOpen = false) } }

    /** Y ▸ Edit App: Cocoon's app sheet (name, icon, treat as game, screen). */
    fun openAppEdit(packageName: String) {
        _state.update { it.copy(appEditPackage = packageName, dockMenuOpen = false, tileMenuOpen = false) }
    }
    fun closePlatformJingle() { _state.update { it.copy(jingleForPlatform = null) } }

    fun closeAppEdit() {
        _state.update { it.copy(appEditPackage = null) }
        rebuildHomeTiles()
    }

    /** The screen an app opens on ("" for the default). */
    fun setAppScreen(packageName: String, screenKey: String?) {
        viewModelScope.launch { runCatching { preferencesRepository.setAppDisplayTarget(packageName, screenKey) } }
    }

    suspend fun appScreen(packageName: String): String? =
        runCatching { preferencesRepository.preferences.first().appDisplayTargets[packageName] }.getOrNull()

    /** Opens the app picker. [replacing] is the app being swapped out by Edit App, or null to fill an empty slot. */
    fun openAddApp(replacing: String?, toHome: Boolean = false) {
        _state.update {
            it.copy(
                addAppOpen = true, addAppReplacing = replacing, addAppIndex = 0, dockMenuOpen = false,
                addAppToHome = toHome, startMenuOpen = false
            )
        }
        viewModelScope.launch {
            val docked = if (toHome) {
                _state.value.tiles.mapNotNull { (it.target as? CocoonTileTarget.App)?.packageName }.toSet()
            } else {
                _state.value.dockApps.toSet()
            }
            val apps = runCatching { appsRepository.getInstalledApps(includeSystemApps = true) }.getOrDefault(emptyList())
                .filter { it.packageName != context.packageName && it.packageName !in docked }
                .map { CocoonPickApp(it.packageName, it.label) }
            _state.update { it.copy(addApps = apps, addAppIndex = 0) }
        }
    }

    fun closeAddApp() { _state.update { it.copy(addAppOpen = false, addAppReplacing = null, addAppToHome = false) } }
    fun focusAddApp(index: Int) { _state.update { it.copy(addAppIndex = index) } }

    /** Pins the picked app; for Edit App it takes the old app's place in the dock. */
    fun pickAddApp(index: Int = _state.value.addAppIndex) {
        val state = _state.value
        val picked = state.addApps.getOrNull(index)?.packageName ?: return
        if (state.addAppToHome) {
            closeAddApp()
            addHomeItem("app", picked)
            return
        }
        val replacing = state.addAppReplacing
        val before = state.dockApps
        // the slot the cursor was on when the picker opened: the new app goes exactly there
        if (state.inDock && state.dockIndex in 1..COCOON_DOCK_SLOTS) {
            CocoonDockSlots.place(picked, state.dockIndex - 1)
        }
        closeAddApp()
        viewModelScope.launch {
            runCatching {
                preferencesRepository.toggleDockPin(picked)
                if (replacing != null && replacing in before) {
                    preferencesRepository.toggleDockPin(replacing)
                    val expected = (before - replacing) + picked
                    val order = before.map { if (it == replacing) picked else it }
                    preferencesRepository.reorderDockPins(expected, order)
                }
            }
        }
    }
    fun focusDockMenu(index: Int) { _state.update { it.copy(dockMenuIndex = index) } }

    /** The app under the dock cursor, if that slot holds one. */
    private fun dockApp(): String? {
        val state = _state.value
        if (!state.inDock) return null
        return (cocoonDockTarget(state.dockIndex, state.dockApps) as? CocoonDockTarget.App)?.packageName
    }

    private fun runDockMenuRow(row: CocoonDockMenuRow, onEditApp: () -> Unit) {
        val packageName = dockApp()
        closeDockMenu()
        if (packageName == null) return
        when (row) {
            CocoonDockMenuRow.LAUNCH -> launchDockApp(_state.value.dockIndex - 1)
            CocoonDockMenuRow.EDIT -> openAppEdit(packageName)
            // Change App swaps the app in this slot for another one.
            CocoonDockMenuRow.CHANGE -> openAddApp(replacing = packageName)
            CocoonDockMenuRow.INFO -> viewModelScope.launch {
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:$packageName")
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                _events.emit(CocoonHomeEvent.LaunchApp(intent, null))
            }
            // Only the dock pin is removed; the app itself is untouched.
            CocoonDockMenuRow.CLEAR -> viewModelScope.launch {
                runCatching { preferencesRepository.toggleDockPin(packageName) }
            }
        }
    }

    // ---- Start menu ----

    fun openStartMenu(fromEmptySlot: Boolean = false) {
        _state.update {
            it.copy(
                startMenuOpen = true, startMenuIndex = 0, tileMenuOpen = false, dockMenuOpen = false,
                // Start ▸ is now just the "New" list (reached from Y or A on an empty space)
                startNewOpen = true,
                pendingSlot = if (fromEmptySlot) it.focusedIndex else null
            )
        }
    }

    private fun gapTile(key: String) = CocoonTile(
        key = key,
        target = CocoonTileTarget.Gap,
        title = null,
        titleRes = null,
        kindRes = R.string.cocoon_kind_shortcut,
        glyph = CocoonGlyph.FOLDER,
        platformSlug = null,
        colorIndex = 0,
    )

    /** Moves a tile into board slot [slot], filling any distance with empty spaces, as Cocoon does. */
    private fun placeAt(key: String, slot: Int) {
        // the board as it looks without the new tile
        val base = _state.value.tiles.filter { it.key != key && !it.key.startsWith("cover:$key:") }
        val logical = base.filter { it.isReal() }.map { it.key }.toMutableList()
        if (slot >= base.size) {
            repeat(slot - base.size) { logical += "gap:" + java.util.UUID.randomUUID().toString().take(6) }
            logical += key
        } else {
            val at = base[slot]
            if (at.key.startsWith("gap:")) {
                logical[logical.indexOf(at.key)] = key
            } else {
                // before the first real tile at or after that slot
                val next = base.drop(slot).firstOrNull { it.isReal() }?.key
                val index = next?.let { logical.indexOf(it) }?.takeIf { it >= 0 } ?: logical.size
                logical.add(index, key)
            }
        }
        boardPrefs.edit().putString("order", logical.joinToString("\n")).apply()
        publish()
    }
    fun closeStartMenu() { _state.update { it.copy(startMenuOpen = false) } }
    fun cancelStartMenu() { _state.update { it.copy(startMenuOpen = false, choice = null, pendingSlot = null) } }
    fun focusStartMenu(index: Int) { _state.update { it.copy(startMenuIndex = index) } }
    fun openChoice(choice: CocoonChoice) { _state.update { it.copy(choice = choice, choiceIndex = 0, startMenuOpen = false) } }
    fun closeChoice() { _state.update { it.copy(choice = null) } }
    fun focusChoice(index: Int) { _state.update { it.copy(choiceIndex = index) } }
    fun closeImagePicker() { _state.update { it.copy(pickingImage = false) } }
    fun closeScreenshots() { _state.update { it.copy(screenshotsOpen = false) } }

    fun choiceCount(choice: CocoonChoice): Int = when (choice) {
        CocoonChoice.WIDGETS -> CocoonWidgetKind.entries.size
        CocoonChoice.PODS -> CocoonPodKind.entries.size
        CocoonChoice.SMART -> CocoonSmartKind.entries.size
        CocoonChoice.MOVE -> moveTargets().size
    }

    /** A in the second step of Start ▸ Add Widget / Add Pod. */
    fun pickChoice(index: Int = _state.value.choiceIndex) {
        val choice = _state.value.choice ?: return
        closeChoice()
        when (choice) {
            CocoonChoice.WIDGETS -> {
                val kind = CocoonWidgetKind.entries.getOrNull(index) ?: return
                if (kind == CocoonWidgetKind.IMAGE_PIN) {
                    _state.update { it.copy(pickingImage = true) }
                } else {
                    addHomeItem("widget:" + kind.name, null)
                }
            }
            CocoonChoice.PODS -> {
                val kind = CocoonPodKind.entries.getOrNull(index) ?: return
                addHomeItem("pod", kind.name)
            }
            CocoonChoice.SMART -> {
                val kind = CocoonSmartKind.entries.getOrNull(index) ?: return
                addHomeItem("smart", kind.name)
            }
            CocoonChoice.MOVE -> {
                val targets = moveTargets()
                if (index in targets.indices) moveInto(targets[index].first)
            }
        }
    }

    fun pinImage(path: String) {
        closeImagePicker()
        addHomeItem("widget:" + CocoonWidgetKind.IMAGE_PIN.name, path)
    }

    /** Opens a Home shortcut: apps launch, Pods open, game widgets open their game. */
    fun openHomeTile(tile: CocoonTile) {
        when (val target = tile.target) {
            is CocoonTileTarget.App -> launchPackage(target.packageName)
            is CocoonTileTarget.Pod -> when (target.kind) {
                CocoonPodKind.LOG -> CocoonPods.openLog()
                CocoonPodKind.SCREENSHOTS -> _state.update { it.copy(screenshotsOpen = true) }
            }
            is CocoonTileTarget.Widget -> {
                if (target.kind == CocoonWidgetKind.MUSIC) {
                    runCatching {
                        val music = dagger.hilt.android.EntryPointAccessors.fromApplication(
                            context.applicationContext, CocoonJingleEntryPoint::class.java
                        ).ambientAudioManager()
                        if (music.playback.value.isPlaying) music.pause() else music.play()
                    }
                    return
                }
                val gameId = when (target.kind) {
                    CocoonWidgetKind.RECENT, CocoonWidgetKind.LAST_PLAYED -> CocoonWidgetData.recent.value.firstOrNull()?.id
                    CocoonWidgetKind.RANDOM -> CocoonWidgetData.random.value?.id
                    else -> null
                } ?: return
                viewModelScope.launch {
                    _events.emit(CocoonHomeEvent.Navigate(com.nendo.argosy.ui.navigation.Screen.GameDetail.createRoute(gameId)))
                }
                // a new pick for next time
                if (target.kind == CocoonWidgetKind.RANDOM) refreshWidgets()
            }
            is CocoonTileTarget.Group -> openGroup(target.id)
            is CocoonTileTarget.Game -> viewModelScope.launch {
                _events.emit(CocoonHomeEvent.Navigate(com.nendo.argosy.ui.navigation.Screen.GameDetail.createRoute(target.gameId)))
            }
            is CocoonTileTarget.Smart -> viewModelScope.launch {
                val filters = when (target.kind) {
                    CocoonSmartKind.FAVORITES -> com.nendo.argosy.domain.model.LibraryLinkFilters(
                        source = com.nendo.argosy.data.model.SourceFilter.FAVORITES
                    )
                    CocoonSmartKind.RECENT -> com.nendo.argosy.domain.model.LibraryLinkFilters(
                        sort = com.nendo.argosy.data.model.ActiveSort(com.nendo.argosy.data.model.SortOption.LAST_PLAYED)
                    )
                    CocoonSmartKind.MOST_PLAYED -> com.nendo.argosy.domain.model.LibraryLinkFilters(
                        sort = com.nendo.argosy.data.model.ActiveSort(com.nendo.argosy.data.model.SortOption.PLAY_TIME)
                    )
                    CocoonSmartKind.NEWLY_ADDED -> com.nendo.argosy.domain.model.LibraryLinkFilters(
                        sort = com.nendo.argosy.data.model.ActiveSort(com.nendo.argosy.data.model.SortOption.RECENTLY_ADDED)
                    )
                }
                _events.emit(
                    CocoonHomeEvent.Navigate(
                        com.nendo.argosy.ui.navigation.Screen.Library.createRoute(
                            filters = com.nendo.argosy.ui.screens.library.LibraryFilterArgs.encode(filters)
                        )
                    )
                )
            }
            else -> Unit
        }
    }

    fun openTileMenu() { _state.update { it.copy(tileMenuOpen = true, tileMenuIndex = 0, deleteArmed = false) } }
    fun closeTileMenu() { _state.update { it.copy(tileMenuOpen = false, deleteArmed = false) } }
    fun focusTileMenu(index: Int) {
        _state.update { it.copy(tileMenuIndex = index, deleteArmed = it.deleteArmed && it.tileMenuIndex == index) }
    }

    /** Deletes a collection folder (and its RomM collection when synced); its games stay put. */
    private fun deleteFolder(tile: CocoonTile) {
        val id = (tile.target as? CocoonTileTarget.Collection)?.collectionId ?: return
        viewModelScope.launch {
            runCatching { romMRepository.deleteCollectionWithSync(id) }
            refreshCollections()
        }
    }

    fun refreshCollections() {
        if (CocoonSuspendTest.resumeInProgress.value) return
        refreshWidgets()
        // a game's Y menu may have added it to Home meanwhile
        rebuildHomeTiles()
        viewModelScope.launch {
            val collections = runCatching { collectionRepository.getAllCollections() }.getOrDefault(emptyList())
            collectionTiles = collections
                .filter { it.type == CollectionType.REGULAR || it.type == CollectionType.SMART }
                .map { collection ->
                    CocoonTile(
                        key = "collection-${collection.id}",
                        target = CocoonTileTarget.Collection(collection.id),
                        title = collection.name,
                        titleRes = null,
                        kindRes = R.string.cocoon_kind_collection,
                        glyph = CocoonGlyph.COLLECTION,
                        platformSlug = null,
                        colorIndex = CocoonFolderPalette.indexFor("collection-${collection.name}"),
                    )
                }
            collectionsLoaded = true
            publish()
        }
    }

    private fun publish() {
        if (!allLoaded || CocoonSuspendTest.resumeInProgress.value) return
        val natural = (platformTiles + collectionTiles + builtInTiles() + homeTiles).map { applyLook(it) }
        // Home folders: on Home, everything not inside one; inside one, just its members.
        val grouped = allGroupMembers()
        val current = groupPath.lastOrNull()
        val order = if (current == null) savedOrder() else members(current)
        val visible = if (current == null) natural.filter { it.key !in grouped }
            else natural.filter { it.key in order }
        // The saved order, with any empty spaces ("gap:…") kept where they were; new tiles go last.
        val byKey = LinkedHashMap<String, CocoonTile>().apply { visible.forEach { put(it.key, it) } }
        val ordered = mutableListOf<CocoonTile>()
        order.forEach { key ->
            if (key.startsWith("gap:")) ordered += gapTile(key) else byKey.remove(key)?.let { ordered += it }
        }
        ordered += byKey.values
        while (ordered.lastOrNull()?.target is CocoonTileTarget.Gap) ordered.removeAt(ordered.lastIndex)
        val tiles: List<CocoonTile> = layout(ordered)
        if (current == null) CocoonBoardCache.tiles = tiles
        if (_state.value.tiles == tiles) {
            if (_state.value.isLoading) _state.update { it.copy(isLoading = false) }
            return
        }
        _state.update { current ->
            // Until you move, the cursor isn't "yours" yet: folders arrive in stages at start-up
            // (Android Games first), so it goes to the first folder, or back to the one you
            // opened, rather than sticking to whatever tile appeared first.
            val keepKey = if (focusSettled) current.focusedTile?.key
                else if (groupPath.isEmpty()) CocoonHomeStore.lastFocusKey else null
            val firstReal = tiles.indexOfFirst { it.target !is CocoonTileTarget.Gap && it.target !is CocoonTileTarget.Covered }
                .coerceAtLeast(0)
            val index = tiles.indexOfFirst { it.key == keepKey }.takeIf { it >= 0 }
                ?: if (focusSettled) current.focusedIndex.coerceIn(0, (current.copy(tiles = tiles).slotCount - 1).coerceAtLeast(0))
                else firstReal
            current.copy(tiles = tiles, focusedIndex = index, isLoading = false, groupTitles = groupTitles())
        }
        // The folder under the cursor at start-up (or on return) gets its covers warmed too.
        prefetchCovers(_state.value.focusedTile)
    }

    // ---- Folder appearance (Y ▸ Appearance) ----

    fun lookFor(key: String): CocoonFolderLook =
        CocoonFolderLook.decode(boardPrefs.getString("look_$key", null)) ?: CocoonFolderLook()

    fun setLook(key: String, look: CocoonFolderLook?) {
        boardPrefs.edit().apply {
            if (look == null || look == CocoonFolderLook()) remove("look_$key") else putString("look_$key", look.encode())
        }.apply()
        publish()
    }

    private fun applyLook(tile: CocoonTile): CocoonTile {
        val look = CocoonFolderLook.decode(boardPrefs.getString("look_${tile.key}", null)) ?: return tile
        return tile.copy(
            colorIndex = look.color ?: tile.colorIndex,
            glyph = if (look.image != null || look.symbol != null) CocoonGlyph.CUSTOM else tile.glyph,
            platformSlug = when {
                look.image != null -> "image:" + look.image
                look.symbol != null -> "symbol:" + look.symbol
                else -> tile.platformSlug
            },
        )
    }

    fun openAppearance() {
        val key = _state.value.focusedTile?.key ?: return
        _state.update { it.copy(appearanceKey = key, tileMenuOpen = false) }
    }

    fun closeAppearance() { _state.update { it.copy(appearanceKey = null) } }

    fun setFocus(index: Int) {
        val before = _state.value.focusedTile?.key
        _state.update { it.copy(inDock = false, focusedIndex = index.coerceIn(0, (it.slotCount - 1).coerceAtLeast(0))) }
        val now = _state.value.focusedTile
        focusSettled = true
        rememberFocus()
        if (now?.key != before) prefetchCovers(now)
    }

    /** The board slot one step from [index] on the column-major board, or null at an edge. */
    private fun neighbour(index: Int, size: Int, dRow: Int, dCol: Int): Int? {
        if (size == 0) return null
        val row = index % COCOON_BOARD_ROWS
        return when {
            dRow < 0 -> if (row > 0) index - 1 else null
            dRow > 0 -> if (row < COCOON_BOARD_ROWS - 1 && index + 1 < size) index + 1 else null
            dCol < 0 -> if (index - COCOON_BOARD_ROWS >= 0) index - COCOON_BOARD_ROWS else null
            dCol > 0 -> {
                val next = index + COCOON_BOARD_ROWS
                when {
                    next < size -> next
                    (index / COCOON_BOARD_ROWS) < ((size - 1) / COCOON_BOARD_ROWS) -> size - 1
                    else -> null
                }
            }
            else -> null
        }
    }

    fun openNewFolder() { _state.update { it.copy(newFolderOpen = true, tileMenuOpen = false) } }
    fun closeNewFolder() { _state.update { it.copy(newFolderOpen = false) } }

    /** Creates the folder as an Argosy collection (synced to RomM when connected) and lands on it. */
    fun createFolder(name: String) {
        closeNewFolder()
        val slot = _state.value.pendingSlot
        _state.update { it.copy(pendingSlot = null) }
        viewModelScope.launch {
            runCatching { romMRepository.createCollectionWithSync(name) }
            refreshCollections()
            kotlinx.coroutines.delay(300)
            val tile = _state.value.tiles.lastOrNull { it.title == name && it.target is CocoonTileTarget.Collection }
            if (tile != null && slot != null) placeAt(tile.key, slot)
            val index = _state.value.tiles.indexOfFirst { it.key == tile?.key }
            if (index >= 0) setFocus(index)
        }
    }

    fun startEditGrid() {
        _state.update { it.copy(editingGrid = true, tileMenuOpen = false, inDock = false) }
    }

    fun finishEditGrid() {
        if (!_state.value.editingGrid) return
        saveOrder(_state.value.tiles)
        _state.update { it.copy(editingGrid = false) }
    }

    /** Edit Grid: carries the lifted tile one slot over, the tiles in between shifting along. */
    private fun moveLifted(dRow: Int, dCol: Int): Boolean {
        val state = _state.value
        val lifted = state.focusedTile ?: return false
        val logical = state.tiles.filter { it.isReal() }.toMutableList()
        val from = logical.indexOfFirst { it.key == lifted.key }
        if (from < 0) return false
        val to = (from + dRow + dCol * COCOON_BOARD_ROWS).coerceIn(0, logical.lastIndex)
        if (to == from) return false
        logical.add(to, logical.removeAt(from))
        val tiles = layout(logical)
        _state.update { it.copy(tiles = tiles, focusedIndex = tiles.indexOfFirst { t -> t.key == lifted.key }.coerceAtLeast(0)) }
        return true
    }

    /** Moves focus on the column-major board; answers false at an edge so the press can be refused. */
    /** True once you've moved the cursor yourself. */
    private var focusSettled = false

    /** Remembers the tile under the cursor (on Home itself) for when Home comes back. */
    private fun rememberFocus() {
        if (groupPath.isEmpty() && !_state.value.inDock) {
            _state.value.focusedTile?.key?.let { CocoonHomeStore.lastFocusKey = it }
        }
    }

    private fun move(dRow: Int, dCol: Int): Boolean {
        focusSettled = true
        val state = _state.value
        val dockBelow = CocoonHomeStore.dockBottom
        if (state.inDock) {
            return when {
                (if (dockBelow) dRow < 0 else dRow > 0) -> { _state.update { it.copy(inDock = false) }; state.tiles.isNotEmpty() }
                dCol != 0 -> {
                    val next = state.dockIndex + dCol
                    if (next in 0 until state.dockSize) { _state.update { it.copy(dockIndex = next) }; true } else false
                }
                else -> false
            }
        }
        val lastRow = state.focusedIndex % COCOON_BOARD_ROWS == COCOON_BOARD_ROWS - 1 || state.focusedIndex + 1 >= state.slotCount
        if (dockBelow && dRow > 0 && lastRow) {
            // the dock sits under the board: down from the bottom row goes into it
            val column = state.focusedIndex / COCOON_BOARD_ROWS
            _state.update { it.copy(inDock = true, dockIndex = column.coerceIn(0, it.dockSize - 1)) }
            return true
        }
        if (!dockBelow && dRow < 0 && state.focusedIndex % COCOON_BOARD_ROWS == 0) {
            // Up from the top row lifts focus into the dock, onto the slot nearest the column.
            val column = state.focusedIndex / COCOON_BOARD_ROWS
            _state.update { it.copy(inDock = true, dockIndex = column.coerceIn(0, it.dockSize - 1)) }
            return true
        }
        val size = state.slotCount
        if (size == 0) return false
        // From a big widget, step out past the slots it covers; landing on a covered slot of
        // another widget lands on that widget.
        val ownerKey = state.focusedTile?.key
        var index = state.focusedIndex
        var target: Int
        while (true) {
            target = step(index, size, dRow, dCol) ?: return false
            val covered = state.tiles.getOrNull(target)?.target as? CocoonTileTarget.Covered ?: break
            if (covered.ownerKey != ownerKey) {
                target = state.tiles.indexOfFirst { it.key == covered.ownerKey }.takeIf { it >= 0 } ?: target
                break
            }
            index = target
        }
        setFocus(target)
        return true
    }

    private fun step(index: Int, size: Int, dRow: Int, dCol: Int): Int? {
        val row = index % COCOON_BOARD_ROWS
        return when {
            dRow < 0 -> if (row > 0) index - 1 else null
            dRow > 0 -> if (row < COCOON_BOARD_ROWS - 1 && index + 1 < size) index + 1 else null
            dCol < 0 -> if (index - COCOON_BOARD_ROWS >= 0) index - COCOON_BOARD_ROWS else null
            dCol > 0 -> {
                val next = index + COCOON_BOARD_ROWS
                when {
                    next < size -> next
                    // the last column may be short: land on its last tile
                    (index / COCOON_BOARD_ROWS) < ((size - 1) / COCOON_BOARD_ROWS) -> size - 1
                    else -> null
                }
            }
            else -> null
        }
    }

    fun createInputHandler(
        onOpen: (CocoonTile) -> Unit,
        onOpenSettings: () -> Unit,
        onUseClassicHome: () -> Unit,
        onOpenAllApps: () -> Unit,
        onEditTile: (CocoonTile) -> Unit,
    ): InputHandler = object : InputHandler {
        private fun menuStep(delta: Int): InputResult {
            val state = _state.value
            val next = state.tileMenuIndex + delta
            if (next !in state.tileMenuRows.indices) return InputResult.handled(com.nendo.argosy.core.input.SoundType.BOUNDARY)
            focusTileMenu(next)
            return InputResult.HANDLED
        }

        private fun runMenuRow(row: CocoonTileMenuRow) {
            val tile = _state.value.focusedTile ?: return
            if (row == CocoonTileMenuRow.DELETE_FOLDER && !_state.value.deleteArmed) {
                _state.update { it.copy(deleteArmed = true) }
                return
            }
            closeTileMenu()
            when (row) {
                CocoonTileMenuRow.OPEN -> onOpen(tile)
                CocoonTileMenuRow.EDIT -> onEditTile(tile)
                CocoonTileMenuRow.EDIT_GRID -> startEditGrid()
                CocoonTileMenuRow.APPEARANCE -> openAppearance()
                CocoonTileMenuRow.EDIT_APP -> (tile.target as? CocoonTileTarget.App)?.let { openAppEdit(it.packageName) }
                CocoonTileMenuRow.SIZE -> cycleWidgetSize(tile)
                CocoonTileMenuRow.MOVE -> startMove(tile)
                CocoonTileMenuRow.JINGLE -> platformTiles.firstOrNull { it.key == tile.key }?.platformSlug
                    ?.let { slug -> _state.update { it.copy(jingleForPlatform = slug) } }
                CocoonTileMenuRow.NEW_FOLDER -> openNewFolder()
                CocoonTileMenuRow.NEW -> openStartMenu()
                CocoonTileMenuRow.SEARCH -> _state.update { it.copy(searchOpen = true) }
                CocoonTileMenuRow.SETTINGS -> onOpenSettings()
                CocoonTileMenuRow.DELETE_FOLDER -> deleteFolder(tile)
                CocoonTileMenuRow.REMOVE -> removeHomeTile(tile)
            }
        }

        private val editing get() = _state.value.editingGrid
        private fun lift(dRow: Int, dCol: Int): InputResult =
            if (moveLifted(dRow, dCol)) InputResult.HANDLED else InputResult.handled(com.nendo.argosy.core.input.SoundType.BOUNDARY)
        private fun done(): InputResult {
            finishEditGrid()
            return InputResult.handled(com.nendo.argosy.core.input.SoundType.SELECT)
        }

        private val dockMenu get() = _state.value.dockMenuOpen
        private val adding get() = _state.value.addAppOpen
        private val starting get() = _state.value.startMenuOpen
        private val choosing get() = _state.value.choice != null

        private fun startStep(delta: Int): InputResult {
            val next = _state.value.startMenuIndex + delta
            if (next !in cocoonStartRows(_state.value.startNewOpen).indices) return InputResult.handled(com.nendo.argosy.core.input.SoundType.BOUNDARY)
            focusStartMenu(next)
            return InputResult.HANDLED
        }

        private fun choiceStep(delta: Int): InputResult {
            val choice = _state.value.choice ?: return InputResult.HANDLED
            val next = _state.value.choiceIndex + delta
            if (next !in 0 until choiceCount(choice)) return InputResult.handled(com.nendo.argosy.core.input.SoundType.BOUNDARY)
            focusChoice(next)
            return InputResult.HANDLED
        }

        private fun runStartRow(row: CocoonStartRow) {
            if (row == CocoonStartRow.NEW) {
                _state.update { it.copy(startNewOpen = true, startMenuIndex = 0) }
                return
            }
            closeStartMenu()
            when (row) {
                CocoonStartRow.NEW_FOLDER -> openNewFolder()
                CocoonStartRow.NEW_HOME_FOLDER -> openNameGroup()
                CocoonStartRow.SEARCH -> _state.update { it.copy(searchOpen = true) }
                CocoonStartRow.ADD_APP -> openAddApp(replacing = null, toHome = true)
                CocoonStartRow.ADD_WIDGET -> openChoice(CocoonChoice.WIDGETS)
                CocoonStartRow.ADD_POD -> openChoice(CocoonChoice.PODS)
                CocoonStartRow.ADD_SMART -> openChoice(CocoonChoice.SMART)
                CocoonStartRow.NEW -> Unit
                CocoonStartRow.EDIT_GRID -> startEditGrid()
                CocoonStartRow.SETTINGS -> onOpenSettings()
                // Argosy's own side menu (library, downloads, social…) stays one press away.
                CocoonStartRow.MORE -> com.nendo.argosy.DualScreenManagerHolder.instance
                    ?.onOpenOverlayFromCompanion(com.nendo.argosy.DualScreenManager.OVERLAY_MENU)
            }
        }

        private fun addStep(delta: Int): InputResult {
            val state = _state.value
            val next = state.addAppIndex + delta
            if (next !in state.addApps.indices) return InputResult.handled(com.nendo.argosy.core.input.SoundType.BOUNDARY)
            // Left/right stop at the row's edges instead of wrapping onto the next row.
            if ((delta == 1 || delta == -1) && next / COCOON_PICK_COLUMNS != state.addAppIndex / COCOON_PICK_COLUMNS) {
                return InputResult.handled(com.nendo.argosy.core.input.SoundType.BOUNDARY)
            }
            focusAddApp(next)
            return InputResult.HANDLED
        }

        private fun dockMenuStep(delta: Int): InputResult {
            val next = _state.value.dockMenuIndex + delta
            if (next !in CocoonDockMenuRow.entries.indices) return InputResult.handled(com.nendo.argosy.core.input.SoundType.BOUNDARY)
            focusDockMenu(next)
            return InputResult.HANDLED
        }

        override fun onSecondaryAction(): InputResult {
            if (adding || starting || choosing) return InputResult.HANDLED
            if (editing) {
                // Cocoon: Y in Edit Grid resizes the lifted widget
                val lifted = _state.value.focusedTile
                if (lifted?.target is CocoonTileTarget.Widget) {
                    cycleWidgetSize(lifted)
                    return InputResult.HANDLED
                }
                return done()
            }
            if (dockMenu) {
                // Y again is the Edit App shortcut, as in Cocoon.
                runDockMenuRow(CocoonDockMenuRow.EDIT, onOpenAllApps)
                return InputResult.HANDLED
            }
            if (dockApp() != null) {
                _state.update { it.copy(dockMenuOpen = true, dockMenuIndex = 0) }
                return InputResult.handled(com.nendo.argosy.core.input.SoundType.OPEN_MODAL)
            }
            val state = _state.value
            if (state.tileMenuOpen) {
                // Y again follows the Edit row's shortcut, as in Cocoon.
                if (CocoonTileMenuRow.EDIT in state.tileMenuRows) runMenuRow(CocoonTileMenuRow.EDIT) else closeTileMenu()
                return InputResult.HANDLED
            }
            if (state.inDock) return InputResult.HANDLED
            if (state.focusedTile == null) {
                openStartMenu(fromEmptySlot = true)
                return InputResult.handled(com.nendo.argosy.core.input.SoundType.OPEN_MODAL)
            }
            openTileMenu()
            return InputResult.handled(com.nendo.argosy.core.input.SoundType.OPEN_MODAL)
        }

        override fun onBack(): InputResult {
            if (choosing) {
                // back to the Start menu, still aiming at the same empty space
                _state.update { it.copy(choice = null, startMenuOpen = true) }
                return InputResult.HANDLED
            }
            if (starting) {
                cancelStartMenu()
                return InputResult.HANDLED
            }
            if (adding) {
                closeAddApp()
                return InputResult.HANDLED
            }
            if (editing) return done()
            if (dockMenu) {
                closeDockMenu()
                return InputResult.HANDLED
            }
            if (_state.value.tileMenuOpen) {
                closeTileMenu()
                return InputResult.HANDLED
            }
            if (_state.value.inDock) {
                _state.update { it.copy(inDock = false) }
                return InputResult.HANDLED
            }
            if (leaveGroup()) return InputResult.handled(com.nendo.argosy.core.input.SoundType.BACK)
            return InputResult.HANDLED
        }

        // Edges answer with the boundary sound rather than falling through to Argosy's drawer.
        private fun step(dRow: Int, dCol: Int): InputResult =
            if (move(dRow, dCol)) { rememberFocus(); InputResult.HANDLED } else InputResult.handled(com.nendo.argosy.core.input.SoundType.BOUNDARY)

        override fun onUp(): InputResult = when {
            choosing -> choiceStep(-1)
            starting -> startStep(-1)
            adding -> addStep(-COCOON_PICK_COLUMNS)
            dockMenu -> dockMenuStep(-1)
            editing -> lift(-1, 0)
            _state.value.tileMenuOpen -> menuStep(-1)
            else -> step(-1, 0)
        }
        override fun onDown(): InputResult = when {
            choosing -> choiceStep(1)
            starting -> startStep(1)
            adding -> addStep(COCOON_PICK_COLUMNS)
            dockMenu -> dockMenuStep(1)
            editing -> lift(1, 0)
            _state.value.tileMenuOpen -> menuStep(1)
            else -> step(1, 0)
        }
        override fun onLeft(): InputResult = when {
            choosing || starting -> InputResult.HANDLED
            adding -> addStep(-1)
            dockMenu -> InputResult.HANDLED
            editing -> lift(0, -1)
            _state.value.tileMenuOpen -> InputResult.HANDLED
            else -> step(0, -1)
        }
        override fun onRight(): InputResult = when {
            choosing || starting -> InputResult.HANDLED
            adding -> addStep(1)
            dockMenu -> InputResult.HANDLED
            editing -> lift(0, 1)
            _state.value.tileMenuOpen -> InputResult.HANDLED
            else -> step(0, 1)
        }

        override fun onConfirm(): InputResult {
            if (choosing) {
                pickChoice()
                return InputResult.handled(com.nendo.argosy.core.input.SoundType.SELECT)
            }
            if (starting) {
                cocoonStartRows(_state.value.startNewOpen).getOrNull(_state.value.startMenuIndex)?.let { runStartRow(it) }
                return InputResult.handled(com.nendo.argosy.core.input.SoundType.SELECT)
            }
            if (adding) {
                pickAddApp()
                return InputResult.handled(com.nendo.argosy.core.input.SoundType.SELECT)
            }
            if (editing) return done()
            if (dockMenu) {
                runDockMenuRow(CocoonDockMenuRow.entries[_state.value.dockMenuIndex], onOpenAllApps)
                return InputResult.HANDLED
            }
            val state = _state.value
            if (state.tileMenuOpen) {
                state.tileMenuRows.getOrNull(state.tileMenuIndex)?.let { runMenuRow(it) }
                return InputResult.HANDLED
            }
            if (state.inDock) {
                when (cocoonDockTarget(state.dockIndex, state.dockApps)) {
                    CocoonDockTarget.AllApps -> onOpenAllApps()
                    CocoonDockTarget.AddApp -> openAddApp(replacing = null)
                    CocoonDockTarget.Pods -> com.nendo.argosy.ui.cocoon.CocoonPods.open()
                    is CocoonDockTarget.App -> launchDockApp(state.dockIndex - 1)
                }
                return InputResult.HANDLED
            }
            val tile = _state.value.focusedTile ?: run {
                // an empty space: Start ▸ New, to put something here
                openStartMenu(fromEmptySlot = true)
                return InputResult.handled(com.nendo.argosy.core.input.SoundType.OPEN_MODAL)
            }
            onOpen(tile)
            return InputResult.HANDLED
        }

        // Start opens Cocoon's Start menu (New ▸ …, Edit Grid, Settings); Start again closes it.
        override fun onMenu(): InputResult {
            if (editing) return done()
            if (starting || choosing) {
                cancelStartMenu()
                return InputResult.handled(com.nendo.argosy.core.input.SoundType.CLOSE_MODAL)
            }
            if (_state.value.tileMenuOpen) closeTileMenu()
            if (dockMenu) closeDockMenu()
            if (adding) closeAddApp()
            // Start opens Argosy's left drawer (library, downloads, settings…); the old Start
            // options now live in Y's menu.
            val dsm = com.nendo.argosy.DualScreenManagerHolder.instance ?: return InputResult.UNHANDLED
            dsm.onOpenOverlayFromCompanion(com.nendo.argosy.DualScreenManager.OVERLAY_MENU)
            return InputResult.handled(com.nendo.argosy.core.input.SoundType.OPEN_MODAL)
        }

        // Cocoon: X on Home swaps which screen shows Home and which shows the preview.
        override fun onContextMenu(): InputResult {
            val state = _state.value
            if (editing || adding || starting || choosing || dockMenu || state.tileMenuOpen) return InputResult.HANDLED
            val manager = com.nendo.argosy.DualScreenManagerHolder.instance ?: return InputResult.UNHANDLED
            manager.swapRoles()
            return InputResult.handled(com.nendo.argosy.core.input.SoundType.TOGGLE)
        }

        override fun onLeftStickClick(): InputResult {
            if (editing) return InputResult.HANDLED
            onUseClassicHome()
            return InputResult.HANDLED
        }

        // R3: Home switches between 2, 3 and 4 rows; the board reflows into the new shape.
        override fun onRightStickClick(): InputResult {
            val state = _state.value
            if (editing || starting || choosing || adding || dockMenu || state.tileMenuOpen) return InputResult.HANDLED
            CocoonHomeStore.setRows(context, if (CocoonHomeStore.rows >= 4) 2 else CocoonHomeStore.rows + 1)
            publish()
            return InputResult.handled(com.nendo.argosy.core.input.SoundType.SELECT)
        }
    }
}

/** Longest the board waits for all its tile sources before showing what it has. */
private const val BOARD_LOAD_WAIT_MS = 700L

/**
 * The last finished Home board, kept for the life of the app. A board rebuilt (coming back from
 * Settings, a screen swap) starts from it straight away instead of from an empty grid.
 */
object CocoonBoardCache {
    @Volatile var tiles: List<CocoonTile> = emptyList()
}
