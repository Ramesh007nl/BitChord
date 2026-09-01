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
- A = MediaStore audio permission; B = persisted `ACTION_OPEN_DOCUMENT_TREE` / `OpenDocumentTree` grants.
- A and B may be enabled simultaneously.
- Local Music remains a separate Library destination while search mixes online + local tracks.
- Local playback must use normal `content://` URIs and work offline.
- Existing original BitChord package remains independently installable.
- Existing Android Auto legacy-browser smoke coverage must continue to pass.

---

## File Structure Map

### Existing files to extend

- `app/build.gradle.kts` — TanTov application ID/label, DocumentFile dependency.
- `app/src/dev/AndroidManifest.xml` — keep Android Auto discovery metadata; no package declaration changes needed.
- `app/src/main/java/com/music/bitchord/data/LocalMediaRepository.kt` — MediaStore + SAF scanning, dedup, cache/index.
- `app/src/main/java/com/music/bitchord/data/settings/AppSettings.kt` — persist setup state, A toggle, and selected tree URIs.
- `app/src/main/java/com/music/bitchord/ui/MainViewModel.kt` — local catalog refresh and combined phone search.
- `app/src/main/java/com/music/bitchord/MainActivity.kt` — first-run setup host, permission launchers, Local Music settings navigation.
- `app/src/main/java/com/music/bitchord/ui/screens/SettingsSheet.kt` — add Local Music settings entry.
- `app/src/main/java/com/music/bitchord/ui/screens/LocalMusicScreen.kt` — add Folders tab.
- `app/src/main/java/com/music/bitchord/ui/screens/SearchScreen.kt` — mark local rows “On device”.
- `app/src/main/java/com/music/bitchord/playback/AndroidAutoMediaIds.kt` — stable local browse routes.
- `app/src/main/java/com/music/bitchord/playback/AndroidAutoCatalog.kt` — local Library tree + combined search.
- `app/src/main/java/com/music/bitchord/playback/PlaybackService.kt` — supply local source to catalog and repeat custom command/button.
- `.github/workflows/android.yml` — keep unit/build artifact checks green.
- `.github/workflows/android-auto-legacy-smoke.yml` — extend smoke assertion to local browse when a fake/local test source is injectable, otherwise preserve current root smoke unchanged and add JVM catalog tests.

### New focused files

- `app/src/main/java/com/music/bitchord/data/local/LocalMusicModels.kt` — local track/folder/catalog value types and pure dedup helpers.
- `app/src/main/java/com/music/bitchord/data/local/LocalMusicAccess.kt` — pure access configuration helpers shared by settings and tests.
- `app/src/main/java/com/music/bitchord/ui/screens/LocalMusicSetupSheet.kt` — first-run A / B / A+B / Not now UI.
- `app/src/main/java/com/music/bitchord/ui/screens/LocalMusicSettingsScreen.kt` — manage All Music, folders, add/remove/rescan.
- `app/src/main/java/com/music/bitchord/playback/AndroidAutoLocalDataSource.kt` — interface + production adapter around local repository.
- `app/src/dev/res/drawable/ic_launcher_background.xml` — TanTov icon background override.
- `app/src/dev/res/drawable/ic_launcher_foreground.xml` — TanTov icon foreground override.
- `app/src/dev/res/drawable/ic_notification_logo.xml` — TanTov notification monochrome mark override.
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
- Produces: installable TanTov build with `BuildConfig.APPLICATION_ID == "com.tantov.music"`, label `TanTov Music`, TanTov launcher resources, and Android Auto root title `TanTov Music`.

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

Run:

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

Keep `namespace = "com.music.bitchord"` and the existing prod application ID unchanged.

Change the Android Auto catalog root label:

```kotlin
fun root(): MediaItem = browsable(AndroidAutoRoute.Root, "TanTov Music")
```

- [ ] **Step 4: Add dev-only adaptive icon resources**

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
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M22,30 L50,30 L50,38 L40,38 L40,76 L32,76 L32,38 L22,38 Z" />
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M54,30 L82,30 L82,38 L72,38 L72,58 L64,58 L64,38 L54,38 Z" />
    <path
        android:fillColor="#FFE9D5FF"
        android:pathData="M69,49 L76,47 L76,69 C76,75 72,79 66,79 C61,79 58,76 58,72 C58,68 62,65 67,65 C68,65 69,65 69,66 Z" />
</vector>
```

`app/src/dev/res/mipmap-anydpi/ic_launcher.xml` and `ic_launcher_round.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

Create a matching white-on-transparent vector in `app/src/dev/res/drawable/ic_notification_logo.xml` using the same TT/music-note silhouette so the media notification no longer carries the old BitChord mark.

- [ ] **Step 5: Run identity test and build**

```bash
./gradlew testDevDebugUnitTest --tests com.music.bitchord.TanTovIdentityTest assembleDevDebug
```

Expected: PASS; APK builds as package `com.tantov.music`.

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

- [ ] **Step 1: Write pure failing access-state tests**

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
        val config = LocalMusicAccessConfig(true, true, setOf("one", "two"))
            .removeTree("one")
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

- [ ] **Step 4: Add AppSettings flows and persistence**

Add flows:

```kotlin
val localMusicSetupSeen = MutableStateFlow(false)
val localAllMusicEnabled = MutableStateFlow(false)
val localMusicTreeUris = MutableStateFlow<Set<String>>(emptySet())
```

Read keys in `readAll()`:

```kotlin
localMusicSetupSeen.value = prefs.getBoolean("local_music_setup_seen", false)
localAllMusicEnabled.value = prefs.getBoolean("local_all_music_enabled", false)
localMusicTreeUris.value = prefs.getStringSet("local_music_tree_uris", emptySet()).orEmpty().toSet()
```

Add setters:

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
- Produces:
  - `LocalMusicTrack(song: Song, folderKey: String, folderLabel: String, identity: String)`
  - `LocalMusicCatalog(tracks: List<LocalMusicTrack>)`
  - `LocalMediaRepository.refresh(context): LocalMusicCatalog`
  - `LocalMediaRepository.catalog(context): LocalMusicCatalog`
  - `LocalMediaRepository.invalidate()`

- [ ] **Step 1: Add DocumentFile dependency**

```kotlin
implementation("androidx.documentfile:documentfile:1.0.1")
```

- [ ] **Step 2: Write RED tests for dedup and folder grouping**

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
    fun foldersRemainDistinctByPathEvenWhenLeafNameMatches() {
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

- [ ] **Step 4: Implement pure catalog models**

```kotlin
package com.music.bitchord.data.local

import com.music.bitchord.data.model.Song

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
        .sortedBy { it.label.lowercase() }

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

- [ ] **Step 5: Refactor MediaStore scanning into track rows**

Keep `getDownloadedSongs()` behavior untouched. Change the all-device path to collect MediaStore fields including `DISPLAY_NAME`, `RELATIVE_PATH` on API 29+, and volume information where available. Build an identity from storage volume + relative path + file name; on older Android use `DATA`.

Representative mapping:

```kotlin
val folder = relativePath?.trimEnd('/') ?: path?.substringBeforeLast('/').orEmpty()
val identity = when {
    !relativePath.isNullOrBlank() -> "$volumeName:${relativePath.trim('/')}/$displayName"
    !path.isNullOrBlank() -> path
    else -> "media:$id:$durationMs:$displayName"
}
LocalMusicTrack(
    song = Song(
        videoId = contentUri,
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

- [ ] **Step 6: Add recursive SAF tree scanning**

For each URI in `AppSettings.localMusicTreeUris.value`:

```kotlin
val root = DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) ?: return emptyList()
```

Recursively walk directories. Accept a file when `type?.startsWith("audio/") == true` or the existing `isAudioFileName(name)` returns true. Use `MediaMetadataRetriever.setDataSource(context, file.uri)` to read title/artist/album/duration. Store the playable `file.uri.toString()` in `Song.localUri` and a readable synthetic folder path in `Song.localPath`.

Derive a stable identity from `DocumentsContract.getDocumentId(file.uri)` when available:

```kotlin
val identity = runCatching { DocumentsContract.getDocumentId(file.uri) }
    .getOrNull()
    ?.takeIf { it.isNotBlank() }
    ?: "saf:${file.uri}:${file.length()}:$durationMs"
```

Catch `SecurityException` per tree; a revoked tree contributes no rows and must not fail the rest of the catalog.

- [ ] **Step 7: Add cache/refresh API**

```kotlin
@Volatile private var cachedCatalog: LocalMusicCatalog? = null

suspend fun refresh(context: Context): LocalMusicCatalog = withContext(Dispatchers.IO) {
    val media = if (AppSettings.localAllMusicEnabled.value && hasStoragePermission(context)) {
        scanMediaStore(context)
    } else emptyList()
    val trees = scanGrantedTrees(context, AppSettings.localMusicTreeUris.value)
    LocalMusicCatalog.merge(media, trees).also { cachedCatalog = it }
}

suspend fun catalog(context: Context): LocalMusicCatalog =
    cachedCatalog ?: refresh(context)

fun invalidate() {
    cachedCatalog = null
}
```

Keep `getLocalMusic(context)` as a compatibility delegate to `catalog(context).songs` while call sites are migrated.

- [ ] **Step 8: Run tests and build**

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

### Task 4: First-run A / B / A+B setup and Local Music settings

**Files:**
- Create: `app/src/main/java/com/music/bitchord/ui/screens/LocalMusicSetupSheet.kt`
- Create: `app/src/main/java/com/music/bitchord/ui/screens/LocalMusicSettingsScreen.kt`
- Modify: `app/src/main/java/com/music/bitchord/MainActivity.kt`
- Modify: `app/src/main/java/com/music/bitchord/ui/screens/SettingsSheet.kt`
- Modify: `app/src/main/java/com/music/bitchord/ui/MainViewModel.kt`

**Interfaces:**
- Consumes: AppSettings access flows, `LocalMediaRepository.invalidate/refresh`.
- Produces: skippable first-run setup and Settings management of A+B grants.

- [ ] **Step 1: Add shared permission/folder callbacks in MainActivity**

Use the existing audio permission launcher and add a tree launcher:

```kotlin
val folderPicker = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocumentTree(),
) { uri ->
    if (uri != null) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            AppSettings.addLocalMusicTreeUri(uri.toString())
            LocalMediaRepository.invalidate()
            viewModel.reloadLocalDetail("local:all")
        }
    }
}
```

When removing a folder, call `releasePersistableUriPermission` inside `runCatching`, remove it from AppSettings even if release reports it was already revoked, invalidate, and refresh.

- [ ] **Step 2: Implement first-run setup sheet**

Expose four actions exactly:

```kotlin
@Composable
fun LocalMusicSetupSheet(
    onAllMusic: () -> Unit,
    onChooseFolders: () -> Unit,
    onUseBoth: () -> Unit,
    onNotNow: () -> Unit,
)
```

Copy:
- `All Music on this phone`
- `Choose music folders`
- `Use both`
- `Not now`

The sheet must not block dismissal forever; every path sets `localMusicSetupSeen = true` before or after launching the relevant system UI.

For `Use both`, set `localAllMusicEnabled = true`, request audio permission, and then allow the folder picker. Permission denial leaves B usable.

- [ ] **Step 3: Show setup only on first launch**

In `BitChordApp`:

```kotlin
val localSetupSeen by AppSettings.localMusicSetupSeen.collectAsStateWithLifecycle()
var showLocalSetup by rememberSaveable { mutableStateOf(!localSetupSeen) }
```

Once any setup choice is made:

```kotlin
AppSettings.setLocalMusicSetupSeen(true)
showLocalSetup = false
```

- [ ] **Step 4: Add Settings -> Local Music row and dedicated screen**

Extend `SettingsScreen` with:

```kotlin
onLocalMusic: () -> Unit
```

Add a Storage/Music settings row:

```kotlin
SettingsRow(
    icon = Icons.Rounded.Storage,
    title = "Local Music",
    subtitle = "All Music, selected folders and rescan",
    onClick = onLocalMusic,
)
```

`LocalMusicSettingsScreen` must show:
- All Music toggle and current permission state.
- selected persisted folder names/URIs.
- `Add folder`.
- remove action for each folder.
- `Rescan music`.

`Rescan music` calls `LocalMediaRepository.refresh(context)` from a coroutine and updates the visible song count.

- [ ] **Step 5: Build UI**

```bash
./gradlew assembleDevDebug
```

Expected: compile succeeds with no new manifest all-files permission.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/music/bitchord/MainActivity.kt app/src/main/java/com/music/bitchord/ui/MainViewModel.kt app/src/main/java/com/music/bitchord/ui/screens/SettingsSheet.kt app/src/main/java/com/music/bitchord/ui/screens/LocalMusicSetupSheet.kt app/src/main/java/com/music/bitchord/ui/screens/LocalMusicSettingsScreen.kt
git commit -m "feat(local): add first-run and settings access flow"
```

---

### Task 5: Add Folders to the phone Local Music library

**Files:**
- Modify: `app/src/main/java/com/music/bitchord/ui/MainViewModel.kt`
- Modify: `app/src/main/java/com/music/bitchord/ui/screens/LocalMusicScreen.kt`
- Test: `app/src/test/java/com/music/bitchord/LocalMusicCatalogTest.kt`

**Interfaces:**
- Consumes: `LocalMusicCatalog.folders`.
- Produces: `Local Music -> Songs / Folders / Artists / Albums` on phone.

- [ ] **Step 1: Add a folder-order regression test**

```kotlin
@Test
fun folderListIsStableAndAlphabetical() {
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

- [ ] **Step 2: Run RED if sorting/grouping is not yet correct**

```bash
./gradlew testDevDebugUnitTest --tests com.music.bitchord.LocalMusicCatalogTest
```

- [ ] **Step 3: Load local detail from the merged catalog**

Replace the existing `local:all` calls that directly call `getLocalMusic(context)` with:

```kotlin
val catalog = LocalMediaRepository.catalog(context)
if (catalog.songs.isEmpty()) UiState.Error("No audio files found on device")
else UiState.Success(catalog.songs)
```

Permission denial should only block A. If B has at least one readable folder, Local Music must still load.

- [ ] **Step 4: Add the Folders tab**

In `LocalMusicScreen.kt`:

```kotlin
private const val LOCAL_TAB_SONGS = 0
private const val LOCAL_TAB_FOLDERS = 1
private const val LOCAL_TAB_ARTISTS = 2
private const val LOCAL_TAB_ALBUMS = 3
```

Add a `Folder` tab using `Icons.Rounded.Folder`. Group songs using their readable `localPath` parent, with the full parent path as the key and leaf directory as the label. Tapping a folder reuses the existing drill-down song list.

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

### Task 6: Combine phone search with local “On device” results and offline fallback

**Files:**
- Modify: `app/src/main/java/com/music/bitchord/ui/MainViewModel.kt`
- Modify: `app/src/main/java/com/music/bitchord/ui/screens/SearchScreen.kt`
- Test: `app/src/test/java/com/music/bitchord/LocalMusicSearchTest.kt`

**Interfaces:**
- Produces: Songs-tab results containing online tracks plus local tracks; local results remain available when online search fails.

- [ ] **Step 1: Add pure merge helper + RED tests**

Create an internal helper in `MainViewModel.kt` or a small adjacent testable file:

```kotlin
internal fun mergeSongSearchResults(
    online: Result<List<SearchResult>>,
    local: List<Song>,
): Result<List<SearchResult>>
```

Tests:

```kotlin
@Test
fun localSongsAreIncludedWithOnlineSongs() {
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
    assertEquals(listOf("Local Track"), merged.map { (it as SearchResult.Track).song.title })
}
```

- [ ] **Step 2: Run RED**

```bash
./gradlew testDevDebugUnitTest --tests com.music.bitchord.LocalMusicSearchTest
```

- [ ] **Step 3: Merge local results only into the Songs filter**

Inside the search collector:

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

Deduplicate by playable identity (`localUri ?: videoId`) while preserving online ordering, then local ordering.

- [ ] **Step 4: Mark local rows in SearchScreen**

When rendering `SearchResult.Track`, use:

```kotlin
val subtitle = if (song.localUri != null) {
    listOf(song.artist, "On device").filter { it.isNotBlank() }.joinToString(" • ")
} else {
    song.artist
}
```

- [ ] **Step 5: Run search tests/build**

```bash
./gradlew testDevDebugUnitTest --tests com.music.bitchord.LocalMusicSearchTest assembleDevDebug
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/music/bitchord/ui/MainViewModel.kt app/src/main/java/com/music/bitchord/ui/screens/SearchScreen.kt app/src/test/java/com/music/bitchord/LocalMusicSearchTest.kt
git commit -m "feat(search): combine online and local music"
```

---

### Task 7: Extend Android Auto with Local Music browse + combined search

**Files:**
- Create: `app/src/main/java/com/music/bitchord/playback/AndroidAutoLocalDataSource.kt`
- Modify: `app/src/main/java/com/music/bitchord/playback/AndroidAutoMediaIds.kt`
- Modify: `app/src/main/java/com/music/bitchord/playback/AndroidAutoCatalog.kt`
- Modify: `app/src/main/java/com/music/bitchord/playback/PlaybackService.kt`
- Test: `app/src/test/java/com/music/bitchord/AndroidAutoLocalMusicTest.kt`
- Test: existing Android Auto catalog/search tests as required by signature changes.

**Interfaces:**
- Produces:
  - Android Auto `Library -> Local Music -> Songs / Folders / Albums / Artists`.
  - combined Auto search with local fallback.
  - local browse rows that resolve back to normal playable `Song.toMediaItem()` content URIs.

- [ ] **Step 1: Define injectable local data source**

```kotlin
interface AndroidAutoLocalDataSource {
    suspend fun catalog(): LocalMusicCatalog
    suspend fun search(query: String): List<Song>
}

class DeviceAndroidAutoLocalDataSource(
    private val context: Context,
) : AndroidAutoLocalDataSource {
    override suspend fun catalog() = LocalMediaRepository.catalog(context)
    override suspend fun search(query: String) = catalog().search(query)
}
```

- [ ] **Step 2: Write RED browse/search tests**

Use a fake local data source with two songs and one folder. Assertions:

```kotlin
@Test
fun libraryContainsSeparateLocalMusicFolder() = runTest {
    val rows = catalog.children(AndroidAutoRoute.Library, 0, 100).getOrThrow()
    assertTrue(rows.any { it.mediaMetadata.title == "Local Music" })
}

@Test
fun localMusicHasSongsFoldersAlbumsArtists() = runTest {
    val rows = catalog.children(AndroidAutoRoute.LocalMusic, 0, 100).getOrThrow()
    assertEquals(
        listOf("Songs", "Folders", "Albums", "Artists"),
        rows.map { it.mediaMetadata.title.toString() },
    )
}

@Test
fun offlineOnlineSearchStillReturnsLocalTrack() = runTest {
    online.failSearch = true
    val rows = catalog.search("local", 0, 20).getOrThrow()
    assertTrue(rows.any { it.mediaMetadata.description?.contains("On device") == true })
}
```

- [ ] **Step 3: Add stable local routes**

Extend `AndroidAutoRoute`:

```kotlin
data object LocalMusic : AndroidAutoRoute
data class LocalSection(val section: AndroidAutoLocalSection) : AndroidAutoRoute
data class LocalCollection(
    val kind: AndroidAutoLocalCollectionKind,
    val key: String,
) : AndroidAutoRoute
```

Enums:

```kotlin
enum class AndroidAutoLocalSection { SONGS, FOLDERS, ALBUMS, ARTISTS }
enum class AndroidAutoLocalCollectionKind { FOLDER, ALBUM, ARTIST }
```

Encode dynamic collection keys with the existing URL-safe Base64 helper. Because `com.tantov.music` is a new package, update the media ID prefix from `bitchord:auto:v1` to `tantov:auto:v1` in the same task and update ID tests accordingly.

- [ ] **Step 4: Make Library local-aware even when signed out**

Refactor the existing `Library` route so Local Music is independent of YouTube sign-in:

```kotlin
AndroidAutoRoute.Library -> libraryFolders()
```

`libraryFolders()` first checks the local catalog and adds:

```kotlin
if (localDataSource.catalog().songs.isNotEmpty()) {
    add(browsable(AndroidAutoRoute.LocalMusic, "Local Music"))
}
```

Then append signed-in online sections exactly as before.

- [ ] **Step 5: Implement Local Music children**

`LocalMusic` returns four browse folders. `SONGS` returns playable rows. `FOLDERS`, `ALBUMS`, and `ARTISTS` return browse collections, and each local collection returns its matching songs.

For local playable rows, continue storing the full `Song` in `rememberedSongs`. Add local URI/path to MediaMetadata extras so a reconstructed row can still resolve:

```kotlin
putString(EXTRA_LOCAL_URI, song.localUri)
putString(EXTRA_LOCAL_PATH, song.localPath)
```

Update `songFromBrowseRow` to restore both fields.

- [ ] **Step 6: Merge Android Auto search with local rows**

Run online and local search independently. If online fails but local rows exist, return local rows. If both fail/empty and online failed, return the online failure.

Mark local metadata:

```kotlin
if (song.localUri != null) {
    setDescription(listOf(song.artist, "On device").filter { it.isNotBlank() }.joinToString(" • "))
}
```

- [ ] **Step 7: Inject production local data source from PlaybackService**

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

Expected: all Android Auto tests PASS.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/music/bitchord/playback app/src/test/java/com/music/bitchord/AndroidAutoLocalMusicTest.kt app/src/test/java/com/music/bitchord/AndroidAutoMediaIdsTest.kt
git commit -m "feat(auto): browse and search local music"
```

---

### Task 8: Expose repeat off/all/one to Android Auto and run release verification

**Files:**
- Modify: `app/src/main/java/com/music/bitchord/playback/PlaybackService.kt`
- Test: `app/src/test/java/com/music/bitchord/AndroidAutoRepeatCommandTest.kt`
- Verify: `.github/workflows/android.yml`
- Verify: `.github/workflows/android-auto-legacy-smoke.yml`

**Interfaces:**
- Produces: a custom session command that cycles the same player repeat mode already used by the phone UI: OFF -> ALL -> ONE -> OFF.

- [ ] **Step 1: Write a pure repeat-cycle test**

Extract a helper:

```kotlin
internal fun nextRepeatMode(current: Int): Int = when (current) {
    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
    else -> Player.REPEAT_MODE_OFF
}
```

Test:

```kotlin
@Test
fun repeatCyclesOffAllOneOff() {
    assertEquals(Player.REPEAT_MODE_ALL, nextRepeatMode(Player.REPEAT_MODE_OFF))
    assertEquals(Player.REPEAT_MODE_ONE, nextRepeatMode(Player.REPEAT_MODE_ALL))
    assertEquals(Player.REPEAT_MODE_OFF, nextRepeatMode(Player.REPEAT_MODE_ONE))
}
```

- [ ] **Step 2: Run RED before adding helper**

```bash
./gradlew testDevDebugUnitTest --tests com.music.bitchord.AndroidAutoRepeatCommandTest
```

Expected: FAIL because helper is missing.

- [ ] **Step 3: Add repeat session command**

```kotlin
const val ACTION_CYCLE_REPEAT = "com.tantov.music.action.CYCLE_REPEAT"
private val repeatCommand = SessionCommand(ACTION_CYCLE_REPEAT, Bundle.EMPTY)
```

Add it to `onConnect()` available session commands.

In `onCustomCommand()`:

```kotlin
ACTION_CYCLE_REPEAT -> {
    session.player.repeatMode = nextRepeatMode(session.player.repeatMode)
    session.setCustomLayout(notificationButtons())
}
```

- [ ] **Step 4: Put repeat in the custom layout**

Build the button from the current player mode using Media3’s built-in repeat icons:

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

Include it in `notificationButtons()` without removing the standard player repeat command. The phone player continues to use the same `repeatMode`; replace its inline cycle `when` in `MainActivity.kt` with `nextRepeatMode(it.repeatMode)` so phone and car cannot diverge.

- [ ] **Step 5: Run repeat + complete unit suite**

```bash
./gradlew testDevDebugUnitTest
```

Expected: PASS with zero failing tests.

- [ ] **Step 6: Build the TanTov APK**

```bash
./gradlew assembleDevDebug
```

Expected: `app/build/outputs/apk/dev/debug/app-dev-debug.apk` exists and uses application ID `com.tantov.music`.

- [ ] **Step 7: Run legacy MediaBrowser smoke test**

Run the existing instrumentation workflow/command used by `.github/workflows/android-auto-legacy-smoke.yml` against this commit. Expected root connection succeeds and the existing root children still load; then manually/with catalog tests verify `Library -> Local Music`.

- [ ] **Step 8: Inspect merged manifest for forbidden permission**

```bash
./gradlew processDevDebugMainManifest
```

Inspect the merged manifest and verify it contains `READ_MEDIA_AUDIO` / legacy `READ_EXTERNAL_STORAGE` as appropriate to existing app declarations, and does **not** contain `MANAGE_EXTERNAL_STORAGE`.

- [ ] **Step 9: Produce fresh CI artifact**

Push the task branch and require GitHub Actions `Android CI` to finish success with:
- Unit tests success
- Dev APK build success
- artifact upload success

Do not claim real-car validation until the user installs the fresh TanTov APK and verifies it in the car.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/music/bitchord/playback/PlaybackService.kt app/src/main/java/com/music/bitchord/MainActivity.kt app/src/test/java/com/music/bitchord/AndroidAutoRepeatCommandTest.kt
git commit -m "feat(auto): add repeat mode control"
```

---

## Final Acceptance Checklist

- [ ] `BuildConfig.APPLICATION_ID == "com.tantov.music"`.
- [ ] Phone launcher label is `TanTov Music`.
- [ ] Android Auto launcher uses the TanTov icon/name.
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
- [ ] Existing Android Auto legacy-browser smoke test still succeeds.
- [ ] `testDevDebugUnitTest` succeeds.
- [ ] `assembleDevDebug` succeeds.
- [ ] Fresh GitHub Actions artifact is produced for real-car testing.
