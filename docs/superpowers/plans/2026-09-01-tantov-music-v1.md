# TanTov Music v1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a distinct TanTov Music Android app (`com.tantov.music`) that preserves the working Android Auto integration, adds A+B local-device music access, exposes `Library -> Local Music -> Songs / Folders / Albums / Artists`, combines online + local search, and adds an Android Auto-visible repeat off/all/one control.

**Architecture:** Keep the existing internal Kotlin namespace and single Media3/ExoPlayer playback pipeline. Extend the existing `LocalMediaRepository`, `LocalMusicScreen`, `MainViewModel`, and Android Auto catalog instead of introducing a second media stack. MediaStore provides “All Music”, Storage Access Framework tree grants provide user-selected folders, and both feed one deduplicated in-memory local catalog.

**Tech Stack:** Kotlin, Android SDK 26–36, Jetpack Compose, Android MediaStore, Storage Access Framework, AndroidX DocumentFile, Media3 1.11.0 / ExoPlayer, coroutines, JUnit 4, Android instrumentation tests, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-01-tantov-music-local-library-design.md`

## Global Constraints

- Permanent Android application ID: `com.tantov.music`.
- Internal Kotlin namespace remains `com.music.bitchord` for this release.
- Preserve the existing single `MediaLibraryService`, one `MediaLibrarySession`, and one active ExoPlayer pipeline.
- Do not request `MANAGE_EXTERNAL_STORAGE` or unrestricted all-files access.
- A = MediaStore audio permission; B = persisted `OpenDocumentTree` grants.
- A and B may be enabled simultaneously.
- Local Music remains a separate Library destination while search mixes online + local tracks.
- Local playback must use normal `content://` URIs and work offline.
- Existing original BitChord package remains independently installable.
- Existing Android Auto legacy-browser smoke coverage must continue to pass.

---

## File Structure Map

### Existing files to extend

- `app/build.gradle.kts` — TanTov application ID/label and DocumentFile dependency.
- `app/src/main/java/com/music/bitchord/data/LocalMediaRepository.kt` — MediaStore + SAF scanning, dedup, cache/index.
- `app/src/main/java/com/music/bitchord/data/settings/AppSettings.kt` — persist setup state, A toggle, and selected tree URIs.
- `app/src/main/java/com/music/bitchord/ui/MainViewModel.kt` — local catalog refresh and combined phone search.
- `app/src/main/java/com/music/bitchord/MainActivity.kt` — first-run setup host, permission launchers, Local Music settings navigation.
- `app/src/main/java/com/music/bitchord/ui/screens/SettingsSheet.kt` — add Local Music settings entry.
- `app/src/main/java/com/music/bitchord/ui/screens/LocalMusicScreen.kt` — add Folders tab.
- `app/src/main/java/com/music/bitchord/ui/screens/SearchScreen.kt` — mark local rows `On device`.
- `app/src/main/java/com/music/bitchord/playback/AndroidAutoMediaIds.kt` — stable TanTov local browse routes.
- `app/src/main/java/com/music/bitchord/playback/AndroidAutoCatalog.kt` — local Library tree + combined search.
- `app/src/main/java/com/music/bitchord/playback/PlaybackService.kt` — local source injection and repeat custom command/button.
- `.github/workflows/android-auto-legacy-smoke.yml` — run the real legacy MediaBrowser smoke workflow on the TanTov branch as well as the Android Auto branch.

### New focused files

- `app/src/main/java/com/music/bitchord/data/local/LocalMusicModels.kt` — local track/folder/catalog value types and pure dedup helpers.
- `app/src/main/java/com/music/bitchord/data/local/LocalMusicAccess.kt` — pure access configuration helpers.
- `app/src/main/java/com/music/bitchord/ui/screens/LocalMusicSetupSheet.kt` — first-run A / B / A+B / Not now UI.
- `app/src/main/java/com/music/bitchord/ui/screens/LocalMusicSettingsScreen.kt` — manage All Music, folders, add/remove/rescan.
- `app/src/main/java/com/music/bitchord/playback/AndroidAutoLocalDataSource.kt` — interface + production adapter around local repository.
- `app/src/dev/res/drawable/ic_launcher_background.xml` — TanTov icon background override.
- `app/src/dev/res/drawable/ic_launcher_foreground.xml` — TanTov icon foreground override.
- `app/src/dev/res/drawable/ic_notification_logo.xml` — TanTov notification mark override.
- `app/src/dev/res/mipmap-anydpi/ic_launcher.xml` — dev adaptive launcher override.
- `app/src/dev/res/mipmap-anydpi/ic_launcher_round.xml` — dev round launcher override.

### New tests

- `app/src/test/java/com/music/bitchord/TanTovIdentityTest.kt`
- `app/src/test/java/com/music/bitchord/LocalMusicAccessTest.kt`
- `app/src/test/java/com/music/bitchord/LocalMusicCatalogTest.kt`
- `app/src/test/java/com/music/bitchord/LocalMusicSearchTest.kt`
- `app/src/test/java/com/music/bitchord/AndroidAutoLocalMusicTest.kt`
- `app/src/test/java/com/music/bitchord/AndroidAutoRepeatCommandTest.kt`

---

### Task 1: TanTov package identity and dev-only branding

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/dev/res/drawable/ic_launcher_background.xml`
- Create: `app/src/dev/res/drawable/ic_launcher_foreground.xml`
- Create: `app/src/dev/res/drawable/ic_notification_logo.xml`
- Create: `app/src/dev/res/mipmap-anydpi/ic_launcher.xml`
- Create: `app/src/dev/res/mipmap-anydpi/ic_launcher_round.xml`
- Modify: `app/src/main/java/com/music/bitchord/playback/AndroidAutoCatalog.kt`
- Test: `app/src/test/java/com/music/bitchord/TanTovIdentityTest.kt`

**Interfaces:**
- Consumes: existing `dev` flavor and main manifest launcher resource names.
- Produces: TanTov build with `BuildConfig.APPLICATION_ID == "com.tantov.music"`, label `TanTov Music`, TanTov launcher/notification resources, and Android Auto root title `TanTov Music`.

- [ ] **Step 1: Write the failing identity test**

```kotlin
package com.music.bitchord

import org.junit.Assert.assertEquals
import org.junit.Test

class TanTovIdentityTest {
    @Test
    fun devBuildUsesPermanentTanTovApplicationId() {
        assertEquals("com.tantov.music", BuildConfig.APPLICATION_ID)
    }
}
```

- [ ] **Step 2: Run the identity test and verify RED**

```bash
./gradlew testDevDebugUnitTest --tests com.music.bitchord.TanTovIdentityTest
```

Expected: FAIL because the current dev application ID is `com.dev.bitchord`.

- [ ] **Step 3: Change only the dev flavor identity**

In `app/build.gradle.kts`:

```kotlin
create("dev") {
    dimension = "env"
    applicationId = "com.tantov.music"
    resValue("string", "app_name", "TanTov Music")
}
```

Keep `namespace = "com.music.bitchord"` and the prod application ID unchanged.

Change the Android Auto root label:

```kotlin
fun root(): MediaItem = browsable(AndroidAutoRoute.Root, "TanTov Music")
```

- [ ] **Step 4: Add dev-only launcher resources**

`app/src/dev/res/drawable/ic_launcher_background.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <gradient
        android:angle="315"
        android:startColor="#111827"
        android:centerColor="#312E81"
        android:endColor="#7C3AED" />
</shape>
```

`app/src/dev/res/drawable/ic_launcher_foreground.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:fillColor="#FFFFFFFF"
        android:pathData="M22,30 L50,30 L50,38 L40,38 L40,76 L32,76 L32,38 L22,38 Z" />
    <path android:fillColor="#FFFFFFFF"
        android:pathData="M54,30 L82,30 L82,38 L72,38 L72,58 L64,58 L64,38 L54,38 Z" />
    <path android:fillColor="#FFE9D5FF"
        android:pathData="M69,49 L76,47 L76,69 C76,75 72,79 66,79 C61,79 58,76 58,72 C58,68 62,65 67,65 C68,65 69,65 69,66 Z" />
</vector>
```

`app/src/dev/res/drawable/ic_notification_logo.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path android:fillColor="#FFFFFFFF"
        android:pathData="M2,4 H10 V6 H7 V20 H5 V6 H2 Z" />
    <path android:fillColor="#FFFFFFFF"
        android:pathData="M11,4 H21 V6 H18 V12.5 L16,13.1 V6 H11 Z M16,12 V17 C16,19 14.6,20 12.7,20 C11.1,20 10,19.1 10,17.8 C10,16.4 11.3,15.4 13,15.4 C13.4,15.4 13.8,15.5 14,15.6 V12.6 Z" />
</vector>
```

Both `app/src/dev/res/mipmap-anydpi/ic_launcher.xml` and `ic_launcher_round.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

- [ ] **Step 5: Run the identity test and build**

```bash
./gradlew testDevDebugUnitTest --tests com.music.bitchord.TanTovIdentityTest assembleDevDebug
```

Expected: PASS and dev APK builds.

- [ ] **Step 6: Commit**

```bash
git add app/build.gradle.kts app/src/dev/res app/src/main/java/com/music/bitchord/playback/AndroidAutoCatalog.kt app/src/test/java/com/music/bitchord/TanTovIdentityTest.kt
git commit -m "feat(tantov): establish app identity"
```

---

### Task 2: Persist A+B Local Music access state

**Files:**
- Create: `app/src/main/java/com/music/bitchord/data/local/LocalMusicAccess.kt`
- Modify: `app/src/main/java/com/music/bitchord/data/settings/AppSettings.kt`
- Test: `app/src/test/java/com/music/bitchord/LocalMusicAccessTest.kt`

**Interfaces:**
- Produces: `LocalMusicAccessConfig(setupSeen, allMusicEnabled, treeUris)` and AppSettings flows/setters used by repository/UI.

- [ ] **Step 1: Write failing access-state tests**

```kotlin
package com.music.bitchord

import com.music.bitchord.data.local.LocalMusicAccessConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMusicAccessTest {
    @Test
    fun aAndBMayBeEnabledTogether() {
        val config = LocalMusicAccessConfig(
            setupSeen = true,
            allMusicEnabled = true,
            treeUris = setOf("content://tree/music", "content://tree/tamil"),
        )
        assertTrue(config.allMusicEnabled)
        assertEquals(2, config.treeUris.size)
    }

    @Test
    fun removingFolderDoesNotDisableAllMusic() {
        val config = LocalMusicAccessConfig(true, true, setOf("one", "two")).removeTree("one")
        assertTrue(config.allMusicEnabled)
        assertEquals(setOf("two"), config.treeUris)
    }

    @Test
    fun skipMarksSetupSeenWithoutGrantingAnything() {
        val config = LocalMusicAccessConfig().markSetupSeen()
        assertTrue(config.setupSeen)
        assertFalse(config.allMusicEnabled)
        assertTrue(config.treeUris.isEmpty())
    }
}
```

- [ ] **Step 2: Run RED**

```bash
./gradlew testDevDebugUnitTest --tests com.music.bitchord.LocalMusicAccessTest
```

Expected: FAIL because `LocalMusicAccessConfig` does not exist.

- [ ] **Step 3: Add the pure access model**

```kotlin
package com.music.bitchord.data.local

data class LocalMusicAccessConfig(
    val setupSeen: Boolean = false,
    val allMusicEnabled: Boolean = false,
    val treeUris: Set<String> = emptySet(),
) {
    fun markSetupSeen() = copy(setupSeen = true)
    fun withAllMusic(enabled: Boolean) = copy(allMusicEnabled = enabled)
    fun addTree(uri: String) = copy(treeUris = treeUris + uri)
    fun removeTree(uri: String) = copy(treeUris = treeUris - uri)
}
```

- [ ] **Step 4: Add AppSettings flows, reads and setters**

```kotlin
val localMusicSetupSeen = MutableStateFlow(false)
val localAllMusicEnabled = MutableStateFlow(false)
val localMusicTreeUris = MutableStateFlow<Set<String>>(emptySet())
```

In `readAll()`:

```kotlin
localMusicSetupSeen.value = prefs.getBoolean("local_music_setup_seen", false)
localAllMusicEnabled.value = prefs.getBoolean("local_all_music_enabled", false)
localMusicTreeUris.value = prefs.getStringSet("local_music_tree_uris", emptySet()).orEmpty().toSet()
```

Setters:

```kotlin
fun setLocalMusicSetupSeen(value: Boolean) {
    localMusicSetupSeen.value = value
    prefs.edit().putBoolean("local_music_setup_seen", value).apply()
}

fun setLocalAllMusicEnabled(value: Boolean) {
    localAllMusicEnabled.value = value
    prefs.edit().putBoolean("local_all_music_enabled", value).apply()
}

fun addLocalMusicTreeUri(uri: String) {
    val updated = localMusicTreeUris.value + uri
    localMusicTreeUris.value = updated
    prefs.edit().putStringSet("local_music_tree_uris", updated).apply()
}

fun removeLocalMusicTreeUri(uri: String) {
    val updated = localMusicTreeUris.value - uri
    localMusicTreeUris.value = updated
    prefs.edit().putStringSet("local_music_tree_uris", updated).apply()
}
```

- [ ] **Step 5: Run tests**

```bash
./gradlew testDevDebugUnitTest --tests com.music.bitchord.LocalMusicAccessTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/music/bitchord/data/local/LocalMusicAccess.kt app/src/main/java/com/music/bitchord/data/settings/AppSettings.kt app/src/test/java/com/music/bitchord/LocalMusicAccessTest.kt
git commit -m "feat(local): persist music access choices"
```

---

### Task 3: Merge MediaStore + selected-folder scanning into one local catalog

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/music/bitchord/data/local/LocalMusicModels.kt`
- Modify: `app/src/main/java/com/music/bitchord/data/LocalMediaRepository.kt`
- Test: `app/src/test/java/com/music/bitchord/LocalMusicCatalogTest.kt`

**Interfaces:**
- Produces `LocalMusicTrack`, `LocalMusicFolder`, `LocalMusicCatalog`.
- Produces `LocalMediaRepository.refresh(context)`, `catalog(context)`, and `invalidate()`.

- [ ] **Step 1: Add DocumentFile dependency**

```kotlin
implementation("androidx.documentfile:documentfile:1.0.1")
```

- [ ] **Step 2: Write RED tests for dedup/folders**

```kotlin
package com.music.bitchord

import com.music.bitchord.data.local.LocalMusicCatalog
import com.music.bitchord.data.local.LocalMusicTrack
import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalMusicCatalogTest {
    private fun track(id: String, identity: String, folder: String) = LocalMusicTrack(
        song = Song(videoId = id, title = id, artist = "Artist", localUri = id),
        folderKey = folder,
        folderLabel = folder.substringAfterLast('/'),
        identity = identity,
    )

    @Test
    fun sameUnderlyingTrackSeenByAAndBIsReturnedOnce() {
        val catalog = LocalMusicCatalog.merge(
            mediaStore = listOf(track("content://media/1", "primary:Music/a.mp3", "Music")),
            trees = listOf(track("content://tree/a", "primary:Music/a.mp3", "Music")),
        )
        assertEquals(1, catalog.tracks.size)
    }

    @Test
    fun equalLeafFolderNamesRemainDistinctByPath() {
        val catalog = LocalMusicCatalog.merge(
            mediaStore = listOf(
                track("1", "primary:Music/Tamil/a.mp3", "Music/Tamil"),
                track("2", "primary:Downloads/Tamil/b.mp3", "Downloads/Tamil"),
            ),
            trees = emptyList(),
        )
        assertEquals(2, catalog.folders.size)
    }
}
```

- [ ] **Step 3: Run RED**

```bash
./gradlew testDevDebugUnitTest --tests com.music.bitchord.LocalMusicCatalogTest
```

Expected: FAIL because local catalog models do not exist.

- [ ] **Step 4: Add catalog models**

```kotlin
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
        .map { (key, rows) -> LocalMusicFolder(key, rows.first().folderLabel, rows.map { it.song }) }
        .sortedBy { it.label.lowercase(Locale.ROOT) }

    fun search(query: String): List<Song> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return songs.filter { song ->
            song.title.contains(q, true) ||
                song.artist.contains(q, true) ||
                song.albumName?.contains(q, true) == true
        }
    }

    companion object {
        fun merge(mediaStore: List<LocalMusicTrack>, trees: List<LocalMusicTrack>): LocalMusicCatalog {
            val byIdentity = linkedMapOf<String, LocalMusicTrack>()
            (mediaStore + trees).forEach { row -> byIdentity.putIfAbsent(row.identity, row) }
            return LocalMusicCatalog(byIdentity.values.toList())
        }
    }
}
```

- [ ] **Step 5: Refactor MediaStore scan to produce `LocalMusicTrack`**

For API 29+, include `DISPLAY_NAME`, `RELATIVE_PATH`, and `VOLUME_NAME`; for older Android retain `DATA`. Build the track with this exact fallback order:

```kotlin
val folder = relativePath?.trimEnd('/')
    ?: path?.substringBeforeLast('/').orEmpty()
val identity = when {
    !relativePath.isNullOrBlank() -> "$volumeName:${relativePath.trim('/')}/$displayName"
    !path.isNullOrBlank() -> path
    else -> "media:$id:$durationMs:$displayName"
}
val row = LocalMusicTrack(
    song = Song(
        videoId = videoIdByUri[contentUri] ?: contentUri,
        title = title,
        artist = artist,
        thumbnailUrl = artworkUrl,
        durationText = formatDuration(durationMs),
        albumName = albumName,
        localUri = contentUri,
        localPath = "$folder/$displayName",
    ),
    folderKey = folder.ifBlank { "On device" },
    folderLabel = folder.substringAfterLast('/').ifBlank { "On device" },
    identity = identity,
)
```

- [ ] **Step 6: Add recursive SAF tree scan**

Imports:

```kotlin
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
```

For each stored tree URI:

```kotlin
private fun scanTree(context: Context, treeUri: String): List<LocalMusicTrack> {
    val rootUri = Uri.parse(treeUri)
    val root = DocumentFile.fromTreeUri(context, rootUri) ?: return emptyList()
    val rootLabel = root.name ?: "Selected folder"
    val out = mutableListOf<LocalMusicTrack>()

    fun walk(folder: DocumentFile, path: String) {
        folder.listFiles().forEach { child ->
            when {
                child.isDirectory -> walk(child, "$path/${child.name ?: "Folder"}")
                child.isFile && (
                    child.type?.startsWith("audio/") == true ||
                        isAudioFileName(child.name.orEmpty())
                ) -> out += buildTrackFromDocument(context, child, path)
            }
        }
    }

    return runCatching {
        walk(root, rootLabel)
        out
    }.getOrElse { error ->
        if (error is SecurityException) emptyList() else throw error
    }
}
```

`buildTrackFromDocument` must read title/artist/album/duration with `MediaMetadataRetriever`, set `Song.localUri = file.uri.toString()`, set `Song.localPath = "$path/${file.name}"`, and use this identity:

```kotlin
val identity = runCatching { DocumentsContract.getDocumentId(file.uri) }
    .getOrNull()
    ?.takeIf { it.isNotBlank() }
    ?: "saf:${file.uri}:${file.length()}:$durationMs"
```

- [ ] **Step 7: Add cache/refresh API**

```kotlin
@Volatile private var cachedCatalog: LocalMusicCatalog? = null

suspend fun refresh(context: Context): LocalMusicCatalog = withContext(Dispatchers.IO) {
    val media = if (AppSettings.localAllMusicEnabled.value && hasStoragePermission(context)) {
        scanMediaStore(context)
    } else {
        emptyList()
    }
    val trees = AppSettings.localMusicTreeUris.value.flatMap { scanTree(context, it) }
    LocalMusicCatalog.merge(media, trees).also { cachedCatalog = it }
}

suspend fun catalog(context: Context): LocalMusicCatalog = cachedCatalog ?: refresh(context)

fun invalidate() {
    cachedCatalog = null
}

suspend fun getLocalMusic(context: Context): List<Song> = catalog(context).songs
```

Keep `getDownloadedSongs()` unchanged.

- [ ] **Step 8: Run tests/build**

```bash
./gradlew testDevDebugUnitTest --tests com.music.bitchord.LocalMusicCatalogTest assembleDevDebug
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/com/music/bitchord/data/LocalMediaRepository.kt app/src/main/java/com/music/bitchord/data/local/LocalMusicModels.kt app/src/test/java/com/music/bitchord/LocalMusicCatalogTest.kt
git commit -m "feat(local): merge MediaStore and folder music"
```

---

### Task 4: First-run setup and Local Music settings

**Files:**
- Create: `app/src/main/java/com/music/bitchord/ui/screens/LocalMusicSetupSheet.kt`
- Create: `app/src/main/java/com/music/bitchord/ui/screens/LocalMusicSettingsScreen.kt`
- Modify: `app/src/main/java/com/music/bitchord/MainActivity.kt`
- Modify: `app/src/main/java/com/music/bitchord/ui/screens/SettingsSheet.kt`
- Modify: `app/src/main/java/com/music/bitchord/ui/MainViewModel.kt`

**Interfaces:**
- Consumes AppSettings access flows and `LocalMediaRepository.invalidate/refresh`.
- Produces skippable first-run A/B/A+B setup and later folder management.

- [ ] **Step 1: Add a persistent tree picker in `BitChordApp`**

```kotlin
val folderPicker = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocumentTree(),
) { uri ->
    if (uri != null) {
        val persisted = runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            true
        }.getOrDefault(false)
        if (persisted) {
            AppSettings.addLocalMusicTreeUri(uri.toString())
            LocalMediaRepository.invalidate()
            viewModel.reloadLocalDetail("local:all")
        }
    }
}
```

When removing a folder:

```kotlin
runCatching {
    context.contentResolver.releasePersistableUriPermission(
        Uri.parse(treeUri),
        Intent.FLAG_GRANT_READ_URI_PERMISSION,
    )
}
AppSettings.removeLocalMusicTreeUri(treeUri)
LocalMediaRepository.invalidate()
viewModel.reloadLocalDetail("local:all")
```

- [ ] **Step 2: Create first-run setup sheet**

```kotlin
@Composable
fun LocalMusicSetupSheet(
    onAllMusic: () -> Unit,
    onChooseFolders: () -> Unit,
    onUseBoth: () -> Unit,
    onNotNow: () -> Unit,
)
```

Render four actions with exact labels:
- `All Music on this phone`
- `Choose music folders`
- `Use both`
- `Not now`

Every action calls `AppSettings.setLocalMusicSetupSeen(true)`. `All Music` sets `localAllMusicEnabled = true` and launches the existing audio permission request. `Choose music folders` launches `folderPicker`. `Use both` sets A true, requests audio permission, and leaves the folder picker action immediately available after the permission result. `Not now` grants nothing.

- [ ] **Step 3: Gate the sheet on first launch**

```kotlin
val localSetupSeen by AppSettings.localMusicSetupSeen.collectAsStateWithLifecycle()
var showLocalSetup by rememberSaveable { mutableStateOf(!localSetupSeen) }

LaunchedEffect(localSetupSeen) {
    if (localSetupSeen) showLocalSetup = false
}
```

- [ ] **Step 4: Add Settings -> Local Music**

Add `onLocalMusic: () -> Unit` to `SettingsScreen` and this row:

```kotlin
SettingsRow(
    icon = Icons.Rounded.Storage,
    title = "Local Music",
    subtitle = "All Music, selected folders and rescan",
    onClick = onLocalMusic,
)
```

Create `LocalMusicSettingsScreen` with:
- switch for A (`AppSettings.localAllMusicEnabled`), requesting audio permission when turned on without permission;
- current tree URIs, displaying `DocumentFile.fromTreeUri(context, Uri.parse(uri))?.name ?: uri`;
- `Add folder` -> `folderPicker.launch(null)`;
- remove button -> release grant + remove setting + invalidate;
- `Rescan music` -> coroutine calls `LocalMediaRepository.refresh(context)` and displays `${catalog.songs.size} songs`.

- [ ] **Step 5: Compile the complete setup/settings flow**

```bash
./gradlew assembleDevDebug
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/music/bitchord/MainActivity.kt app/src/main/java/com/music/bitchord/ui/MainViewModel.kt app/src/main/java/com/music/bitchord/ui/screens/SettingsSheet.kt app/src/main/java/com/music/bitchord/ui/screens/LocalMusicSetupSheet.kt app/src/main/java/com/music/bitchord/ui/screens/LocalMusicSettingsScreen.kt
git commit -m "feat(local): add first-run and settings access flow"
```

---

### Task 5: Add Folders to phone Local Music

**Files:**
- Modify: `app/src/main/java/com/music/bitchord/ui/MainViewModel.kt`
- Modify: `app/src/main/java/com/music/bitchord/ui/screens/LocalMusicScreen.kt`
- Test: `app/src/test/java/com/music/bitchord/LocalMusicCatalogTest.kt`

**Interfaces:**
- Consumes `LocalMusicCatalog`.
- Produces phone `Local Music -> Songs / Folders / Artists / Albums`.

- [ ] **Step 1: Add alphabetical folder test**

```kotlin
@Test
fun folderListIsAlphabetical() {
    val catalog = LocalMusicCatalog.merge(
        mediaStore = listOf(
            track("2", "two", "Music/Zed"),
            track("1", "one", "Music/Alpha"),
        ),
        trees = emptyList(),
    )
    assertEquals(listOf("Alpha", "Zed"), catalog.folders.map { it.label })
}
```

- [ ] **Step 2: Run test**

```bash
./gradlew testDevDebugUnitTest --tests com.music.bitchord.LocalMusicCatalogTest
```

Expected: PASS once Task 3 sorting is present; if it fails, fix catalog ordering before touching UI.

- [ ] **Step 3: Migrate `local:all` ViewModel reads**

Use the merged catalog rather than treating missing MediaStore permission as fatal:

```kotlin
val catalog = LocalMediaRepository.catalog(context)
if (catalog.songs.isEmpty()) UiState.Error("No audio files found on device")
else UiState.Success(catalog.songs)
```

Do not return `Storage permission required` when A is unavailable if a selected B folder still provides songs.

- [ ] **Step 4: Add the Folders tab**

```kotlin
private const val LOCAL_TAB_SONGS = 0
private const val LOCAL_TAB_FOLDERS = 1
private const val LOCAL_TAB_ARTISTS = 2
private const val LOCAL_TAB_ALBUMS = 3
```

Add:

```kotlin
LocalTab(
    icon = Icons.Rounded.Folder,
    label = "Folders",
    selected = selectedTab == LOCAL_TAB_FOLDERS,
    onClick = {
        selectedTab = LOCAL_TAB_FOLDERS
        leaveDrillDown()
    },
)
```

Build folder groups from each `Song.localPath?.substringBeforeLast('/')`; use the full parent as the key and `substringAfterLast('/')` as the label. Tapping a folder sets `drillDownLabel` and `drillDownSongs`, reusing `DrillDownSongList`.

- [ ] **Step 5: Run tests/build**

```bash
./gradlew testDevDebugUnitTest --tests com.music.bitchord.LocalMusicCatalogTest assembleDevDebug
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/music/bitchord/ui/MainViewModel.kt app/src/main/java/com/music/bitchord/ui/screens/LocalMusicScreen.kt app/src/test/java/com/music/bitchord/LocalMusicCatalogTest.kt
git commit -m "feat(local): browse music by folder"
```

---

### Task 6: Combine phone search with local results and offline fallback

**Files:**
- Modify: `app/src/main/java/com/music/bitchord/ui/MainViewModel.kt`
- Modify: `app/src/main/java/com/music/bitchord/ui/screens/SearchScreen.kt`
- Test: `app/src/test/java/com/music/bitchord/LocalMusicSearchTest.kt`

**Interfaces:**
- Produces Songs-tab search with online + local tracks and `On device` labels.

- [ ] **Step 1: Write RED merge tests**

```kotlin
package com.music.bitchord

import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.mergeSongSearchResults
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalMusicSearchTest {
    @Test
    fun localSongsAreIncludedAfterOnlineSongs() {
        val onlineSong = Song("yt", "Yellow", "Coldplay")
        val localSong = Song("local", "Yellow Live", "Coldplay", localUri = "content://local")
        val merged = mergeSongSearchResults(
            Result.success(listOf(SearchResult.Track(onlineSong))),
            listOf(localSong),
        ).getOrThrow()
        assertEquals(2, merged.size)
    }

    @Test
    fun onlineFailureStillReturnsLocalSongs() {
        val localSong = Song("local", "Local Track", "Artist", localUri = "content://local")
        val merged = mergeSongSearchResults(
            Result.failure(IllegalStateException("offline")),
            listOf(localSong),
        ).getOrThrow()
        assertEquals("Local Track", (merged.single() as SearchResult.Track).song.title)
    }
}
```

- [ ] **Step 2: Run RED**

```bash
./gradlew testDevDebugUnitTest --tests com.music.bitchord.LocalMusicSearchTest
```

Expected: FAIL because `mergeSongSearchResults` does not exist.

- [ ] **Step 3: Implement merge helper**

```kotlin
internal fun mergeSongSearchResults(
    online: Result<List<SearchResult>>,
    local: List<Song>,
): Result<List<SearchResult>> {
    val localRows = local.map(SearchResult::Track)
    val onlineRows = online.getOrNull()
    if (onlineRows == null && localRows.isEmpty()) return Result.failure(online.exceptionOrNull()!!)
    val merged = (onlineRows.orEmpty() + localRows).distinctBy { result ->
        when (result) {
            is SearchResult.Track -> result.song.localUri ?: result.song.videoId
            is SearchResult.Browse -> "browse:${result.item.browseId}"
        }
    }
    return Result.success(merged)
}
```

- [ ] **Step 4: Use it only for Songs filter**

```kotlin
val online = YtMusicRepository.search(request.query, request.filter)
val localSongs = if (request.filter == SearchFilter.SONGS) {
    LocalMediaRepository.catalog(getApplication()).search(request.query)
} else {
    emptyList()
}
val result = if (request.filter == SearchFilter.SONGS) {
    mergeSongSearchResults(online, localSongs)
} else {
    online
}
```

- [ ] **Step 5: Mark local SearchScreen rows**

When rendering `SearchResult.Track`:

```kotlin
val subtitle = if (song.localUri != null) {
    listOf(song.artist, "On device").filter { it.isNotBlank() }.joinToString(" • ")
} else {
    song.artist
}
```

- [ ] **Step 6: Run tests/build**

```bash
./gradlew testDevDebugUnitTest --tests com.music.bitchord.LocalMusicSearchTest assembleDevDebug
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/music/bitchord/ui/MainViewModel.kt app/src/main/java/com/music/bitchord/ui/screens/SearchScreen.kt app/src/test/java/com/music/bitchord/LocalMusicSearchTest.kt
git commit -m "feat(search): combine online and local music"
```

---

### Task 7: Extend Android Auto with Local Music browse/search

**Files:**
- Create: `app/src/main/java/com/music/bitchord/playback/AndroidAutoLocalDataSource.kt`
- Modify: `app/src/main/java/com/music/bitchord/playback/AndroidAutoMediaIds.kt`
- Modify: `app/src/main/java/com/music/bitchord/playback/AndroidAutoCatalog.kt`
- Modify: `app/src/main/java/com/music/bitchord/playback/PlaybackService.kt`
- Test: `app/src/test/java/com/music/bitchord/AndroidAutoLocalMusicTest.kt`
- Modify existing Android Auto ID/catalog/search tests for the new TanTov ID prefix where assertions depend on it.

**Interfaces:**
- Produces Android Auto `Library -> Local Music -> Songs / Folders / Albums / Artists` and combined local/online search.

- [ ] **Step 1: Add injectable local data source**

```kotlin
interface AndroidAutoLocalDataSource {
    suspend fun catalog(): LocalMusicCatalog
    suspend fun search(query: String): List<Song>
}

object EmptyAndroidAutoLocalDataSource : AndroidAutoLocalDataSource {
    override suspend fun catalog() = LocalMusicCatalog(emptyList())
    override suspend fun search(query: String) = emptyList<Song>()
}

class DeviceAndroidAutoLocalDataSource(private val context: Context) : AndroidAutoLocalDataSource {
    override suspend fun catalog() = LocalMediaRepository.catalog(context)
    override suspend fun search(query: String) = catalog().search(query)
}
```

Change catalog constructor without breaking old tests:

```kotlin
class AndroidAutoCatalog(
    private val dataSource: AndroidAutoDataSource,
    private val localDataSource: AndroidAutoLocalDataSource = EmptyAndroidAutoLocalDataSource,
    private val nowMs: () -> Long = System::currentTimeMillis,
)
```

- [ ] **Step 2: Write RED local browse/search tests**

Use a fake local source and assert:

```kotlin
@Test
fun libraryContainsSeparateLocalMusicFolder() = runTest {
    val rows = catalog.children(AndroidAutoRoute.Library, 0, 100).getOrThrow()
    assertTrue(rows.any { it.mediaMetadata.title == "Local Music" })
}

@Test
fun localMusicHasFourSections() = runTest {
    val rows = catalog.children(AndroidAutoRoute.LocalMusic, 0, 100).getOrThrow()
    assertEquals(
        listOf("Songs", "Folders", "Albums", "Artists"),
        rows.map { it.mediaMetadata.title.toString() },
    )
}

@Test
fun onlineFailureStillReturnsLocalAutoSearch() = runTest {
    online.failSearch = true
    val rows = catalog.search("local", 0, 20).getOrThrow()
    assertTrue(rows.any { it.mediaMetadata.description?.contains("On device") == true })
}
```

- [ ] **Step 3: Add TanTov local media routes**

```kotlin
data object LocalMusic : AndroidAutoRoute
data class LocalSection(val section: AndroidAutoLocalSection) : AndroidAutoRoute
data class LocalCollection(
    val kind: AndroidAutoLocalCollectionKind,
    val key: String,
) : AndroidAutoRoute

enum class AndroidAutoLocalSection { SONGS, FOLDERS, ALBUMS, ARTISTS }
enum class AndroidAutoLocalCollectionKind { FOLDER, ALBUM, ARTIST }
```

Change media ID prefix to:

```kotlin
private const val PREFIX = "tantov:auto:v1"
```

Use existing URL-safe Base64 helpers for local collection keys.

- [ ] **Step 4: Make Library local-aware without YouTube sign-in**

Route:

```kotlin
AndroidAutoRoute.Library -> libraryFolders()
```

Folder builder:

```kotlin
private suspend fun libraryFolders(): List<MediaItem> = buildList {
    val local = localDataSource.catalog()
    if (local.songs.isNotEmpty()) {
        add(browsable(AndroidAutoRoute.LocalMusic, "Local Music"))
    }
    if (!dataSource.isSignedIn()) return@buildList
    val page = library()
    if (page.likedSongs.isNotEmpty()) {
        add(browsable(AndroidAutoRoute.LibrarySection(AndroidAutoLibrarySection.LIKED), "Liked Songs"))
    }
    if (page.librarySongs.isNotEmpty()) {
        add(browsable(AndroidAutoRoute.LibrarySection(AndroidAutoLibrarySection.SONGS), "Songs"))
    }
    LIBRARY_SHELVES.forEach { (section, title) ->
        if (page.shelves.firstOrNull { it.title.equals(title, true) }?.items?.isNotEmpty() == true) {
            add(browsable(AndroidAutoRoute.LibrarySection(section), title))
        }
    }
}
```

- [ ] **Step 5: Implement four local browse sections**

`LocalMusic` returns four `LocalSection` rows. `SONGS` returns `catalog.songs.map(::playableRow)`. `FOLDERS` maps `catalog.folders` to `LocalCollection(FOLDER, folder.key)`. `ALBUMS` groups nonblank `song.albumName`; `ARTISTS` groups nonblank `song.artist`. A `LocalCollection` route returns the group’s songs.

Add extras to playable rows:

```kotlin
putString(EXTRA_LOCAL_URI, song.localUri)
putString(EXTRA_LOCAL_PATH, song.localPath)
```

Restore them in `songFromBrowseRow`:

```kotlin
localUri = extras?.getString(EXTRA_LOCAL_URI),
localPath = extras?.getString(EXTRA_LOCAL_PATH),
```

- [ ] **Step 6: Merge Android Auto search with local fallback**

Implement online and local parts independently. Produce `localRows = localDataSource.search(clean).map(::playableRow)`. If online search fails and `localRows` is nonempty, return local rows; if both have results, append local rows after online rows and deduplicate by media ID; if online fails and local rows are empty, propagate online failure.

For a local playable row, description must be:

```kotlin
setDescription(
    listOf(song.artist, "On device")
        .filter { it.isNotBlank() }
        .joinToString(" • "),
)
```

- [ ] **Step 7: Inject device local source**

```kotlin
private val androidAutoCatalog by lazy {
    AndroidAutoCatalog(
        dataSource = YtMusicAndroidAutoDataSource,
        localDataSource = DeviceAndroidAutoLocalDataSource(this),
    )
}
```

- [ ] **Step 8: Run Android Auto unit tests**

```bash
./gradlew testDevDebugUnitTest --tests 'com.music.bitchord.AndroidAuto*'
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/music/bitchord/playback app/src/test/java/com/music/bitchord/AndroidAutoLocalMusicTest.kt app/src/test/java/com/music/bitchord/AndroidAutoMediaIdsTest.kt
git commit -m "feat(auto): browse and search local music"
```

---

### Task 8: Add Android Auto repeat control and complete verification

**Files:**
- Modify: `app/src/main/java/com/music/bitchord/playback/PlaybackService.kt`
- Modify: `app/src/main/java/com/music/bitchord/MainActivity.kt`
- Modify: `.github/workflows/android-auto-legacy-smoke.yml`
- Test: `app/src/test/java/com/music/bitchord/AndroidAutoRepeatCommandTest.kt`

**Interfaces:**
- Produces repeat cycle OFF -> ALL -> ONE -> OFF through the same player state on phone and Android Auto.

- [ ] **Step 1: Write RED repeat-cycle test**

```kotlin
package com.music.bitchord

import androidx.media3.common.Player
import com.music.bitchord.playback.nextRepeatMode
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidAutoRepeatCommandTest {
    @Test
    fun repeatCyclesOffAllOneOff() {
        assertEquals(Player.REPEAT_MODE_ALL, nextRepeatMode(Player.REPEAT_MODE_OFF))
        assertEquals(Player.REPEAT_MODE_ONE, nextRepeatMode(Player.REPEAT_MODE_ALL))
        assertEquals(Player.REPEAT_MODE_OFF, nextRepeatMode(Player.REPEAT_MODE_ONE))
    }
}
```

- [ ] **Step 2: Run RED**

```bash
./gradlew testDevDebugUnitTest --tests com.music.bitchord.AndroidAutoRepeatCommandTest
```

Expected: FAIL because `nextRepeatMode` does not exist.

- [ ] **Step 3: Add shared repeat helper and custom command**

```kotlin
internal fun nextRepeatMode(current: Int): Int = when (current) {
    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
    else -> Player.REPEAT_MODE_OFF
}

const val ACTION_CYCLE_REPEAT = "com.tantov.music.action.CYCLE_REPEAT"
```

In service fields:

```kotlin
private val repeatCommand = SessionCommand(ACTION_CYCLE_REPEAT, Bundle.EMPTY)
```

Add `repeatCommand` to `onConnect()` session commands.

In `onCustomCommand()`:

```kotlin
ACTION_CYCLE_REPEAT -> {
    session.player.repeatMode = nextRepeatMode(session.player.repeatMode)
    session.setCustomLayout(notificationButtons())
}
```

- [ ] **Step 4: Add repeat button to custom layout**

```kotlin
val repeat = CommandButton.Builder(
    when (player?.repeatMode ?: Player.REPEAT_MODE_OFF) {
        Player.REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE
        Player.REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL
        else -> CommandButton.ICON_REPEAT_OFF
    },
)
    .setSessionCommand(repeatCommand)
    .setDisplayName(
        when (player?.repeatMode ?: Player.REPEAT_MODE_OFF) {
            Player.REPEAT_MODE_ONE -> "Repeat one"
            Player.REPEAT_MODE_ALL -> "Repeat all"
            else -> "Repeat off"
        },
    )
    .build()
```

Include `repeat` in `notificationButtons()` and refresh layout from `onRepeatModeChanged`:

```kotlin
mediaSession?.setCustomLayout(notificationButtons())
```

Replace the phone player’s inline repeat `when` with:

```kotlin
it.repeatMode = nextRepeatMode(it.repeatMode)
```

- [ ] **Step 5: Make legacy smoke workflow run on TanTov branch**

Change:

```yaml
on:
  push:
    branches: [feat/android-auto-full, feat/tantov-music-v1]
  pull_request:
    branches: [main]
```

Keep the emulator script exactly:

```yaml
script: ./gradlew connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.music.bitchord.AndroidAutoLegacyBrowserTest
```

- [ ] **Step 6: Run full JVM verification**

```bash
./gradlew testDevDebugUnitTest
```

Expected: PASS with zero failing tests.

- [ ] **Step 7: Build TanTov APK**

```bash
./gradlew assembleDevDebug
```

Expected: `app/build/outputs/apk/dev/debug/app-dev-debug.apk` exists.

- [ ] **Step 8: Verify merged manifest has no all-files permission**

```bash
./gradlew processDevDebugMainManifest
! grep -R "MANAGE_EXTERNAL_STORAGE" app/build/intermediates/merged_manifests/devDebug
```

Expected: command exits successfully because `MANAGE_EXTERNAL_STORAGE` is absent.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/music/bitchord/playback/PlaybackService.kt app/src/main/java/com/music/bitchord/MainActivity.kt app/src/test/java/com/music/bitchord/AndroidAutoRepeatCommandTest.kt .github/workflows/android-auto-legacy-smoke.yml
git commit -m "feat(auto): add repeat mode control"
```

- [ ] **Step 10: Push and verify both GitHub Actions workflows**

Require:
- `Android CI`: Unit tests = success, Build BitChord Dev APK = success, Upload artifact = success.
- `Android Auto Legacy Browser Smoke`: emulator instrumentation job = success.

Download the fresh `bitchord-dev-debug` artifact, rename the extracted APK to `TanTov-Music-Android-Auto-v1.apk`, compute SHA-256, and give it to the user for phone/car validation. Do not claim real-car validation until the user confirms it.

---

## Final Acceptance Checklist

- [ ] `BuildConfig.APPLICATION_ID == "com.tantov.music"`.
- [ ] Phone launcher label is `TanTov Music`.
- [ ] Android Auto launcher uses TanTov name/icon.
- [ ] Original BitChord package can remain installed separately.
- [ ] First launch offers All Music / Choose music folders / Use both / Not now.
- [ ] A and B can stay enabled together.
- [ ] Multiple SAF folders can be added and removed later.
- [ ] Revoked tree grants do not crash scans.
- [ ] No `MANAGE_EXTERNAL_STORAGE` permission.
- [ ] Library has separate Local Music destination.
- [ ] Local Music has Songs / Folders / Albums / Artists.
- [ ] Local files play offline through the existing player.
- [ ] Phone search merges online + local Songs results and labels local rows `On device`.
- [ ] Local search still returns results when online search fails.
- [ ] Android Auto Library exposes Local Music independently of YouTube sign-in.
- [ ] Android Auto local rows play through the existing MediaLibrarySession/ExoPlayer.
- [ ] Android Auto search merges local results and keeps local fallback offline.
- [ ] Repeat cycles OFF -> ALL -> ONE -> OFF from phone and car through the same player state.
- [ ] Existing Android Auto legacy-browser smoke test succeeds.
- [ ] `testDevDebugUnitTest` succeeds.
- [ ] `assembleDevDebug` succeeds.
- [ ] Fresh GitHub Actions APK artifact is produced for real-car testing.
