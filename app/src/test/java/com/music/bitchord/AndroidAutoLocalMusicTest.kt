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
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.AndroidAutoCatalog
import com.music.bitchord.playback.AndroidAutoDataSource
import com.music.bitchord.playback.AndroidAutoLocalDataSource
import com.music.bitchord.playback.AndroidAutoLocalSection
import com.music.bitchord.playback.AndroidAutoMediaIds
import com.music.bitchord.playback.AndroidAutoRoute
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AndroidAutoLocalMusicTest {
    private class FakeOnlineDataSource : AndroidAutoDataSource {
        var signedIn = true
        var libraryCalls = 0
        var failSearch = false

        override suspend fun home() = Result.success(HomeFeed(emptyList(), null))
        override suspend fun explore() = Result.success(emptyList<HomeShelf>())
        override suspend fun history() = Result.success(emptyList<Song>())
        override suspend fun library(): Result<LibraryPage> {
            libraryCalls++
            return Result.success(LibraryPage(emptyList(), emptyList(), emptyList()))
        }
        override suspend fun browseSongs(browseId: String): Result<YtMusicRepository.SongPage> =
            Result.failure(IllegalArgumentException("not used"))
        override suspend fun artistPage(browseId: String): Result<ArtistPage> =
            Result.failure(IllegalArgumentException("not used"))
        override suspend fun search(query: String, filter: SearchFilter): Result<List<SearchResult>> =
            if (failSearch && filter == SearchFilter.SONGS) {
                Result.failure(IOException("offline"))
            } else {
                Result.success(emptyList())
            }
        override fun isSignedIn() = signedIn
    }

    private class FakeLocalDataSource(
        private val currentCatalog: LocalMusicCatalog,
    ) : AndroidAutoLocalDataSource {
        override suspend fun catalog(): LocalMusicCatalog = currentCatalog
        override suspend fun search(query: String): List<Song> = currentCatalog.search(query)
    }

    private fun localSong(
        id: String = "local-1",
        title: String = "Local Track",
        artist: String = "Local Artist",
        album: String = "Local Album",
        uri: String = "content://media/external/audio/media/1",
        path: String = "Music/Tamil/local.mp3",
    ) = Song(
        videoId = id,
        title = title,
        artist = artist,
        thumbnailUrl = null,
        durationText = "3:30",
        albumName = album,
        localUri = uri,
        localPath = path,
    )

    private fun catalogWith(song: Song = localSong()): LocalMusicCatalog = LocalMusicCatalog(
        listOf(
            LocalMusicTrack(
                song = song,
                folderKey = "Music/Tamil",
                folderLabel = "Tamil",
                identity = "storage:primary:music/tamil/local.mp3",
            ),
        ),
    )

    @Test
    fun tanTovMediaIdsUseTanTovPrefix() {
        assertTrue(AndroidAutoMediaIds.encode(AndroidAutoRoute.Root).startsWith("tantov:auto:v1:"))
    }

    @Test
    fun signedOutLibraryStillContainsSeparateLocalMusicFolder() = runBlocking {
        val online = FakeOnlineDataSource().apply { signedIn = false }
        val catalog = AndroidAutoCatalog(
            dataSource = online,
            localDataSource = FakeLocalDataSource(catalogWith()),
        )

        val rows = catalog.children(AndroidAutoRoute.Library, 0, 100).getOrThrow()

        assertEquals(listOf("Local Music"), rows.map { it.mediaMetadata.title.toString() })
        assertEquals(AndroidAutoRoute.LocalMusic, AndroidAutoMediaIds.parse(rows.single().mediaId))
        assertEquals(0, online.libraryCalls)
    }

    @Test
    fun localMusicHasSongsFoldersAlbumsAndArtists() = runBlocking {
        val catalog = AndroidAutoCatalog(
            dataSource = FakeOnlineDataSource(),
            localDataSource = FakeLocalDataSource(catalogWith()),
        )

        val rows = catalog.children(AndroidAutoRoute.LocalMusic, 0, 100).getOrThrow()

        assertEquals(
            listOf("Songs", "Folders", "Albums", "Artists"),
            rows.map { it.mediaMetadata.title.toString() },
        )
        assertEquals(
            AndroidAutoLocalSection.entries.toList(),
            rows.map {
                (AndroidAutoMediaIds.parse(it.mediaId) as AndroidAutoRoute.LocalSection).section
            },
        )
    }

    @Test
    fun localSongBrowseRowResolvesBackToNormalPlayableItem() = runBlocking {
        val song = localSong()
        val catalog = AndroidAutoCatalog(
            dataSource = FakeOnlineDataSource(),
            localDataSource = FakeLocalDataSource(catalogWith(song)),
        )

        val row = catalog.children(
            AndroidAutoRoute.LocalSection(AndroidAutoLocalSection.SONGS),
            0,
            100,
        ).getOrThrow().single()

        assertEquals(AndroidAutoRoute.Track(song.videoId), AndroidAutoMediaIds.parse(row.mediaId))
        assertTrue(row.mediaMetadata.description?.toString()?.contains("On device") == true)

        // The JVM android.jar used by local unit tests stubs Bundle/Uri methods,
        // so this test pins the service-lifetime contract rather than pretending
        // a Binder transport round-trip can be proven here. The same catalog that
        // emitted the row remembers the complete Song (including localUri/localPath)
        // and converts the selected car row through Song.toMediaItem().
        val playable = catalog.playableTrack(row).getOrThrow()

        assertEquals(song.videoId, playable.mediaId)
        assertFalse(playable.mediaId.startsWith("tantov:auto:"))
        assertEquals(song.title, playable.mediaMetadata.title.toString())
        assertEquals(song.artist, playable.mediaMetadata.artist.toString())
        assertEquals(song.albumName, playable.mediaMetadata.albumTitle.toString())
    }

    @Test
    fun onlineFailureStillReturnsLocalAutoSearchMarkedOnDevice() = runBlocking {
        val online = FakeOnlineDataSource().apply { failSearch = true }
        val catalog = AndroidAutoCatalog(
            dataSource = online,
            localDataSource = FakeLocalDataSource(catalogWith(localSong(title = "Offline Local"))),
        )

        val rows = catalog.search("offline", 0, 20).getOrThrow()

        assertEquals(1, rows.size)
        assertEquals("Offline Local", rows.single().mediaMetadata.title.toString())
        assertTrue(rows.single().mediaMetadata.description?.toString()?.contains("On device") == true)
    }
}
