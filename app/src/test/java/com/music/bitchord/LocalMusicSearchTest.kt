package com.music.bitchord

import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.mergeSongSearchResults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Task 6 regression coverage for combined online + on-device search behavior.
class LocalMusicSearchTest {
    private fun song(
        id: String,
        title: String,
        artist: String,
        localUri: String? = null,
    ) = Song(
        videoId = id,
        title = title,
        artist = artist,
        thumbnailUrl = null,
        localUri = localUri,
    )

    @Test
    fun localSongsAreIncludedAfterOnlineSongs() {
        val onlineSong = song("yt", "Yellow", "Coldplay")
        val localSong = song(
            id = "local",
            title = "Yellow Live",
            artist = "Coldplay",
            localUri = "content://local/yellow-live",
        )

        val merged = mergeSongSearchResults(
            Result.success(listOf(SearchResult.Track(onlineSong))),
            listOf(localSong),
        ).getOrThrow()

        assertEquals(
            listOf("yt", "local"),
            merged.filterIsInstance<SearchResult.Track>().map { it.song.videoId },
        )
    }

    @Test
    fun onlineFailureStillReturnsLocalSongs() {
        val localSong = song(
            id = "local",
            title = "Local Track",
            artist = "Artist",
            localUri = "content://local/track",
        )

        val merged = mergeSongSearchResults(
            Result.failure(IllegalStateException("offline")),
            listOf(localSong),
        ).getOrThrow()

        assertEquals("Local Track", (merged.single() as SearchResult.Track).song.title)
    }

    @Test
    fun onlineFailureWithoutLocalMatchesRemainsFailure() {
        val failure = IllegalStateException("offline")

        val merged = mergeSongSearchResults(
            Result.failure(failure),
            emptyList(),
        )

        assertTrue(merged.isFailure)
        assertEquals(failure, merged.exceptionOrNull())
    }
}
