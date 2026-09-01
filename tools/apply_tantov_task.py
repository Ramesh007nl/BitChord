from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one match, found {count}")
    p.write_text(text.replace(old, new, 1))


# Task 6: combine online + local Songs search with local offline fallback.
view_model = "app/src/main/java/com/music/bitchord/ui/MainViewModel.kt"

replace_once(
    view_model,
    '''import java.util.Locale\n\nclass MainViewModel(app: Application) : AndroidViewModel(app) {''',
    '''import java.util.Locale\n\n/**\n * Combines the normal online result page with local-device song matches.\n *\n * Online rows stay first so existing search ranking is preserved. Local songs\n * are still a useful result when the network is unavailable; only an online\n * failure with no local matches remains a failure.\n */\ninternal fun mergeSongSearchResults(\n    online: Result<List<SearchResult>>,\n    local: List<Song>,\n): Result<List<SearchResult>> {\n    val localRows = local.map(SearchResult::Track)\n    val onlineRows = online.getOrNull()\n    if (onlineRows == null && localRows.isEmpty()) {\n        return Result.failure(\n            online.exceptionOrNull() ?: IllegalStateException("Search failed"),\n        )\n    }\n\n    val merged = (onlineRows.orEmpty() + localRows).distinctBy { result ->\n        when (result) {\n            is SearchResult.Track -> result.song.localUri ?: result.song.videoId\n            is SearchResult.Browse -> "browse:${result.item.browseId}"\n        }\n    }\n    return Result.success(merged)\n}\n\nclass MainViewModel(app: Application) : AndroidViewModel(app) {''',
)

replace_once(
    view_model,
    '''                // Search is YouTube's alone. A module is a *substitution*\n                // layer, not a catalogue to browse: it never has cover art,\n                // radio, related tracks or an album page, so its rows arrived\n                // in the results list looking like YouTube's and then behaved\n                // nothing like them. Every track found here takes the ordinary\n                // YouTube path and is handed to the module at playback time —\n                // see [SourceResolver.substituteForYouTube] — which upgrades\n                // the ones it holds without any of them having to be a\n                // separate row to pick between.\n                val result = YtMusicRepository.search(request.query, request.filter)\n                // A search that has been superseded shouldn't land on screen,\n                // whether it succeeded or failed.\n                if (request.requestId != newestRequestId.get()) return@collectLatest\n                _results.value = result.fold(\n                    onSuccess = { rows -> published(rows, key) },\n                    onFailure = { failure -> UiState.Error(failure.friendly()) },\n                )''',
    '''                // Online search remains the primary catalogue. The Songs\n                // filter additionally searches the merged A+B local catalog;\n                // browse-shaped filters (albums, artists, playlists) stay\n                // online-only. A local match also gives search an offline\n                // fallback when the network request fails.\n                val online = YtMusicRepository.search(request.query, request.filter)\n                val localSongs = if (request.filter == SearchFilter.SONGS) {\n                    runCatching {\n                        LocalMediaRepository.catalog(getApplication<Application>())\n                            .search(request.query)\n                    }.getOrDefault(emptyList())\n                } else {\n                    emptyList()\n                }\n                val result = if (request.filter == SearchFilter.SONGS) {\n                    mergeSongSearchResults(online, localSongs)\n                } else {\n                    online\n                }\n\n                // A search that has been superseded shouldn't land on screen,\n                // whether it succeeded or failed.\n                if (request.requestId != newestRequestId.get()) return@collectLatest\n                _results.value = result.fold(\n                    onSuccess = { rows -> published(rows, key) },\n                    onFailure = { failure -> UiState.Error(failure.friendly()) },\n                )''',
)

screen = "app/src/main/java/com/music/bitchord/ui/screens/SearchScreen.kt"
replace_once(
    screen,
    '''                            is SearchResult.Track -> SongRow(\n                                song = row.song,\n                                onClick = {\n                                    onSongClick(tracks, tracks.indexOf(row.song).coerceAtLeast(0))\n                                },\n                                onLongPress = { onSongLongPress(row.song) },\n                                onSwipeToQueue = { onSongSwipe(row.song) },\n                            )''',
    '''                            is SearchResult.Track -> {\n                                // Keep the real Song untouched for playback and\n                                // actions; only the search row's subtitle gains\n                                // the local-source marker.\n                                val displaySong = if (row.song.localUri != null) {\n                                    row.song.copy(\n                                        artist = listOf(row.song.artist, "On device")\n                                            .filter { it.isNotBlank() }\n                                            .joinToString(" • "),\n                                    )\n                                } else {\n                                    row.song\n                                }\n                                SongRow(\n                                    song = displaySong,\n                                    onClick = {\n                                        onSongClick(tracks, tracks.indexOf(row.song).coerceAtLeast(0))\n                                    },\n                                    onLongPress = { onSongLongPress(row.song) },\n                                    onSwipeToQueue = { onSongSwipe(row.song) },\n                                )\n                            }''',
)

Path("/tmp/tantov-commit-message").write_text("feat(search): combine online and local music\n")
