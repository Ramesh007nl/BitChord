from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one match, found {count}")
    p.write_text(text.replace(old, new, 1))


# Task 3: merge MediaStore (A) + persisted SAF folder trees (B) into one catalog.
models = Path("app/src/main/java/com/music/bitchord/data/local/LocalMusicModels.kt")
models.parent.mkdir(parents=True, exist_ok=True)
if models.exists():
    raise SystemExit(f"{models}: expected new file but it already exists")
models.write_text('''package com.music.bitchord.data.local\n\nimport com.music.bitchord.data.model.Song\nimport java.util.Locale\n\ndata class LocalMusicTrack(\n    val song: Song,\n    val folderKey: String,\n    val folderLabel: String,\n    val identity: String,\n)\n\ndata class LocalMusicFolder(\n    val key: String,\n    val label: String,\n    val songs: List<Song>,\n)\n\ndata class LocalMusicCatalog(val tracks: List<LocalMusicTrack>) {\n    val songs: List<Song> get() = tracks.map { it.song }\n\n    val folders: List<LocalMusicFolder> get() = tracks\n        .groupBy { it.folderKey }\n        .map { (key, rows) ->\n            LocalMusicFolder(key, rows.first().folderLabel, rows.map { it.song })\n        }\n        .sortedBy { it.label.lowercase(Locale.ROOT) }\n\n    fun search(query: String): List<Song> {\n        val q = query.trim()\n        if (q.isEmpty()) return emptyList()\n        return songs.filter { song ->\n            song.title.contains(q, ignoreCase = true) ||\n                song.artist.contains(q, ignoreCase = true) ||\n                song.albumName?.contains(q, ignoreCase = true) == true\n        }\n    }\n\n    companion object {\n        fun merge(vararg sources: List<LocalMusicTrack>): LocalMusicCatalog =\n            LocalMusicCatalog(\n                sources.asSequence()\n                    .flatten()\n                    .distinctBy { it.identity }\n                    .toList(),\n            )\n    }\n}\n''')

# AndroidX DocumentFile is the SAF tree traversal helper.
replace_once(
    "app/build.gradle.kts",
    '    implementation("androidx.core:core-ktx:1.15.0")\n    implementation("androidx.appcompat:appcompat:1.7.0")',
    '    implementation("androidx.core:core-ktx:1.15.0")\n    implementation("androidx.documentfile:documentfile:1.0.1")\n    implementation("androidx.appcompat:appcompat:1.7.0")',
)

repo = "app/src/main/java/com/music/bitchord/data/LocalMediaRepository.kt"
replace_once(
    repo,
    '''import androidx.core.content.ContextCompat\nimport com.music.bitchord.data.model.Song''',
    '''import androidx.core.content.ContextCompat\nimport androidx.documentfile.provider.DocumentFile\nimport com.music.bitchord.data.local.LocalMusicCatalog\nimport com.music.bitchord.data.local.LocalMusicTrack\nimport com.music.bitchord.data.model.Song\nimport com.music.bitchord.data.settings.AppSettings''',
)

replace_once(
    repo,
    '''object LocalMediaRepository {\n\n    private const val TAG = "BitChord"''',
    '''object LocalMediaRepository {\n\n    private const val TAG = "BitChord"\n\n    @Volatile\n    private var cachedCatalog: LocalMusicCatalog? = null''',
)

insert = r'''
    /**
     * One catalog for both access modes. MediaStore rows are merged first so a
     * file visible through both A and B keeps the stable MediaStore content URI.
     */
    suspend fun refresh(context: Context): LocalMusicCatalog = withContext(Dispatchers.IO) {
        val allMusicRows = if (
            AppSettings.localAllMusicEnabled.value && hasStoragePermission(context)
        ) {
            getLocalMusic(context).map(::mediaStoreTrack)
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

    private fun mediaStoreTrack(song: Song): LocalMusicTrack {
        val parent = song.localPath
            ?.substringBeforeLast('/', missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() }
            ?: "On device"
        val label = parent.substringAfterLast('/').ifBlank { "On device" }
        return LocalMusicTrack(
            song = song,
            folderKey = parent,
            folderLabel = label,
            identity = identityFor(song),
        )
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
                        val song = buildSongFromUri(
                            context = context,
                            uriStr = uri,
                            fileName = name.ifBlank { "Audio" },
                        ).copy(localPath = "$path/${name.ifBlank { "Audio" }}")
                        rows += LocalMusicTrack(
                            song = song,
                            folderKey = path,
                            folderLabel = path.substringAfterLast('/').ifBlank { rootLabel },
                            identity = identityFor(song),
                        )
                    }
                }
            }
        }

        runCatching { visit(root, rootLabel) }
            .onFailure { Log.w(TAG, "Failed scanning selected music tree: ${it.message}") }
        return rows
    }

    /**
     * A and B can expose the same physical file through different content URIs.
     * Tags + duration give us a provider-independent identity without asking for
     * filesystem-wide path access. The first occurrence wins during merge.
     */
    private fun identityFor(song: Song): String = listOf(
        song.title.trim().lowercase(Locale.ROOT),
        song.artist.trim().lowercase(Locale.ROOT),
        song.albumName.orEmpty().trim().lowercase(Locale.ROOT),
        song.durationText.orEmpty().trim(),
    ).joinToString("|")

'''
replace_once(
    repo,
    '''    private fun isAudioFileName(name: String): Boolean {''',
    insert + '''    private fun isAudioFileName(name: String): Boolean {''',
)

Path("/tmp/tantov-commit-message").write_text("feat(local): merge MediaStore and folder music\n")
