from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one match, found {count}")
    p.write_text(text.replace(old, new, 1))


# Task 2: persistent A+B local-music access state.
model = Path("app/src/main/java/com/music/bitchord/data/local/LocalMusicAccess.kt")
model.parent.mkdir(parents=True, exist_ok=True)
if model.exists():
    raise SystemExit(f"{model}: expected new file but it already exists")
model.write_text('''package com.music.bitchord.data.local\n\ndata class LocalMusicAccessConfig(\n    val setupSeen: Boolean = false,\n    val allMusicEnabled: Boolean = false,\n    val treeUris: Set<String> = emptySet(),\n) {\n    fun markSetupSeen() = copy(setupSeen = true)\n    fun withAllMusic(enabled: Boolean) = copy(allMusicEnabled = enabled)\n    fun addTree(uri: String) = copy(treeUris = treeUris + uri)\n    fun removeTree(uri: String) = copy(treeUris = treeUris - uri)\n}\n''')

settings = "app/src/main/java/com/music/bitchord/data/settings/AppSettings.kt"
replace_once(
    settings,
    '''    val pinnedPlaylists = MutableStateFlow<List<String>>(emptyList())\n\n    /** How many playlists [pinnedPlaylists] can hold at once. */''',
    '''    val pinnedPlaylists = MutableStateFlow<List<String>>(emptyList())\n\n    /** Whether the optional Local Music first-run choice has already been shown. */\n    val localMusicSetupSeen = MutableStateFlow(false)\n\n    /** A: expose Android's MediaStore music library. */\n    val localAllMusicEnabled = MutableStateFlow(false)\n\n    /** B: Storage Access Framework tree URIs the user explicitly granted. */\n    val localMusicTreeUris = MutableStateFlow<Set<String>>(emptySet())\n\n    /** How many playlists [pinnedPlaylists] can hold at once. */''',
)

replace_once(
    settings,
    '''        replayGenres.value = prefs.getBoolean(KEY_REPLAY_GENRES, true)\n        pinnedPlaylists.value = readPinnedPlaylists()\n        discordToken.value = authStore.discordToken.orEmpty()''',
    '''        replayGenres.value = prefs.getBoolean(KEY_REPLAY_GENRES, true)\n        pinnedPlaylists.value = readPinnedPlaylists()\n        localMusicSetupSeen.value = prefs.getBoolean(KEY_LOCAL_MUSIC_SETUP_SEEN, false)\n        localAllMusicEnabled.value = prefs.getBoolean(KEY_LOCAL_ALL_MUSIC_ENABLED, false)\n        localMusicTreeUris.value = prefs.getStringSet(KEY_LOCAL_MUSIC_TREE_URIS, emptySet()).orEmpty().toSet()\n        discordToken.value = authStore.discordToken.orEmpty()''',
)

replace_once(
    settings,
    '''    fun togglePinnedPlaylist(browseId: String): Boolean {''',
    '''    fun setLocalMusicSetupSeen(value: Boolean) {\n        localMusicSetupSeen.value = value\n        prefs.edit().putBoolean(KEY_LOCAL_MUSIC_SETUP_SEEN, value).apply()\n    }\n\n    fun setLocalAllMusicEnabled(value: Boolean) {\n        localAllMusicEnabled.value = value\n        prefs.edit().putBoolean(KEY_LOCAL_ALL_MUSIC_ENABLED, value).apply()\n    }\n\n    fun addLocalMusicTreeUri(uri: String) {\n        if (uri.isBlank()) return\n        val updated = localMusicTreeUris.value + uri\n        localMusicTreeUris.value = updated\n        prefs.edit().putStringSet(KEY_LOCAL_MUSIC_TREE_URIS, updated).apply()\n    }\n\n    fun removeLocalMusicTreeUri(uri: String) {\n        val updated = localMusicTreeUris.value - uri\n        localMusicTreeUris.value = updated\n        prefs.edit().putStringSet(KEY_LOCAL_MUSIC_TREE_URIS, updated).apply()\n    }\n\n    fun togglePinnedPlaylist(browseId: String): Boolean {''',
)

replace_once(
    settings,
    '''    private const val KEY_REPLAY_GENRES = "replay_genres"\n    private const val KEY_PINNED_PLAYLISTS = "pinned_playlists"\n\n    private const val KEY_LASTFM_ENABLED = "lastfm_enabled"''',
    '''    private const val KEY_REPLAY_GENRES = "replay_genres"\n    private const val KEY_PINNED_PLAYLISTS = "pinned_playlists"\n    private const val KEY_LOCAL_MUSIC_SETUP_SEEN = "local_music_setup_seen"\n    private const val KEY_LOCAL_ALL_MUSIC_ENABLED = "local_all_music_enabled"\n    private const val KEY_LOCAL_MUSIC_TREE_URIS = "local_music_tree_uris"\n\n    private const val KEY_LASTFM_ENABLED = "lastfm_enabled"''',
)

Path("/tmp/tantov-commit-message").write_text("feat(local): persist music access choices\n")
