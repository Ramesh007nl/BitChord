package com.music.bitchord.playback

import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.model.ArtistPage
import com.music.bitchord.data.model.HomeFeed
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song

/** Small injectable seam between Android Auto browsing and YouTube Music network calls. */
interface AndroidAutoDataSource {
    suspend fun home(): Result<HomeFeed>
    suspend fun explore(): Result<List<HomeShelf>>
    suspend fun history(): Result<List<Song>>
    suspend fun library(): Result<LibraryPage>
    suspend fun browseSongs(browseId: String): Result<YtMusicRepository.SongPage>
    suspend fun artistPage(browseId: String): Result<ArtistPage>
    suspend fun search(query: String, filter: SearchFilter): Result<List<SearchResult>>
    fun isSignedIn(): Boolean
}

object YtMusicAndroidAutoDataSource : AndroidAutoDataSource {
    override suspend fun home() = YtMusicRepository.home()
    override suspend fun explore() = YtMusicRepository.explore()
    override suspend fun history() = YtMusicRepository.history()
    override suspend fun library() = YtMusicRepository.library()
    override suspend fun browseSongs(browseId: String) = YtMusicRepository.browseSongs(browseId)
    override suspend fun artistPage(browseId: String) = YtMusicRepository.artistPage(browseId)
    override suspend fun search(query: String, filter: SearchFilter) = YtMusicRepository.search(query, filter)
    override fun isSignedIn(): Boolean = Innertube.cookie != null
}
