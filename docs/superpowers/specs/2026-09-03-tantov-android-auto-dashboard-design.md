# TanTov Music: Android Auto Dashboard Design

Date: 2026-09-03
Base branch: `feat/tantov-music-v1`
Base commit: `a769b23de2f9aed97990d79bf4f6642bf798879e`
Status: Approved product design; implementation not started

## Goal

Replace TanTov Music's current folder-first Android Auto landing experience with a media dashboard that brings the most useful music to the first screen. The dashboard should resemble the supplied Android for Cars reference while remaining driver-safe and using supported Car App Library components.

The first screen prioritizes Local Music, followed by the same useful personalized shelves available on the phone. Selecting Local Music opens a dedicated categorized browser rather than immediately starting playback.

## Confirmed product decisions

- Use a Car App Library templated media experience rather than limiting the redesign to the existing host-rendered `MediaBrowser` interface.
- The main navigation contains four destinations: **Home**, **Recents**, **Browse**, and **Library**.
- **Home** is the initial destination.
- The first Home item is one **Local Music** entry.
- Selecting Local Music opens four choices: **All Songs**, **Folders**, **Albums**, and **Artists**.
- Home then presents **Recently Played**, **Quick Picks**, **Listen Again**, and the remaining eligible phone Home recommendations.
- Use supported mixed row/grid dashboard components. Do not depend on Car App Library 1.9 alpha-only spotlight, progress-bar, chip, expanded-header, or condensed-item features in the first implementation.
- Preserve the existing `MediaLibraryService`, `MediaLibrarySession`, ExoPlayer, local catalog, voice search, media controls, and Android Auto media IDs underneath the new UI.
- Do not merge the TanTov branch until the user explicitly approves after testing the new APK in the real car.

## Platform constraint

The reference image includes components introduced with Car App Library 1.9, including spotlight sections, progress bars, chips, condensed items, and expanded headers. Android currently describes those components as alpha/beta features requiring beta access and a developer-host feature flag. They are therefore outside this first implementation.

The initial dashboard uses `TabTemplate`, `SectionedItemTemplate`, `RowSection`, and `GridSection`. The Android Auto host remains responsible for responsive placement, including whether navigation appears across the top or along the side and how many cards fit on screen. Consequently, TanTov controls the content, grouping, order, artwork, labels, and actions, but not exact pixel placement on every vehicle.

## Screen structure

### App shell

Use a `TabTemplate` with these four destinations:

1. Home
2. Recents
3. Browse
4. Library

Each tab has a short title and a monochrome car-safe icon. The host may render the tab navigation horizontally or vertically according to screen size.

Every browsing screen provides the standard route to media playback. When supported by the selected compatible Car App Library version, this uses the minimized playback control/action supplied by the host. The existing MediaSession remains the only playback authority.

### Home

Use a `SectionedItemTemplate`. Sections appear in this order:

1. **Local Music** — a single prominent browsable item, always shown first when local music is configured or discoverable.
2. **Recently Played** — recent tracks or collections, displayed as an artwork-led section.
3. **Quick Picks** — playable recommendations from the phone Home feed.
4. **Listen Again** — repeat-listening recommendations from the phone Home feed.
5. **Other recommendations** — remaining eligible phone Home shelves, preserving their source order after duplicate titles are removed.

The Local Music entry is browsable only; selecting it never unexpectedly plays the entire device library.

If Local Music has not been configured, the entry still opens a clear explanatory state directing the user to configure Local Music on the phone. The car UI must not request broad storage access or attempt a complex folder-selection flow while driving.

### Local Music

Selecting Local Music opens a categorized `SectionedItemTemplate` or grid containing:

- All Songs
- Folders
- Albums
- Artists

These choices reuse the current combined MediaStore and Storage Access Framework catalog. Their descendants reuse the existing TanTov local-media browse IDs and `content://` playback URIs.

### Recents

Recents exposes the existing playback-history source. It must show a useful empty state if no history exists. Local items should appear when they are part of the existing history model rather than being silently filtered out.

### Browse

Browse exposes online discovery content corresponding to the current Explore experience. An online failure affects only this destination and online Home sections; it must not remove Local Music.

### Library

Library preserves the current library destinations and account-aware behavior. Local Music remains reachable here as well as from the first Home item, so it is not lost if the user navigates directly to Library.

## Data flow and reuse

The new car UI is an adapter over existing application data rather than a second music implementation.

- Phone Home and car Home continue to use the shared `AndroidAutoDataSource.home()` / `YtMusicRepository.home()` feed.
- The dashboard mapper recognizes Recently Played, Quick Picks, and Listen Again by stable shelf identity where available, with normalized title matching as a compatibility fallback.
- Remaining feed shelves are appended without duplicate section titles.
- Local Music uses the existing injected local catalog data source.
- A selected playable item is converted to the existing Media3 `MediaItem` and sent to the existing MediaSession/ExoPlayer pipeline.
- The current `tantov:auto:v1` media-ID namespace remains stable. New template navigation identifiers must not replace playable IDs or invalidate current Android Auto search and playback behavior.
- No second player, queue, local scanner, online repository, or playback state is introduced.

## Loading, caching, and failure behavior

Home data sources load independently so slow online requests cannot block the Local Music entry.

- Render Local Music immediately from local configuration/catalog state.
- Render cached Home recommendations when available while refreshing online data.
- Add online sections as their data becomes available without replacing already-rendered local content with a full-screen spinner.
- Give online loading a bounded timeout and surface a concise retryable error/empty state instead of an indefinite skeleton or spinner.
- If the network fails, keep Local Music available and retain valid cached recommendations.
- If the user is signed out, keep Local Music available and show only sections that do not require an account.
- A revoked folder grant or removed local file follows the current safe local-catalog behavior and must not crash the dashboard.
- Template callbacks must be lifecycle-safe: cancelled screens do not publish stale results, and repeated tab selection does not launch duplicate refresh jobs.

## Playback, search, and controls

- Playback uses the existing `PlaybackService`, `MediaLibrarySession`, and ExoPlayer instance.
- The template layer registers the existing session token with the Car App Library media playback manager as required for Media3 interoperability.
- The playback surface is driven by the MediaSession state; the template does not maintain a second copy of play/pause, queue, or repeat state.
- Existing previous, next, play/pause, voice-search, and steering-wheel behavior remains unchanged.
- The existing repeat cycle—OFF, ALL, ONE, OFF—remains authoritative. Whether a host displays the repeat action is still host-dependent.
- Search continues to combine online and local tracks and retains the local-only fallback when online search fails.

## Android integration

Implementation will add the minimum Car App Library integration required for a templated media app:

- Car App Library dependency at a version supporting the approved non-1.9-alpha templates.
- A `CarAppService` declaring the media category.
- `androidx.car.app.MEDIA_TEMPLATES` permission.
- `androidx.car.app.minCarApiLevel` compatible with media templates.
- Android Auto descriptor entries for both `media` and `template`.
- Media playback token registration bridging the existing Media3 platform token to the Car App Library.
- Handling for the standard `SHOW_MEDIA_PLAYBACK` intent so launches from system media surfaces open Now Playing.

Manifest and component changes must remain scoped to the TanTov app configuration and must not change the internal Kotlin namespace `com.music.bitchord` or the TanTov application ID `com.tantov.music`.

## Compatibility strategy

The redesigned interface depends on a compatible Android Auto host with templated-media support. During development, Android Auto developer mode and its Car App Library beta-features setting may be required.

The underlying `MediaLibraryService` remains mandatory for voice actions, recommendations, media-session interoperability, and existing automated smoke coverage. Retaining it also minimizes regression risk even though the selected product direction replaces the in-app car browsing UI with templates on supporting hosts.

The APK must fail safely on an unsupported host. Exact fallback behavior must be validated against the selected Car App Library version and Android Auto host during implementation; it must not be guessed in code or claimed without an emulator/device result.

## Test-driven implementation strategy

Add failing tests before production code wherever practical.

### Unit tests

- Home section ordering always places Local Music first.
- Local Music maps to one browsable entry and never to a play-all action.
- Local Music children are ordered All Songs, Folders, Albums, Artists.
- Recently Played, Quick Picks, and Listen Again map to their intended sections.
- Remaining phone shelves preserve order and duplicate section titles are removed.
- Local content is returned before an online Home request completes.
- Online timeout/failure leaves Local Music and valid cached sections available.
- Signed-out state retains Local Music.
- Template item selection maps to existing stable media IDs and playback commands.
- Repeated refresh/tab activation does not duplicate jobs or sections.

### Integration and instrumentation tests

- Manifest contains the required templated-media declarations without losing the existing media service.
- The app launches to Home and Home begins with Local Music.
- Selecting Local Music displays All Songs, Folders, Albums, and Artists.
- A local track plays through the existing MediaSession.
- Now Playing is reachable from every browsing destination.
- Home remains usable with network disabled.
- Combined Android Auto search and local-only fallback still work.
- Existing repeat and transport controls still control the single shared player.

### Regression verification

- Run the complete `testDevDebugUnitTest` suite.
- Run `assembleDevDebug`.
- Keep the legacy MediaBrowser emulator smoke test green because the underlying service remains part of the architecture.
- Add a templated-media emulator/navigation smoke test where the available Android Auto host supports it.
- Inspect the built APK for the expected application ID and absence of `MANAGE_EXTERNAL_STORAGE`.
- Install the APK separately from BitChord.
- Re-test in the user's real car one item at a time. Record the Android Auto version and whether the beta-features setting was required.

## Rollout and real-car validation

This feature produces a new test APK; it does not modify the already-tested APK in place.

Validation order:

1. Automated unit tests and build.
2. Android Auto emulator/Desktop Head Unit templated-media test.
3. Phone installation alongside BitChord.
4. Real-car launch and Home rendering.
5. Local Music navigation and offline playback.
6. Recents, Quick Picks, Listen Again, and remaining recommendations.
7. Search, voice, steering-wheel controls, Now Playing, and repeat behavior.

No real-car success claim is made until the user reports the results of the newly built dashboard APK. No branch merge occurs without the user's explicit approval after that test.

## Non-goals

- Pixel-perfect reproduction of the reference on every car display.
- Car App Library 1.9 alpha-only spotlight, progress-bar, chip, expanded-header, condensed-item, or banner components.
- A custom player separate from Media3/ExoPlayer.
- Folder selection or broad storage-permission setup on the car screen.
- Changing the phone Home design as part of this feature.
- Renaming the internal Kotlin namespace.
- Merging `feat/tantov-music-v1`.

## Success criteria

The feature is ready for real-car testing when:

1. A compatible Android Auto host opens TanTov directly to the four-tab templated experience.
2. Home shows one Local Music entry first.
3. Local Music opens All Songs, Folders, Albums, and Artists.
4. Recently Played, Quick Picks, Listen Again, and remaining valid phone shelves follow in the approved order.
5. Local Music appears without waiting for online Home data and remains usable offline or signed out.
6. Online failure produces a bounded error state rather than an indefinite spinner.
7. Local and online selections use the existing player and Now Playing surface.
8. Search, voice actions, transport controls, and repeat behavior do not regress.
9. Full unit tests, build, legacy browser smoke coverage, and available template-host testing pass.
10. The APK retains `com.tantov.music`, does not request unrestricted storage access, installs separately from BitChord, and is not merged before explicit approval.

## Baseline verification note

Before this document was added, `./gradlew testDevDebugUnitTest` was attempted in a clean worktree based on `a769b23de2f9aed97990d79bf4f6642bf798879e`. The test tasks did not start because the workspace had no cached Gradle 8.11.1 distribution and network access to `services.gradle.org` was unavailable. This is an environment/bootstrap limitation, not a passing or failing test result. The user approved proceeding with documentation only; implementation must establish a runnable clean baseline before changing production code.
