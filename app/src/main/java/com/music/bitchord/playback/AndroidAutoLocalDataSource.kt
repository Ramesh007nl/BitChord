package com.music.bitchord.playback

import android.content.Context
import com.music.bitchord.data.LocalMediaRepository
import com.music.bitchord.data.local.LocalMusicCatalog
import com.music.bitchord.data.model.Song

/** Device-local Android Auto seam, kept injectable so car browsing stays unit-testable. */
interface AndroidAutoLocalDataSource {
    suspend fun catalog(): LocalMusicCatalog
    suspend fun search(query: String): List<Song>
}

object EmptyAndroidAutoLocalDataSource : AndroidAutoLocalDataSource {
    override suspend fun catalog() = LocalMusicCatalog(emptyList())
    override suspend fun search(query: String) = emptyList<Song>()
}

class DeviceAndroidAutoLocalDataSource(context: Context) : AndroidAutoLocalDataSource {
    private val appContext = context.applicationContext

    override suspend fun catalog(): LocalMusicCatalog = LocalMediaRepository.catalog(appContext)

    override suspend fun search(query: String): List<Song> = catalog().search(query)
}
