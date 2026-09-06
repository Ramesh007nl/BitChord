# BitChord Android Auto Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port the useful Android Auto browsing, caching, and content-style improvements from current upstream BitChord into TanTov while preserving TanTov's custom car dashboard, branding, local-music flow, and existing playback behavior.

**Architecture:** Keep TanTov's current `AndroidAutoCatalog` as the single Media3 browse model used by both legacy/media-browser clients and the Android for Cars template dashboard. Add upstream-style Recents and Quick Picks APIs behind `AndroidAutoDataSource`, cache those feeds independently in `AndroidAutoCatalog`, surface Quick Picks as a synthetic Home shelf so the existing TanTov dashboard automatically renders it, and add Android Auto content-style hints without replacing TanTov's template UI. The service remains the playback authority, and every car-selected track is converted back to the normal phone-app `MediaItem` before ExoPlayer receives it.

**Tech Stack:** Kotlin, Android Media3 `MediaLibraryService`, Android for Cars App Library, ExoPlayer, Kotlin coroutines, JUnit4/Robolectric, Android instrumentation tests, Gradle, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-06-bitChord-android-auto-integration-design.md`

## Global Constraints

- Work only on `integration/bitChord-android-auto-2026-09` until verification is complete.
- Do not modify `main`, `feat/tantov-music-v1`, `feat/tantov-auto-dashboard`, or `feat/android-auto-full`.
- Preserve `AndroidAutoDashboard`, `AndroidAutoDashboardSource`, `CarArtworkLoader`, `CarTemplateFactory`, `TanTovCarAppService`, and `TanTovCarSession` as TanTov's presentation architecture.
- Preserve the stable Android Auto root ID `tantov:auto:v1:root` and the top-level order `Home`, `Explore`, `Recently Played`, `Library`.
- Preserve both automotive capabilities in the dev descriptor: `<uses name="media" />` and `<uses name="template" />`.
- Preserve Local Music when signed out or offline.
- Do not merge unrelated upstream UI, addon/source-module, audio-output, or player-redesign changes.
- Do not port upstream synced-lyrics service metadata in this batch; it is cross-cutting and will be considered separately after the real-car acceptance test.
- Existing phone-app playback and notification behavior must remain unchanged except for Android Auto browse metadata/hints.

---

## File Structure

### Existing files to modify

- `app/src/main/java/com/music/bitchord/data/YtMusicRepository.kt` — expose upstream-style `recents()` and `quickPicks()` data APIs with local-history fallback.
- `app/src/main/java/com/music/bitchord/playback/AndroidAutoDataSource.kt` — add injectable Recents/Quick Picks methods.
- `app/src/main/java/com/music/bitchord/playback/AndroidAutoCatalog.kt` — own short-lived car browse caches, synthesize the Quick Picks shelf, keep Recent available without sign-in, and preserve playable-item resolution.
- `app/src/main/java/com/music/bitchord/playback/PlaybackService.kt` — return Android Auto content-style `LibraryParams` while keeping TanTov's catalog/service callbacks.
- `app/src/main/java/com/music/bitchord/car/AndroidAutoDashboardSource.kt` — keep partial-failure behavior and ensure the synthetic Quick Picks shelf flows through unchanged.
- `app/src/test/java/com/music/bitchord/AndroidAutoCatalogTest.kt` — cover Recents fallback, Quick Picks dedupe/cache, auth cache invalidation, and top-level compatibility.
- `app/src/test/java/com/music/bitchord/AndroidAutoDashboardSourceTest.kt` — cover Quick Picks shelf ordering and failure isolation.
- `app/src/test/java/com/music/bitchord/AndroidAutoSearchTest.kt` — update fake data source for the expanded interface; preserve mixed search/cache assertions.
- `app/src/test/java/com/music/bitchord/AndroidAutoVoiceSearchTest.kt` — update fake data source for the expanded interface; preserve voice-playback contract.
- `app/src/androidTest/java/com/music/bitchord/TanTovCarManifestTest.kt` — assert both Media3 library and legacy browser service actions remain present.
- `.github/workflows/android-auto-legacy-smoke.yml` — include the integration branch and the new Media3 instrumentation test.

### New files

- `app/src/main/java/com/music/bitchord/playback/AndroidAutoBrowseHints.kt` — Android Auto/Automotive content-style constants and `LibraryParams` builders.
- `app/src/test/java/com/music/bitchord/AndroidAutoBrowseHintsTest.kt` — Robolectric tests for returned grid/list style extras.
- `app/src/androidTest/java/com/music/bitchord/AndroidAutoMediaLibraryTest.kt` — Media3 `MediaBrowser` smoke tests adapted from upstream to TanTov IDs/routes.

---

### Task 1: Add upstream-style Recents and Quick Picks repository APIs

**Files:**
- Modify: `app/src/main/java/com/music/bitchord/data/YtMusicRepository.kt` in the `recentlyPlayed`, `history`, and Home-feed helper area.
- Modify: `app/src/main/java/com/music/bitchord/playback/AndroidAutoDataSource.kt` interface and `YtMusicAndroidAutoDataSource` implementation.
- Test: `app/src/test/java/com/music/bitchord/AndroidAutoCatalogTest.kt` fake data source compile contract.
- Test: `app/src/test/java/com/music/bitchord/AndroidAutoSearchTest.kt` fake data source compile contract.
- Test: `app/src/test/java/com/music/bitchord/AndroidAutoVoiceSearchTest.kt` fake data source compile contract.

**Interfaces:**
- Consumes: `YtMusicRepository.home()`, `YtMusicRepository.history()`, `LastPlayed.load()`, `Innertube.browse("FEmusic_home")`, `InnertubeParser.parseHome(...)`.
- Produces:
  - `suspend fun AndroidAutoDataSource.recents(): Result<List<Song>>`
  - `suspend fun AndroidAutoDataSource.quickPicks(excludeSongIds: Set<String> = emptySet()): Result<List<Song>>`
  - `suspend fun YtMusicRepository.recents(): Result<List<Song>>`
  - `suspend fun YtMusicRepository.quickPicks(excludeSongIds: Set<String> = emptySet()): Result<List<Song>>`

- [ ] **Step 1: Write the failing fake-data-source changes first**

Add these members to every test fake implementing `AndroidAutoDataSource`:

```kotlin
var recentsResult: Result<List<Song>> = Result.success(emptyList())
var quickPicksResult: Result<List<Song>> = Result.success(emptyList())
var recentsCalls = 0
var quickPicksCalls = 0
var quickPicksExcludedIds: Set<String> = emptySet()

override suspend fun recents(): Result<List<Song>> {
    recentsCalls++
    return recentsResult
}

override suspend fun quickPicks(excludeSongIds: Set<String>): Result<List<Song>> {
    quickPicksCalls++
    quickPicksExcludedIds = excludeSongIds
    return quickPicksResult
}
```

For simple search/voice fakes that do not use these calls, use:

```kotlin
override suspend fun recents() = Result.success(emptyList<Song>())
override suspend fun quickPicks(excludeSongIds: Set<String>) = Result.success(emptyList<Song>())
```

- [ ] **Step 2: Run the focused unit tests and confirm compile failure**

Run:

```bash
./gradlew testDevDebugUnitTest --tests "com.music.bitchord.AndroidAutoCatalogTest" --tests "com.music.bitchord.AndroidAutoSearchTest" --tests "com.music.bitchord.AndroidAutoVoiceSearchTest"
```

Expected: compilation fails because `AndroidAutoDataSource` does not yet declare `recents()` or `quickPicks(...)`.

- [ ] **Step 3: Extend `AndroidAutoDataSource` minimally**

Change the interface to:

```kotlin
interface AndroidAutoDataSource {
    suspend fun home(): Result<HomeFeed>
    suspend fun explore(): Result<List<HomeShelf>>
    suspend fun history(): Result<List<Song>>
    suspend fun recents(): Result<List<Song>>
    suspend fun quickPicks(excludeSongIds: Set<String> = emptySet()): Result<List<Song>>
    suspend fun library(): Result<LibraryPage>
    suspend fun browseSongs(browseId: String): Result<YtMusicRepository.SongPage>
    suspend fun artistPage(browseId: String): Result<ArtistPage>
    suspend fun search(query: String, filter: SearchFilter): Result<List<SearchResult>>
    fun isSignedIn(): Boolean
}
```

Wire the production adapter directly:

```kotlin
override suspend fun recents() = YtMusicRepository.recents()
override suspend fun quickPicks(excludeSongIds: Set<String>) =
    YtMusicRepository.quickPicks(excludeSongIds)
```

- [ ] **Step 4: Add `YtMusicRepository.recents()` with local fallback**

Use the upstream behavior, but keep TanTov's existing models:

```kotlin
suspend fun recents(): Result<List<Song>> = call("recents") {
    if (Innertube.cookie != null) {
        val accountHistory = runCatching { fetchHistory() }.getOrDefault(emptyList())
        if (accountHistory.isNotEmpty()) return@call accountHistory
    }

    LastPlayed.load()?.songs?.map { last ->
        Song(
            videoId = last.videoId,
            title = last.title,
            artist = last.artist,
            thumbnailUrl = last.thumbnailUrl,
        )
    }.orEmpty()
}
```

Also change `recentlyPlayed()` so Home can still show recent listening when the account history is unavailable:

```kotlin
private suspend fun recentlyPlayed(): HomeShelf? {
    val accountSongs = if (Innertube.cookie != null) {
        runCatching { fetchHistory() }.getOrDefault(emptyList())
    } else {
        emptyList()
    }
    val localSongs = LastPlayed.load()?.songs?.map { last ->
        Song(last.videoId, last.title, last.artist, last.thumbnailUrl)
    }.orEmpty()
    val songs = (localSongs + accountSongs).distinctBy { it.videoId }.take(RECENT_LIMIT)
    if (songs.isEmpty()) return null
    return HomeShelf(
        title = "Recents",
        items = songs.map { song ->
            ShelfItem(song.title, song.artist, song.thumbnailUrl, song.videoId, null)
        },
    )
}
```

- [ ] **Step 5: Add `YtMusicRepository.quickPicks()` using the upstream fallback order**

Implement this concrete selection order:

```kotlin
suspend fun quickPicks(excludeSongIds: Set<String> = emptySet()): Result<List<Song>> =
    call("quickPicks") {
        val homeRaw = runCatching { Innertube.browse("FEmusic_home") }.getOrNull()
        val shelves = homeRaw?.let(InnertubeParser::parseHome).orEmpty()

        fun HomeShelf.toSongs(): List<Song> = items.mapNotNull { item ->
            item.videoId
                ?.takeUnless(excludeSongIds::contains)
                ?.let { id -> Song(id, item.title, item.subtitle, item.thumbnailUrl) }
        }

        val namedShelf = shelves.firstOrNull { shelf ->
            val title = shelf.title.lowercase(Locale.ROOT)
            "quick" in title || "pick" in title || "mix" in title || "recommend" in title
        }
        namedShelf?.toSongs()?.distinctBy { it.videoId }
            ?.takeIf { it.isNotEmpty() }
            ?.let { return@call it }

        val homeSongs = shelves
            .filterNot { shelf ->
                val title = shelf.title.lowercase(Locale.ROOT)
                "recent" in title || "history" in title || "listen again" in title
            }
            .flatMap(HomeShelf::toSongs)
            .distinctBy { it.videoId }
        if (homeSongs.isNotEmpty()) return@call homeSongs

        shelvesOf("FEmusic_new_releases")
            .flatMap(HomeShelf::toSongs)
            .distinctBy { it.videoId }
    }
```

- [ ] **Step 6: Run the focused unit tests again**

Run the same command from Step 2.

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/music/bitchord/data/YtMusicRepository.kt \
        app/src/main/java/com/music/bitchord/playback/AndroidAutoDataSource.kt \
        app/src/test/java/com/music/bitchord/AndroidAutoCatalogTest.kt \
        app/src/test/java/com/music/bitchord/AndroidAutoSearchTest.kt \
        app/src/test/java/com/music/bitchord/AndroidAutoVoiceSearchTest.kt
git commit -m "feat(auto): add recents and quick picks data APIs"
```

---

### Task 2: Port short-lived Recents and Quick Picks caching into `AndroidAutoCatalog`

**Files:**
- Modify: `app/src/main/java/com/music/bitchord/playback/AndroidAutoCatalog.kt` cache fields, `children`, `homeFeed`, Recent handling, and authenticated-cache reset.
- Modify: `app/src/test/java/com/music/bitchord/AndroidAutoCatalogTest.kt`.

**Interfaces:**
- Consumes: `AndroidAutoDataSource.recents()`, `AndroidAutoDataSource.quickPicks(Set<String>)`, `AndroidAutoDataSource.home()`.
- Produces: unchanged public `AndroidAutoCatalog.children(...)`, but Home now contains a synthetic `Quick Picks` shelf and Recent works without sign-in when local history exists.

- [ ] **Step 1: Add failing Recents fallback test**

Add:

```kotlin
@Test
fun signedOutRecentUsesLocalRecentsFeed() = runBlocking {
    val fake = FakeAutoDataSource().apply {
        signedIn = false
        recentsResult = Result.success(listOf(song("local-recent", "Local Recent")))
    }
    val catalog = AndroidAutoCatalog(fake)

    val rows = catalog.children(AndroidAutoRoute.Recent, 0, 20).getOrThrow()

    assertEquals(listOf(AndroidAutoRoute.Track("local-recent")), rows.mapNotNull {
        AndroidAutoMediaIds.parse(it.mediaId)
    })
    assertEquals(1, fake.recentsCalls)
    assertEquals(0, fake.historyCalls)
}
```

Replace the old signed-out assertion that Recent must be empty; Library remains auth-gated.

- [ ] **Step 2: Add failing Quick Picks merge/dedupe test**

```kotlin
@Test
fun homePrependsQuickPicksAndExcludesRecentIds() = runBlocking {
    val fake = FakeAutoDataSource().apply {
        recentsResult = Result.success(listOf(song("recent")))
        quickPicksResult = Result.success(listOf(song("recent"), song("fresh")))
        homeResult = Result.success(
            HomeFeed(
                shelves = listOf(
                    HomeShelf(
                        "Listen Again",
                        listOf(ShelfItem("Recent", "Artist", null, "recent", null)),
                    ),
                ),
                continuation = null,
            ),
        )
    }
    val catalog = AndroidAutoCatalog(fake)

    val home = catalog.children(AndroidAutoRoute.Home, 0, 20).getOrThrow()

    assertEquals("Quick Picks", home.first().mediaMetadata.title.toString())
    assertEquals(setOf("recent"), fake.quickPicksExcludedIds)
    val quickRoute = AndroidAutoMediaIds.parse(home.first().mediaId) as AndroidAutoRoute.Shelf
    val quickRows = catalog.children(quickRoute, 0, 20).getOrThrow()
    assertEquals(listOf(AndroidAutoRoute.Track("fresh")), quickRows.mapNotNull {
        AndroidAutoMediaIds.parse(it.mediaId)
    })
}
```

- [ ] **Step 3: Add failing TTL test**

```kotlin
@Test
fun recentsAndQuickPicksUseIndependentShortTtls() = runBlocking {
    var clock = 1_000L
    val fake = FakeAutoDataSource().apply {
        recentsResult = Result.success(listOf(song("r1")))
        quickPicksResult = Result.success(listOf(song("q1")))
    }
    val catalog = AndroidAutoCatalog(fake, nowMs = { clock })

    catalog.children(AndroidAutoRoute.Recent, 0, 20).getOrThrow()
    catalog.children(AndroidAutoRoute.Home, 0, 20).getOrThrow()
    catalog.children(AndroidAutoRoute.Recent, 0, 20).getOrThrow()
    catalog.children(AndroidAutoRoute.Home, 0, 20).getOrThrow()
    assertEquals(1, fake.recentsCalls)
    assertEquals(1, fake.quickPicksCalls)

    clock += 30_001L
    catalog.children(AndroidAutoRoute.Recent, 0, 20).getOrThrow()
    assertEquals(2, fake.recentsCalls)

    clock += 30_000L
    catalog.children(AndroidAutoRoute.Home, 0, 20).getOrThrow()
    assertEquals(2, fake.quickPicksCalls)
}
```

- [ ] **Step 4: Run the catalog tests and confirm failure**

```bash
./gradlew testDevDebugUnitTest --tests "com.music.bitchord.AndroidAutoCatalogTest"
```

Expected: the new tests fail because Recent still uses sign-in/history and Home does not synthesize Quick Picks.

- [ ] **Step 5: Implement independent car browse caches**

In `AndroidAutoCatalog`, add:

```kotlin
private var recentsCache: CacheEntry<List<Song>>? = null
private var quickPicksCache: CacheEntry<List<Song>>? = null

private suspend fun recents(): List<Song> {
    recentsCache?.takeIf { nowMs() - it.storedAt <= RECENTS_TTL_MS }?.let { return it.value }
    return dataSource.recents().getOrThrow().also {
        recentsCache = CacheEntry(it, nowMs())
    }
}

private suspend fun quickPicks(): List<Song> {
    quickPicksCache?.takeIf { nowMs() - it.storedAt <= QUICK_PICKS_TTL_MS }?.let { return it.value }
    val excluded = recents().mapTo(HashSet()) { it.videoId }
    return dataSource.quickPicks(excluded).getOrThrow()
        .filterNot { it.videoId in excluded }
        .distinctBy { it.videoId }
        .also { quickPicksCache = CacheEntry(it, nowMs()) }
}
```

Use:

```kotlin
private const val RECENTS_TTL_MS = 30_000L
private const val QUICK_PICKS_TTL_MS = 60_000L
```

- [ ] **Step 6: Change Recent and Home behavior without changing root IDs**

Change Recent handling to:

```kotlin
AndroidAutoRoute.Recent -> recents().map(::playableRow)
```

Keep root children exactly:

```kotlin
listOf(
    browsable(AndroidAutoRoute.Home, "Home"),
    browsable(AndroidAutoRoute.Explore, "Explore"),
    browsable(AndroidAutoRoute.Recent, "Recently Played"),
    browsable(AndroidAutoRoute.Library, "Library"),
)
```

Build Home shelves as:

```kotlin
private suspend fun homeShelves(): List<MediaItem> {
    val base = homeFeed()
    val quickSongs = runCatching { quickPicks() }.getOrDefault(emptyList())
    val syntheticQuick = quickSongs.takeIf { it.isNotEmpty() }?.let { songs ->
        HomeShelf(
            title = "Quick Picks",
            items = songs.map { song ->
                ShelfItem(song.title, song.artist, song.thumbnailUrl, song.videoId, null)
            },
        )
    }
    val shelves = listOfNotNull(syntheticQuick) + base.shelves.filterNot { shelf ->
        shelf.title.equals("Quick Picks", ignoreCase = true)
    }
    return shelves.mapIndexed { index, shelf ->
        browsable(
            AndroidAutoRoute.Shelf(AndroidAutoRoute.Shelf.Source.HOME, index, shelf.title),
            shelf.title,
            shelf.subtitle,
            shelf.items.firstOrNull()?.thumbnailUrl,
        )
    }
}
```

Update `shelfRows(...)` so HOME shelf ordinals resolve against the exact same merged shelf list. Extract one shared `mergedHomeShelves()` function and call it from both `homeShelves()` and `shelfRows(...)`; do not calculate different ordinal lists in two places.

- [ ] **Step 7: Clear new caches on auth changes**

Extend `clearAuthenticatedCache()`:

```kotlin
recentsCache = null
quickPicksCache = null
```

Keep Local Music cache/data independent of account auth.

- [ ] **Step 8: Run catalog tests**

```bash
./gradlew testDevDebugUnitTest --tests "com.music.bitchord.AndroidAutoCatalogTest"
```

Expected: PASS, including unchanged top-level route order.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/music/bitchord/playback/AndroidAutoCatalog.kt \
        app/src/test/java/com/music/bitchord/AndroidAutoCatalogTest.kt
git commit -m "feat(auto): cache recents and quick picks"
```

---

### Task 3: Preserve TanTov dashboard ordering and partial-failure behavior

**Files:**
- Modify: `app/src/main/java/com/music/bitchord/car/AndroidAutoDashboardSource.kt` only if required for deterministic Quick Picks handling.
- Modify: `app/src/test/java/com/music/bitchord/AndroidAutoDashboardSourceTest.kt`.
- Verify: `app/src/main/java/com/music/bitchord/car/AndroidAutoDashboard.kt` remains authoritative for section ordering.

**Interfaces:**
- Consumes: `AndroidAutoRoute.Home` rows from the updated catalog.
- Produces: `DashboardOnlineResult` with Recents and Home shelves, including synthetic Quick Picks, while retaining partial successes.

- [ ] **Step 1: Add a failing dashboard test for the synthetic Quick Picks shelf**

```kotlin
@Test
fun syntheticQuickPicksShelfFlowsIntoDashboardBeforeOtherHomeShelves() = runTest {
    val quickRoute = AndroidAutoRoute.Shelf(
        AndroidAutoRoute.Shelf.Source.HOME,
        0,
        "Quick Picks",
    )
    val otherRoute = AndroidAutoRoute.Shelf(
        AndroidAutoRoute.Shelf.Source.HOME,
        1,
        "Fresh Finds",
    )
    val gateway = FakeDashboardBrowseGateway().apply {
        children[AndroidAutoRoute.Recent] = { Result.success(emptyList()) }
        children[AndroidAutoRoute.Home] = {
            Result.success(
                listOf(
                    browseItem(AndroidAutoMediaIds.encode(quickRoute), "Quick Picks"),
                    browseItem(AndroidAutoMediaIds.encode(otherRoute), "Fresh Finds"),
                ),
            )
        }
        children[quickRoute] = { Result.success(listOf(playableItem("q1", "Quick One"))) }
        children[otherRoute] = { Result.success(listOf(playableItem("f1", "Fresh One"))) }
    }

    val result = AndroidAutoDashboardSource(gateway, 100).loadHomeOnline()

    assertEquals(listOf("Quick Picks", "Fresh Finds"), result.homeShelves.map { it.title })
}
```

- [ ] **Step 2: Add a failing partial-failure test for Quick Picks**

```kotlin
@Test
fun quickPicksFailureKeepsOtherHomeShelvesAndReportsPartialError() = runTest {
    val quickRoute = AndroidAutoRoute.Shelf(AndroidAutoRoute.Shelf.Source.HOME, 0, "Quick Picks")
    val otherRoute = AndroidAutoRoute.Shelf(AndroidAutoRoute.Shelf.Source.HOME, 1, "Fresh Finds")
    val gateway = FakeDashboardBrowseGateway().apply {
        children[AndroidAutoRoute.Recent] = { Result.success(emptyList()) }
        children[AndroidAutoRoute.Home] = {
            Result.success(
                listOf(
                    browseItem(AndroidAutoMediaIds.encode(quickRoute), "Quick Picks"),
                    browseItem(AndroidAutoMediaIds.encode(otherRoute), "Fresh Finds"),
                ),
            )
        }
        children[quickRoute] = { Result.failure(IOException("quick picks offline")) }
        children[otherRoute] = { Result.success(listOf(playableItem("f1", "Fresh One"))) }
    }

    val result = AndroidAutoDashboardSource(gateway, 100).loadHomeOnline()

    assertEquals(listOf("Fresh Finds"), result.homeShelves.map { it.title })
    assertNotNull(result.errorMessage)
}
```

- [ ] **Step 3: Run dashboard-source tests**

```bash
./gradlew testDevDebugUnitTest --tests "com.music.bitchord.AndroidAutoDashboardSourceTest"
```

Expected: if current source already satisfies both tests, PASS and do not change production code. If either fails, make only the minimal change needed in `loadHomeShelves()` to preserve emitted order and set `hadFailure = true` for a failed shelf while continuing remaining shelves.

- [ ] **Step 4: Verify TanTov's final dashboard ordering remains unchanged**

Run:

```bash
./gradlew testDevDebugUnitTest --tests "com.music.bitchord.AndroidAutoDashboardTest"
```

Expected order remains:

```text
Local Music -> Recently Played -> Quick Picks -> Listen Again -> other shelves
```

- [ ] **Step 5: Commit only if code/tests changed**

```bash
git add app/src/main/java/com/music/bitchord/car/AndroidAutoDashboardSource.kt \
        app/src/test/java/com/music/bitchord/AndroidAutoDashboardSourceTest.kt
git commit -m "test(auto): preserve dashboard quick picks behavior"
```

---

### Task 4: Add Android Auto grid/list content-style hints without replacing TanTov templates

**Files:**
- Create: `app/src/main/java/com/music/bitchord/playback/AndroidAutoBrowseHints.kt`.
- Create: `app/src/test/java/com/music/bitchord/AndroidAutoBrowseHintsTest.kt`.
- Modify: `app/src/main/java/com/music/bitchord/playback/PlaybackService.kt` in `onGetLibraryRoot`, `onGetChildren`, `onGetItem`, and `onGetSearchResult` result construction.

**Interfaces:**
- Produces:
  - `internal object AndroidAutoBrowseHints`
  - `fun rootParams(): MediaLibraryService.LibraryParams`
  - `fun childParams(parent: AndroidAutoRoute): MediaLibraryService.LibraryParams?`
- Does not change any `MediaItem.mediaId` or playback queue.

- [ ] **Step 1: Write the failing hint tests**

Create `AndroidAutoBrowseHintsTest.kt`:

```kotlin
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AndroidAutoBrowseHintsTest {
    @Test
    fun rootAdvertisesContentStyleSupportAndGridHints() {
        val extras = AndroidAutoBrowseHints.rootParams().extras
        assertTrue(extras.getBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED"))
        assertEquals(2, extras.getInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"))
        assertEquals(2, extras.getInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"))
    }

    @Test
    fun homeAndLibraryFoldersPreferGridButTrackListsPreferList() {
        assertEquals(
            2,
            AndroidAutoBrowseHints.childParams(AndroidAutoRoute.Home)
                ?.extras
                ?.getInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"),
        )
        assertEquals(
            1,
            AndroidAutoBrowseHints.childParams(AndroidAutoRoute.Recent)
                ?.extras
                ?.getInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"),
        )
    }
}
```

Use `1` for list and `2` for grid, matching Android Auto's legacy content-style values and upstream BitChord.

- [ ] **Step 2: Run the test and confirm failure**

```bash
./gradlew testDevDebugUnitTest --tests "com.music.bitchord.AndroidAutoBrowseHintsTest"
```

Expected: compile failure because `AndroidAutoBrowseHints` does not exist.

- [ ] **Step 3: Create the focused hint helper**

Implement constants for both legacy Android Auto and Automotive OS key spellings so either host can consume them:

```kotlin
internal object AndroidAutoBrowseHints {
    private const val LIST = 1
    private const val GRID = 2

    private const val SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
    private const val BROWSABLE = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
    private const val PLAYABLE = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
    private const val BROWSABLE_LEGACY = "android.media.extras.CONTENT_STYLE_BROWSABLE_HINT"
    private const val PLAYABLE_LEGACY = "android.media.extras.CONTENT_STYLE_PLAYABLE_HINT"

    fun rootParams(): MediaLibraryService.LibraryParams =
        MediaLibraryService.LibraryParams.Builder()
            .setExtras(styleExtras(GRID, GRID, supported = true))
            .build()

    fun childParams(parent: AndroidAutoRoute): MediaLibraryService.LibraryParams =
        MediaLibraryService.LibraryParams.Builder()
            .setExtras(
                when (parent) {
                    AndroidAutoRoute.Home,
                    AndroidAutoRoute.Explore,
                    AndroidAutoRoute.Library,
                    -> styleExtras(GRID, GRID)

                    else -> styleExtras(LIST, LIST)
                },
            )
            .build()

    private fun styleExtras(browsable: Int, playable: Int, supported: Boolean = false) =
        Bundle().apply {
            if (supported) putBoolean(SUPPORTED, true)
            putInt(BROWSABLE, browsable)
            putInt(PLAYABLE, playable)
            putInt(BROWSABLE_LEGACY, browsable)
            putInt(PLAYABLE_LEGACY, playable)
        }
}
```

If the existing Media3 version exposes official constants for any of these keys, import those constants instead of duplicating their strings; keep the legacy `android.media.extras.*` aliases because upstream explicitly sends both.

- [ ] **Step 4: Return these params from the MediaLibrary callbacks**

In `PlaybackService.sessionCallback`:

```kotlin
override fun onGetLibraryRoot(...): ListenableFuture<LibraryResult<MediaItem>> =
    Futures.immediateFuture(
        LibraryResult.ofItem(androidAutoCatalog.root(), AndroidAutoBrowseHints.rootParams()),
    )
```

In `onGetChildren`, after parsing `route`:

```kotlin
val returnParams = AndroidAutoBrowseHints.childParams(route)
androidAutoCatalog.children(route, page, pageSize).fold(
    onSuccess = { LibraryResult.ofItemList(it, returnParams) },
    onFailure = { LibraryResult.ofError(SessionError.ERROR_IO) },
)
```

Keep `onGetItem` item metadata unchanged. In `onGetSearchResult`, use list-style params:

```kotlin
LibraryResult.ofItemList(
    it,
    AndroidAutoBrowseHints.childParams(AndroidAutoRoute.Recent),
)
```

- [ ] **Step 5: Run focused tests**

```bash
./gradlew testDevDebugUnitTest --tests "com.music.bitchord.AndroidAutoBrowseHintsTest" --tests "com.music.bitchord.AndroidAutoCatalogTest" --tests "com.music.bitchord.AndroidAutoSearchTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/music/bitchord/playback/AndroidAutoBrowseHints.kt \
        app/src/main/java/com/music/bitchord/playback/PlaybackService.kt \
        app/src/test/java/com/music/bitchord/AndroidAutoBrowseHintsTest.kt
git commit -m "feat(auto): add Android Auto content style hints"
```

---

### Task 5: Add Media3 browser instrumentation coverage and strengthen manifest checks

**Files:**
- Create: `app/src/androidTest/java/com/music/bitchord/AndroidAutoMediaLibraryTest.kt`.
- Modify: `app/src/androidTest/java/com/music/bitchord/TanTovCarManifestTest.kt`.
- Verify: `app/src/dev/AndroidManifest.xml` and `app/src/dev/res/xml/automotive_app_desc.xml`.
- Modify: `.github/workflows/android-auto-legacy-smoke.yml`.

**Interfaces:**
- Consumes: `PlaybackService` as a Media3 `MediaLibraryService` and legacy `MediaBrowserService`.
- Produces: emulator proof that modern Media3 and legacy Android Auto clients can both browse TanTov.

- [ ] **Step 1: Create the Media3 instrumentation test from upstream, adapted to TanTov IDs**

Core assertions:

```kotlin
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class AndroidAutoMediaLibraryTest {
    private lateinit var mediaBrowser: MediaBrowser

    @Test
    fun rootAndTopLevelChildrenUseStableTanTovIds() = runBlocking {
        val root = withContext(Dispatchers.Main) {
            mediaBrowser.getLibraryRoot(null).get(10, TimeUnit.SECONDS)
        }.value!!
        assertEquals("tantov:auto:v1:root", root.mediaId)
        assertTrue(root.mediaMetadata.isBrowsable == true)

        val children = withContext(Dispatchers.Main) {
            mediaBrowser.getChildren(root.mediaId, 0, 20, null).get(10, TimeUnit.SECONDS)
        }.value!!

        assertEquals(
            listOf("Home", "Explore", "Recently Played", "Library"),
            children.map { it.mediaMetadata.title.toString() },
        )
    }
}
```

Also include upstream-style `getItem(...)`, `search(...)`, and `getSearchResult(...)` smoke tests, but assert only non-crash/non-null for network-backed search so CI does not depend on a signed-in account.

- [ ] **Step 2: Strengthen the manifest instrumentation test**

In `TanTovCarManifestTest`, resolve `PlaybackService` and inspect its intent filters via `PackageManager.queryIntentServices(...)` for both actions:

```kotlin
val media3 = Intent("androidx.media3.session.MediaLibraryService").setPackage(context.packageName)
val legacy = Intent("android.media.browse.MediaBrowserService").setPackage(context.packageName)
assertTrue(context.packageManager.queryIntentServices(media3, PackageManager.ResolveInfoFlags.of(0)).isNotEmpty())
assertTrue(context.packageManager.queryIntentServices(legacy, PackageManager.ResolveInfoFlags.of(0)).isNotEmpty())
```

Keep the existing assertions for `MEDIA_TEMPLATES`, `media`, `template`, min car API 8, and the attribution icon.

- [ ] **Step 3: Confirm the dev manifest already contains the required declarations**

Expected merged source entries in `app/src/dev/AndroidManifest.xml`:

```xml
<service android:name=".playback.PlaybackService">
    <intent-filter>
        <action android:name="androidx.media3.session.MediaLibraryService" />
        <action android:name="android.media.browse.MediaBrowserService" />
    </intent-filter>
</service>
```

Expected `app/src/dev/res/xml/automotive_app_desc.xml`:

```xml
<automotiveApp>
    <uses name="media" />
    <uses name="template" />
</automotiveApp>
```

Do not move these dev-only TanTov declarations into the production/main flavor in this integration.

- [ ] **Step 4: Update the emulator workflow**

Change push branches to include the integration branch:

```yaml
branches:
  - feat/android-auto-full
  - feat/tantov-music-v1
  - feat/tantov-auto-dashboard
  - integration/bitChord-android-auto-2026-09
```

Add the new test class to the instrumentation runner list:

```text
com.music.bitchord.AndroidAutoLegacyBrowserTest,
com.music.bitchord.AndroidAutoMediaLibraryTest,
com.music.bitchord.TanTovTopBarBrandingTest,
com.music.bitchord.TanTovCarManifestTest
```

Keep API 35, Google APIs, x86_64, and Java 17.

- [ ] **Step 5: Run compile-time instrumentation checks locally**

```bash
./gradlew compileDevDebugAndroidTestKotlin
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/androidTest/java/com/music/bitchord/AndroidAutoMediaLibraryTest.kt \
        app/src/androidTest/java/com/music/bitchord/TanTovCarManifestTest.kt \
        .github/workflows/android-auto-legacy-smoke.yml
git commit -m "test(auto): add Media3 Android Auto smoke coverage"
```

---

### Task 6: Regression-test Local Music, search, playback handoff, and TanTov templates

**Files:**
- Test/verify existing:
  - `app/src/test/java/com/music/bitchord/AndroidAutoLocalMusicTest.kt`
  - `app/src/test/java/com/music/bitchord/AndroidAutoSearchTest.kt`
  - `app/src/test/java/com/music/bitchord/AndroidAutoVoiceSearchTest.kt`
  - `app/src/test/java/com/music/bitchord/AndroidAutoDashboardTest.kt`
  - `app/src/test/java/com/music/bitchord/AndroidAutoDashboardSourceTest.kt`
  - `app/src/test/java/com/music/bitchord/TanTovCarTemplateTest.kt`
  - `app/src/test/java/com/music/bitchord/AndroidAutoSessionCommandsTest.kt`
  - `app/src/test/java/com/music/bitchord/AndroidAutoRepeatCommandTest.kt`
- Modify only failing tests/production code when the failure is caused by this integration.

**Interfaces:**
- Confirms car browse rows still resolve through `AndroidAutoCatalog.playableTrack(...)` to normal phone `MediaItem`s.
- Confirms Local Music remains available independently of online/auth state.

- [ ] **Step 1: Run the full focused Android Auto unit-test group**

```bash
./gradlew testDevDebugUnitTest \
  --tests "com.music.bitchord.AndroidAutoCatalogTest" \
  --tests "com.music.bitchord.AndroidAutoLocalMusicTest" \
  --tests "com.music.bitchord.AndroidAutoSearchTest" \
  --tests "com.music.bitchord.AndroidAutoVoiceSearchTest" \
  --tests "com.music.bitchord.AndroidAutoDashboardTest" \
  --tests "com.music.bitchord.AndroidAutoDashboardSourceTest" \
  --tests "com.music.bitchord.TanTovCarTemplateTest" \
  --tests "com.music.bitchord.AndroidAutoSessionCommandsTest" \
  --tests "com.music.bitchord.AndroidAutoRepeatCommandTest" \
  --tests "com.music.bitchord.AndroidAutoBrowseHintsTest"
```

Expected: all PASS.

- [ ] **Step 2: Verify the key playback-handoff invariant**

The existing/updated tests must continue asserting:

```kotlin
val playable = catalog.playableTrack(carRow).getOrThrow()
assertFalse(playable.mediaId.startsWith("tantov:auto:"))
assertEquals(expectedVideoId, playable.mediaId)
```

This ensures Android Auto never sends a synthetic browse ID directly to ExoPlayer.

- [ ] **Step 3: Verify Local Music is independent of auth/network**

The test suite must prove:

```kotlin
fake.signedIn = false
val library = catalog.children(AndroidAutoRoute.Library, 0, 20).getOrThrow()
assertTrue(library.any { AndroidAutoMediaIds.parse(it.mediaId) == AndroidAutoRoute.LocalMusic })
```

If the existing local-music test expresses the same invariant differently, keep that existing assertion rather than duplicating it.

- [ ] **Step 4: Run the complete dev unit suite**

```bash
./gradlew testDevDebugUnitTest
```

Expected: PASS.

- [ ] **Step 5: Commit any integration-only regression fixes**

If no files changed, do not create an empty commit. If fixes were needed:

```bash
git add <only-the-files-changed-for-these-regressions>
git commit -m "fix(auto): preserve TanTov car regressions"
```

---

### Task 7: Build the test APK and verify CI before real-car testing

**Files:**
- No planned production-source changes.
- Verify `.github/workflows/android.yml` remains Java 17 + `testDevDebugUnitTest` + `assembleDevDebug`.

**Interfaces:**
- Produces: installable `app/build/outputs/apk/dev/debug/app-dev-debug.apk` from the integration branch.

- [ ] **Step 1: Run the same validation as CI**

```bash
./gradlew testDevDebugUnitTest assembleDevDebug
```

Expected: `BUILD SUCCESSFUL` and APK exists at:

```text
app/build/outputs/apk/dev/debug/app-dev-debug.apk
```

- [ ] **Step 2: Push the completed integration branch and wait for both workflows**

Required checks:

```text
Android CI / dev-check
Android Auto Legacy Browser Smoke / legacy-browser-smoke
```

Both must be green before the APK is considered ready for the car.

- [ ] **Step 3: Verify the branch has no unintended upstream bulk merge**

Run:

```bash
git diff --stat feat/tantov-auto-dashboard...HEAD
git log --oneline feat/tantov-auto-dashboard..HEAD
```

Expected changed areas are limited to:

```text
YtMusicRepository Android Auto feed helpers
AndroidAutoDataSource
AndroidAutoCatalog
AndroidAutoBrowseHints
PlaybackService Android Auto callback params
Android Auto/TanTov tests
Android Auto smoke workflow
this integration plan/spec
```

No general UI redesign, source-module system, audio pipeline rewrite, or unrelated upstream feature should appear.

- [ ] **Step 4: Record the APK-producing commit SHA**

```bash
git rev-parse HEAD
```

Use that exact SHA when handing the APK to the user so any car-test report can be tied to one build.

---

### Task 8: Real Hyundai Kona acceptance test before merge

**Files:**
- No source changes unless the real-car test exposes a defect.

**Interfaces:**
- Consumes: test APK from Task 7 installed on the user's Samsung phone.
- Produces: go/no-go result for merging into the TanTov feature line.

- [ ] **Step 1: Phone-side preparation**

Install the integration APK, open TanTov once, grant local audio/notification permissions needed by the current app, and confirm normal phone playback works before connecting Android Auto.

- [ ] **Step 2: Confirm Android Auto discovery**

On the Hyundai Kona, verify:

```text
TanTov appears in the Android Auto launcher
TanTov icon and label are correct
opening TanTov does not show a blank/error screen
```

- [ ] **Step 3: Browse all primary destinations**

Verify:

```text
Home
Recently Played
Quick Picks
Browse/Explore
Library
Local Music
```

Home should keep TanTov's custom dashboard order, with Local Music first, then Recently Played when available, then Quick Picks, then Listen Again/other shelves.

- [ ] **Step 4: Verify playback controls**

Start one online track and one Local Music track from the car. For each, verify:

```text
tap starts the intended track
artwork/title/artist are correct
play/pause works
next works
previous works
phone now-playing state stays synchronized
```

- [ ] **Step 5: Verify search**

Use Android Auto search/voice search for a known song. Confirm TanTov receives the result and starts a normal playable item rather than exposing a synthetic `tantov:auto:*` ID to playback.

- [ ] **Step 6: Verify reconnect behavior**

Disconnect Android Auto, reconnect, reopen TanTov, and confirm the browse tree still loads without reinstalling or force-stopping the app.

- [ ] **Step 7: Gate the merge**

Only after all acceptance checks pass should the integration branch be merged into the TanTov feature branch. Any car-only defect must be fixed on `integration/bitChord-android-auto-2026-09`, re-run through Tasks 6-7, and re-tested on the Kona before merge.

---

## Self-Review

### Spec coverage

- Preserve TanTov custom dashboard: Tasks 2-3 and Global Constraints.
- Android Auto discovery/service compatibility: Task 5.
- MediaLibraryService root/children/item/search: Tasks 4-5.
- Home/Recents/Quick Picks/Playlists/Liked/Library/Local Music/Search: Tasks 1-3 and existing catalog/library tests in Task 6.
- Browse caching/performance: Task 2.
- Artwork/presentation compatibility: Task 4 preserves existing `CarArtworkLoader` and adds host content-style hints.
- Playback handoff: Task 6.
- Build/test APK: Task 7.
- Real-car acceptance: Task 8.
- Synced lyrics intentionally excluded from this batch per Global Constraints because it changes service playback metadata globally and is not required for Android Auto discovery/browse acceptance.

### Placeholder scan

No implementation step uses `TBD`, `TODO`, or an unspecified "handle errors" instruction. Failure behavior is explicitly defined: Recents/Quick Picks cache failures propagate at the catalog boundary unless dashboard partial-failure handling can preserve other shelves; Local Music remains independent; Quick Picks failure must not erase healthy Home shelves.

### Type consistency

- `AndroidAutoDataSource.recents(): Result<List<Song>>` is used consistently by `AndroidAutoCatalog.recents()`.
- `AndroidAutoDataSource.quickPicks(Set<String>): Result<List<Song>>` is used consistently by `AndroidAutoCatalog.quickPicks()`.
- `AndroidAutoBrowseHints.rootParams()` and `childParams(AndroidAutoRoute)` both return `MediaLibraryService.LibraryParams` and are consumed only by `PlaybackService` MediaLibrary results.
- Existing stable `AndroidAutoRoute`/`AndroidAutoMediaIds` types are unchanged.
