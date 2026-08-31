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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AndroidAutoVoiceSearchTest {
    private class FakeVoiceDataSource : AndroidAutoDataSource {
        override suspend fun home() = Result.success(HomeFeed(emptyList(), null))
        override suspend fun explore() = Result.success(emptyList<HomeShelf>())
        override suspend fun history() = Result.success(emptyList<Song>())
        override suspend fun library() = Result.success(LibraryPage(emptyList(), emptyList(), emptyList()))
        override suspend fun browseSongs(browseId: String): Result<YtMusicRepository.SongPage> =
            Result.failure(IllegalArgumentException("not used"))
        override suspend fun artistPage(browseId: String): Result<ArtistPage> =
            Result.failure(IllegalArgumentException("not used"))

        override suspend fun search(query: String, filter: SearchFilter): Result<List<SearchResult>> =
            if (filter == SearchFilter.SONGS) {
                Result.success(
                    listOf(
                        SearchResult.Track(
                            Song(
                                videoId = "song-1",
                                title = "Jai Ho",
                                artist = "A.R. Rahman",
                                thumbnailUrl = null,
                                durationText = "5:19",
                            ),
                        ),
                    ),
                )
            } else {
                Result.success(emptyList())
            }

        override fun isSignedIn() = true
    }

    @Test
    fun voiceSearchResolvesFirstSongToNormalPlayableBitChordItem() = runBlocking {
        val catalog = AndroidAutoCatalog(FakeVoiceDataSource())

        val playable = catalog.playableSearchResult("Rahman").getOrThrow()

        assertEquals("song-1", playable.mediaId)
        assertFalse(playable.mediaId.startsWith("bitchord:auto:"))
        assertEquals("Jai Ho", playable.mediaMetadata.title.toString())
    }
}
