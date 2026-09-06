from pathlib import Path
import sys


def read(path: str) -> str:
    return Path(path).read_text()


def write(path: str, text: str) -> None:
    p = Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text)


def replace_once(path: str, old: str, new: str) -> None:
    text = read(path)
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one match, found {count}")
    write(path, text.replace(old, new, 1))


def insert_before(path: str, marker: str, addition: str) -> None:
    replace_once(path, marker, addition + marker)


def task1_tests() -> None:
    catalog_test = "app/src/test/java/com/music/bitchord/AndroidAutoCatalogTest.kt"
    replace_once(
        catalog_test,
        '        var historyResult: Result<List<Song>> = Result.success(emptyList())\n',
        '        var historyResult: Result<List<Song>> = Result.success(emptyList())\n'
        '        var recentsResult: Result<List<Song>> = Result.success(emptyList())\n'
        '        var quickPicksResult: Result<List<Song>> = Result.success(emptyList())\n'
        '        var quickPicksExcludedIds: Set<String> = emptySet()\n',
    )
    replace_once(
        catalog_test,
        '        var historyCalls = 0\n',
        '        var historyCalls = 0\n'
        '        var recentsCalls = 0\n'
        '        var quickPicksCalls = 0\n',
    )
    replace_once(
        catalog_test,
        '''        override suspend fun history(): Result<List<Song>> {\n            historyCalls++\n            return historyResult\n        }\n\n''',
        '''        override suspend fun history(): Result<List<Song>> {\n            historyCalls++\n            return historyResult\n        }\n\n        override suspend fun recents(): Result<List<Song>> {\n            recentsCalls++\n            return recentsResult\n        }\n\n        override suspend fun quickPicks(excludeSongIds: Set<String>): Result<List<Song>> {\n            quickPicksCalls++\n            quickPicksExcludedIds = excludeSongIds\n            return quickPicksResult\n        }\n\n''',
    )
    insert_before(
        catalog_test,
        '    @Test\n    fun rootHasExpectedOrderAndBrowsableFlags()',
        '''    @Test\n    fun dataSourceContractExposesRecentsAndQuickPicks() = runBlocking {\n        val recent = song("recent-contract")\n        val pick = song("pick-contract")\n        val fake = FakeAutoDataSource().apply {\n            recentsResult = Result.success(listOf(recent))\n            quickPicksResult = Result.success(listOf(pick))\n        }\n        val source: AndroidAutoDataSource = fake\n\n        assertEquals(listOf(recent), source.recents().getOrThrow())\n        assertEquals(listOf(pick), source.quickPicks(setOf("excluded")).getOrThrow())\n        assertEquals(setOf("excluded"), fake.quickPicksExcludedIds)\n    }\n\n''',
    )

    for path in (
        "app/src/test/java/com/music/bitchord/AndroidAutoSearchTest.kt",
        "app/src/test/java/com/music/bitchord/AndroidAutoVoiceSearchTest.kt",
        "app/src/test/java/com/music/bitchord/AndroidAutoLocalMusicTest.kt",
    ):
        replace_once(
            path,
            '        override suspend fun history() = Result.success(emptyList<Song>())\n',
            '        override suspend fun history() = Result.success(emptyList<Song>())\n'
            '        override suspend fun recents() = Result.success(emptyList<Song>())\n'
            '        override suspend fun quickPicks(excludeSongIds: Set<String>) = Result.success(emptyList<Song>())\n',
        )


def task1_prod() -> None:
    data_source = "app/src/main/java/com/music/bitchord/playback/AndroidAutoDataSource.kt"
    replace_once(
        data_source,
        '    suspend fun history(): Result<List<Song>>\n',
        '    suspend fun history(): Result<List<Song>>\n'
        '    suspend fun recents(): Result<List<Song>>\n'
        '    suspend fun quickPicks(excludeSongIds: Set<String> = emptySet()): Result<List<Song>>\n',
    )
    replace_once(
        data_source,
        '    override suspend fun history() = YtMusicRepository.history()\n',
        '    override suspend fun history() = YtMusicRepository.history()\n'
        '    override suspend fun recents() = YtMusicRepository.recents()\n'
        '    override suspend fun quickPicks(excludeSongIds: Set<String>) = YtMusicRepository.quickPicks(excludeSongIds)\n',
    )

    repo = "app/src/main/java/com/music/bitchord/data/YtMusicRepository.kt"
    replace_once(
        repo,
        'import com.music.bitchord.data.sources.TrackMatcher\n',
        'import com.music.bitchord.data.sources.TrackMatcher\nimport com.music.bitchord.playback.LastPlayed\n',
    )
    replace_once(
        repo,
        '''    private suspend fun recentlyPlayed(): HomeShelf? {\n        if (Innertube.cookie == null) return null\n        val songs = fetchHistory().take(RECENT_LIMIT)\n        if (songs.isEmpty()) return null\n        return HomeShelf(\n            title = RECENT_TITLE,\n            items = songs.map {\n                ShelfItem(\n                    title = it.title,\n                    subtitle = it.artist,\n                    thumbnailUrl = it.thumbnailUrl,\n                    videoId = it.videoId,\n                    browseId = null,\n                )\n            },\n        )\n    }\n''',
        '''    private suspend fun recentlyPlayed(): HomeShelf? {\n        val songs = recents().getOrDefault(emptyList()).take(RECENT_LIMIT)\n        if (songs.isEmpty()) return null\n        return HomeShelf(\n            title = RECENT_TITLE,\n            items = songs.map {\n                ShelfItem(\n                    title = it.title,\n                    subtitle = it.artist,\n                    thumbnailUrl = it.thumbnailUrl,\n                    videoId = it.videoId,\n                    browseId = null,\n                )\n            },\n        )\n    }\n''',
    )
    insert_before(
        repo,
        '    private const val HISTORY = "FEmusic_history"\n',
        '''    /** Account history for Android Auto, with the last local queue as an offline/signed-out fallback. */\n    suspend fun recents(): Result<List<Song>> = call("recents") {\n        if (Innertube.cookie != null) {\n            val accountHistory = runCatching { fetchHistory() }.getOrDefault(emptyList())\n            if (accountHistory.isNotEmpty()) return@call accountHistory\n        }\n        runCatching { LastPlayed.load()?.songs.orEmpty() }\n            .getOrDefault(emptyList())\n            .distinctBy { it.videoId }\n    }\n\n    /** Recommended discovery tracks for Android Auto, excluding anything already in Recents. */\n    suspend fun quickPicks(excludeSongIds: Set<String> = emptySet()): Result<List<Song>> =\n        call("quickPicks") {\n            val homeRaw = runCatching { Innertube.browse("FEmusic_home") }.getOrNull()\n            val shelves = homeRaw?.let(InnertubeParser::parseHome).orEmpty()\n\n            fun HomeShelf.toSongs(): List<Song> = items.mapNotNull { item ->\n                item.videoId\n                    ?.takeUnless(excludeSongIds::contains)\n                    ?.let { id ->\n                        Song(\n                            videoId = id,\n                            title = item.title,\n                            artist = item.subtitle,\n                            thumbnailUrl = item.thumbnailUrl,\n                        )\n                    }\n            }\n\n            val namedShelf = shelves.firstOrNull { shelf ->\n                val title = shelf.title.lowercase(Locale.ROOT)\n                "quick" in title || "pick" in title || "mix" in title || "recommend" in title\n            }\n            namedShelf?.toSongs()\n                ?.distinctBy { it.videoId }\n                ?.takeIf { it.isNotEmpty() }\n                ?.let { return@call it }\n\n            val homeSongs = shelves\n                .filterNot { shelf ->\n                    val title = shelf.title.lowercase(Locale.ROOT)\n                    "recent" in title || "history" in title || "listen again" in title\n                }\n                .flatMap { it.toSongs() }\n                .distinctBy { it.videoId }\n            if (homeSongs.isNotEmpty()) return@call homeSongs\n\n            shelvesOf("FEmusic_new_releases")\n                .flatMap { it.toSongs() }\n                .distinctBy { it.videoId }\n        }\n\n''',
    )


def task2_tests() -> None:
    path = "app/src/test/java/com/music/bitchord/AndroidAutoCatalogTest.kt"
    replace_once(
        path,
        '            historyResult = Result.success(listOf(song("vid1", "Track")))\n',
        '            recentsResult = Result.success(listOf(song("vid1", "Track")))\n',
    )
    insert_before(
        path,
        '    @Test\n    fun libraryOnlyShowsNonEmptyCategories()',
        '''    @Test\n    fun signedOutRecentUsesLocalRecentsFeed() = runBlocking {\n        val fake = FakeAutoDataSource().apply {\n            signedIn = false\n            recentsResult = Result.success(listOf(song("local-recent", "Local Recent")))\n        }\n        val catalog = AndroidAutoCatalog(fake)\n\n        val rows = catalog.children(AndroidAutoRoute.Recent, 0, 20).getOrThrow()\n\n        assertEquals(\n            listOf(AndroidAutoRoute.Track("local-recent")),\n            rows.mapNotNull { AndroidAutoMediaIds.parse(it.mediaId) },\n        )\n        assertEquals(1, fake.recentsCalls)\n        assertEquals(0, fake.historyCalls)\n    }\n\n    @Test\n    fun homePrependsQuickPicksAndExcludesRecentIds() = runBlocking {\n        val fake = FakeAutoDataSource().apply {\n            recentsResult = Result.success(listOf(song("recent")))\n            quickPicksResult = Result.success(listOf(song("recent"), song("fresh")))\n            homeResult = Result.success(\n                HomeFeed(\n                    shelves = listOf(\n                        HomeShelf(\n                            "Listen Again",\n                            listOf(ShelfItem("Recent", "Artist", null, "recent", null)),\n                        ),\n                    ),\n                    continuation = null,\n                ),\n            )\n        }\n        val catalog = AndroidAutoCatalog(fake)\n\n        val home = catalog.children(AndroidAutoRoute.Home, 0, 20).getOrThrow()\n\n        assertEquals("Quick Picks", home.first().mediaMetadata.title.toString())\n        assertEquals(setOf("recent"), fake.quickPicksExcludedIds)\n        val quickRoute = AndroidAutoMediaIds.parse(home.first().mediaId) as AndroidAutoRoute.Shelf\n        val quickRows = catalog.children(quickRoute, 0, 20).getOrThrow()\n        assertEquals(\n            listOf(AndroidAutoRoute.Track("fresh")),\n            quickRows.mapNotNull { AndroidAutoMediaIds.parse(it.mediaId) },\n        )\n    }\n\n    @Test\n    fun recentsAndQuickPicksUseIndependentShortTtls() = runBlocking {\n        var clock = 1_000L\n        val fake = FakeAutoDataSource().apply {\n            recentsResult = Result.success(listOf(song("r1")))\n            quickPicksResult = Result.success(listOf(song("q1")))\n        }\n        val catalog = AndroidAutoCatalog(fake, nowMs = { clock })\n\n        catalog.children(AndroidAutoRoute.Recent, 0, 20).getOrThrow()\n        catalog.children(AndroidAutoRoute.Home, 0, 20).getOrThrow()\n        catalog.children(AndroidAutoRoute.Recent, 0, 20).getOrThrow()\n        catalog.children(AndroidAutoRoute.Home, 0, 20).getOrThrow()\n        assertEquals(1, fake.recentsCalls)\n        assertEquals(1, fake.quickPicksCalls)\n\n        clock += 30_001L\n        catalog.children(AndroidAutoRoute.Recent, 0, 20).getOrThrow()\n        assertEquals(2, fake.recentsCalls)\n\n        clock += 30_000L\n        catalog.children(AndroidAutoRoute.Home, 0, 20).getOrThrow()\n        assertEquals(2, fake.quickPicksCalls)\n    }\n\n''',
    )
    replace_once(
        path,
        '''    @Test\n    fun signedOutAuthenticatedRoutesAreEmptyWithoutRepositoryCalls() = runBlocking {\n        val fake = FakeAutoDataSource().apply {\n            signedIn = false\n            historyResult = Result.failure(IOException("should not be called"))\n            libraryResult = Result.failure(IOException("should not be called"))\n        }\n        val catalog = AndroidAutoCatalog(fake)\n\n        assertTrue(catalog.children(AndroidAutoRoute.Recent, 0, 20).getOrThrow().isEmpty())\n        assertTrue(catalog.children(AndroidAutoRoute.Library, 0, 20).getOrThrow().isEmpty())\n        assertTrue(\n            catalog.children(\n                AndroidAutoRoute.LibrarySection(AndroidAutoLibrarySection.LIKED),\n                0,\n                20,\n            ).getOrThrow().isEmpty(),\n        )\n        assertEquals(0, fake.historyCalls)\n        assertEquals(0, fake.libraryCalls)\n    }\n''',
        '''    @Test\n    fun signedOutLibraryStaysAuthGatedWhileRecentsUseTheirOwnFeed() = runBlocking {\n        val fake = FakeAutoDataSource().apply {\n            signedIn = false\n            recentsResult = Result.success(emptyList())\n            historyResult = Result.failure(IOException("should not be called"))\n            libraryResult = Result.failure(IOException("should not be called"))\n        }\n        val catalog = AndroidAutoCatalog(fake)\n\n        assertTrue(catalog.children(AndroidAutoRoute.Recent, 0, 20).getOrThrow().isEmpty())\n        assertTrue(catalog.children(AndroidAutoRoute.Library, 0, 20).getOrThrow().isEmpty())\n        assertTrue(\n            catalog.children(\n                AndroidAutoRoute.LibrarySection(AndroidAutoLibrarySection.LIKED),\n                0,\n                20,\n            ).getOrThrow().isEmpty(),\n        )\n        assertEquals(1, fake.recentsCalls)\n        assertEquals(0, fake.historyCalls)\n        assertEquals(0, fake.libraryCalls)\n    }\n''',
    )


def task2_prod() -> None:
    path = "app/src/main/java/com/music/bitchord/playback/AndroidAutoCatalog.kt"
    replace_once(
        path,
        '    private var historyCache: CacheEntry<List<Song>>? = null\n',
        '    private var historyCache: CacheEntry<List<Song>>? = null\n'
        '    private var recentsCache: CacheEntry<List<Song>>? = null\n'
        '    private var quickPicksCache: CacheEntry<List<Song>>? = null\n',
    )
    replace_once(
        path,
        '            AndroidAutoRoute.Recent -> if (dataSource.isSignedIn()) historyRows() else emptyList()\n',
        '            AndroidAutoRoute.Recent -> recents().map(::playableRow)\n',
    )
    replace_once(
        path,
        '        historyCache = null\n',
        '        historyCache = null\n        recentsCache = null\n        quickPicksCache = null\n',
    )
    insert_before(
        path,
        '    private suspend fun library(): LibraryPage {\n',
        '''    private suspend fun recents(): List<Song> {\n        recentsCache?.takeIf { nowMs() - it.storedAt <= RECENTS_TTL_MS }?.let { return it.value }\n        return dataSource.recents().getOrThrow().also {\n            recentsCache = CacheEntry(it, nowMs())\n        }\n    }\n\n    private suspend fun quickPicks(): List<Song> {\n        quickPicksCache?.takeIf { nowMs() - it.storedAt <= QUICK_PICKS_TTL_MS }?.let { return it.value }\n        val excluded = recents().mapTo(HashSet()) { it.videoId }\n        return dataSource.quickPicks(excluded).getOrThrow()\n            .filterNot { it.videoId in excluded }\n            .distinctBy { it.videoId }\n            .also { quickPicksCache = CacheEntry(it, nowMs()) }\n    }\n\n''',
    )
    replace_once(
        path,
        '''    private suspend fun homeShelves(): List<MediaItem> = homeFeed().shelves.mapIndexed { index, shelf ->\n        browsable(\n            AndroidAutoRoute.Shelf(AndroidAutoRoute.Shelf.Source.HOME, index, shelf.title),\n            shelf.title,\n            shelf.subtitle,\n            shelf.items.firstOrNull()?.thumbnailUrl,\n        )\n    }\n''',
        '''    private suspend fun mergedHomeShelves(): List<HomeShelf> {\n        val base = homeFeed().shelves\n        val quickSongs = runCatching { quickPicks() }.getOrDefault(emptyList())\n        val syntheticQuick = quickSongs.takeIf { it.isNotEmpty() }?.let { songs ->\n            HomeShelf(\n                title = "Quick Picks",\n                items = songs.map { song ->\n                    ShelfItem(\n                        title = song.title,\n                        subtitle = song.artist,\n                        thumbnailUrl = song.thumbnailUrl,\n                        videoId = song.videoId,\n                        browseId = null,\n                    )\n                },\n            )\n        }\n        return listOfNotNull(syntheticQuick) + base.filterNot { shelf ->\n            shelf.title.equals("Quick Picks", ignoreCase = true)\n        }\n    }\n\n    private suspend fun homeShelves(): List<MediaItem> = mergedHomeShelves().mapIndexed { index, shelf ->\n        browsable(\n            AndroidAutoRoute.Shelf(AndroidAutoRoute.Shelf.Source.HOME, index, shelf.title),\n            shelf.title,\n            shelf.subtitle,\n            shelf.items.firstOrNull()?.thumbnailUrl,\n        )\n    }\n''',
    )
    replace_once(
        path,
        '            AndroidAutoRoute.Shelf.Source.HOME -> homeFeed().shelves\n',
        '            AndroidAutoRoute.Shelf.Source.HOME -> mergedHomeShelves()\n',
    )
    replace_once(
        path,
        '        private const val HOME_TTL_MS = 5 * 60_000L\n',
        '        private const val HOME_TTL_MS = 5 * 60_000L\n'
        '        private const val RECENTS_TTL_MS = 30_000L\n'
        '        private const val QUICK_PICKS_TTL_MS = 60_000L\n',
    )


def task3_tests() -> None:
    path = "app/src/test/java/com/music/bitchord/AndroidAutoDashboardSourceTest.kt"
    insert_before(
        path,
        '    private class FakeDashboardBrowseGateway : DashboardBrowseGateway {\n',
        '''    @Test\n    fun syntheticQuickPicksShelfFlowsIntoDashboardBeforeOtherHomeShelves() = runTest {\n        val quickRoute = AndroidAutoRoute.Shelf(\n            AndroidAutoRoute.Shelf.Source.HOME,\n            0,\n            "Quick Picks",\n        )\n        val otherRoute = AndroidAutoRoute.Shelf(\n            AndroidAutoRoute.Shelf.Source.HOME,\n            1,\n            "Fresh Finds",\n        )\n        val gateway = FakeDashboardBrowseGateway().apply {\n            children[AndroidAutoRoute.Recent] = { Result.success(emptyList()) }\n            children[AndroidAutoRoute.Home] = {\n                Result.success(\n                    listOf(\n                        browseItem(AndroidAutoMediaIds.encode(quickRoute), "Quick Picks"),\n                        browseItem(AndroidAutoMediaIds.encode(otherRoute), "Fresh Finds"),\n                    ),\n                )\n            }\n            children[quickRoute] = { Result.success(listOf(playableItem("q1", "Quick One"))) }\n            children[otherRoute] = { Result.success(listOf(playableItem("f1", "Fresh One"))) }\n        }\n\n        val result = AndroidAutoDashboardSource(gateway, 100).loadHomeOnline()\n\n        assertEquals(listOf("Quick Picks", "Fresh Finds"), result.homeShelves.map { it.title })\n    }\n\n    @Test\n    fun quickPicksFailureKeepsOtherHomeShelvesAndReportsPartialError() = runTest {\n        val quickRoute = AndroidAutoRoute.Shelf(AndroidAutoRoute.Shelf.Source.HOME, 0, "Quick Picks")\n        val otherRoute = AndroidAutoRoute.Shelf(AndroidAutoRoute.Shelf.Source.HOME, 1, "Fresh Finds")\n        val gateway = FakeDashboardBrowseGateway().apply {\n            children[AndroidAutoRoute.Recent] = { Result.success(emptyList()) }\n            children[AndroidAutoRoute.Home] = {\n                Result.success(\n                    listOf(\n                        browseItem(AndroidAutoMediaIds.encode(quickRoute), "Quick Picks"),\n                        browseItem(AndroidAutoMediaIds.encode(otherRoute), "Fresh Finds"),\n                    ),\n                )\n            }\n            children[quickRoute] = { Result.failure(IOException("quick picks offline")) }\n            children[otherRoute] = { Result.success(listOf(playableItem("f1", "Fresh One"))) }\n        }\n\n        val result = AndroidAutoDashboardSource(gateway, 100).loadHomeOnline()\n\n        assertEquals(listOf("Fresh Finds"), result.homeShelves.map { it.title })\n        assertNotNull(result.errorMessage)\n    }\n\n''',
    )


def task4_tests() -> None:
    write(
        "app/src/test/java/com/music/bitchord/AndroidAutoBrowseHintsTest.kt",
        '''package com.music.bitchord\n\nimport androidx.test.ext.junit.runners.AndroidJUnit4\nimport com.music.bitchord.playback.AndroidAutoBrowseHints\nimport com.music.bitchord.playback.AndroidAutoRoute\nimport org.junit.Assert.assertEquals\nimport org.junit.Assert.assertTrue\nimport org.junit.Test\nimport org.junit.runner.RunWith\nimport org.robolectric.annotation.Config\n\n@RunWith(AndroidJUnit4::class)\n@Config(sdk = [35])\nclass AndroidAutoBrowseHintsTest {\n    @Test\n    fun rootAdvertisesContentStyleSupportAndGridHints() {\n        val extras = AndroidAutoBrowseHints.rootParams().extras\n\n        assertTrue(extras.getBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED"))\n        assertEquals(2, extras.getInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"))\n        assertEquals(2, extras.getInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"))\n        assertEquals(2, extras.getInt("android.media.extras.CONTENT_STYLE_BROWSABLE_HINT"))\n        assertEquals(2, extras.getInt("android.media.extras.CONTENT_STYLE_PLAYABLE_HINT"))\n    }\n\n    @Test\n    fun homeAndLibraryPreferGridButTrackListsPreferList() {\n        assertEquals(\n            2,\n            AndroidAutoBrowseHints.childParams(AndroidAutoRoute.Home)\n                .extras\n                .getInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"),\n        )\n        assertEquals(\n            2,\n            AndroidAutoBrowseHints.childParams(AndroidAutoRoute.Library)\n                .extras\n                .getInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"),\n        )\n        assertEquals(\n            1,\n            AndroidAutoBrowseHints.childParams(AndroidAutoRoute.Recent)\n                .extras\n                .getInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"),\n        )\n    }\n}\n''',
    )


def task4_prod() -> None:
    write(
        "app/src/main/java/com/music/bitchord/playback/AndroidAutoBrowseHints.kt",
        '''package com.music.bitchord.playback\n\nimport android.os.Bundle\nimport androidx.media3.session.MediaLibraryService\n\n/** Host hints for Android Auto/Automotive media browsers; TanTov templates remain authoritative. */\ninternal object AndroidAutoBrowseHints {\n    private const val LIST = 1\n    private const val GRID = 2\n\n    private const val SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"\n    private const val BROWSABLE = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"\n    private const val PLAYABLE = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"\n    private const val BROWSABLE_LEGACY = "android.media.extras.CONTENT_STYLE_BROWSABLE_HINT"\n    private const val PLAYABLE_LEGACY = "android.media.extras.CONTENT_STYLE_PLAYABLE_HINT"\n\n    fun rootParams(): MediaLibraryService.LibraryParams =\n        MediaLibraryService.LibraryParams.Builder()\n            .setExtras(styleExtras(GRID, GRID, supported = true))\n            .build()\n\n    fun childParams(parent: AndroidAutoRoute): MediaLibraryService.LibraryParams =\n        MediaLibraryService.LibraryParams.Builder()\n            .setExtras(\n                when (parent) {\n                    AndroidAutoRoute.Home,\n                    AndroidAutoRoute.Explore,\n                    AndroidAutoRoute.Library,\n                    -> styleExtras(GRID, GRID)\n\n                    else -> styleExtras(LIST, LIST)\n                },\n            )\n            .build()\n\n    private fun styleExtras(\n        browsable: Int,\n        playable: Int,\n        supported: Boolean = false,\n    ): Bundle = Bundle().apply {\n        if (supported) putBoolean(SUPPORTED, true)\n        putInt(BROWSABLE, browsable)\n        putInt(PLAYABLE, playable)\n        putInt(BROWSABLE_LEGACY, browsable)\n        putInt(PLAYABLE_LEGACY, playable)\n    }\n}\n''',
    )

    service = "app/src/main/java/com/music/bitchord/playback/PlaybackService.kt"
    replace_once(
        service,
        '            LibraryResult.ofItem(androidAutoCatalog.root(), params),\n',
        '            LibraryResult.ofItem(androidAutoCatalog.root(), AndroidAutoBrowseHints.rootParams()),\n',
    )
    replace_once(
        service,
        '''            androidAutoCatalog.children(route, page, pageSize).fold(\n                onSuccess = { LibraryResult.ofItemList(it, params) },\n                onFailure = { LibraryResult.ofError(SessionError.ERROR_IO) },\n            )\n''',
        '''            val returnParams = AndroidAutoBrowseHints.childParams(route)\n            androidAutoCatalog.children(route, page, pageSize).fold(\n                onSuccess = { LibraryResult.ofItemList(it, returnParams) },\n                onFailure = { LibraryResult.ofError(SessionError.ERROR_IO) },\n            )\n''',
    )
    replace_once(
        service,
        '''            androidAutoCatalog.search(query, page, pageSize).fold(\n                onSuccess = { LibraryResult.ofItemList(it, params) },\n                onFailure = { LibraryResult.ofError(SessionError.ERROR_IO) },\n            )\n''',
        '''            androidAutoCatalog.search(query, page, pageSize).fold(\n                onSuccess = {\n                    LibraryResult.ofItemList(\n                        it,\n                        AndroidAutoBrowseHints.childParams(AndroidAutoRoute.Recent),\n                    )\n                },\n                onFailure = { LibraryResult.ofError(SessionError.ERROR_IO) },\n            )\n''',
    )


def task5_tests() -> None:
    write(
        "app/src/androidTest/java/com/music/bitchord/AndroidAutoMediaLibraryTest.kt",
        '''package com.music.bitchord\n\nimport android.content.ComponentName\nimport androidx.media3.common.util.UnstableApi\nimport androidx.media3.session.MediaBrowser\nimport androidx.media3.session.SessionToken\nimport androidx.test.ext.junit.runners.AndroidJUnit4\nimport androidx.test.platform.app.InstrumentationRegistry\nimport com.music.bitchord.playback.AndroidAutoMediaIds\nimport com.music.bitchord.playback.AndroidAutoRoute\nimport com.music.bitchord.playback.PlaybackService\nimport kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.runBlocking\nimport kotlinx.coroutines.withContext\nimport org.junit.After\nimport org.junit.Assert.assertEquals\nimport org.junit.Assert.assertNotNull\nimport org.junit.Assert.assertTrue\nimport org.junit.Before\nimport org.junit.Test\nimport org.junit.runner.RunWith\nimport java.util.concurrent.CountDownLatch\nimport java.util.concurrent.TimeUnit\n\n@OptIn(UnstableApi::class)\n@RunWith(AndroidJUnit4::class)\nclass AndroidAutoMediaLibraryTest {\n    private lateinit var mediaBrowser: MediaBrowser\n\n    @Before\n    fun setup() {\n        val instrumentation = InstrumentationRegistry.getInstrumentation()\n        val context = instrumentation.targetContext\n        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))\n        val latch = CountDownLatch(1)\n        var browserInstance: MediaBrowser? = null\n\n        instrumentation.runOnMainSync {\n            val future = MediaBrowser.Builder(context, token).buildAsync()\n            future.addListener(\n                {\n                    browserInstance = future.get()\n                    latch.countDown()\n                },\n                { command -> command.run() },\n            )\n        }\n\n        assertTrue("MediaBrowser connection timed out", latch.await(10, TimeUnit.SECONDS))\n        assertNotNull("MediaBrowser instance must not be null", browserInstance)\n        mediaBrowser = browserInstance!!\n    }\n\n    @After\n    fun tearDown() {\n        if (::mediaBrowser.isInitialized) {\n            InstrumentationRegistry.getInstrumentation().runOnMainSync {\n                mediaBrowser.release()\n            }\n        }\n    }\n\n    @Test\n    fun rootAndTopLevelChildrenUseStableTanTovIds() = runBlocking {\n        val root = withContext(Dispatchers.Main) {\n            mediaBrowser.getLibraryRoot(null).get(10, TimeUnit.SECONDS)\n        }.value\n        assertNotNull(root)\n        assertEquals("tantov:auto:v1:root", root!!.mediaId)\n        assertTrue(root.mediaMetadata.isBrowsable == true)\n\n        val children = withContext(Dispatchers.Main) {\n            mediaBrowser.getChildren(root.mediaId, 0, 20, null).get(10, TimeUnit.SECONDS)\n        }.value\n        assertNotNull(children)\n        assertEquals(\n            listOf("Home", "Explore", "Recently Played", "Library"),\n            children!!.map { it.mediaMetadata.title.toString() },\n        )\n    }\n\n    @Test\n    fun getItemResolvesStableHomeRoute() = runBlocking {\n        val homeId = AndroidAutoMediaIds.encode(AndroidAutoRoute.Home)\n        val item = withContext(Dispatchers.Main) {\n            mediaBrowser.getItem(homeId).get(10, TimeUnit.SECONDS)\n        }.value\n\n        assertNotNull(item)\n        assertEquals(homeId, item!!.mediaId)\n        assertTrue(item.mediaMetadata.isBrowsable == true)\n    }\n\n    @Test\n    fun searchAndSearchResultReturnLibraryResultsWithoutCrashing() = runBlocking {\n        val search = withContext(Dispatchers.Main) {\n            mediaBrowser.search("Adele", null).get(10, TimeUnit.SECONDS)\n        }\n        assertNotNull(search)\n\n        val results = withContext(Dispatchers.Main) {\n            mediaBrowser.getSearchResult("Adele", 0, 10, null).get(10, TimeUnit.SECONDS)\n        }\n        assertNotNull(results)\n    }\n}\n''',
    )

    manifest_test = "app/src/androidTest/java/com/music/bitchord/TanTovCarManifestTest.kt"
    replace_once(
        manifest_test,
        'import android.content.pm.PackageManager\n',
        'import android.content.Intent\nimport android.content.pm.PackageManager\n',
    )
    replace_once(
        manifest_test,
        '''        assertTrue(appInfo.metaData.getInt("androidx.car.app.TintableAttributionIcon") != 0)\n    }\n}\n''',
        '''        assertTrue(appInfo.metaData.getInt("androidx.car.app.TintableAttributionIcon") != 0)\n\n        val media3 = Intent("androidx.media3.session.MediaLibraryService")\n            .setPackage(context.packageName)\n        val legacy = Intent("android.media.browse.MediaBrowserService")\n            .setPackage(context.packageName)\n        assertTrue(\n            context.packageManager.queryIntentServices(\n                media3,\n                PackageManager.ResolveInfoFlags.of(0),\n            ).isNotEmpty(),\n        )\n        assertTrue(\n            context.packageManager.queryIntentServices(\n                legacy,\n                PackageManager.ResolveInfoFlags.of(0),\n            ).isNotEmpty(),\n        )\n    }\n}\n''',
    )

    workflow = ".github/workflows/android-auto-legacy-smoke.yml"
    replace_once(
        workflow,
        '    branches: [feat/android-auto-full, feat/tantov-music-v1, feat/tantov-auto-dashboard]\n',
        '    branches: [feat/android-auto-full, feat/tantov-music-v1, feat/tantov-auto-dashboard, integration/bitChord-android-auto-2026-09]\n',
    )
    replace_once(
        workflow,
        '            -Pandroid.testInstrumentationRunnerArguments.class=com.music.bitchord.AndroidAutoLegacyBrowserTest,com.music.bitchord.TanTovTopBarBrandingTest,com.music.bitchord.TanTovCarManifestTest\n',
        '            -Pandroid.testInstrumentationRunnerArguments.class=com.music.bitchord.AndroidAutoLegacyBrowserTest,com.music.bitchord.AndroidAutoMediaLibraryTest,com.music.bitchord.TanTovTopBarBrandingTest,com.music.bitchord.TanTovCarManifestTest\n',
    )


PHASES = {
    "task1_tests": task1_tests,
    "task1_prod": task1_prod,
    "task2_tests": task2_tests,
    "task2_prod": task2_prod,
    "task3_tests": task3_tests,
    "task4_tests": task4_tests,
    "task4_prod": task4_prod,
    "task5_tests": task5_tests,
}


if __name__ == "__main__":
    if len(sys.argv) != 2 or sys.argv[1] not in PHASES:
        raise SystemExit(f"usage: {sys.argv[0]} <{'|'.join(PHASES)}>")
    PHASES[sys.argv[1]]()
