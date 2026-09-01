package com.music.bitchord

import com.music.bitchord.data.local.LocalMusicCatalog
import com.music.bitchord.data.local.LocalMusicTrack
import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalMusicCatalogTest {
    private fun track(id: String, identity: String, folder: String) = LocalMusicTrack(
        song = Song(
            videoId = id,
            title = id,
            artist = "Artist",
            thumbnailUrl = null,
            localUri = id,
        ),
        folderKey = folder,
        folderLabel = folder.substringAfterLast('/'),
        identity = identity,
    )

    @Test
    fun aPlusBDeduplicatesTheSameTrackIdentity() {
        val allMusic = listOf(track("media://one", "same-file", "Music/Tamil"))
        val selectedFolder = listOf(
            track("tree://one", "same-file", "Tamil"),
            track("tree://two", "second-file", "Tamil"),
        )

        val catalog = LocalMusicCatalog.merge(allMusic, selectedFolder)

        assertEquals(listOf("media://one", "tree://two"), catalog.songs.map { it.videoId })
    }

    @Test
    fun foldersAreGroupedAndSortedByDisplayName() {
        val catalog = LocalMusicCatalog.merge(
            listOf(
                track("b", "b", "Music/Zulu"),
                track("a", "a", "Music/Albums"),
                track("c", "c", "Music/Albums"),
            ),
        )

        assertEquals(listOf("Albums", "Zulu"), catalog.folders.map { it.label })
        assertEquals(listOf("a", "c"), catalog.folders.first().songs.map { it.videoId })
    }
}
