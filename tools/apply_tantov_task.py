from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one match, found {count}")
    p.write_text(text.replace(old, new, 1))


# Task 5: phone Local Music consumes the merged A+B catalog and exposes folders.
models = "app/src/main/java/com/music/bitchord/data/local/LocalMusicModels.kt"
replace_once(
    models,
    '''data class LocalMusicFolder(\n    val key: String,\n    val label: String,\n    val songs: List<Song>,\n)\n\ndata class LocalMusicCatalog(val tracks: List<LocalMusicTrack>) {''',
    '''data class LocalMusicFolder(\n    val key: String,\n    val label: String,\n    val songs: List<Song>,\n)\n\n/** Folder-tab filtering: match the visible leaf or its parent path. */\nfun filterLocalMusicFolders(\n    folders: List<LocalMusicFolder>,\n    query: String,\n): List<LocalMusicFolder> {\n    val q = query.trim()\n    if (q.isEmpty()) return folders\n    return folders.filter { folder ->\n        folder.label.contains(q, ignoreCase = true) ||\n            folder.key.contains(q, ignoreCase = true)\n    }\n}\n\ndata class LocalMusicCatalog(val tracks: List<LocalMusicTrack>) {''',
)

view_model = "app/src/main/java/com/music/bitchord/ui/MainViewModel.kt"
replace_once(
    view_model,
    '''import com.music.bitchord.data.LocalMediaRepository\nimport com.music.bitchord.data.LikeState''',
    '''import com.music.bitchord.data.LocalMediaRepository\nimport com.music.bitchord.data.local.LocalMusicFolder\nimport com.music.bitchord.data.LikeState''',
)
replace_once(
    view_model,
    '''    private val _detailStack = MutableStateFlow<List<DetailPage>>(emptyList())\n    val detailStack: StateFlow<List<DetailPage>> = _detailStack.asStateFlow()\n\n\n    /** Set once per launch if GitHub has a release newer than this build. */''',
    '''    private val _detailStack = MutableStateFlow<List<DetailPage>>(emptyList())\n    val detailStack: StateFlow<List<DetailPage>> = _detailStack.asStateFlow()\n\n    /** Folder groups from the same merged A+B catalog backing local:all. */\n    private val _localMusicFolders = MutableStateFlow<List<LocalMusicFolder>>(emptyList())\n    val localMusicFolders: StateFlow<List<LocalMusicFolder>> = _localMusicFolders.asStateFlow()\n\n\n    /** Set once per launch if GitHub has a release newer than this build. */''',
)

# The first local:all loader declares its context inside the branch.
replace_once(
    view_model,
    '''                browseId == "local:all" -> {\n                    val context = getApplication<Application>()\n                    if (!LocalMediaRepository.hasStoragePermission(context)) {\n                        UiState.Error("Storage permission required to view local audio files")\n                    } else {\n                        val songs = LocalMediaRepository.getLocalMusic(context)\n                        if (songs.isEmpty()) UiState.Error("No audio files found on device")\n                        else UiState.Success(songs)\n                    }\n                }''',
    '''                browseId == "local:all" -> mergedLocalMusicState()''',
)

# reloadLocalDetail already has a context in scope.
replace_once(
    view_model,
    '''                browseId == "local:all" -> {\n                    if (!LocalMediaRepository.hasStoragePermission(context)) {\n                        UiState.Error("Storage permission required to view local audio files")\n                    } else {\n                        val songs = LocalMediaRepository.getLocalMusic(context)\n                        if (songs.isEmpty()) UiState.Error("No audio files found on device")\n                        else UiState.Success(songs)\n                    }\n                }''',
    '''                browseId == "local:all" -> mergedLocalMusicState()''',
)

# collectSongs returns Result rather than UiState, but must consume the same catalog.
replace_once(
    view_model,
    '''                browseId == "local:all" -> runCatching {\n                    if (!LocalMediaRepository.hasStoragePermission(context)) {\n                        error("Storage permission required to read local audio files")\n                    }\n                    LocalMediaRepository.getLocalMusic(context)\n                        .ifEmpty { error("No audio files found on device") }\n                }''',
    '''                browseId == "local:all" -> runCatching { mergedLocalMusicSongs() }''',
)

replace_once(
    view_model,
    '''    fun reloadLocalDetail(browseId: String) {''',
    '''    /**\n     * Phone Local Music is always the merged repository catalog. Broad audio\n     * permission is only one source (A); selected SAF folders (B) keep working\n     * when A is disabled or denied.\n     */\n    private suspend fun mergedLocalMusicSongs(): List<Song> {\n        val context = getApplication<Application>()\n        val catalog = LocalMediaRepository.catalog(context)\n        _localMusicFolders.value = catalog.folders\n        if (catalog.songs.isNotEmpty()) return catalog.songs\n\n        val message = when {\n            AppSettings.localAllMusicEnabled.value &&\n                !LocalMediaRepository.hasStoragePermission(context) &&\n                AppSettings.localMusicTreeUris.value.isEmpty() ->\n                "Audio permission required to view all music on this phone"\n            !AppSettings.localAllMusicEnabled.value &&\n                AppSettings.localMusicTreeUris.value.isEmpty() ->\n                "Set up Local Music in Settings"\n            else -> "No audio files found in your Local Music sources"\n        }\n        error(message)\n    }\n\n    private suspend fun mergedLocalMusicState(): UiState<List<Song>> =\n        runCatching { mergedLocalMusicSongs() }.fold(\n            onSuccess = { UiState.Success(it) },\n            onFailure = { UiState.Error(it.message ?: "Couldn't load Local Music") },\n        )\n\n    fun reloadLocalDetail(browseId: String) {''',
)

main = "app/src/main/java/com/music/bitchord/MainActivity.kt"
replace_once(
    main,
    '''    val account by viewModel.account.collectAsStateWithLifecycle()\n    val localMusicSetupSeen by AppSettings.localMusicSetupSeen.collectAsStateWithLifecycle()''',
    '''    val account by viewModel.account.collectAsStateWithLifecycle()\n    val localMusicFolders by viewModel.localMusicFolders.collectAsStateWithLifecycle()\n    val localMusicSetupSeen by AppSettings.localMusicSetupSeen.collectAsStateWithLifecycle()''',
)
replace_once(
    main,
    '''                        LocalMusicScreen(\n                            songs = localSongs,\n                            collections = downloadCollections,''',
    '''                        LocalMusicScreen(\n                            songs = localSongs,\n                            folders = if (page.browseId == "local:all") localMusicFolders else null,\n                            collections = downloadCollections,''',
)

screen = Path("app/src/main/java/com/music/bitchord/ui/screens/LocalMusicScreen.kt")
text = screen.read_text()


def one(old: str, new: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{screen}: expected exactly one match, found {count}")
    text = text.replace(old, new, 1)


one(
    '''import androidx.compose.material.icons.rounded.Close\nimport androidx.compose.material.icons.rounded.LibraryMusic''',
    '''import androidx.compose.material.icons.rounded.Close\nimport androidx.compose.material.icons.rounded.Folder\nimport androidx.compose.material.icons.rounded.LibraryMusic''',
)
one(
    '''import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.getValue\nimport androidx.compose.runtime.mutableIntStateOf\nimport androidx.compose.runtime.mutableStateOf''',
    '''import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.getValue\nimport androidx.compose.runtime.mutableStateOf''',
)
one(
    '''import coil3.compose.AsyncImage\nimport com.music.bitchord.data.model.ROW_ART_PX''',
    '''import coil3.compose.AsyncImage\nimport com.music.bitchord.data.local.LocalMusicFolder\nimport com.music.bitchord.data.local.filterLocalMusicFolders\nimport com.music.bitchord.data.model.ROW_ART_PX''',
)
one(
    '''private const val LOCAL_TAB_SONGS = 0\nprivate const val LOCAL_TAB_ARTISTS = 1\nprivate const val LOCAL_TAB_ALBUMS = 2''',
    '''private enum class LocalTabPage {\n    SONGS, FOLDERS, ALBUMS, ARTISTS,\n}''',
)
one(
    ''' * Local Music folder view with three tabs: Songs (default), Artists, Albums.''',
    ''' * Local Music view uses Songs / Folders / Albums / Artists. Downloads keeps\n * its existing Songs / Artists / Albums tabs because it is not a source-folder browser.''',
)
one(
    '''fun LocalMusicScreen(\n    songs: List<Song>,\n    onSongClick:''',
    '''fun LocalMusicScreen(\n    songs: List<Song>,\n    /** Non-null only for Local Music; null keeps the Downloads tab set unchanged. */\n    folders: List<LocalMusicFolder>? = null,\n    onSongClick:''',
)
one(
    '''    // Which top-level tab is selected.\n    var selectedTab by rememberSaveable { mutableIntStateOf(LOCAL_TAB_SONGS) }''',
    '''    // Stored by name so Local Music can have four tabs while Downloads keeps three.\n    var selectedTabName by rememberSaveable { mutableStateOf(LocalTabPage.SONGS.name) }''',
)
one(
    '''    // Narrows whichever tab is showing — songs by title/artist/album, artists\n    // and albums by name. Not saved across process death: a filter left on a''',
    '''    // Narrows whichever tab is showing — songs by metadata, folders by leaf\n    // or path, and artists/albums by name. Not saved across process death: a filter left on a''',
)
one(
    '''    // When non-null, we are showing a drill-down list for that artist or album.''',
    '''    // When non-null, we are showing a drill-down list for a folder, album or artist.''',
)
one(
    '''    val barHeight = topBarHeight()\n\n    Column(modifier = modifier.fillMaxSize()) {''',
    '''    val barHeight = topBarHeight()\n\n    val availableTabs = if (folders == null) {\n        listOf(LocalTabPage.SONGS, LocalTabPage.ARTISTS, LocalTabPage.ALBUMS)\n    } else {\n        listOf(LocalTabPage.SONGS, LocalTabPage.FOLDERS, LocalTabPage.ALBUMS, LocalTabPage.ARTISTS)\n    }\n    val requestedTab = runCatching { LocalTabPage.valueOf(selectedTabName) }\n        .getOrDefault(LocalTabPage.SONGS)\n    val selectedTab = requestedTab.takeIf { it in availableTabs } ?: LocalTabPage.SONGS\n    val selectedTabIndex = availableTabs.indexOf(selectedTab).coerceAtLeast(0)\n\n    Column(modifier = modifier.fillMaxSize()) {''',
)

# Replace the complete fixed tab-row block.
tab_start = text.index("        // ── Tab row")
tab_end = text.index("        // ── Content", tab_start)
new_tabs = '''        // ── Tab row ──────────────────────────────────────────────────────────\n        TabRow(\n            selectedTabIndex = selectedTabIndex,\n            containerColor = MaterialTheme.colorScheme.background,\n            contentColor = MaterialTheme.colorScheme.primary,\n            indicator = { tabPositions ->\n                TabRowDefaults.SecondaryIndicator(\n                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),\n                    color = MaterialTheme.colorScheme.primary,\n                )\n            },\n        ) {\n            availableTabs.forEach { tab ->\n                LocalTab(\n                    icon = when (tab) {\n                        LocalTabPage.SONGS -> Icons.Rounded.MusicNote\n                        LocalTabPage.FOLDERS -> Icons.Rounded.Folder\n                        LocalTabPage.ALBUMS -> Icons.Rounded.Album\n                        LocalTabPage.ARTISTS -> Icons.Rounded.Person\n                    },\n                    label = when (tab) {\n                        LocalTabPage.SONGS -> "Songs"\n                        LocalTabPage.FOLDERS -> "Folders"\n                        LocalTabPage.ALBUMS -> "Albums"\n                        LocalTabPage.ARTISTS -> "Artists"\n                    },\n                    selected = selectedTab == tab,\n                    onClick = {\n                        selectedTabName = tab.name\n                        leaveDrillDown()\n                    },\n                )\n            }\n        }\n\n'''
text = text[:tab_start] + new_tabs + text[tab_end:]

one(
    '''            targetState = if (inDrillDown) "drill:$drillDownLabel" else "tab:$selectedTab",''',
    '''            targetState = if (inDrillDown) "drill:$drillDownLabel" else "tab:${selectedTab.name}",''',
)

# Replace all top-level tab-content branches; keep empty + drill-down handling above them.
branch_start = text.index('                key == "tab:$LOCAL_TAB_SONGS" -> {')
branch_end_marker = "\n            }\n        }\n    }\n}\n\n// ── Songs tab"
branch_end = text.index(branch_end_marker, branch_start)
new_branches = '''                key == "tab:${LocalTabPage.SONGS.name}" -> {\n                    val filteredSongs = remember(songs, searchQuery) {\n                        if (searchQuery.isBlank()) songs\n                        else songs.filter { it.matchesSearch(searchQuery) }\n                    }\n                    SongsTab(\n                        songs = filteredSongs,\n                        onSongClick = onSongClick,\n                        onSongLongPress = onSongLongPress,\n                        onSongSwipe = onSongSwipe,\n                        contentPadding = bodyContentPadding,\n                    )\n                }\n\n                key == "tab:${LocalTabPage.FOLDERS.name}" -> {\n                    val visibleFolders = remember(folders, searchQuery) {\n                        filterLocalMusicFolders(folders.orEmpty(), searchQuery)\n                    }\n                    FoldersTab(\n                        folders = visibleFolders,\n                        onFolderClick = { folder ->\n                            drillDownLabel = folder.label\n                            drillDownSongs = folder.songs\n                            drillDownArt = null\n                        },\n                        onFolderLongPress = onCollectionLongPress,\n                        contentPadding = bodyContentPadding,\n                    )\n                }\n\n                key == "tab:${LocalTabPage.ALBUMS.name}" -> {\n                    val albums = remember(songs, collections, searchQuery) {\n                        albumEntries(songs, collections).filter {\n                            searchQuery.isBlank() ||\n                                it.title.contains(searchQuery, ignoreCase = true) ||\n                                it.artist.contains(searchQuery, ignoreCase = true)\n                        }\n                    }\n                    AlbumsTab(\n                        albums = albums,\n                        onAlbumClick = { entry ->\n                            drillDownLabel = entry.title\n                            drillDownSongs = entry.songs\n                            drillDownArt = entry.thumbnailUrl\n                        },\n                        onAlbumLongPress = onCollectionLongPress,\n                        contentPadding = bodyContentPadding,\n                    )\n                }\n\n                else -> {\n                    val artists = remember(songs, searchQuery) {\n                        songs.groupBy { it.artist }\n                            .entries\n                            .filter { searchQuery.isBlank() || it.key.contains(searchQuery, ignoreCase = true) }\n                            .sortedBy { it.key.lowercase(Locale.ROOT) }\n                    }\n                    ArtistsTab(\n                        artists = artists,\n                        onArtistClick = { artist, artistSongs ->\n                            drillDownLabel = artist\n                            drillDownSongs = artistSongs\n                            drillDownArt = null\n                        },\n                        onArtistLongPress = onCollectionLongPress,\n                        contentPadding = bodyContentPadding,\n                    )\n                }'''
text = text[:branch_start] + new_branches + text[branch_end:]

# Add folder rows between Songs and Artists sections.
folder_marker = "// ── Artists tab ───────────────────────────────────────────────────────────────"
folder_at = text.index(folder_marker)
folder_code = '''// ── Folders tab ───────────────────────────────────────────────────────────────\n\n@Composable\nprivate fun FoldersTab(\n    folders: List<LocalMusicFolder>,\n    onFolderClick: (LocalMusicFolder) -> Unit,\n    onFolderLongPress: ((String, List<Song>) -> Unit)?,\n    contentPadding: PaddingValues,\n) {\n    val listState = rememberLazyListState()\n    LazyColumn(\n        state = listState,\n        modifier = Modifier.fillMaxSize(),\n        contentPadding = contentPadding,\n    ) {\n        item {\n            SectionHeader(\n                icon = Icons.Rounded.Folder,\n                title = "${folders.size} ${if (folders.size == 1) "folder" else "folders"}",\n            )\n        }\n        if (folders.isEmpty()) {\n            item { MessageState(message = "No music folders match this search") }\n        }\n        items(folders, key = { it.key }) { folder ->\n            FolderRow(\n                folder = folder,\n                onClick = { onFolderClick(folder) },\n                onLongPress = onFolderLongPress?.let { more ->\n                    { more(folder.label, folder.songs) }\n                },\n            )\n            HorizontalDivider(\n                modifier = Modifier.padding(start = ROW_DIVIDER_INSET),\n                thickness = 0.5.dp,\n                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),\n            )\n        }\n    }\n}\n\n@OptIn(ExperimentalFoundationApi::class)\n@Composable\nprivate fun FolderRow(\n    folder: LocalMusicFolder,\n    onClick: () -> Unit,\n    onLongPress: (() -> Unit)? = null,\n) {\n    val parent = folder.key.substringBeforeLast('/', "")\n        .takeIf { it.isNotBlank() && it != folder.label }\n    Row(\n        modifier = Modifier\n            .fillMaxWidth()\n            .combinedClickable(onClick = onClick, onLongClick = onLongPress)\n            .padding(horizontal = PAGE_GUTTER, vertical = 10.dp),\n        verticalAlignment = Alignment.CenterVertically,\n    ) {\n        Box(\n            modifier = Modifier\n                .size(48.dp)\n                .clip(RoundedCornerShape(10.dp))\n                .background(MaterialTheme.colorScheme.secondaryContainer),\n            contentAlignment = Alignment.Center,\n        ) {\n            Icon(\n                imageVector = Icons.Rounded.Folder,\n                contentDescription = null,\n                tint = MaterialTheme.colorScheme.onSecondaryContainer,\n                modifier = Modifier.size(27.dp),\n            )\n        }\n        Spacer(Modifier.width(14.dp))\n        Column(Modifier.weight(1f)) {\n            Text(\n                text = folder.label,\n                style = MaterialTheme.typography.titleMedium,\n                color = MaterialTheme.colorScheme.onBackground,\n                maxLines = 1,\n                overflow = TextOverflow.Ellipsis,\n            )\n            Text(\n                text = buildString {\n                    if (parent != null) append("$parent · ")\n                    append("${folder.songs.size} ${if (folder.songs.size == 1) "song" else "songs"}")\n                },\n                style = MaterialTheme.typography.bodySmall,\n                color = MaterialTheme.colorScheme.onSurfaceVariant,\n                maxLines = 1,\n                overflow = TextOverflow.Ellipsis,\n            )\n        }\n        Icon(\n            imageVector = Icons.Rounded.PlayArrow,\n            contentDescription = null,\n            tint = MaterialTheme.colorScheme.onSurfaceVariant,\n            modifier = Modifier.size(20.dp),\n        )\n    }\n}\n\n'''
text = text[:folder_at] + folder_code + text[folder_at:]
screen.write_text(text)

Path("/tmp/tantov-commit-message").write_text("feat(local): browse merged phone folders\n")
