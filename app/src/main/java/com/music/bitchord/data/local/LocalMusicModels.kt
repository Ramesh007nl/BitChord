package com.music.bitchord.data.local

import com.music.bitchord.data.model.Song
import java.util.Locale

data class LocalMusicTrack(
    val song: Song,
    val folderKey: String,
    val folderLabel: String,
    val identity: String,
)

data class LocalMusicFolder(
    val key: String,
    val label: String,
    val songs: List<Song>,
)

data class LocalMusicCatalog(val tracks: List<LocalMusicTrack>) {
    val songs: List<Song> get() = tracks.map { it.song }

    val folders: List<LocalMusicFolder> get() = tracks
        .groupBy { it.folderKey }
        .map { (key, rows) ->
            LocalMusicFolder(key, rows.first().folderLabel, rows.map { it.song })
        }
        .sortedBy { it.label.lowercase(Locale.ROOT) }

    fun search(query: String): List<Song> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return songs.filter { song ->
            song.title.contains(q, ignoreCase = true) ||
                song.artist.contains(q, ignoreCase = true) ||
                song.albumName?.contains(q, ignoreCase = true) == true
        }
    }

    companion object {
        fun merge(vararg sources: List<LocalMusicTrack>): LocalMusicCatalog =
            LocalMusicCatalog(
                sources.asSequence()
                    .flatten()
                    .distinctBy { it.identity }
                    .toList(),
            )
    }
}
