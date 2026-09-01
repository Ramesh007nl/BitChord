# TanTov Music: Local Library, Repeat, and Branding Design

Date: 2026-09-01
Branch: `feat/tantov-music-v1`

## Goal

Turn the working Android Auto BitChord fork into a distinct app called **TanTov Music** while preserving the Android Auto browse/playback behavior that is already working. Add local-device music support, combined online + local search, repeat controls, and a permanent app identity.

## Product identity

- App name: **TanTov Music**
- Permanent Android application ID: **`com.tantov.music`**
- Internal Kotlin namespace initially remains **`com.music.bitchord`** to reduce migration risk.
- Existing original BitChord remains separate and untouched.
- Current Android Auto implementation remains the foundation.
- TanTov Music gets a new dedicated app icon and Android Auto launcher icon.

## Repeat behavior

TanTov Music exposes a three-state repeat mode through the existing shared player/session:

1. Repeat off
2. Repeat all
3. Repeat current song

The repeat state must be shared between phone UI, Android Auto, notification controls, and steering-wheel/session behavior wherever the host supports exposing it. No second Android Auto player is introduced.

## Local Music architecture

Introduce a dedicated `LocalMusicRepository` responsible for discovering, indexing, deduplicating, and exposing music that exists on the phone.

Two acquisition modes are supported and may be enabled at the same time:

### A — All Music on this phone

Use Android MediaStore and the platform audio/media permission to discover audio files Android already indexes as music. Do not request unrestricted all-files access.

### B — Choose music folders

Use Android's Storage Access Framework folder picker (`ACTION_OPEN_DOCUMENT_TREE`) to let the user grant persistent access to one or more folders. Persist only the URI permissions the user explicitly grants.

The repository merges A + B into one local catalog and deduplicates the same underlying track when it is visible through both paths.

## Local library browsing

Under the existing Android Auto and phone Library, add a separate top-level entry:

`Library -> Local Music`

Inside Local Music expose:

- Songs
- Folders
- Albums
- Artists

Local music remains separate from the existing online Songs / Albums / Artists sections while browsing.

## Playback model

Local songs are converted into standard Media3 `MediaItem`s using Android `content://` URIs and are played by the same ExoPlayer / MediaLibrarySession pipeline already used for online music.

This preserves:

- one player instance
- one queue model
- one Now Playing surface
- Android Auto transport controls
- steering-wheel media controls
- audio focus and car output behavior

Local playback must work offline.

## Search

Search combines online and local results in one result set.

- Existing online search remains available.
- Local results are added alongside online results.
- Local tracks are clearly marked **On device** in UI metadata where possible.
- Search must not duplicate the same local track when it was discovered through both A and B.
- Search remains usable when offline for the local portion even if online requests fail.

## First-run Local Music setup

The Local Music setup is optional and skippable.

First-run choices:

- All Music on this phone
- Choose music folders
- Use both
- Not now

Choosing A requests only the platform audio/media permission appropriate for the Android version.

Choosing B opens the secure folder picker and allows the user to add one or more folders.

The user can continue into the app even if they skip, deny, or cancel Local Music setup.

## Settings: Local Music

Add `Settings -> Local Music` with:

- All Music enabled/disabled state
- current granted folders
- Add folder
- Remove folder access
- Rescan music
- clear handling for revoked/invalid folder permissions

Removing access to a folder removes those tracks from the TanTov Music local catalog on the next refresh rather than leaving broken entries.

## Permissions and privacy

TanTov Music must not request MANAGE_EXTERNAL_STORAGE or equivalent unrestricted file-system access.

Use least-privilege access:

- MediaStore permission for A
- persisted Storage Access Framework tree grants for B

Permission denial must not block online music use.

## Android Auto behavior

Preserve the working Android Auto architecture:

- `MediaLibraryService`
- one `MediaLibrarySession`
- native Android Auto browse UI
- native Now Playing UI
- existing browse/search transport path

Extend the catalog so `Library -> Local Music` can be browsed from Android Auto.

Local rows use stable TanTov/Android Auto media IDs for browsing, then convert to normal playable MediaItems before reaching ExoPlayer.

Repeat-one must affect the same player session used by Android Auto.

## Error handling

- MediaStore permission denied: Local Music A unavailable, online music still works.
- Folder selection cancelled: no folder is added; app remains usable.
- Persisted folder grant revoked: remove or flag that source, refresh local catalog, and continue with remaining sources.
- Local file removed/moved: ignore stale entry after rescan and return a normal playback failure if requested before refresh.
- Online search fails while local search succeeds: return local results rather than failing the entire search.
- Local scan failure must not break the online Library or Android Auto root.

## Data and caching

The first implementation should keep local-library state simple:

- persist granted tree URIs and setup choices in app preferences/data store
- derive the local catalog from MediaStore and folder scans
- use an in-memory/index cache suitable for browsing/search
- provide explicit rescan from Settings

Do not introduce a new remote backend or cloud database for local tracks.

## Branding scope

For the first TanTov release:

- change app label to TanTov Music
- change application ID to `com.tantov.music`
- provide new launcher/adaptive icon resources
- update Android Auto visible app name/icon
- keep internal Kotlin package declarations unchanged unless a build requirement forces a targeted migration

## Testing strategy

Add tests before implementation for:

- repeat state transitions and shared player repeat mode
- MediaStore result mapping
- SAF folder result mapping
- A+B deduplication
- permission denied / folder revoked behavior
- Local Music browse hierarchy
- combined online + local search
- local-only search fallback when online search fails
- local MediaItem conversion to playable content URI
- Android Auto `Library -> Local Music` browse path
- permanent application ID / label configuration

Keep the existing Android Auto legacy-browser smoke test and normal unit/build CI green.

## Non-goals for this phase

- Renaming every internal Kotlin package from `com.music.bitchord`
- Requesting unrestricted all-files access
- Building a second player specifically for Android Auto
- Mixing local tracks directly into the existing online Library browse categories
- Adding a backend/cloud sync system for local files

## Success criteria

TanTov Music is considered ready for user testing when:

1. It installs as package `com.tantov.music` and displays as TanTov Music.
2. The new icon appears on phone and Android Auto.
3. Android Auto still opens and browses without regressing the currently working flow.
4. Repeat off/all/one works through the shared player.
5. First-run Local Music setup supports A, B, A+B, and Not now.
6. `Library -> Local Music -> Songs / Folders / Albums / Artists` works.
7. Local songs play on phone and through Android Auto without internet.
8. Search returns online + local results together and marks local results On device.
9. The app never requires unrestricted all-files permission.
10. Existing BitChord can remain installed separately.
