from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one match, found {count}")
    p.write_text(text.replace(old, new, 1))


# Task 7: Android Auto Local Music browse + combined online/local search.
local_source = Path("app/src/main/java/com/music/bitchord/playback/AndroidAutoLocalDataSource.kt")
local_source.write_text(
    '''package com.music.bitchord.playback

import android.content.Context
import com.music.bitchord.data.LocalMediaRepository
import com.music.bitchord.data.local.LocalMusicCatalog
import com.music.bitchord.data.model.Song

/** Device-local seam for Android Auto, kept injectable so car browsing stays unit-testable. */
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
'''
)

ids = "app/src/main/java/com/music/bitchord/playback/AndroidAutoMediaIds.kt"
replace_once(
    ids,
    '''    data object Library : AndroidAutoRoute\n    data class LibrarySection(val section: AndroidAutoLibrarySection) : AndroidAutoRoute''',
    '''    data object Library : AndroidAutoRoute\n    data object LocalMusic : AndroidAutoRoute\n    data class LocalSection(val section: AndroidAutoLocalSection) : AndroidAutoRoute\n    data class LocalCollection(\n        val kind: AndroidAutoLocalCollectionKind,\n        val key: String,\n    ) : AndroidAutoRoute\n    data class LibrarySection(val section: AndroidAutoLibrarySection) : AndroidAutoRoute''',
)
replace_once(
    ids,
    '''enum class AndroidAutoLibrarySection {\n    LIKED,''',
    '''enum class AndroidAutoLocalSection { SONGS, FOLDERS, ALBUMS, ARTISTS }\n\nenum class AndroidAutoLocalCollectionKind { FOLDER, ALBUM, ARTIST }\n\nenum class AndroidAutoLibrarySection {\n    LIKED,''',
)
replace_once(ids, 'private const val PREFIX = "bitchord:auto:v1"', 'private const val PREFIX = "tantov:auto:v1"')
replace_once(
    ids,
    '''        AndroidAutoRoute.Library -> "$PREFIX:library"\n        is AndroidAutoRoute.LibrarySection ->''',
    '''        AndroidAutoRoute.Library -> "$PREFIX:library"\n        AndroidAutoRoute.LocalMusic -> "$PREFIX:local"\n        is AndroidAutoRoute.LocalSection -> "$PREFIX:local-section:${route.section.name.lowercase()}"\n        is AndroidAutoRoute.LocalCollection -> "$PREFIX:local-collection:${route.kind.name.lowercase()}:${payload(route.key)}"\n        is AndroidAutoRoute.LibrarySection ->''',
)
replace_once(
    ids,
    '''        if (parts.size < 4 || parts[0] != "bitchord" || parts[1] != "auto" || parts[2] != "v1") return null''',
    '''        if (parts.size < 4 || parts[0] != "tantov" || parts[1] != "auto" || parts[2] != "v1") return null''',
)
replace_once(
    ids,
    '''                "library" -> AndroidAutoRoute.Library.takeIf { parts.size == 4 }\n                "library-section" -> {''',
    '''                "library" -> AndroidAutoRoute.Library.takeIf { parts.size == 4 }\n                "local" -> AndroidAutoRoute.LocalMusic.takeIf { parts.size == 4 }\n                "local-section" -> {\n                    if (parts.size != 5) null\n                    else AndroidAutoLocalSection.entries\n                        .firstOrNull { it.name.equals(parts[4], ignoreCase = true) }\n                        ?.let(AndroidAutoRoute::LocalSection)\n                }\n                "local-collection" -> {\n                    if (parts.size != 6) null\n                    else {\n                        val kind = AndroidAutoLocalCollectionKind.entries\n                            .firstOrNull { it.name.equals(parts[4], ignoreCase = true) }\n                            ?: return@runCatching null\n                        val key = decodePayload(parts[5]) ?: return@runCatching null\n                        key.takeIf(String::isNotBlank)?.let { AndroidAutoRoute.LocalCollection(kind, it) }\n                    }\n                }\n                "library-section" -> {''',
)

catalog = "app/src/main/java/com/music/bitchord/playback/AndroidAutoCatalog.kt"
replace_once(
    catalog,
    '''class AndroidAutoCatalog(\n    private val dataSource: AndroidAutoDataSource,\n    private val nowMs: () -> Long = System::currentTimeMillis,\n) {''',
    '''class AndroidAutoCatalog(\n    private val dataSource: AndroidAutoDataSource,\n    private val localDataSource: AndroidAutoLocalDataSource = EmptyAndroidAutoLocalDataSource,\n    private val nowMs: () -> Long = System::currentTimeMillis,\n) {''',
)
replace_once(
    catalog,
    '''            AndroidAutoRoute.Recent -> if (dataSource.isSignedIn()) historyRows() else emptyList()\n            AndroidAutoRoute.Library -> if (dataSource.isSignedIn()) libraryFolders() else emptyList()\n            is AndroidAutoRoute.LibrarySection -> if (dataSource.isSignedIn()) {\n                librarySectionRows(route.section)\n            } else {\n                emptyList()\n            }\n            is AndroidAutoRoute.Shelf -> shelfRows(route)\n            is AndroidAutoRoute.Collection -> collectionRows(route)\n            is AndroidAutoRoute.Track -> emptyList()''',
    '''            AndroidAutoRoute.Recent -> if (dataSource.isSignedIn()) historyRows() else emptyList()\n            AndroidAutoRoute.Library -> libraryFolders()\n            AndroidAutoRoute.LocalMusic -> localMusicSections()\n            is AndroidAutoRoute.LocalSection -> localSectionRows(route.section)\n            is AndroidAutoRoute.LocalCollection -> localCollectionRows(route)\n            is AndroidAutoRoute.LibrarySection -> if (dataSource.isSignedIn()) {\n                librarySectionRows(route.section)\n            } else {\n                emptyList()\n            }\n            is AndroidAutoRoute.Shelf -> shelfRows(route)\n            is AndroidAutoRoute.Collection -> collectionRows(route)\n            is AndroidAutoRoute.Track -> emptyList()''',
)
replace_once(
    catalog,
    '''            AndroidAutoRoute.Recent -> browsable(route, "Recently Played")\n            AndroidAutoRoute.Library -> browsable(route, "Library")\n            is AndroidAutoRoute.LibrarySection -> browsable(route, librarySectionTitle(route.section))\n            is AndroidAutoRoute.Collection,\n            is AndroidAutoRoute.Shelf,\n            is AndroidAutoRoute.Track,\n            -> error("Unknown dynamic Android Auto item")''',
    '''            AndroidAutoRoute.Recent -> browsable(route, "Recently Played")\n            AndroidAutoRoute.Library -> browsable(route, "Library")\n            AndroidAutoRoute.LocalMusic -> browsable(route, "Local Music")\n            is AndroidAutoRoute.LocalSection -> browsable(route, localSectionTitle(route.section))\n            is AndroidAutoRoute.LibrarySection -> browsable(route, librarySectionTitle(route.section))\n            is AndroidAutoRoute.LocalCollection,\n            is AndroidAutoRoute.Collection,\n            is AndroidAutoRoute.Shelf,\n            is AndroidAutoRoute.Track,\n            -> error("Unknown dynamic Android Auto item")''',
)
replace_once(
    catalog,
    '''        val route = AndroidAutoMediaIds.parse(incoming.mediaId) as? AndroidAutoRoute.Track\n            ?: error("Not a BitChord Android Auto track")''',
    '''        val route = AndroidAutoMediaIds.parse(incoming.mediaId) as? AndroidAutoRoute.Track\n            ?: error("Not a TanTov Android Auto track")''',
)
replace_once(
    catalog,
    '''            val trackResults = dataSource.search(clean, SearchFilter.SONGS).getOrThrow()\n            val browseResults = coroutineScope {\n                SEARCH_FILTERS.drop(1)\n                    .map { filter -> async { dataSource.search(clean, filter).getOrThrow() } }\n                    .awaitAll()\n                    .flatten()\n            }\n            val rows = (trackResults + browseResults).map { result ->\n                when (result) {\n                    is SearchResult.Track -> playableRow(result.song)\n                    is SearchResult.Browse -> collectionRow(result.item)\n                }\n            }.distinctBy { it.mediaId }\n            searchCache[cacheKey] = CacheEntry(rows, nowMs())\n            rows''',
    '''            // Online and local are independent: losing the network must not\n            // erase an on-device match, and a local scan problem must not hide\n            // healthy online results. The existing four-filter online search is\n            // kept intact, including concurrent browse-filter requests.\n            val onlineRows = runCatching {\n                val trackResults = dataSource.search(clean, SearchFilter.SONGS).getOrThrow()\n                val browseResults = coroutineScope {\n                    SEARCH_FILTERS.drop(1)\n                        .map { filter -> async { dataSource.search(clean, filter).getOrThrow() } }\n                        .awaitAll()\n                        .flatten()\n                }\n                (trackResults + browseResults).map { result ->\n                    when (result) {\n                        is SearchResult.Track -> playableRow(result.song)\n                        is SearchResult.Browse -> collectionRow(result.item)\n                    }\n                }\n            }\n            val localRows = runCatching {\n                localDataSource.search(clean).map(::playableRow)\n            }.getOrDefault(emptyList())\n\n            val rows = onlineRows.fold(\n                onSuccess = { online -> (online + localRows).distinctBy { it.mediaId } },\n                onFailure = { failure ->\n                    if (localRows.isNotEmpty()) localRows.distinctBy { it.mediaId }\n                    else throw failure\n                },\n            )\n            searchCache[cacheKey] = CacheEntry(rows, nowMs())\n            rows''',
)
replace_once(
    catalog,
    '''        allowedCollections.clear()\n        rememberedSongs.clear()''',
    '''        allowedCollections.clear()\n        rememberedSongs.clear()''',
)
replace_once(
    catalog,
    '''    private suspend fun libraryFolders(): List<MediaItem> {\n        val page = library()\n        return buildList {\n            if (page.likedSongs.isNotEmpty()) {\n                add(browsable(AndroidAutoRoute.LibrarySection(AndroidAutoLibrarySection.LIKED), "Liked Songs"))\n            }\n            if (page.librarySongs.isNotEmpty()) {\n                add(browsable(AndroidAutoRoute.LibrarySection(AndroidAutoLibrarySection.SONGS), "Songs"))\n            }\n            LIBRARY_SHELVES.forEach { (section, title) ->\n                if (page.shelves.firstOrNull { it.title.equals(title, ignoreCase = true) }?.items?.isNotEmpty() == true) {\n                    add(browsable(AndroidAutoRoute.LibrarySection(section), title))\n                }\n            }\n        }\n    }''',
    '''    private suspend fun libraryFolders(): List<MediaItem> = buildList {\n        // Local Music is independent of YouTube authentication. A signed-out\n        // driver can still browse and play the music already on the phone.\n        val local = runCatching { localDataSource.catalog() }.getOrNull()\n        if (local?.songs?.isNotEmpty() == true) {\n            add(browsable(AndroidAutoRoute.LocalMusic, "Local Music"))\n        }\n\n        if (!dataSource.isSignedIn()) return@buildList\n        val page = library()\n        if (page.likedSongs.isNotEmpty()) {\n            add(browsable(AndroidAutoRoute.LibrarySection(AndroidAutoLibrarySection.LIKED), "Liked Songs"))\n        }\n        if (page.librarySongs.isNotEmpty()) {\n            add(browsable(AndroidAutoRoute.LibrarySection(AndroidAutoLibrarySection.SONGS), "Songs"))\n        }\n        LIBRARY_SHELVES.forEach { (section, title) ->\n            if (page.shelves.firstOrNull { it.title.equals(title, ignoreCase = true) }?.items?.isNotEmpty() == true) {\n                add(browsable(AndroidAutoRoute.LibrarySection(section), title))\n            }\n        }\n    }\n\n    private fun localMusicSections(): List<MediaItem> = AndroidAutoLocalSection.entries.map { section ->\n        browsable(AndroidAutoRoute.LocalSection(section), localSectionTitle(section))\n    }\n\n    private suspend fun localSectionRows(section: AndroidAutoLocalSection): List<MediaItem> {\n        val local = localDataSource.catalog()\n        return when (section) {\n            AndroidAutoLocalSection.SONGS -> local.songs.map(::playableRow)\n            AndroidAutoLocalSection.FOLDERS -> local.folders.map { folder ->\n                browsable(\n                    AndroidAutoRoute.LocalCollection(AndroidAutoLocalCollectionKind.FOLDER, folder.key),\n                    folder.label,\n                    "${folder.songs.size} songs",\n                    folder.songs.firstOrNull()?.thumbnailUrl,\n                )\n            }\n            AndroidAutoLocalSection.ALBUMS -> local.songs\n                .mapNotNull { song ->\n                    song.albumName?.trim()?.takeIf { it.isNotBlank() }?.let { it to song }\n                }\n                .groupBy({ it.first }, { it.second })\n                .toSortedMap(String.CASE_INSENSITIVE_ORDER)\n                .map { (album, songs) ->\n                    val artists = songs.map { it.artist }.filter { it.isNotBlank() }.distinct()\n                    browsable(\n                        AndroidAutoRoute.LocalCollection(AndroidAutoLocalCollectionKind.ALBUM, album),\n                        album,\n                        artists.joinToString(" • "),\n                        songs.firstOrNull()?.thumbnailUrl,\n                    )\n                }\n            AndroidAutoLocalSection.ARTISTS -> local.songs\n                .filter { it.artist.isNotBlank() }\n                .groupBy { it.artist.trim() }\n                .toSortedMap(String.CASE_INSENSITIVE_ORDER)\n                .map { (artist, songs) ->\n                    browsable(\n                        AndroidAutoRoute.LocalCollection(AndroidAutoLocalCollectionKind.ARTIST, artist),\n                        artist,\n                        "${songs.size} songs",\n                        songs.firstOrNull()?.thumbnailUrl,\n                    )\n                }\n        }\n    }\n\n    private suspend fun localCollectionRows(route: AndroidAutoRoute.LocalCollection): List<MediaItem> {\n        val routeId = AndroidAutoMediaIds.encode(route)\n        require(routeId in allowedCollections) { "Unknown Android Auto local collection" }\n        val local = localDataSource.catalog()\n        val songs = when (route.kind) {\n            AndroidAutoLocalCollectionKind.FOLDER ->\n                local.folders.firstOrNull { it.key == route.key }?.songs.orEmpty()\n            AndroidAutoLocalCollectionKind.ALBUM ->\n                local.songs.filter { it.albumName?.trim() == route.key }\n            AndroidAutoLocalCollectionKind.ARTIST ->\n                local.songs.filter { it.artist.trim() == route.key }\n        }\n        return songs.map(::playableRow)\n    }''',
)
replace_once(
    catalog,
    '''        if (route is AndroidAutoRoute.Collection) allowedCollections += id''',
    '''        if (route is AndroidAutoRoute.Collection || route is AndroidAutoRoute.LocalCollection) {\n            allowedCollections += id\n        }''',
)
replace_once(
    catalog,
    '''            putString(EXTRA_ALBUM_ID, song.albumId)\n            putString(EXTRA_THUMBNAIL, song.thumbnailUrl)''',
    '''            putString(EXTRA_ALBUM_ID, song.albumId)\n            putString(EXTRA_THUMBNAIL, song.thumbnailUrl)\n            putString(EXTRA_LOCAL_URI, song.localUri)\n            putString(EXTRA_LOCAL_PATH, song.localPath)''',
)
replace_once(
    catalog,
    '''            .setIsPlayable(true)\n            .setIsBrowsable(false)\n            .setExtras(extras)\n            .apply {\n                song.albumName?.let(::setAlbumTitle)''',
    '''            .setIsPlayable(true)\n            .setIsBrowsable(false)\n            .setExtras(extras)\n            .apply {\n                if (song.localUri != null) {\n                    setDescription(\n                        listOf(song.artist, "On device")\n                            .filter { it.isNotBlank() }\n                            .joinToString(" • "),\n                    )\n                }\n                song.albumName?.let(::setAlbumTitle)''',
)
replace_once(
    catalog,
    '''            albumId = extras?.getString(EXTRA_ALBUM_ID),\n            albumName = item.mediaMetadata.albumTitle?.toString(),\n        )''',
    '''            albumId = extras?.getString(EXTRA_ALBUM_ID),\n            albumName = item.mediaMetadata.albumTitle?.toString(),\n            localUri = extras?.getString(EXTRA_LOCAL_URI),\n            localPath = extras?.getString(EXTRA_LOCAL_PATH),\n        )''',
)
replace_once(
    catalog,
    '''    private fun librarySectionTitle(section: AndroidAutoLibrarySection): String = when (section) {''',
    '''    private fun localSectionTitle(section: AndroidAutoLocalSection): String = when (section) {\n        AndroidAutoLocalSection.SONGS -> "Songs"\n        AndroidAutoLocalSection.FOLDERS -> "Folders"\n        AndroidAutoLocalSection.ALBUMS -> "Albums"\n        AndroidAutoLocalSection.ARTISTS -> "Artists"\n    }\n\n    private fun librarySectionTitle(section: AndroidAutoLibrarySection): String = when (section) {''',
)
replace_once(
    catalog,
    '''        private const val EXTRA_ALBUM_ID = "bitchord.auto.albumId"\n        private const val EXTRA_THUMBNAIL = "bitchord.auto.thumbnailUrl"''',
    '''        private const val EXTRA_ALBUM_ID = "bitchord.auto.albumId"\n        private const val EXTRA_THUMBNAIL = "bitchord.auto.thumbnailUrl"\n        private const val EXTRA_LOCAL_URI = "tantov.auto.localUri"\n        private const val EXTRA_LOCAL_PATH = "tantov.auto.localPath"''',
)

service = "app/src/main/java/com/music/bitchord/playback/PlaybackService.kt"
replace_once(
    service,
    '''    private var mediaSession: MediaLibrarySession? = null\n    private val androidAutoCatalog by lazy { AndroidAutoCatalog(YtMusicAndroidAutoDataSource) }''',
    '''    private var mediaSession: MediaLibrarySession? = null\n    private val androidAutoCatalog by lazy {\n        AndroidAutoCatalog(\n            dataSource = YtMusicAndroidAutoDataSource,\n            localDataSource = DeviceAndroidAutoLocalDataSource(this),\n        )\n    }''',
)

# Existing tests should assert against the new TanTov car-ID namespace.
for test_path in [
    "app/src/test/java/com/music/bitchord/AndroidAutoCatalogTest.kt",
    "app/src/test/java/com/music/bitchord/AndroidAutoVoiceSearchTest.kt",
]:
    p = Path(test_path)
    text = p.read_text()
    if 'startsWith("bitchord:auto:")' in text:
        p.write_text(text.replace('startsWith("bitchord:auto:")', 'startsWith("tantov:auto:")'))

media_ids_test = "app/src/test/java/com/music/bitchord/AndroidAutoMediaIdsTest.kt"
replace_once(
    media_ids_test,
    '''        assertNull(AndroidAutoMediaIds.parse("bitchord:auto:v1:track:"))''',
    '''        assertNull(AndroidAutoMediaIds.parse("bitchord:auto:v1:track:"))\n        assertNull(AndroidAutoMediaIds.parse("tantov:auto:v1:track:"))''',
)

Path("/tmp/tantov-commit-message").write_text("feat(auto): browse and search local music")
