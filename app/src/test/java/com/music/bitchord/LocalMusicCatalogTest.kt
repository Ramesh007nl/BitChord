package com.music.bitchord

import com.music.bitchord.data.local.LocalMusicCatalog
import com.music.bitchord.data.local.LocalMusicTrack
import com.music.bitchord.data.local.filterLocalMusicFolders
import com.music.bitchord.data.local.mediaStoreStorageIdentity
import com.music.bitchord.data.local.safStorageIdentity
import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    @Test
    fun folderBrowseSearchMatchesLeafNameOrParentPath() {
        val folders = LocalMusicCatalog.merge(
            listOf(
                track("music", "music", "Music/Tamil"),
                track("download", "download", "Downloads/Tamil"),
                track("rock", "rock", "Music/Rock"),
            ),
        ).folders

        assertEquals(
            setOf("Music/Tamil", "Downloads/Tamil"),
            filterLocalMusicFolders(folders, "Tamil").map { it.key }.toSet(),
        )
        assertEquals(
            listOf("Downloads/Tamil"),
            filterLocalMusicFolders(folders, "Downloads").map { it.key },
        )
    }

    @Test
    fun equalLeafFolderNamesRemainDistinctByFullPath() {
        val catalog = LocalMusicCatalog.merge(
            listOf(
                track("music", "music", "Music/Tamil"),
                track("downloads", "downloads", "Downloads/Tamil"),
            ),
        )

        assertEquals(2, catalog.folders.size)
        assertEquals(setOf("Music/Tamil", "Downloads/Tamil"), catalog.folders.map { it.key }.toSet())
    }

    @Test
    fun primaryMediaStoreAndSafDocumentShareCanonicalIdentity() {
        val media = mediaStoreStorageIdentity(
            volumeName = "external_primary",
            relativePath = "Music/Tamil/",
            displayName = "song.mp3",
            legacyPath = null,
            mediaId = 42L,
            durationMs = 123_000L,
        )
        val saf = safStorageIdentity(
            documentId = "primary:Music/Tamil/song.mp3",
            uri = "content://com.android.externalstorage.documents/document/primary%3AMusic%2FTamil%2Fsong.mp3",
            sizeBytes = 4_000_000L,
        )

        assertEquals(media, saf)
    }

    @Test
    fun differentPathsDoNotCollapseEvenWhenFileNamesMatch() {
        val first = mediaStoreStorageIdentity(
            volumeName = "external_primary",
            relativePath = "Music/AlbumA/",
            displayName = "Track 01.mp3",
            legacyPath = null,
            mediaId = 1L,
            durationMs = 180_000L,
        )
        val second = mediaStoreStorageIdentity(
            volumeName = "external_primary",
            relativePath = "Music/AlbumB/",
            displayName = "Track 01.mp3",
            legacyPath = null,
            mediaId = 2L,
            durationMs = 180_000L,
        )

        assertNotEquals(first, second)
    }
}
