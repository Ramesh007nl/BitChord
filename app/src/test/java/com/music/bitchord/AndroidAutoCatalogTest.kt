package com.music.bitchord

import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.ArtistPage
import com.music.bitchord.data.model.HomeFeed
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.AndroidAutoCatalog
import com.music.bitchord.playback.AndroidAutoDataSource
import com.music.bitchord.playback.AndroidAutoMediaIds
import com.music.bitchord.playback.AndroidAutoRoute
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAutoCatalogTest {
    private class FakeAutoDataSource : AndroidAutoDataSource {
        var homeResult: Result<HomeFeed> = Result.success(HomeFeed(emptyList(), null))
        var exploreResult: Result<List<HomeShelf>> = Result.success(emptyList())
        var historyResult: Result<List<Song>> = Result.success(emptyList())
        var libraryResult: Result<LibraryPage> = Result.success(LibraryPage(emptyList(), emptyList(), emptyList()))
        var signedIn: Boolean = true

        override suspend fun home() = homeResult
        override suspend fun explore() = exploreResult
        override suspend fun history() = historyResult
        override suspend fun library() = libraryResult
        override suspend fun browseSongs(browseId: String): Result<YtMusicRepository.SongPage> =
            Result.failure(IllegalArgumentException("No browse page for $browseId"))
        override suspend fun artistPage(browseId: String): Result<ArtistPage> =
            Result.failure(IllegalArgumentException("No artist page for $browseId"))
        override suspend fun search(query: String, filter: SearchFilter): Result<List<SearchResult>> =
            Result.success(emptyList())
        override fun isSignedIn() = signedIn
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
        val song = Song(
            videoId = "vid1",
            title = "Track",
            artist = "Artist",
            thumbnailUrl = null,
            durationText = "3:30",
        )
        val fake = FakeAutoDataSource().apply {
            historyResult = Result.success(listOf(song))
        }
        val catalog = AndroidAutoCatalog(fake)

        val row = catalog.children(AndroidAutoRoute.Recent, 0, 20).getOrThrow().single()
        assertEquals(AndroidAutoRoute.Track("vid1"), AndroidAutoMediaIds.parse(row.mediaId))

        val playable = catalog.playableTrack(row).getOrThrow()
        assertEquals("vid1", playable.mediaId)
        assertFalse(playable.mediaId.startsWith("bitchord:auto:"))
        assertEquals("Track", playable.mediaMetadata.title.toString())
        assertEquals("Artist", playable.mediaMetadata.artist.toString())
    }
}
