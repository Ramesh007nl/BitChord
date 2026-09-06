package com.music.bitchord

import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.local.LocalMusicCatalog
import com.music.bitchord.data.local.LocalMusicTrack
import com.music.bitchord.data.model.ArtistPage
import com.music.bitchord.data.model.HomeFeed
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.AndroidAutoCatalog
import com.music.bitchord.playback.AndroidAutoDataSource
import com.music.bitchord.playback.AndroidAutoLocalDataSource
import com.music.bitchord.playback.AndroidAutoRoute
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidAutoRealCarFeedbackTest {
    private class OnlineSource : AndroidAutoDataSource {
        override suspend fun home() = Result.success(HomeFeed(emptyList(), null))
        override suspend fun explore() = Result.success(emptyList<HomeShelf>())
        override suspend fun history() = Result.success(emptyList<Song>())
        override suspend fun recents() = Result.success(emptyList<Song>())
        override suspend fun quickPicks(excludeSongIds: Set<String>) = Result.success(emptyList<Song>())
        override suspend fun library() = Result.success(
            LibraryPage(
                likedSongs = listOf(song("liked", "https://img.example/w120-h120/liked.jpg")),
                librarySongs = emptyList(),
                shelves = listOf(
                    HomeShelf(
                        title = "Playlists",
                        items = listOf(
                            ShelfItem(
                                title = "Road Trip",
                                subtitle = "Playlist",
                                thumbnailUrl = "https://img.example/w120-h120/playlist.jpg",
                                videoId = null,
                                browseId = "VLPL-road",
                            ),
                        ),
                    ),
                ),
            ),
        )
        override suspend fun browseSongs(browseId: String) =
            Result.success(YtMusicRepository.SongPage(emptyList(), null))
        override suspend fun artistPage(browseId: String) =
            Result.success(ArtistPage(emptyList(), null, emptyList()))
        override suspend fun search(query: String, filter: SearchFilter) = Result.success(emptyList<SearchResult>())
        override fun isSignedIn() = true
    }

    private class CountingLocalSource : AndroidAutoLocalDataSource {
        var catalogCalls = 0
        private val localSong = song("local", "https://img.example/w120-h120/local.jpg").copy(
            localUri = "content://media/external/audio/media/1",
            localPath = "Music/local.mp3",
        )
        private val catalog = LocalMusicCatalog(
            listOf(
                LocalMusicTrack(
                    song = localSong,
                    folderKey = "Music",
                    folderLabel = "Music",
                    identity = "storage:primary:music/local.mp3",
                ),
            ),
        )

        override suspend fun catalog(): LocalMusicCatalog {
            catalogCalls++
            return catalog
        }

        override suspend fun search(query: String): List<Song> = catalog.search(query)
    }

    @Test
    fun rootNavigationUsesReferenceLabels() = runBlocking {
        val catalog = AndroidAutoCatalog(OnlineSource())
        val rows = catalog.children(AndroidAutoRoute.Root, 0, 20).getOrThrow()

        assertEquals(
            listOf("Home", "Recents", "Browse", "Library"),
            rows.map { it.mediaMetadata.title.toString() },
        )
    }

    @Test
    fun openingLibraryDoesNotTriggerFullLocalMusicScan() = runBlocking {
        val local = CountingLocalSource()
        val catalog = AndroidAutoCatalog(OnlineSource(), local)

        val rows = catalog.children(AndroidAutoRoute.Library, 0, 20).getOrThrow()

        assertEquals(0, local.catalogCalls)
        assertTrue(rows.any { it.mediaMetadata.title.toString() == "Local Music" })
    }

    @Test
    fun libraryRootImmediatelyMirrorsMobileCategories() = runBlocking {
        val catalog = AndroidAutoCatalog(OnlineSource(), CountingLocalSource())

        val rows = catalog.children(AndroidAutoRoute.Library, 0, 20).getOrThrow()

        assertEquals(
            listOf(
                "Local Music",
                "Liked Songs",
                "Songs",
                "Playlists",
                "Albums",
                "Artists",
                "Subscriptions",
                "Podcasts",
            ),
            rows.map { it.mediaMetadata.title.toString() },
        )
    }

    @Test
    fun libraryGridCategoriesAlwaysHaveArtwork() = runBlocking {
        val catalog = AndroidAutoCatalog(OnlineSource(), CountingLocalSource())

        val rows = catalog.children(AndroidAutoRoute.Library, 0, 20).getOrThrow()

        assertTrue(rows.isNotEmpty())
        rows.forEach { row ->
            assertNotNull("${row.mediaMetadata.title} must have artwork", row.mediaMetadata.artworkUri)
        }
    }

    @Test
    fun androidAutoDataSourceExposesHomeContinuationApi() {
        assertTrue(
            AndroidAutoDataSource::class.java.methods.any { method -> method.name == "moreHome" },
        )
    }

    private companion object {
        fun song(id: String, artwork: String?) = Song(
            videoId = id,
            title = "Track $id",
            artist = "Artist",
            thumbnailUrl = artwork,
            durationText = "3:30",
        )
    }
}
