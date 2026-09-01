package com.music.bitchord.playback

import java.nio.charset.StandardCharsets
import java.util.Base64

/** Stable browse routes exposed to Android Auto / MediaBrowser clients. */
sealed interface AndroidAutoRoute {
    data object Root : AndroidAutoRoute
    data object Home : AndroidAutoRoute
    data object Explore : AndroidAutoRoute
    data object Recent : AndroidAutoRoute
    data object Library : AndroidAutoRoute
    data object LocalMusic : AndroidAutoRoute
    data class LocalSection(val section: AndroidAutoLocalSection) : AndroidAutoRoute
    data class LocalCollection(
        val kind: AndroidAutoLocalCollectionKind,
        val key: String,
    ) : AndroidAutoRoute
    data class LibrarySection(val section: AndroidAutoLibrarySection) : AndroidAutoRoute
    data class Collection(
        val kind: AndroidAutoCollectionKind,
        val browseId: String,
    ) : AndroidAutoRoute
    data class Shelf(
        val source: Source,
        val ordinal: Int,
        val title: String,
    ) : AndroidAutoRoute {
        enum class Source { HOME, EXPLORE, ARTIST }
    }
    data class Track(val videoId: String) : AndroidAutoRoute
}

enum class AndroidAutoLocalSection { SONGS, FOLDERS, ALBUMS, ARTISTS }

enum class AndroidAutoLocalCollectionKind { FOLDER, ALBUM, ARTIST }

enum class AndroidAutoLibrarySection {
    LIKED,
    SONGS,
    PLAYLISTS,
    ALBUMS,
    ARTISTS,
    SUBSCRIPTIONS,
    PODCASTS,
}

enum class AndroidAutoCollectionKind {
    PLAYLIST,
    ALBUM,
    ARTIST,
    SUBSCRIPTION,
    PODCAST,
    UNKNOWN,
}

/**
 * Versioned, fail-closed media IDs for car browsing.
 *
 * Dynamic values are URL-safe Base64 so YouTube browse/video IDs and translated shelf titles
 * can contain punctuation without becoming accidental route delimiters.
 */
object AndroidAutoMediaIds {
    private const val PREFIX = "tantov:auto:v1"
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(route: AndroidAutoRoute): String = when (route) {
        AndroidAutoRoute.Root -> "$PREFIX:root"
        AndroidAutoRoute.Home -> "$PREFIX:home"
        AndroidAutoRoute.Explore -> "$PREFIX:explore"
        AndroidAutoRoute.Recent -> "$PREFIX:recent"
        AndroidAutoRoute.Library -> "$PREFIX:library"
        AndroidAutoRoute.LocalMusic -> "$PREFIX:local"
        is AndroidAutoRoute.LocalSection -> "$PREFIX:local-section:${route.section.name.lowercase()}"
        is AndroidAutoRoute.LocalCollection -> "$PREFIX:local-collection:${route.kind.name.lowercase()}:${payload(route.key)}"
        is AndroidAutoRoute.LibrarySection -> "$PREFIX:library-section:${route.section.name.lowercase()}"
        is AndroidAutoRoute.Collection -> "$PREFIX:collection:${route.kind.name.lowercase()}:${payload(route.browseId)}"
        is AndroidAutoRoute.Shelf -> "$PREFIX:shelf:${route.source.name.lowercase()}:${route.ordinal}:${payload(route.title)}"
        is AndroidAutoRoute.Track -> "$PREFIX:track:${payload(route.videoId)}"
    }

    fun parse(mediaId: String): AndroidAutoRoute? {
        if (!mediaId.startsWith("$PREFIX:")) return null
        val parts = mediaId.split(':')
        if (parts.size < 4 || parts[0] != "tantov" || parts[1] != "auto" || parts[2] != "v1") return null

        return runCatching {
            when (parts[3]) {
                "root" -> AndroidAutoRoute.Root.takeIf { parts.size == 4 }
                "home" -> AndroidAutoRoute.Home.takeIf { parts.size == 4 }
                "explore" -> AndroidAutoRoute.Explore.takeIf { parts.size == 4 }
                "recent" -> AndroidAutoRoute.Recent.takeIf { parts.size == 4 }
                "library" -> AndroidAutoRoute.Library.takeIf { parts.size == 4 }
                "local" -> AndroidAutoRoute.LocalMusic.takeIf { parts.size == 4 }
                "local-section" -> {
                    if (parts.size != 5) null
                    else AndroidAutoLocalSection.entries
                        .firstOrNull { it.name.equals(parts[4], ignoreCase = true) }
                        ?.let(AndroidAutoRoute::LocalSection)
                }
                "local-collection" -> {
                    if (parts.size != 6) null
                    else {
                        val kind = AndroidAutoLocalCollectionKind.entries
                            .firstOrNull { it.name.equals(parts[4], ignoreCase = true) }
                            ?: return@runCatching null
                        val key = decodePayload(parts[5]) ?: return@runCatching null
                        key.takeIf(String::isNotBlank)?.let { AndroidAutoRoute.LocalCollection(kind, it) }
                    }
                }
                "library-section" -> {
                    if (parts.size != 5) null
                    else AndroidAutoLibrarySection.entries
                        .firstOrNull { it.name.equals(parts[4], ignoreCase = true) }
                        ?.let(AndroidAutoRoute::LibrarySection)
                }
                "collection" -> {
                    if (parts.size != 6) null
                    else {
                        val kind = AndroidAutoCollectionKind.entries
                            .firstOrNull { it.name.equals(parts[4], ignoreCase = true) }
                            ?: return@runCatching null
                        val id = decodePayload(parts[5]) ?: return@runCatching null
                        id.takeIf(String::isNotBlank)?.let { AndroidAutoRoute.Collection(kind, it) }
                    }
                }
                "shelf" -> {
                    if (parts.size != 7) null
                    else {
                        val source = AndroidAutoRoute.Shelf.Source.entries
                            .firstOrNull { it.name.equals(parts[4], ignoreCase = true) }
                            ?: return@runCatching null
                        val ordinal = parts[5].toIntOrNull()?.takeIf { it >= 0 }
                            ?: return@runCatching null
                        val title = decodePayload(parts[6]) ?: return@runCatching null
                        title.takeIf(String::isNotBlank)?.let { AndroidAutoRoute.Shelf(source, ordinal, it) }
                    }
                }
                "track" -> {
                    if (parts.size != 5) null
                    else decodePayload(parts[4])
                        ?.takeIf(String::isNotBlank)
                        ?.let(AndroidAutoRoute::Track)
                }
                else -> null
            }
        }.getOrNull()
    }

    private fun payload(value: String): String =
        encoder.encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodePayload(value: String): String? = runCatching {
        String(decoder.decode(value), StandardCharsets.UTF_8)
    }.getOrNull()
}
