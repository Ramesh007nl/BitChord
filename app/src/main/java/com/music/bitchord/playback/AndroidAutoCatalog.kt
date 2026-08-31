package com.music.bitchord.playback

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.ArtistPage
import com.music.bitchord.data.model.BrowseItem
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.HomeFeed
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.NOTIFICATION_ART_PX
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt

/**
 * Native-media browse tree used by Android Auto and other MediaBrowser clients.
 *
 * It deliberately knows nothing about Compose. Every playable row is translated back through
 * [Song.toMediaItem] before it reaches ExoPlayer, so Android Auto uses the exact same queue,
 * download lookup, source matching, stream resolver and playback engine as the phone UI.
 */
class AndroidAutoCatalog(
    private val dataSource: AndroidAutoDataSource,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private data class CacheEntry<T>(val value: T, val storedAt: Long)

    private var lastSignedIn = dataSource.isSignedIn()
    private var homeCache: CacheEntry<HomeFeed>? = null
    private var exploreCache: CacheEntry<List<HomeShelf>>? = null
    private var historyCache: CacheEntry<List<Song>>? = null
    private var libraryCache: CacheEntry<LibraryPage>? = null
    private val searchCache = mutableMapOf<String, CacheEntry<List<MediaItem>>>()

    /** Dynamic routes that were actually emitted by this catalog, not fabricated by a client. */
    private val allowedCollections = mutableSetOf<String>()
    private val rememberedSongs = mutableMapOf<String, Song>()
    private val rememberedItems = mutableMapOf<String, MediaItem>()

    suspend fun root(): MediaItem = browsable(AndroidAutoRoute.Root, "BitChord")

    suspend fun children(
        route: AndroidAutoRoute,
        page: Int,
        pageSize: Int,
    ): Result<List<MediaItem>> = runCatching {
        refreshAuthStateIfNeeded()
        val all = when (route) {
            AndroidAutoRoute.Root -> rootChildren()
            AndroidAutoRoute.Home -> homeShelves()
            AndroidAutoRoute.Explore -> exploreShelves()
            AndroidAutoRoute.Recent -> if (dataSource.isSignedIn()) historyRows() else emptyList()
            AndroidAutoRoute.Library -> if (dataSource.isSignedIn()) libraryFolders() else emptyList()
            is AndroidAutoRoute.LibrarySection -> if (dataSource.isSignedIn()) {
                librarySectionRows(route.section)
            } else {
                emptyList()
            }
            is AndroidAutoRoute.Shelf -> shelfRows(route)
            is AndroidAutoRoute.Collection -> collectionRows(route)
            is AndroidAutoRoute.Track -> emptyList()
        }
        all.page(page, pageSize)
    }

    suspend fun item(route: AndroidAutoRoute): Result<MediaItem> = runCatching {
        refreshAuthStateIfNeeded()
        rememberedItems[AndroidAutoMediaIds.encode(route)] ?: when (route) {
            AndroidAutoRoute.Root -> root()
            AndroidAutoRoute.Home -> browsable(route, "Home")
            AndroidAutoRoute.Explore -> browsable(route, "Explore")
            AndroidAutoRoute.Recent -> browsable(route, "Recently Played")
            AndroidAutoRoute.Library -> browsable(route, "Library")
            is AndroidAutoRoute.LibrarySection -> browsable(route, librarySectionTitle(route.section))
            is AndroidAutoRoute.Collection,
            is AndroidAutoRoute.Shelf,
            is AndroidAutoRoute.Track,
            -> error("Unknown dynamic Android Auto item")
        }
    }

    /** Turn a browsed car row back into BitChord's normal playable MediaItem. */
    suspend fun playableTrack(incoming: MediaItem): Result<MediaItem> = runCatching {
        val route = AndroidAutoMediaIds.parse(incoming.mediaId) as? AndroidAutoRoute.Track
            ?: error("Not a BitChord Android Auto track")
        val song = rememberedSongs[route.videoId]
            ?: songFromBrowseRow(incoming, route.videoId)
            ?: error("Track metadata is unavailable")
        song.toMediaItem()
    }

    suspend fun search(query: String, page: Int, pageSize: Int): Result<List<MediaItem>> = runCatching {
        refreshAuthStateIfNeeded()
        val clean = query.trim()
        if (clean.isEmpty()) return@runCatching emptyList()
        val cacheKey = clean.lowercase()
        val cached = searchCache[cacheKey]
        val all = if (cached != null && nowMs() - cached.storedAt <= SEARCH_TTL_MS) {
            cached.value
        } else {
            val rows = buildList {
                for (filter in SEARCH_FILTERS) {
                    dataSource.search(clean, filter).getOrThrow().forEach { result ->
                        when (result) {
                            is SearchResult.Track -> add(playableRow(result.song))
                            is SearchResult.Browse -> add(collectionRow(result.item))
                        }
                    }
                }
            }.distinctBy { it.mediaId }
            searchCache[cacheKey] = CacheEntry(rows, nowMs())
            rows
        }
        all.page(page, pageSize)
    }

    fun clearAuthenticatedCache() {
        homeCache = null
        exploreCache = null
        historyCache = null
        libraryCache = null
        searchCache.clear()
        allowedCollections.clear()
        rememberedSongs.clear()
        rememberedItems.clear()
    }

    private fun refreshAuthStateIfNeeded() {
        val signedIn = dataSource.isSignedIn()
        if (signedIn != lastSignedIn) {
            clearAuthenticatedCache()
            lastSignedIn = signedIn
        }
    }

    private fun rootChildren(): List<MediaItem> = listOf(
        browsable(AndroidAutoRoute.Home, "Home"),
        browsable(AndroidAutoRoute.Explore, "Explore"),
        browsable(AndroidAutoRoute.Recent, "Recently Played"),
        browsable(AndroidAutoRoute.Library, "Library"),
    )

    private suspend fun homeFeed(): HomeFeed {
        homeCache?.takeIf { nowMs() - it.storedAt <= HOME_TTL_MS }?.let { return it.value }
        return dataSource.home().getOrThrow().also { homeCache = CacheEntry(it, nowMs()) }
    }

    private suspend fun exploreFeed(): List<HomeShelf> {
        exploreCache?.takeIf { nowMs() - it.storedAt <= HOME_TTL_MS }?.let { return it.value }
        return dataSource.explore().getOrThrow().also { exploreCache = CacheEntry(it, nowMs()) }
    }

    private suspend fun history(): List<Song> {
        historyCache?.takeIf { nowMs() - it.storedAt <= AUTH_TTL_MS }?.let { return it.value }
        return dataSource.history().getOrThrow().also { historyCache = CacheEntry(it, nowMs()) }
    }

    private suspend fun library(): LibraryPage {
        libraryCache?.takeIf { nowMs() - it.storedAt <= AUTH_TTL_MS }?.let { return it.value }
        return dataSource.library().getOrThrow().also { libraryCache = CacheEntry(it, nowMs()) }
    }

    private suspend fun homeShelves(): List<MediaItem> = homeFeed().shelves.mapIndexed { index, shelf ->
        browsable(
            AndroidAutoRoute.Shelf(AndroidAutoRoute.Shelf.Source.HOME, index, shelf.title),
            shelf.title,
            shelf.subtitle,
            shelf.items.firstOrNull()?.thumbnailUrl,
        )
    }

    private suspend fun exploreShelves(): List<MediaItem> = exploreFeed().mapIndexed { index, shelf ->
        browsable(
            AndroidAutoRoute.Shelf(AndroidAutoRoute.Shelf.Source.EXPLORE, index, shelf.title),
            shelf.title,
            shelf.subtitle,
            shelf.items.firstOrNull()?.thumbnailUrl,
        )
    }

    private suspend fun historyRows(): List<MediaItem> = history().map(::playableRow)

    private suspend fun libraryFolders(): List<MediaItem> {
        val page = library()
        return buildList {
            if (page.likedSongs.isNotEmpty()) {
                add(browsable(AndroidAutoRoute.LibrarySection(AndroidAutoLibrarySection.LIKED), "Liked Songs"))
            }
            if (page.librarySongs.isNotEmpty()) {
                add(browsable(AndroidAutoRoute.LibrarySection(AndroidAutoLibrarySection.SONGS), "Songs"))
            }
            LIBRARY_SHELVES.forEach { (section, title) ->
                if (page.shelves.firstOrNull { it.title.equals(title, ignoreCase = true) }?.items?.isNotEmpty() == true) {
                    add(browsable(AndroidAutoRoute.LibrarySection(section), title))
                }
            }
        }
    }

    private suspend fun librarySectionRows(section: AndroidAutoLibrarySection): List<MediaItem> {
        val page = library()
        return when (section) {
            AndroidAutoLibrarySection.LIKED -> page.likedSongs.map(::playableRow)
            AndroidAutoLibrarySection.SONGS -> page.librarySongs.map(::playableRow)
            else -> {
                val title = librarySectionTitle(section)
                val shelf = page.shelves.firstOrNull { it.title.equals(title, ignoreCase = true) }
                    ?: return emptyList()
                val kind = collectionKind(section)
                shelf.items.mapNotNull { shelfItemRow(it, kind) }
            }
        }
    }

    private suspend fun shelfRows(route: AndroidAutoRoute.Shelf): List<MediaItem> {
        val shelves = when (route.source) {
            AndroidAutoRoute.Shelf.Source.HOME -> homeFeed().shelves
            AndroidAutoRoute.Shelf.Source.EXPLORE -> exploreFeed()
            AndroidAutoRoute.Shelf.Source.ARTIST -> return emptyList()
        }
        val shelf = shelves.getOrNull(route.ordinal)
            ?.takeIf { it.title == route.title }
            ?: return emptyList()
        return shelf.items.mapNotNull { shelfItemRow(it, AndroidAutoCollectionKind.UNKNOWN) }
    }

    private suspend fun collectionRows(route: AndroidAutoRoute.Collection): List<MediaItem> {
        val routeId = AndroidAutoMediaIds.encode(route)
        require(routeId in allowedCollections) { "Unknown Android Auto collection" }
        return when (route.kind) {
            AndroidAutoCollectionKind.PLAYLIST,
            AndroidAutoCollectionKind.ALBUM,
            AndroidAutoCollectionKind.PODCAST,
            -> browseSongRows(route.browseId)

            AndroidAutoCollectionKind.ARTIST,
            AndroidAutoCollectionKind.SUBSCRIPTION,
            -> artistRows(dataSource.artistPage(route.browseId).getOrThrow())

            AndroidAutoCollectionKind.UNKNOWN -> {
                val browsed = dataSource.browseSongs(route.browseId)
                if (browsed.isSuccess && browsed.getOrThrow().songs.isNotEmpty()) {
                    browsed.getOrThrow().songs.map(::playableRow)
                } else {
                    artistRows(dataSource.artistPage(route.browseId).getOrThrow())
                }
            }
        }
    }

    private suspend fun browseSongRows(browseId: String): List<MediaItem> =
        dataSource.browseSongs(browseId).getOrThrow().songs.map(::playableRow)

    private fun artistRows(page: ArtistPage): List<MediaItem> = buildList {
        page.songs.forEach { add(playableRow(it)) }
        page.sections.forEach { shelf ->
            shelf.items.forEach { item ->
                shelfItemRow(item, AndroidAutoCollectionKind.UNKNOWN)?.let(::add)
            }
        }
    }.distinctBy { it.mediaId }

    private fun shelfItemRow(
        item: ShelfItem,
        collectionKind: AndroidAutoCollectionKind,
    ): MediaItem? = when {
        !item.videoId.isNullOrBlank() -> playableRow(
            Song(
                videoId = item.videoId,
                title = item.title,
                artist = item.subtitle,
                thumbnailUrl = item.thumbnailUrl,
            ),
        )
        !item.browseId.isNullOrBlank() -> browsable(
            AndroidAutoRoute.Collection(collectionKind, item.browseId),
            item.title,
            item.subtitle,
            item.thumbnailUrl,
        )
        else -> null
    }

    private fun collectionRow(item: BrowseItem): MediaItem = browsable(
        AndroidAutoRoute.Collection(
            kind = when (item.type) {
                BrowseType.PLAYLIST -> AndroidAutoCollectionKind.PLAYLIST
                BrowseType.ALBUM -> AndroidAutoCollectionKind.ALBUM
                BrowseType.ARTIST -> AndroidAutoCollectionKind.ARTIST
                BrowseType.OTHER -> AndroidAutoCollectionKind.UNKNOWN
            },
            browseId = item.browseId,
        ),
        item.title,
        item.subtitle,
        item.thumbnailUrl,
    )

    private fun browsable(
        route: AndroidAutoRoute,
        title: String,
        subtitle: String? = null,
        artwork: String? = null,
    ): MediaItem {
        val id = AndroidAutoMediaIds.encode(route)
        if (route is AndroidAutoRoute.Collection) allowedCollections += id
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .apply {
                if (!subtitle.isNullOrBlank()) setDescription(subtitle)
                artwork?.artworkAt(NOTIFICATION_ART_PX)?.toUri()?.let(::setArtworkUri)
            }
            .build()
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(metadata)
            .build()
            .also { rememberedItems[id] = it }
    }

    private fun playableRow(song: Song): MediaItem {
        rememberedSongs[song.videoId] = song
        val route = AndroidAutoRoute.Track(song.videoId)
        val id = AndroidAutoMediaIds.encode(route)
        val extras = Bundle().apply {
            putString(EXTRA_VIDEO_ID, song.videoId)
            putString(EXTRA_DURATION, song.durationText)
            putString(EXTRA_ARTIST_ID, song.artistId)
            putString(EXTRA_ALBUM_ID, song.albumId)
            putString(EXTRA_THUMBNAIL, song.thumbnailUrl)
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setExtras(extras)
            .apply {
                song.albumName?.let(::setAlbumTitle)
                song.artworkAt(NOTIFICATION_ART_PX)?.toUri()?.let(::setArtworkUri)
            }
            .build()
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(metadata)
            .build()
            .also { rememberedItems[id] = it }
    }

    private fun songFromBrowseRow(item: MediaItem, fallbackId: String): Song? {
        val title = item.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() } ?: return null
        val extras = item.mediaMetadata.extras
        val videoId = extras?.getString(EXTRA_VIDEO_ID)?.takeIf { it.isNotBlank() } ?: fallbackId
        return Song(
            videoId = videoId,
            title = title,
            artist = item.mediaMetadata.artist?.toString().orEmpty(),
            thumbnailUrl = extras?.getString(EXTRA_THUMBNAIL) ?: item.mediaMetadata.artworkUri?.toString(),
            durationText = extras?.getString(EXTRA_DURATION),
            artistId = extras?.getString(EXTRA_ARTIST_ID),
            albumId = extras?.getString(EXTRA_ALBUM_ID),
            albumName = item.mediaMetadata.albumTitle?.toString(),
        )
    }

    private fun librarySectionTitle(section: AndroidAutoLibrarySection): String = when (section) {
        AndroidAutoLibrarySection.LIKED -> "Liked Songs"
        AndroidAutoLibrarySection.SONGS -> "Songs"
        AndroidAutoLibrarySection.PLAYLISTS -> YtMusicRepository.PLAYLISTS_SHELF
        AndroidAutoLibrarySection.ALBUMS -> "Albums"
        AndroidAutoLibrarySection.ARTISTS -> "Artists"
        AndroidAutoLibrarySection.SUBSCRIPTIONS -> "Subscriptions"
        AndroidAutoLibrarySection.PODCASTS -> "Podcasts"
    }

    private fun collectionKind(section: AndroidAutoLibrarySection): AndroidAutoCollectionKind = when (section) {
        AndroidAutoLibrarySection.PLAYLISTS -> AndroidAutoCollectionKind.PLAYLIST
        AndroidAutoLibrarySection.ALBUMS -> AndroidAutoCollectionKind.ALBUM
        AndroidAutoLibrarySection.ARTISTS -> AndroidAutoCollectionKind.ARTIST
        AndroidAutoLibrarySection.SUBSCRIPTIONS -> AndroidAutoCollectionKind.SUBSCRIPTION
        AndroidAutoLibrarySection.PODCASTS -> AndroidAutoCollectionKind.PODCAST
        AndroidAutoLibrarySection.LIKED,
        AndroidAutoLibrarySection.SONGS,
        -> AndroidAutoCollectionKind.UNKNOWN
    }

    private fun <T> List<T>.page(page: Int, pageSize: Int): List<T> {
        if (page < 0 || pageSize <= 0) return emptyList()
        val from = page.toLong() * pageSize.toLong()
        if (from >= size || from > Int.MAX_VALUE) return emptyList()
        val start = from.toInt()
        val end = minOf(size, start + pageSize)
        return subList(start, end)
    }

    companion object {
        private const val HOME_TTL_MS = 5 * 60_000L
        private const val AUTH_TTL_MS = 2 * 60_000L
        private const val SEARCH_TTL_MS = 60_000L

        private const val EXTRA_VIDEO_ID = "bitchord.auto.videoId"
        private const val EXTRA_DURATION = "bitchord.auto.durationText"
        private const val EXTRA_ARTIST_ID = "bitchord.auto.artistId"
        private const val EXTRA_ALBUM_ID = "bitchord.auto.albumId"
        private const val EXTRA_THUMBNAIL = "bitchord.auto.thumbnailUrl"

        private val SEARCH_FILTERS = listOf(
            SearchFilter.SONGS,
            SearchFilter.ALBUMS,
            SearchFilter.ARTISTS,
            SearchFilter.PLAYLISTS,
        )

        private val LIBRARY_SHELVES = listOf(
            AndroidAutoLibrarySection.PLAYLISTS to YtMusicRepository.PLAYLISTS_SHELF,
            AndroidAutoLibrarySection.ALBUMS to "Albums",
            AndroidAutoLibrarySection.ARTISTS to "Artists",
            AndroidAutoLibrarySection.SUBSCRIPTIONS to "Subscriptions",
            AndroidAutoLibrarySection.PODCASTS to "Podcasts",
        )
    }
}
