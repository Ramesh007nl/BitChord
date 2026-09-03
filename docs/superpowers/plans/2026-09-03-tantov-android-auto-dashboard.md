# TanTov Android Auto Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace TanTov's folder-first Android Auto entry screen with a four-tab Car App Library media dashboard that shows Local Music first, then Recently Played, Quick Picks, Listen Again, and the remaining phone Home shelves.

**Architecture:** Add a projected `CarAppService` using Car App Library 1.8.0-rc01 while retaining `PlaybackService`, `MediaLibrarySession`, `AndroidAutoCatalog`, and ExoPlayer as the only browse/playback authorities. A small dashboard adapter converts existing catalog routes and `MediaItem`s into presentation-neutral sections; Car App Library screens render those sections with `TabTemplate` and `SectionedItemTemplate`. The first template renders Local Music immediately, then invalidates when bounded online/history loads complete.

**Tech Stack:** Kotlin, Android SDK 36, AndroidX Car App Library 1.8.0-rc01, Android Auto projected host, Media3 1.11.0, Kotlin coroutines, JUnit 4, AndroidX instrumentation, existing ExoPlayer/MediaLibraryService pipeline.

**Spec:** `docs/superpowers/specs/2026-09-03-tantov-android-auto-dashboard-design.md`

## Global Constraints

- Base implementation on `feat/tantov-music-v1` at or after `a769b23de2f9aed97990d79bf4f6642bf798879e`.
- Keep TanTov application ID `com.tantov.music` and internal Kotlin namespace `com.music.bitchord`.
- Keep the original BitChord independently installable.
- Pin `androidx.car.app:app` and `androidx.car.app:app-projected` to `1.8.0-rc01`; do not use `1.9.0-alpha01` APIs.
- Use Car App API level 8.
- Keep `MediaLibraryService`, one `MediaLibrarySession`, and one active ExoPlayer playback pipeline.
- Keep the `tantov:auto:v1` media-ID namespace stable.
- Local Music must be usable offline and while signed out.
- Online loading must not block the Local Music entry or spin indefinitely.
- Do not request `MANAGE_EXTERNAL_STORAGE`.
- Do not merge any branch without explicit user approval after testing the new APK in the real car.
- Establish a runnable clean Gradle baseline before modifying production code; the design worktree could not download Gradle 8.11.1.

## Planned file structure

- `app/src/main/java/com/music/bitchord/car/TanTovCarAppService.kt` — projected Car App Library service and host validation.
- `app/src/main/java/com/music/bitchord/car/TanTovCarSession.kt` — car session lifecycle, controller ownership, and initial screen.
- `app/src/main/java/com/music/bitchord/car/TanTovCarTabsScreen.kt` — four-tab navigation and selected-tab state.
- `app/src/main/java/com/music/bitchord/car/TanTovCarBrowseScreen.kt` — reusable screen for Local Music descendants, Recents, Browse, Library, and dynamic collections.
- `app/src/main/java/com/music/bitchord/car/TanTovCarHomeScreen.kt` — local-first progressive Home loading and retry state.
- `app/src/main/java/com/music/bitchord/car/AndroidAutoDashboard.kt` — presentation-neutral section models and deterministic ordering.
- `app/src/main/java/com/music/bitchord/car/AndroidAutoDashboardSource.kt` — adapter over `AndroidAutoCatalog` with bounded loading.
- `app/src/main/java/com/music/bitchord/car/CarTemplateFactory.kt` — conversion of dashboard/catalog media into Car App Library rows, grids, and empty/error templates.
- `app/src/main/java/com/music/bitchord/car/CarArtworkLoader.kt` — bounded artwork decoding and fallback icons.
- `app/src/main/java/com/music/bitchord/car/CarMediaController.kt` — `CarPlaybackController` boundary plus Media3 connection, playback commands, and compat-token registration.
- `app/src/main/res/xml/automotive_app_desc.xml` — declares both `media` and `template` support.
- `app/src/main/res/drawable/ic_car_home.xml`, `ic_car_recents.xml`, `ic_car_browse.xml`, `ic_car_library.xml`, `ic_car_local_music.xml` — monochrome navigation/fallback icons.
- `app/src/test/java/com/music/bitchord/AndroidAutoDashboardTest.kt` — ordering, deduplication, and title normalization.
- `app/src/test/java/com/music/bitchord/AndroidAutoDashboardSourceTest.kt` — catalog adaptation, timeout, and partial-failure behavior.
- `app/src/test/java/com/music/bitchord/CarMediaCommandsTest.kt` — same-app token command and stable playback route contracts.
- `app/src/androidTest/java/com/music/bitchord/TanTovCarManifestTest.kt` — packaged manifest and descriptor assertions.
- `app/src/androidTest/java/com/music/bitchord/TanTovCarTemplateTest.kt` — Car App Library screen and navigation smoke assertions.
- `.github/workflows/android-auto-legacy-smoke.yml` — includes the new template smoke classes without removing the legacy browser test.

---

### Task 1: Establish the clean build gate and register a minimal templated media app

**Files:**
- Modify: `app/build.gradle.kts` dependencies block
- Modify: `app/src/main/AndroidManifest.xml` permissions, application metadata, and services
- Create: `app/src/main/res/xml/automotive_app_desc.xml`
- Create: `app/src/main/java/com/music/bitchord/car/TanTovCarAppService.kt`
- Create: `app/src/main/java/com/music/bitchord/car/TanTovCarSession.kt`
- Create: `app/src/androidTest/java/com/music/bitchord/TanTovCarManifestTest.kt`

**Interfaces:**
- Consumes: existing `PlaybackService` component and `BuildConfig.DEBUG`.
- Produces: `TanTovCarAppService : CarAppService`; `TanTovCarSession : Session`; manifest registration for `androidx.car.app.category.MEDIA` at Car App API 8.

- [ ] **Step 1: Prove the clean baseline is runnable before editing**

Run:

```bash
./gradlew --version
./gradlew testDevDebugUnitTest
```

Expected: Gradle 8.11.1 starts and `testDevDebugUnitTest` exits 0. If the wrapper cannot download, move execution to an environment with the Gradle distribution or restore only the verified Gradle cache; do not start production edits with an unknown baseline.

- [ ] **Step 2: Write the failing packaged-manifest test**

Create `TanTovCarManifestTest.kt` with assertions that the installed dev APK has the template permission, projected car service, API-level metadata, and descriptor:

```kotlin
@RunWith(AndroidJUnit4::class)
class TanTovCarManifestTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun devApkDeclaresTemplatedMediaSupport() {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(
                PackageManager.GET_PERMISSIONS.toLong() or PackageManager.GET_SERVICES.toLong(),
            ),
        )
        assertTrue(packageInfo.requestedPermissions.orEmpty().contains("androidx.car.app.MEDIA_TEMPLATES"))
        assertTrue(
            packageInfo.services.orEmpty().any {
                it.name == "com.music.bitchord.car.TanTovCarAppService" && it.exported
            },
        )
        val appInfo = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
        )
        assertEquals(8, appInfo.metaData.getInt("androidx.car.app.minCarApiLevel"))
    }
}
```

- [ ] **Step 3: Run the new test and verify RED**

Run:

```bash
./gradlew connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.music.bitchord.TanTovCarManifestTest
```

Expected: FAIL because the permission, service, and API metadata are absent.

- [ ] **Step 4: Add the exact Car App Library dependencies**

Add to `app/build.gradle.kts`:

```kotlin
implementation("androidx.car.app:app:1.8.0-rc01")
implementation("androidx.car.app:app-projected:1.8.0-rc01")
implementation("androidx.media:media:1.7.0")
androidTestImplementation("androidx.car.app:app-testing:1.8.0-rc01")
```

Do not add `app-automotive`; this task targets the phone-projected Android Auto APK.

- [ ] **Step 5: Add the descriptor and manifest declarations**

Create `automotive_app_desc.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<automotiveApp xmlns:android="http://schemas.android.com/apk/res/android">
    <uses name="media" />
    <uses name="template" />
</automotiveApp>
```

Add to the manifest:

```xml
<uses-permission android:name="androidx.car.app.MEDIA_TEMPLATES" />
```

Inside `<application>` add:

```xml
<meta-data
    android:name="com.google.android.gms.car.application"
    android:resource="@xml/automotive_app_desc" />
<meta-data
    android:name="androidx.car.app.minCarApiLevel"
    android:value="8" />

<service
    android:name=".car.TanTovCarAppService"
    android:exported="true">
    <intent-filter>
        <action android:name="androidx.car.app.CarAppService" />
        <category android:name="androidx.car.app.category.MEDIA" />
    </intent-filter>
</service>
```

- [ ] **Step 6: Add a minimal service/session that renders a testable message**

`TanTovCarAppService.kt`:

```kotlin
class TanTovCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator =
        if (BuildConfig.DEBUG) HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        else HostValidator.Builder(this).addAllowedHosts(R.array.hosts_allowlist).build()

    override fun onCreateSession(sessionInfo: SessionInfo): Session = TanTovCarSession()
}
```

Add `res/values/hosts.xml` with an empty release allowlist array and keep release distribution blocked until the official Android Auto host certificate entry is supplied through the Play distribution process:

```xml
<resources>
    <string-array name="hosts_allowlist" />
</resources>
```

`TanTovCarSession.kt` initially returns a message screen so the component can launch before dashboard code exists:

```kotlin
class TanTovCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen = object : Screen(carContext) {
        override fun onGetTemplate(): Template = MessageTemplate.Builder("TanTov Music is loading")
            .setHeader(Header.Builder().setTitle("TanTov Music").build())
            .build()
    }
}
```

- [ ] **Step 7: Run manifest test, unit suite, and build for GREEN**

Run:

```bash
./gradlew connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.music.bitchord.TanTovCarManifestTest
./gradlew testDevDebugUnitTest assembleDevDebug
```

Expected: all commands exit 0.

- [ ] **Step 8: Commit the registration slice**

```bash
git add app/build.gradle.kts app/src/main/AndroidManifest.xml \
  app/src/main/res/xml/automotive_app_desc.xml app/src/main/res/values/hosts.xml \
  app/src/main/java/com/music/bitchord/car/TanTovCarAppService.kt \
  app/src/main/java/com/music/bitchord/car/TanTovCarSession.kt \
  app/src/androidTest/java/com/music/bitchord/TanTovCarManifestTest.kt
git commit -m "feat(auto): register templated TanTov media app"
```

---

### Task 2: Define and test deterministic dashboard ordering

**Files:**
- Create: `app/src/main/java/com/music/bitchord/car/AndroidAutoDashboard.kt`
- Create: `app/src/test/java/com/music/bitchord/AndroidAutoDashboardTest.kt`

**Interfaces:**
- Consumes: `HomeShelf`, Media3 `MediaItem`, and `AndroidAutoRoute.LocalMusic`.
- Produces: `DashboardShelf`, `DashboardSection`, `DashboardSectionKind`, `DashboardLayout`, and `AndroidAutoDashboard.order(localMusic, recents, homeShelves)`.

- [ ] **Step 1: Write the failing ordering tests**

Cover Local Music first, preferred shelf order, remaining shelf order, duplicate normalized titles, empty recents, and translated/case-varied titles:

```kotlin
@Test
fun localThenRecentsQuickPicksListenAgainThenOtherShelves() {
    val sections = AndroidAutoDashboard.order(
        localMusic = browseItem("tantov:auto:v1:local", "Local Music"),
        recents = listOf(playableItem("recent", "Recent Song")),
        homeShelves = listOf(
            shelf("Made For You", "other"),
            shelf("Listen Again", "again"),
            shelf("QUICK-PICKS", "quick"),
            shelf("Quick Picks", "duplicate"),
        ),
    )

    assertEquals(
        listOf(
            DashboardSectionKind.LOCAL_MUSIC,
            DashboardSectionKind.RECENTLY_PLAYED,
            DashboardSectionKind.QUICK_PICKS,
            DashboardSectionKind.LISTEN_AGAIN,
            DashboardSectionKind.OTHER,
        ),
        sections.map { it.kind },
    )
    assertEquals(listOf("other"), sections.last().items.map { it.mediaId })
}
```

Add a second test proving that an empty `recents` list omits only the recents section and never omits Local Music.

- [ ] **Step 2: Run the tests and verify RED**

Run:

```bash
./gradlew testDevDebugUnitTest \
  --tests com.music.bitchord.AndroidAutoDashboardTest
```

Expected: compilation FAIL because the dashboard types do not exist.

- [ ] **Step 3: Implement the minimal pure ordering model**

Create these public contracts:

```kotlin
enum class DashboardSectionKind { LOCAL_MUSIC, RECENTLY_PLAYED, QUICK_PICKS, LISTEN_AGAIN, OTHER }
enum class DashboardLayout { SINGLE, ROW, GRID }

data class DashboardShelf(
    val title: String,
    val subtitle: String,
    val items: List<MediaItem>,
)

data class DashboardSection(
    val key: String,
    val title: String,
    val kind: DashboardSectionKind,
    val layout: DashboardLayout,
    val items: List<MediaItem>,
)

object AndroidAutoDashboard {
    fun order(
        localMusic: MediaItem,
        recents: List<MediaItem>,
        homeShelves: List<DashboardShelf>,
    ): List<DashboardSection>
}
```

Normalize only for matching/deduplication:

```kotlin
private fun String.dashboardKey(): String = lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)
```

Recognize `quickpicks` and `listenagain`; preserve the original displayed title. Use one `OTHER` section per remaining source shelf, with a stable key of `other:<normalized-title>`.

- [ ] **Step 4: Run the targeted test for GREEN**

Run:

```bash
./gradlew testDevDebugUnitTest \
  --tests com.music.bitchord.AndroidAutoDashboardTest
```

Expected: PASS.

- [ ] **Step 5: Commit the ordering slice**

```bash
git add app/src/main/java/com/music/bitchord/car/AndroidAutoDashboard.kt \
  app/src/test/java/com/music/bitchord/AndroidAutoDashboardTest.kt
git commit -m "feat(auto): define TanTov dashboard ordering"
```

---

### Task 3: Adapt the existing catalog with local-first bounded loading

**Files:**
- Create: `app/src/main/java/com/music/bitchord/car/AndroidAutoDashboardSource.kt`
- Create: `app/src/test/java/com/music/bitchord/AndroidAutoDashboardSourceTest.kt`

**Interfaces:**
- Consumes: `AndroidAutoCatalog.item(route)`, `AndroidAutoCatalog.children(route, page, pageSize)`, `AndroidAutoMediaIds.parse`, and dashboard models from Task 2.
- Produces: `DashboardBrowseGateway`, `CatalogDashboardBrowseGateway`, `AndroidAutoDashboardSource.localSection()`, `loadHomeOnline()`, `loadRecents()`, `loadBrowse()`, and `loadLibrary()`.

- [ ] **Step 1: Write failing adapter tests with a fake gateway**

Define the gateway in the test first:

```kotlin
private class FakeGateway : DashboardBrowseGateway {
    val children = mutableMapOf<AndroidAutoRoute, Result<List<MediaItem>>>()
    override suspend fun item(route: AndroidAutoRoute) = Result.success(browseItem(routeId(route), route.toString()))
    override suspend fun children(route: AndroidAutoRoute) =
        children[route] ?: Result.success(emptyList())
}
```

Required tests:

```kotlin
@Test fun localSectionDoesNotRequestHomeOrHistory()
@Test fun homeShelfRowsAreExpandedThroughTheirEmittedShelfRoutes()
@Test fun onlineTimeoutReturnsFailureWithoutRemovingLocalSection()
@Test fun historyFailureDoesNotDiscardSuccessfulHomeShelves()
@Test fun signedOutEmptyHistoryStillReturnsHomeShelves()
```

Use `kotlinx.coroutines.test.runTest` and a constructor-injected `onlineTimeoutMs`. Add `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0` to `testImplementation` in this task.

- [ ] **Step 2: Run tests and verify RED**

Run:

```bash
./gradlew testDevDebugUnitTest \
  --tests com.music.bitchord.AndroidAutoDashboardSourceTest
```

Expected: compilation FAIL because `DashboardBrowseGateway` and `AndroidAutoDashboardSource` are absent.

- [ ] **Step 3: Implement the gateway and source**

Use these contracts:

```kotlin
interface DashboardBrowseGateway {
    suspend fun item(route: AndroidAutoRoute): Result<MediaItem>
    suspend fun children(route: AndroidAutoRoute): Result<List<MediaItem>>
}

class CatalogDashboardBrowseGateway(
    private val catalog: AndroidAutoCatalog,
) : DashboardBrowseGateway {
    override suspend fun item(route: AndroidAutoRoute) = catalog.item(route)
    override suspend fun children(route: AndroidAutoRoute) =
        catalog.children(route, page = 0, pageSize = Int.MAX_VALUE)
}

data class DashboardOnlineResult(
    val recents: List<MediaItem>,
    val homeShelves: List<DashboardShelf>,
    val errorMessage: String?,
)
```

`localSection()` obtains `AndroidAutoRoute.LocalMusic` only. `loadHomeOnline()` starts Recent and Home requests with `async`, bounds each using `withTimeout(onlineTimeoutMs)`, expands only shelf routes actually returned by `AndroidAutoCatalog`, and returns partial successful data plus a concise message when one request fails. It must never catch `CancellationException` as an ordinary error.

- [ ] **Step 4: Run adapter and existing catalog tests for GREEN**

Run:

```bash
./gradlew testDevDebugUnitTest \
  --tests com.music.bitchord.AndroidAutoDashboardSourceTest \
  --tests com.music.bitchord.AndroidAutoCatalogTest \
  --tests com.music.bitchord.AndroidAutoLocalMusicTest
```

Expected: PASS with existing catalog behavior unchanged.

- [ ] **Step 5: Commit the data-adapter slice**

```bash
git add app/build.gradle.kts \
  app/src/main/java/com/music/bitchord/car/AndroidAutoDashboardSource.kt \
  app/src/test/java/com/music/bitchord/AndroidAutoDashboardSourceTest.kt
git commit -m "feat(auto): load dashboard data local first"
```

---

### Task 4: Build car-safe artwork and template conversion

**Files:**
- Create: `app/src/main/java/com/music/bitchord/car/CarArtworkLoader.kt`
- Create: `app/src/main/java/com/music/bitchord/car/CarTemplateFactory.kt`
- Create: `app/src/main/res/drawable/ic_car_home.xml`
- Create: `app/src/main/res/drawable/ic_car_recents.xml`
- Create: `app/src/main/res/drawable/ic_car_browse.xml`
- Create: `app/src/main/res/drawable/ic_car_library.xml`
- Create: `app/src/main/res/drawable/ic_car_local_music.xml`
- Create: `app/src/androidTest/java/com/music/bitchord/TanTovCarTemplateTest.kt`

**Interfaces:**
- Consumes: `DashboardSection`, `AndroidAutoRoute`, Media3 `MediaItem`, existing Coil 3 dependencies, and `ScreenManager` navigation callbacks.
- Produces: `CarArtworkLoader.load(uri, fallbackRes): CarIcon`; `CarTemplateFactory.home(...)`; `browse(...)`; `message(...)`; and `tabIcon(destination)`.

- [ ] **Step 1: Write failing template structure tests**

Using `TestCarContext`, assert:

```kotlin
@Test fun homeTemplateContainsLocalSectionFirst()
@Test fun gridSectionsKeepArtworkItemsBrowsableOrPlayable()
@Test fun errorTemplateKeepsLocalMusicAndAddsRetryRow()
@Test fun localTemplateTitlesAreAllSongsFoldersAlbumsArtists()
```

For the first test, build a `DashboardSection` list and inspect `SectionedItemTemplate.sections.first()`; assert its title/item media key identifies Local Music.

- [ ] **Step 2: Run the instrumentation test and verify RED**

Run:

```bash
./gradlew connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.music.bitchord.TanTovCarTemplateTest
```

Expected: compilation FAIL because `CarTemplateFactory` is absent.

- [ ] **Step 3: Implement bounded artwork loading**

`CarArtworkLoader` must:

- return the supplied vector fallback immediately when the artwork URI is null;
- use Coil's existing `ImageLoader` for remote artwork;
- request at most 256×256 pixels;
- accept only a `BitmapDrawable` result;
- convert the bitmap with `IconCompat.createWithBitmap` and `CarIcon.Builder`;
- return the fallback on its own decode timeout, decode failure, or network failure;
- rethrow an external `CancellationException` so a disposed screen cannot publish stale artwork;
- cache by URI using an access-ordered map capped at 40 icons.

Expose this exact suspending signature:

```kotlin
suspend fun load(artworkUri: Uri?, @DrawableRes fallbackRes: Int): CarIcon
```

- [ ] **Step 4: Implement the template factory**

Use `GridSection` for artwork-led playable collections and `RowSection` for the single Local Music action and text-heavy fallback states. Each clickable template item carries the source Media3 `mediaId` in its callback closure; never create a second ID scheme.

Core builder shape:

```kotlin
fun home(
    sections: List<DashboardSection>,
    errorMessage: String?,
    onItemClick: (MediaItem) -> Unit,
    onRetry: () -> Unit,
): SectionedItemTemplate = SectionedItemTemplate.Builder()
    .apply {
        sections.forEach { section -> addSection(section.toCarSection(onItemClick)) }
        if (errorMessage != null) addSection(retrySection(errorMessage, onRetry))
    }
    .build()
```

Use the existing item title, artist/subtitle, and artwork. Show at most six source items in each Home dashboard section. When a source shelf contains more than six items, show five source items plus a sixth browsable **More** item that opens the existing `AndroidAutoRoute.Shelf` route.

- [ ] **Step 5: Run template tests for GREEN**

Run:

```bash
./gradlew connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.music.bitchord.TanTovCarTemplateTest
```

Expected: PASS.

- [ ] **Step 6: Commit the rendering slice**

```bash
git add app/src/main/java/com/music/bitchord/car/CarArtworkLoader.kt \
  app/src/main/java/com/music/bitchord/car/CarTemplateFactory.kt \
  app/src/main/res/drawable/ic_car_*.xml \
  app/src/androidTest/java/com/music/bitchord/TanTovCarTemplateTest.kt
git commit -m "feat(auto): render sectioned TanTov dashboard"
```

---

### Task 5: Connect the Car App Library UI to the existing Media3 session

**Files:**
- Create: `app/src/main/java/com/music/bitchord/car/CarMediaController.kt`
- Modify: `app/src/main/java/com/music/bitchord/playback/PlaybackService.kt` session commands and custom-command handler
- Create: `app/src/test/java/com/music/bitchord/CarMediaCommandsTest.kt`
- Modify: `app/src/test/java/com/music/bitchord/AndroidAutoSessionCommandsTest.kt`

**Interfaces:**
- Consumes: `PlaybackService`, `MediaController`, `MediaPlaybackManager`, `AndroidAutoCatalog` resolution through `onAddMediaItems`, and existing repeat command.
- Produces: `ACTION_GET_PLATFORM_TOKEN`, `KEY_PLATFORM_TOKEN`; `CarPlaybackController`; and `CarMediaController.connect()`, `play(item)`, `registerPlaybackToken()`, and `release()`.

- [ ] **Step 1: Write failing command-contract tests**

Add tests proving:

```kotlin
@Test fun platformTokenCommandUsesTanTovNamespace()
@Test fun platformTokenCommandIsAvailableOnlyToSamePackageController()
@Test fun carPlaybackKeepsIncomingTantovAutoMediaIdForServiceResolution()
@Test fun existingRepeatCommandRemainsAvailable()
```

Create this pure helper:

```kotlin
internal fun canReadPlatformToken(controllerPackage: String, appPackage: String): Boolean =
    controllerPackage == appPackage
```

- [ ] **Step 2: Run tests and verify RED**

Run:

```bash
./gradlew testDevDebugUnitTest \
  --tests com.music.bitchord.CarMediaCommandsTest \
  --tests com.music.bitchord.AndroidAutoSessionCommandsTest
```

Expected: FAIL because the platform-token command is absent.

- [ ] **Step 3: Expose the platform token safely from `PlaybackService`**

Add constants:

```kotlin
const val ACTION_GET_PLATFORM_TOKEN = "com.tantov.music.action.GET_PLATFORM_TOKEN"
const val KEY_PLATFORM_TOKEN = "com.tantov.music.extra.PLATFORM_TOKEN"
```

In `onConnect`, add the command only when `controller.packageName == packageName`. In `onCustomCommand`, handle it before the existing notification actions:

```kotlin
ACTION_GET_PLATFORM_TOKEN -> {
    if (!canReadPlatformToken(controller.packageName, packageName)) {
        return Futures.immediateFuture(SessionResult(SessionError.ERROR_PERMISSION_DENIED))
    }
    val extras = Bundle().apply {
        putParcelable(KEY_PLATFORM_TOKEN, session.platformToken)
    }
    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, extras))
}
```

Do not alter favorite, autoplay, shuffle, or repeat handling.

- [ ] **Step 4: Implement the car controller bridge**

Define the injectable boundary and its Media3 implementation:

```kotlin
interface CarPlaybackController {
    suspend fun connect()
    fun play(item: MediaItem)
    fun release()
}

class CarMediaController(
    private val carContext: CarContext,
) : CarPlaybackController
```

`CarMediaController` creates one controller:

```kotlin
val token = SessionToken(carContext, ComponentName(carContext, PlaybackService::class.java))
val future = MediaController.Builder(carContext, token).buildAsync()
```

After connection, send `ACTION_GET_PLATFORM_TOKEN`, retrieve `android.media.session.MediaSession.Token`, convert it with `MediaSessionCompat.Token.fromToken`, and register it using:

```kotlin
carContext.getCarService(MediaPlaybackManager::class.java)
    .registerMediaPlaybackToken(compatToken)
```

Playback uses:

```kotlin
fun play(item: MediaItem) {
    controller.setMediaItem(item)
    controller.prepare()
    controller.play()
}
```

The incoming item keeps its `tantov:auto:v1` ID so `PlaybackService.onAddMediaItems` resolves it through the existing catalog. `release()` removes listeners, releases the controller, and cancels any pending future.

- [ ] **Step 5: Run command, playback-resolution, repeat, and voice tests for GREEN**

Run:

```bash
./gradlew testDevDebugUnitTest \
  --tests com.music.bitchord.CarMediaCommandsTest \
  --tests com.music.bitchord.AndroidAutoSessionCommandsTest \
  --tests com.music.bitchord.AndroidAutoRepeatCommandTest \
  --tests com.music.bitchord.AndroidAutoVoiceSearchTest \
  --tests com.music.bitchord.AndroidAutoSearchTest
```

Expected: PASS.

- [ ] **Step 6: Commit the Media3 bridge slice**

```bash
git add app/src/main/java/com/music/bitchord/car/CarMediaController.kt \
  app/src/main/java/com/music/bitchord/playback/PlaybackService.kt \
  app/src/test/java/com/music/bitchord/CarMediaCommandsTest.kt \
  app/src/test/java/com/music/bitchord/AndroidAutoSessionCommandsTest.kt
git commit -m "feat(auto): bridge dashboard to shared media session"
```

---

### Task 6: Implement Home, tabs, browsing, and lifecycle-safe refresh

**Files:**
- Create: `app/src/main/java/com/music/bitchord/car/TanTovCarHomeScreen.kt`
- Create: `app/src/main/java/com/music/bitchord/car/TanTovCarTabsScreen.kt`
- Create: `app/src/main/java/com/music/bitchord/car/TanTovCarBrowseScreen.kt`
- Modify: `app/src/main/java/com/music/bitchord/car/TanTovCarSession.kt`
- Modify: `app/src/androidTest/java/com/music/bitchord/TanTovCarTemplateTest.kt`

**Interfaces:**
- Consumes: Tasks 2–5 dashboard source, template factory, media controller, `ScreenManager`, and existing Android Auto routes.
- Produces: initial `TanTovCarTabsScreen`; Home/Recents/Browse/Library tab templates; Local Music and collection navigation; standard media playback intent handling.

- [ ] **Step 1: Extend tests for navigation and progressive loading**

Add failing tests:

```kotlin
@Test fun initialScreenSelectsHomeTab()
@Test fun tabsAreHomeRecentsBrowseLibraryInThatOrder()
@Test fun initialHomeTemplateContainsLocalMusicBeforeOnlineCompletes()
@Test fun selectingLocalMusicPushesAllSongsFoldersAlbumsArtists()
@Test fun selectingPlayableItemCallsSharedMediaController()
@Test fun selectingBrowsedShelfPushesItsExistingCatalogRoute()
@Test fun retryStartsOneRefreshAndDoesNotDuplicateSections()
@Test fun showMediaPlaybackIntentOpensPlaybackTemplate()
```

Drive the screens with fake `DashboardBrowseGateway`, fake `CarPlaybackController`, and controllable coroutine dispatchers.

- [ ] **Step 2: Run navigation tests and verify RED**

Run:

```bash
./gradlew connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.music.bitchord.TanTovCarTemplateTest
```

Expected: FAIL because the real tab/home/browse screens are not wired.

- [ ] **Step 3: Implement the four-tab shell**

`TanTovCarTabsScreen` owns exactly one selected content ID:

```kotlin
enum class CarDestination(val contentId: String) {
    HOME("home"), RECENTS("recents"), BROWSE("browse"), LIBRARY("library")
}
```

Build a `TabTemplate` with four `Tab`s and `TabContents` supplied by the corresponding screen/factory state. On tab selection, update the destination and call `invalidate()`; do not push another screen for a tab switch.

- [ ] **Step 4: Implement local-first Home**

On screen creation:

1. obtain the Local Music item only;
2. set state to `AndroidAutoDashboard.order(local, emptyList(), emptyList())`;
3. call `invalidate()` so Local Music renders immediately;
4. launch one lifecycle-bound online refresh;
5. merge recents and Home shelves through `AndroidAutoDashboard.order`;
6. preserve Local Music and successful partial data if one online request fails;
7. cancel the job when the screen is destroyed.

Use a stored `Job?` and this guard:

```kotlin
private fun refresh() {
    if (refreshJob?.isActive == true) return
    refreshJob = screenScope.launch {
        val online = dashboardSource.loadHomeOnline()
        sections = AndroidAutoDashboard.order(localMusic, online.recents, online.homeShelves)
        errorMessage = online.errorMessage
        invalidate()
    }
}
```

Define `screenScope` as `CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)` and register a lifecycle observer that calls `screenScope.cancel()` on `Lifecycle.Event.ON_DESTROY`.

- [ ] **Step 5: Implement shared browse navigation**

`TanTovCarBrowseScreen(route)` calls the existing gateway for the route and renders:

- `LocalMusic`: All Songs, Folders, Albums, Artists;
- `Recent`: playback history;
- `Explore`: online browse shelves;
- `Library`: existing library items with Local Music retained;
- `Shelf`, `LocalSection`, `LocalCollection`, `LibrarySection`, and `Collection`: existing descendants.

Playable items call `CarPlaybackController.play(item)`. Browsable items parse their existing media ID and push a new `TanTovCarBrowseScreen`. Unknown IDs show a bounded error and never navigate.

- [ ] **Step 6: Handle playback launch intents**

In `TanTovCarSession.onNewIntent`, when the action is `androidx.car.app.media.action.SHOW_MEDIA_PLAYBACK`, push the screen that returns `MediaPlaybackTemplate`. Avoid pushing a duplicate when that screen is already on top.

- [ ] **Step 7: Run template, catalog, local, search, voice, and repeat tests for GREEN**

Run:

```bash
./gradlew connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.music.bitchord.TanTovCarTemplateTest
./gradlew testDevDebugUnitTest \
  --tests 'com.music.bitchord.AndroidAuto*Test'
```

Expected: PASS.

- [ ] **Step 8: Commit the complete navigation slice**

```bash
git add app/src/main/java/com/music/bitchord/car/TanTovCarHomeScreen.kt \
  app/src/main/java/com/music/bitchord/car/TanTovCarTabsScreen.kt \
  app/src/main/java/com/music/bitchord/car/TanTovCarBrowseScreen.kt \
  app/src/main/java/com/music/bitchord/car/TanTovCarSession.kt \
  app/src/androidTest/java/com/music/bitchord/TanTovCarTemplateTest.kt
git commit -m "feat(auto): add TanTov dashboard navigation"
```

---

### Task 7: Preserve legacy Android Auto behavior and automate template smoke coverage

**Files:**
- Modify: `app/src/androidTest/java/com/music/bitchord/TanTovCarTemplateTest.kt`
- Modify: `.github/workflows/android-auto-legacy-smoke.yml`

**Interfaces:**
- Consumes: installed dev APK, existing MediaBrowser root, templated `CarAppService`, and Android emulator.
- Produces: CI coverage for both the legacy media service and the new template registration/navigation contract.

- [ ] **Step 1: Add an end-to-end template service smoke assertion**

Extend `TanTovCarTemplateTest` to instantiate the registered service/session through Car App testing APIs and assert:

```kotlin
assertEquals(listOf("Home", "Recents", "Browse", "Library"), renderedTabTitles)
assertEquals("Local Music", firstHomeItemTitle)
```

Keep `AndroidAutoLegacyBrowserTest` expecting root ID `tantov:auto:v1:root` and its existing four legacy children. The legacy test is not rewritten to expect the new template's tabs because they are separate host interfaces.

- [ ] **Step 2: Run both smoke classes locally and verify behavior**

Run:

```bash
./gradlew connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.music.bitchord.AndroidAutoLegacyBrowserTest,com.music.bitchord.TanTovCarManifestTest,com.music.bitchord.TanTovCarTemplateTest
```

Expected: PASS for all three classes.

- [ ] **Step 3: Add the new classes to CI**

Update the workflow runner argument to:

```yaml
script: >-
  ./gradlew connectedDevDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=com.music.bitchord.AndroidAutoLegacyBrowserTest,com.music.bitchord.TanTovTopBarBrandingTest,com.music.bitchord.TanTovCarManifestTest,com.music.bitchord.TanTovCarTemplateTest
```

Keep both trigger branches: `feat/android-auto-full` and `feat/tantov-music-v1`.

- [ ] **Step 4: Run workflow syntax and full local instrumentation verification**

Run:

```bash
git diff --check
./gradlew connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.music.bitchord.AndroidAutoLegacyBrowserTest,com.music.bitchord.TanTovTopBarBrandingTest,com.music.bitchord.TanTovCarManifestTest,com.music.bitchord.TanTovCarTemplateTest
```

Expected: no whitespace errors and all tests PASS.

- [ ] **Step 5: Commit the smoke coverage slice**

```bash
git add app/src/androidTest/java/com/music/bitchord/TanTovCarTemplateTest.kt \
  .github/workflows/android-auto-legacy-smoke.yml
git commit -m "test(auto): cover templated dashboard smoke flow"
```

---

### Task 8: Full verification, APK inspection, and controlled handoff

**Files:**
- No planned source changes; this task verifies the committed implementation and produces the APK handoff
- Do not create a merge commit or pull request without user authorization

**Interfaces:**
- Consumes: complete implementation and all existing CI workflows.
- Produces: verified dev APK and one-at-a-time real-car test instructions.

- [ ] **Step 1: Run the complete unit and build gates**

Run:

```bash
./gradlew testDevDebugUnitTest
./gradlew assembleDevDebug
```

Expected: both commands exit 0.

- [ ] **Step 2: Run complete Android Auto instrumentation coverage**

Run:

```bash
./gradlew connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.music.bitchord.AndroidAutoLegacyBrowserTest,com.music.bitchord.TanTovTopBarBrandingTest,com.music.bitchord.TanTovCarManifestTest,com.music.bitchord.TanTovCarTemplateTest
```

Expected: all listed test classes PASS.

- [ ] **Step 3: Inspect the APK identity, permissions, and car components**

Run with the Android SDK build tools:

```bash
apk=app/build/outputs/apk/dev/debug/app-dev-debug.apk
apkanalyzer manifest application-id "$apk"
apkanalyzer manifest permissions "$apk"
apkanalyzer manifest print "$apk" | rg 'TanTovCarAppService|PlaybackService|MEDIA_TEMPLATES|automotive_app_desc'
sha256sum "$apk"
```

Expected:

- application ID is `com.tantov.music`;
- permissions include `androidx.car.app.MEDIA_TEMPLATES`;
- permissions do not include `android.permission.MANAGE_EXTERNAL_STORAGE`;
- both `TanTovCarAppService` and `PlaybackService` are packaged;
- the SHA-256 value is recorded for the user.

- [ ] **Step 4: Verify the branch diff is scoped and clean**

Run:

```bash
git status --short
git diff --check origin/feat/tantov-music-v1...HEAD
git diff --stat origin/feat/tantov-music-v1...HEAD
```

Expected: no uncommitted files, no whitespace errors, and changes limited to the approved dashboard, tests, resources, build declarations, and workflow.

- [ ] **Step 5: Push only after local verification, then verify actual CI**

Push the feature commits to `feat/tantov-music-v1` only after confirming the remote head has not moved unexpectedly. Check the real GitHub Actions runs for the full Android build and Android Auto smoke workflow; do not infer status from a local run.

- [ ] **Step 6: Hand off the new APK without claiming real-car success**

Give the user the exact APK filename and SHA-256. First ask them only to:

1. enable Android Auto developer mode;
2. enable **Unknown sources**;
3. enable **CAL beta features** if that option is present;
4. install the new APK;
5. open TanTov in the parked car and report whether the four-tab dashboard appears with Local Music first.

Wait for that result before asking them to test Local Music navigation, offline playback, recommendations, search, voice, steering-wheel controls, Now Playing, or repeat. Do not merge until the user explicitly approves after this new real-car test.
