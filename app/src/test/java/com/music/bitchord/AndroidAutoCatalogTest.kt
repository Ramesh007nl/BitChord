package com.music.bitchord

import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.ArtistPage
import com.music.bitchord.data.model.HomeFeed
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.AndroidAutoCatalog
import com.music.bitchord.playback.AndroidAutoCollectionKind
import com.music.bitchord.playback.AndroidAutoDataSource
import com.music.bitchord.playback.AndroidAutoLibrarySection
import com.music.bitchord.playback.AndroidAutoMediaIds
import com.music.bitchord.playback.AndroidAutoRoute
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AndroidAutoCatalogTest {
    private class FakeAutoDataSource : AndroidAutoDataSource {
        var homeResult: Result<HomeFeed> = Result.success(HomeFeed(emptyList(), null))
        var exploreResult: Result<List<HomeShelf>> = Result.success(emptyList())
        var historyResult: Result<List<Song>> = Result.success(emptyList())
        var recentsResult: Result<List<Song>> = Result.success(emptyList())
        var quickPicksResult: Result<List<Song>> = Result.success(emptyList())
        var quickPicksExcludedIds: Set<String> = emptySet()
        var libraryResult: Result<LibraryPage> = Result.success(LibraryPage(emptyList(), emptyList(), emptyList()))
        val browsePages = mutableMapOf<String, Result<YtMusicRepository.SongPage>>()
        val artistPages = mutableMapOf<String, Result<ArtistPage>>()
        val searches = mutableMapOf<Pair<String, SearchFilter>, Result<List<SearchResult>>>()
        var signedIn: Boolean = true
        var homeCalls = 0
        var historyCalls = 0
        var recentsCalls = 0
        var quickPicksCalls = 0
        var libraryCalls = 0
        var browseCalls = 0

        override suspend fun home(): Result<HomeFeed> {
            homeCalls++
            return homeResult
        }

        override suspend fun explore() = exploreResult

        override suspend fun history(): Result<List<Song>> {
            historyCalls++
            return historyResult
        }

        override suspend fun recents(): Result<List<Song>> {
            recentsCalls++
            return recentsResult
        }

        override suspend fun quickPicks(excludeSongIds: Set<String>): Result<List<Song>> {
            quickPicksCalls++
            quickPicksExcludedIds = excludeSongIds
            return quickPicksResult
        }

        override suspend fun library(): Result<LibraryPage> {
            libraryCalls++
            return libraryResult
        }

        override suspend fun browseSongs(browseId: String): Result<YtMusicRepository.SongPage> {
            browseCalls++
            return browsePages[browseId]
                ?: Result.failure(IllegalArgumentException("No browse page for $browseId"))
        }

        override suspend fun artistPage(browseId: String): Result<ArtistPage> =
            artistPages[browseId]
                ?: Result.failure(IllegalArgumentException("No artist page for $browseId"))

        override suspend fun search(query: String, filter: SearchFilter): Result<List<SearchResult>> =
            searches[query to filter] ?: Result.success(emptyList())

        override fun isSignedIn() = signedIn
    }

    private fun song(id: String, title: String = "Track $id") = Song(
        videoId = id,
        title = title,
        artist = "Artist",
        thumbnailUrl = null,
        durationText = "3:30",
    )

    @Test
    fun dataSourceContractExposesRecentsAndQuickPicks() = runBlocking {
        val recent = song("recent-contract")
        val pick = song("pick-contract")
        val fake = FakeAutoDataSource().apply {
            recentsResult = Result.success(listOf(recent))
            quickPicksResult = Result.success(listOf(pick))
        }
        val source: AndroidAutoDataSource = fake

        assertEquals(listOf(recent), source.recents().getOrThrow())
        assertEquals(listOf(pick), source.quickPicks(setOf("excluded")).getOrThrow())
        assertEquals(setOf("excluded"), fake.quickPicksExcludedIds)
    }

    @Test
    fun rootHasExpectedOrderAndBrowsableFlags() = runBlocking {
        val catalog = AndroidAutoCatalog(FakeAutoDataSource())
        val children = catalog.children(AndroidAutoRoute.Root, 0, 20).getOrThrow()

        assertEquals(
            listOf("Home", "Explore", "Recently Played", "Library"),
            children.map { it.mediaMetadata.title.toString() },
        )
        assertTrue(children.all { it.mediaMetadata.isBrowsable == true })
        assertTrue(children.none { it.mediaMetadata.isPlayable == true })
    }

    @Test
    fun recentTrackResolvesBackToNormalBitChordPlayableItem() = runBlocking {
        val fake = FakeAutoDataSource().apply {
            recentsResult = Result.success(listOf(song("vid1", "Track")))
        }
        val catalog = AndroidAutoCatalog(fake)

        val row = catalog.children(AndroidAutoRoute.Recent, 0, 20).getOrThrow().single()
        assertEquals(AndroidAutoRoute.Track("vid1"), AndroidAutoMediaIds.parse(row.mediaId))

        val playable = catalog.playableTrack(row).getOrThrow()
        assertEquals("vid1", playable.mediaId)
        assertFalse(playable.mediaId.startsWith("tantov:auto:"))
        assertEquals("Track", playable.mediaMetadata.title.toString())
        assertEquals("Artist", playable.mediaMetadata.artist.toString())
    }

    @Test
    fun signedOutRecentUsesLocalRecentsFeed() = runBlocking {
        val fake = FakeAutoDataSource().apply {
            signedIn = false
            recentsResult = Result.success(listOf(song("local-recent", "Local Recent")))
        }
        val catalog = AndroidAutoCatalog(fake)

        val rows = catalog.children(AndroidAutoRoute.Recent, 0, 20).getOrThrow()

        assertEquals(
            listOf(AndroidAutoRoute.Track("local-recent")),
            rows.mapNotNull { AndroidAutoMediaIds.parse(it.mediaId) },
        )
        assertEquals(1, fake.recentsCalls)
        assertEquals(0, fake.historyCalls)
    }

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
        assertEquals(
            listOf(AndroidAutoRoute.Track("fresh")),
            quickRows.mapNotNull { AndroidAutoMediaIds.parse(it.mediaId) },
        )
    }

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

    @Test
    fun libraryOnlyShowsNonEmptyCategories() = runBlocking {
        val playlist = ShelfItem("Road Trip", "Playlist", null, null, "VLPL-road")
        val fake = FakeAutoDataSource().apply {
            libraryResult = Result.success(
                LibraryPage(
                    likedSongs = listOf(song("liked")),
                    librarySongs = emptyList(),
                    shelves = listOf(HomeShelf("Playlists", listOf(playlist))),
                ),
            )
        }
        val catalog = AndroidAutoCatalog(fake)

        val folders = catalog.children(AndroidAutoRoute.Library, 0, 20).getOrThrow()
        assertEquals(listOf("Liked Songs", "Playlists"), folders.map { it.mediaMetadata.title.toString() })
        assertEquals(
            listOf(
                AndroidAutoRoute.LibrarySection(AndroidAutoLibrarySection.LIKED),
                AndroidAutoRoute.LibrarySection(AndroidAutoLibrarySection.PLAYLISTS),
            ),
            folders.mapNotNull { AndroidAutoMediaIds.parse(it.mediaId) },
        )
    }

    @Test
    fun emittedPlaylistCanBeBrowsedButFabricatedCollectionFailsClosed() = runBlocking {
        val playlist = ShelfItem("Road Trip", "Playlist", null, null, "VLPL-road")
        val fake = FakeAutoDataSource().apply {
            libraryResult = Result.success(
                LibraryPage(emptyList(), emptyList(), listOf(HomeShelf("Playlists", listOf(playlist)))),
            )
            browsePages["VLPL-road"] = Result.success(YtMusicRepository.SongPage(listOf(song("inside")), null))
            browsePages["VLPL-fabricated"] = Result.success(YtMusicRepository.SongPage(listOf(song("bad")), null))
        }
        val catalog = AndroidAutoCatalog(fake)

        val collectionRow = catalog.children(
            AndroidAutoRoute.LibrarySection(AndroidAutoLibrarySection.PLAYLISTS),
            0,
            20,
        ).getOrThrow().single()
        val route = AndroidAutoMediaIds.parse(collectionRow.mediaId) as AndroidAutoRoute.Collection
        assertEquals(AndroidAutoCollectionKind.PLAYLIST, route.kind)

        val tracks = catalog.children(route, 0, 20).getOrThrow()
        assertEquals(listOf(AndroidAutoRoute.Track("inside")), tracks.mapNotNull { AndroidAutoMediaIds.parse(it.mediaId) })

        val fabricated = AndroidAutoRoute.Collection(AndroidAutoCollectionKind.PLAYLIST, "VLPL-fabricated")
        assertTrue(catalog.children(fabricated, 0, 20).isFailure)
        assertEquals(1, fake.browseCalls)
    }

    @Test
    fun homeUsesFiveMinuteCacheAndPagination() = runBlocking {
        var clock = 1_000L
        val shelfRows = (0 until 4).map { index ->
            HomeShelf(
                title = "Shelf $index",
                items = listOf(ShelfItem("Song $index", "Artist", null, "v$index", null)),
            )
        }
        val fake = FakeAutoDataSource().apply {
            homeResult = Result.success(HomeFeed(shelfRows, null))
        }
        val catalog = AndroidAutoCatalog(fake, nowMs = { clock })

        val firstPage = catalog.children(AndroidAutoRoute.Home, 0, 2).getOrThrow()
        val secondPage = catalog.children(AndroidAutoRoute.Home, 1, 2).getOrThrow()
        assertEquals(listOf("Shelf 0", "Shelf 1"), firstPage.map { it.mediaMetadata.title.toString() })
        assertEquals(listOf("Shelf 2", "Shelf 3"), secondPage.map { it.mediaMetadata.title.toString() })
        assertEquals(1, fake.homeCalls)

        clock += 5 * 60_000L + 1
        catalog.children(AndroidAutoRoute.Home, 0, 2).getOrThrow()
        assertEquals(2, fake.homeCalls)
    }

    @Test
    fun signedOutLibraryStaysAuthGatedWhileRecentsUseTheirOwnFeed() = runBlocking {
        val fake = FakeAutoDataSource().apply {
            signedIn = false
            recentsResult = Result.success(emptyList())
            historyResult = Result.failure(IOException("should not be called"))
            libraryResult = Result.failure(IOException("should not be called"))
        }
        val catalog = AndroidAutoCatalog(fake)

        assertTrue(catalog.children(AndroidAutoRoute.Recent, 0, 20).getOrThrow().isEmpty())
        assertTrue(catalog.children(AndroidAutoRoute.Library, 0, 20).getOrThrow().isEmpty())
        assertTrue(
            catalog.children(
                AndroidAutoRoute.LibrarySection(AndroidAutoLibrarySection.LIKED),
                0,
                20,
            ).getOrThrow().isEmpty(),
        )
        assertEquals(1, fake.recentsCalls)
        assertEquals(0, fake.historyCalls)
        assertEquals(0, fake.libraryCalls)
    }

    @Test
    fun signedInNetworkFailurePropagatesInsteadOfLookingLikeEmptyLibrary() = runBlocking {
        val fake = FakeAutoDataSource().apply {
            signedIn = true
            libraryResult = Result.failure(IOException("offline"))
        }
        val catalog = AndroidAutoCatalog(fake)

        assertTrue(catalog.children(AndroidAutoRoute.Library, 0, 20).isFailure)
        assertEquals(1, fake.libraryCalls)
    }
}
