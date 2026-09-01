package com.music.bitchord.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.music.bitchord.data.DebugLog as Log
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.music.bitchord.data.local.LocalMusicCatalog
import com.music.bitchord.data.local.LocalMusicTrack
import com.music.bitchord.data.local.mediaStoreStorageIdentity
import com.music.bitchord.data.local.safStorageIdentity
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.download.DownloadStore
import com.music.bitchord.download.Downloads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

object LocalMediaRepository {

    private const val TAG = "BitChord"

    @Volatile
    private var cachedCatalog: LocalMusicCatalog? = null

    /** Check if storage/audio permission is granted to query device local music. */
    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Retrieves all songs in the `Music/BitChord` directory, combining app downloads
     * with any local audio files present in that folder.
     *
     * The download record is the better source for a title and a credit — it
     * holds what the catalogue row said, not what a scanner guessed off a
     * filename — but it only started carrying the album at all recently, and
     * an album page's rows never name their own release. So whatever the media
     * scanner read off each file is collected alongside and used to fill the
     * gaps, which is what keeps the Albums tab from being empty for everything
     * downloaded before that field existed.
     */
    suspend fun getDownloadedSongs(context: Context): List<Song> = withContext(Dispatchers.IO) {
        val appDownloads = Downloads.getDownloadedSongs(context)
        val knownUris = appDownloads.mapNotNull { it.localUri }.toSet()
        val extraSongs = mutableListOf<Song>()

        /** uri to what the media scanner read off that file. */
        val scanned = mutableMapOf<String, ScannedTags>()

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.RELATIVE_PATH,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.ALBUM_ID,
                )
                val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf("%${DownloadStore.FOLDER}%")

                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null,
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                    val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val albumArtBaseUri = Uri.parse("content://media/external/audio/albumart")

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: continue
                        val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
                        val albumId = cursor.getLong(albumIdCol)
                        val tags = ScannedTags(
                            albumName = cursor.getString(albumCol).cleanTag(),
                            artworkUrl = if (albumId > 0) {
                                ContentUris.withAppendedId(albumArtBaseUri, albumId).toString()
                            } else {
                                null
                            },
                        )
                        scanned[contentUri] = tags
                        if (contentUri !in knownUris && isAudioFileName(name)) {
                            extraSongs.add(buildSongFromUri(context, contentUri, name, tags))
                        }
                    }
                }
            } else {
                val folder = File(
                    android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_MUSIC,
                    ),
                    DownloadStore.FOLDER,
                )
                if (folder.exists() && folder.isDirectory) {
                    folder.listFiles()?.forEach { file ->
                        if (file.isFile && isAudioFileName(file.name)) {
                            val uriStr = Uri.fromFile(file).toString()
                            if (uriStr !in knownUris) {
                                extraSongs.add(buildSongFromUri(context, uriStr, file.name))
                            }
                        }
                    }
                }
            }
        }.onFailure { Log.w(TAG, "Failed scanning Music/BitChord directory: ${it.message}") }

        val filled = appDownloads.map { song ->
            if (song.albumName != null) return@map song
            val uri = song.localUri ?: return@map song
            val album = scanned[uri]?.albumName ?: return@map song
            song.copy(albumName = album)
        }

        (filled + extraSongs).distinctBy { it.localUri ?: it.videoId }
    }

    /**
     * The parts of a scanner row worth reading back — everything else about a
     * download is better known from the record that made it.
     */
    private class ScannedTags(val albumName: String?, val artworkUrl: String?)

    /** What MediaStore writes into a column it has nothing for. */
    private fun String?.cleanTag(): String? =
        takeUnless { it.isNullOrBlank() || it == "<unknown>" }

    /**
     * Queries MediaStore for all audio files available on the device.
     * Kept as a Song API for existing callers; the merged A+B path consumes the
     * richer LocalMusicTrack rows from [scanMediaStore] directly.
     */
    suspend fun getLocalMusic(context: Context): List<Song> = withContext(Dispatchers.IO) {
        if (!hasStoragePermission(context)) return@withContext emptyList()
        scanMediaStore(context).map { it.song }
    }

    /**
     * MediaStore source for A (All Music). Android 10+ deliberately uses
     * VOLUME_NAME + RELATIVE_PATH + DISPLAY_NAME instead of the deprecated DATA
     * filesystem column. Android 9 and below retain DATA because it is the
     * platform-supported representation there.
     */
    private fun scanMediaStore(context: Context): List<LocalMusicTrack> {
        if (!hasStoragePermission(context)) return emptyList()

        val videoIdByUri = Downloads.saved.value.entries.associate { (id, uri) -> uri to id }
        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.MediaColumns.DISPLAY_NAME,
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
                add(MediaStore.MediaColumns.VOLUME_NAME)
            } else {
                add(MediaStore.Audio.Media.DATA)
            }
        }.toTypedArray()

        val rows = mutableListOf<LocalMusicTrack>()
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 5000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder,
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val displayNameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val relativePathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                } else {
                    -1
                }
                val volumeNameCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.VOLUME_NAME)
                } else {
                    -1
                }
                val dataCol = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                } else {
                    -1
                }

                val albumArtBaseUri = Uri.parse("content://media/external/audio/albumart")

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val rawTitle = cursor.getString(titleCol)
                    val rawArtist = cursor.getString(artistCol)
                    val rawAlbum = cursor.getString(albumCol)
                    val albumId = cursor.getLong(albumIdCol)
                    val durationMs = cursor.getLong(durationCol)
                    val displayName = cursor.getString(displayNameCol)
                        ?.takeIf { it.isNotBlank() }
                        ?: "Track-$id"
                    val relativePath = if (relativePathCol >= 0) cursor.getString(relativePathCol) else null
                    val volumeName = if (volumeNameCol >= 0) cursor.getString(volumeNameCol) else null
                    val legacyPath = if (dataCol >= 0) cursor.getString(dataCol) else null

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id,
                    ).toString()
                    val title = rawTitle.takeUnless { it.isNullOrBlank() } ?: displayName.substringBeforeLast('.')
                    val artist = rawArtist.takeUnless { it.isNullOrBlank() || it == "<unknown>" } ?: "Unknown Artist"
                    val albumName = rawAlbum.cleanTag()
                    val artworkUrl = if (albumId > 0) {
                        ContentUris.withAppendedId(albumArtBaseUri, albumId).toString()
                    } else {
                        null
                    }
                    val durationText = formatDuration(durationMs)

                    val logicalParent = when {
                        relativePath != null -> relativePath.replace('\\', '/').trim('/')
                        !legacyPath.isNullOrBlank() -> legacyPath.replace('\\', '/').substringBeforeLast('/', "")
                        else -> ""
                    }
                    val folderKey = logicalParent.ifBlank { "On device" }
                    val folderLabel = folderKey.substringAfterLast('/').ifBlank { "On device" }
                    val localPath = when {
                        relativePath != null -> {
                            val parent = relativePath.replace('\\', '/').trim('/')
                            if (parent.isBlank()) displayName else "$parent/$displayName"
                        }
                        else -> legacyPath
                    }

                    rows += LocalMusicTrack(
                        song = Song(
                            videoId = videoIdByUri[contentUri] ?: contentUri,
                            title = title,
                            artist = artist,
                            thumbnailUrl = artworkUrl,
                            durationText = durationText,
                            albumName = albumName,
                            localUri = contentUri,
                            localPath = localPath,
                        ),
                        folderKey = folderKey,
                        folderLabel = folderLabel,
                        identity = mediaStoreStorageIdentity(
                            volumeName = volumeName,
                            relativePath = relativePath,
                            displayName = displayName,
                            legacyPath = legacyPath,
                            mediaId = id,
                            durationMs = durationMs,
                        ),
                    )
                }
            }
        }.onFailure { Log.w(TAG, "Failed scanning device local music: ${it.message}") }

        return rows
    }

    /**
     * One catalog for both access modes. MediaStore rows are merged first so a
     * file visible through both A and B keeps the stable MediaStore content URI.
     */
    suspend fun refresh(context: Context): LocalMusicCatalog = withContext(Dispatchers.IO) {
        val allMusicRows = if (
            AppSettings.localAllMusicEnabled.value && hasStoragePermission(context)
        ) {
            scanMediaStore(context)
        } else {
            emptyList()
        }

        val selectedRows = AppSettings.localMusicTreeUris.value
            .toList()
            .sorted()
            .flatMap { scanTree(context, it) }

        LocalMusicCatalog.merge(allMusicRows, selectedRows).also { cachedCatalog = it }
    }

    suspend fun catalog(context: Context): LocalMusicCatalog =
        cachedCatalog ?: refresh(context)

    fun invalidate() {
        cachedCatalog = null
    }

    /** Scan one persisted Storage Access Framework tree without broad file access. */
    private fun scanTree(context: Context, treeUriString: String): List<LocalMusicTrack> {
        val treeUri = runCatching { Uri.parse(treeUriString) }.getOrNull() ?: return emptyList()
        val hasGrant = context.contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isReadPermission
        }
        if (!hasGrant) return emptyList()

        val root = runCatching { DocumentFile.fromTreeUri(context, treeUri) }
            .getOrNull() ?: return emptyList()
        if (!root.exists() || !root.isDirectory) return emptyList()

        val rows = mutableListOf<LocalMusicTrack>()
        val rootLabel = root.name?.takeIf { it.isNotBlank() } ?: "Selected folder"

        fun visit(directory: DocumentFile, path: String) {
            val children = runCatching { directory.listFiles().toList() }
                .getOrElse {
                    Log.w(TAG, "Cannot read selected music folder $path: ${it.message}")
                    return
                }
            children.sortedBy { it.name.orEmpty().lowercase(Locale.ROOT) }.forEach { child ->
                val name = child.name.orEmpty()
                when {
                    child.isDirectory -> {
                        val nextLabel = name.ifBlank { "Folder" }
                        visit(child, "$path/$nextLabel")
                    }
                    child.isFile && (
                        child.type?.startsWith("audio/", ignoreCase = true) == true ||
                            isAudioFileName(name)
                    ) -> {
                        val uri = child.uri.toString()
                        val fileName = name.ifBlank { "Audio" }
                        val song = buildSongFromUri(
                            context = context,
                            uriStr = uri,
                            fileName = fileName,
                        ).copy(localPath = "$path/$fileName")
                        val documentId = runCatching {
                            DocumentsContract.getDocumentId(child.uri)
                        }.getOrNull()
                        val sizeBytes = runCatching { child.length() }.getOrDefault(0L)

                        rows += LocalMusicTrack(
                            song = song,
                            folderKey = path,
                            folderLabel = path.substringAfterLast('/').ifBlank { rootLabel },
                            identity = safStorageIdentity(
                                documentId = documentId,
                                uri = uri,
                                sizeBytes = sizeBytes,
                            ),
                        )
                    }
                }
            }
        }

        runCatching { visit(root, rootLabel) }
            .onFailure { Log.w(TAG, "Failed scanning selected music tree: ${it.message}") }
        return rows
    }

    private fun isAudioFileName(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return lower.endsWith(".mp3") || lower.endsWith(".m4a") ||
            lower.endsWith(".flac") || lower.endsWith(".wav") ||
            lower.endsWith(".ogg") || lower.endsWith(".opus") ||
            lower.endsWith(".aac") || lower.endsWith(".webm") ||
            lower.endsWith(".3gp")
    }

    /**
     * A song built from a file in the downloads folder the app has no record of
     * — one copied in by hand, or left behind by an install whose record is
     * gone. The file's own tags are the only thing there is to go on; [scanned]
     * fills in what the retriever couldn't read, since the media scanner and
     * `MediaMetadataRetriever` do not agree on every container.
     */
    private fun buildSongFromUri(
        context: Context,
        uriStr: String,
        fileName: String,
        scanned: ScannedTags? = null,
    ): Song {
        var title = fileName.substringBeforeLast(".")
        var artist = "Unknown Artist"
        var albumName: String? = null
        var durationText: String? = null

        val retriever = MediaMetadataRetriever()
        runCatching {
            try {
                retriever.setDataSource(context, Uri.parse(uriStr))
                val metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                val metaArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                val metaAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                val metaDur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()

                if (!metaTitle.isNullOrBlank()) title = metaTitle
                if (!metaArtist.isNullOrBlank()) artist = metaArtist
                albumName = metaAlbum.cleanTag()
                if (metaDur != null && metaDur > 0) durationText = formatDuration(metaDur)
            } finally {
                runCatching { retriever.release() }
            }
        }

        return Song(
            videoId = uriStr,
            title = title,
            artist = artist,
            thumbnailUrl = scanned?.artworkUrl,
            durationText = durationText,
            albumName = albumName ?: scanned?.albumName,
            localUri = uriStr,
        )
    }

    private fun formatDuration(ms: Long): String {
        val totalSecs = ms / 1000
        val minutes = totalSecs / 60
        val secs = totalSecs % 60
        return String.format(Locale.ROOT, "%d:%02d", minutes, secs)
    }
}
