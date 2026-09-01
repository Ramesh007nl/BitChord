package com.music.bitchord

import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.ArtistPage
import com.music.bitchord.data.model.BrowseItem
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.HomeFeed
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.AndroidAutoCatalog
import com.music.bitchord.playback.AndroidAutoCollectionKind
import com.music.bitchord.playback.AndroidAutoDataSource
import com.music.bitchord.playback.AndroidAutoMediaIds
import com.music.bitchord.playback.AndroidAutoRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidAutoSearchTest {
    private class FakeSearchDataSource : AndroidAutoDataSource {
        val searches = mutableMapOf<Pair<String, SearchFilter>, Result<List<SearchResult>>>()
        var activeSearches = 0
        var maxConcurrentSearches = 0
        var searchCalls = 0
        var delayMs = 30L

        override suspend fun home() = Result.success(HomeFeed(emptyList(), null))
        override suspend fun explore() = Result.success(emptyList<HomeShelf>())
        override suspend fun history() = Result.success(emptyList<Song>())
        override suspend fun library() = Result.success(LibraryPage(emptyList(), emptyList(), emptyList()))
        override suspend fun browseSongs(browseId: String): Result<YtMusicRepository.SongPage> =
            Result.failure(IllegalArgumentException("not used"))
        override suspend fun artistPage(browseId: String): Result<ArtistPage> =
            Result.failure(IllegalArgumentException("not used"))

        override suspend fun search(query: String, filter: SearchFilter): Result<List<SearchResult>> {
            searchCalls++
            activeSearches++
            maxConcurrentSearches = maxOf(maxConcurrentSearches, activeSearches)
            try {
                delay(delayMs)
                return searches[query to filter] ?: Result.success(emptyList())
            } finally {
                activeSearches--
            }
        }

        override fun isSignedIn() = true
    }

    @Test
    fun mixedSearchRunsBrowseFiltersConcurrentlyAndCachesNormalizedQuery() = runBlocking {
        val song = Song("song-1", "Jai Ho", "A.R. Rahman", null, "5:19")
        val album = BrowseItem("MPRE-album", "Slumdog Millionaire", "Album", null, BrowseType.ALBUM)
        val artist = BrowseItem("UC-rahman", "A.R. Rahman", "Artist", null, BrowseType.ARTIST)
        val playlist = BrowseItem("VLPL-rahman", "Rahman Mix", "Playlist", null, BrowseType.PLAYLIST)
        val fake = FakeSearchDataSource().apply {
            searches["rahman" to SearchFilter.SONGS] = Result.success(listOf(SearchResult.Track(song)))
            searches["rahman" to SearchFilter.ALBUMS] = Result.success(listOf(SearchResult.Browse(album)))
            searches["rahman" to SearchFilter.ARTISTS] = Result.success(listOf(SearchResult.Browse(artist)))
            searches["rahman" to SearchFilter.PLAYLISTS] = Result.success(listOf(SearchResult.Browse(playlist)))
        }
        val catalog = AndroidAutoCatalog(fake)

        val rows = catalog.search("rahman", 0, 20).getOrThrow()
        assertEquals(
            listOf(
                AndroidAutoRoute.Track("song-1"),
                AndroidAutoRoute.Collection(AndroidAutoCollectionKind.ALBUM, "MPRE-album"),
                AndroidAutoRoute.Collection(AndroidAutoCollectionKind.ARTIST, "UC-rahman"),
                AndroidAutoRoute.Collection(AndroidAutoCollectionKind.PLAYLIST, "VLPL-rahman"),
            ),
            rows.mapNotNull { AndroidAutoMediaIds.parse(it.mediaId) },
        )
        assertEquals(3, fake.maxConcurrentSearches)
        assertEquals(4, fake.searchCalls)

        catalog.search("  RAHMAN  ", 0, 20).getOrThrow()
        assertEquals(4, fake.searchCalls)
    }
}
