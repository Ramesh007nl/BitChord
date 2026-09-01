package com.music.bitchord.data.local

import com.music.bitchord.data.model.Song
import java.util.Locale

private val externalVolumeUuid = Regex("[0-9a-fA-F]{4}-[0-9a-fA-F]{4}")

private fun cleanPath(value: String): String =
    value.replace('\\', '/').trim().trim('/')

private fun normalizedMediaStoreVolume(value: String?): String {
    val raw = value?.trim()?.lowercase(Locale.ROOT).orEmpty()
    return when (raw) {
        "", "external", "external_primary", "primary" -> "primary"
        else -> raw
    }
}

private fun storageIdentity(volume: String, path: String): String =
    "storage:${volume.lowercase(Locale.ROOT)}:${cleanPath(path).lowercase(Locale.ROOT)}"

private fun legacyStorageIdentity(path: String?): String? {
    val normalized = path
        ?.replace('\\', '/')
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: return null
    val lower = normalized.lowercase(Locale.ROOT)

    val primaryPrefixes = listOf("/storage/emulated/0/", "/sdcard/")
    primaryPrefixes.firstOrNull { lower.startsWith(it) }?.let { prefix ->
        return storageIdentity("primary", normalized.substring(prefix.length))
    }

    if (lower.startsWith("/storage/")) {
        val remainder = normalized.substring("/storage/".length)
        val volume = remainder.substringBefore('/', missingDelimiterValue = "")
        val relative = remainder.substringAfter('/', missingDelimiterValue = "")
        if (externalVolumeUuid.matches(volume) && relative.isNotBlank()) {
            return storageIdentity(volume, relative)
        }
    }

    return "path:${lower.trimEnd('/')}"
}

/**
 * Stable identity for a MediaStore row. On Android 10+ this is based on the
 * storage volume + relative path instead of deprecated raw filesystem access.
 * Older Android falls back to its readable DATA path, normalized so it can
 * still match an ExternalStorageProvider SAF document where possible.
 */
internal fun mediaStoreStorageIdentity(
    volumeName: String?,
    relativePath: String?,
    displayName: String,
    legacyPath: String?,
    mediaId: Long,
    durationMs: Long,
): String {
    val fileName = displayName.trim()
    if (relativePath != null && fileName.isNotBlank()) {
        val parent = cleanPath(relativePath)
        val fullPath = if (parent.isBlank()) fileName else "$parent/$fileName"
        return storageIdentity(normalizedMediaStoreVolume(volumeName), fullPath)
    }

    legacyStorageIdentity(legacyPath)?.let { return it }

    return "media:$mediaId:$durationMs:${fileName.lowercase(Locale.ROOT)}"
}

/**
 * Stable identity for an explicitly granted SAF document. External storage
 * document IDs use `primary:path` (or `UUID:path`), which can be normalized to
 * the same identity as the equivalent MediaStore row. Other providers keep an
 * opaque URI-based identity rather than guessing what their document ID means.
 */
internal fun safStorageIdentity(
    documentId: String?,
    uri: String,
    sizeBytes: Long,
): String {
    val cleanedId = documentId
        ?.replace('\\', '/')
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    if (cleanedId != null && ':' in cleanedId) {
        val rawVolume = cleanedId.substringBefore(':').trim()
        val relative = cleanedId.substringAfter(':').trim('/')
        val volume = when {
            rawVolume.equals("primary", ignoreCase = true) ||
                rawVolume.equals("external_primary", ignoreCase = true) -> "primary"
            externalVolumeUuid.matches(rawVolume) -> rawVolume.lowercase(Locale.ROOT)
            else -> null
        }
        if (volume != null && relative.isNotBlank()) {
            return storageIdentity(volume, relative)
        }
    }

    return "saf:${uri.trim().lowercase(Locale.ROOT)}:$sizeBytes"
}

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

/** Folder-tab filtering: match the visible leaf or its parent path. */
fun filterLocalMusicFolders(
    folders: List<LocalMusicFolder>,
    query: String,
): List<LocalMusicFolder> {
    val q = query.trim()
    if (q.isEmpty()) return folders
    return folders.filter { folder ->
        folder.label.contains(q, ignoreCase = true) ||
            folder.key.contains(q, ignoreCase = true)
    }
}

data class LocalMusicCatalog(val tracks: List<LocalMusicTrack>) {
    val songs: List<Song> get() = tracks.map { it.song }

    val folders: List<LocalMusicFolder> get() = tracks
        .groupBy { it.folderKey }
        .map { (key, rows) ->
            LocalMusicFolder(key, rows.first().folderLabel, rows.map { it.song })
        }
        .sortedWith(
            compareBy<LocalMusicFolder> { it.label.lowercase(Locale.ROOT) }
                .thenBy { it.key.lowercase(Locale.ROOT) },
        )

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
