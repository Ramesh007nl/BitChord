# TanTov Android Auto – BitChord Upstream Integration Design

Date: 2026-09-06

## Goal

Integrate the useful Android Auto improvements that landed upstream in `kushagrasinghx/BitChord` into TanTov while preserving TanTov's existing custom Android Auto dashboard, branding, car templates, and current feature branches.

The integration must improve Android Auto reliability and browsing without replacing TanTov's car UI architecture wholesale.

## Starting Point

- Repository: `Ramesh007nl/BitChord`
- Base branch: `feat/tantov-auto-dashboard`
- Integration branch: `integration/bitChord-android-auto-2026-09`
- Upstream reference: `kushagrasinghx/BitChord` current `main`
- Key upstream Android Auto introduction commit: `8672aec5fa960f8a6834428a2af0b3b4cda40d9c`

TanTov already has a custom Android Auto subsystem with car templates, custom dashboard data, artwork loading, a car app service/session, Android Auto-specific tests, and a descriptor that declares both `media` and `template`.

## Integration Strategy

Do not merge upstream `main` wholesale and do not replace the TanTov dashboard with BitChord's standard media browser implementation.

Instead, port Android Auto-specific backend improvements selectively into the TanTov architecture. Preserve TanTov-specific components and only adopt upstream changes that directly improve Android Auto discovery, Media3 browsing, search, playback handoff, caching, local media access, or car compatibility.

## Components to Preserve

The following TanTov concepts remain authoritative:

- `AndroidAutoDashboard`
- `AndroidAutoDashboardSource`
- `CarArtworkLoader`
- `CarTemplateFactory`
- `TanTovCarAppService`
- `TanTovCarSession`
- TanTov car navigation icons and templates
- TanTov branding and app identity
- Existing `media` + `template` automotive declaration
- TanTov Android Auto test coverage

## Upstream Improvements to Evaluate and Port

### Android Auto discovery and compatibility

Evaluate and adopt the relevant upstream manifest/service declarations, including Media3 library service and legacy media browser compatibility, while keeping TanTov's template service.

### MediaLibraryService browsing

Use the upstream implementation as the reference for robust Media3 browsing behavior where it improves TanTov's current browse tree.

Required capabilities:

- library root
- top-level browse children
- item lookup
- paging
- search
- playable media item resolution
- play-from-search behavior

### Browse content

Ensure Android Auto can expose, where supported by current TanTov data sources:

- Home
- Recents / Recently Played
- Quick Picks / recommendations
- Playlists
- Liked Music
- Library
- Local Music
- Search results

TanTov's custom dashboard remains the presentation layer when using Android for Cars templates.

### Performance and caching

Port short-lived Android Auto browse caches where they materially reduce repeated network calls while navigating in the car. Cache behavior must not interfere with normal phone-app playback state.

### Artwork and metadata

Keep TanTov's car artwork loader, but adopt upstream metadata/media item construction fixes when needed for Android Auto compatibility.

### Playback handoff

Verify that selecting media from Android Auto produces the same queue and playback behavior as selecting the equivalent media in the phone app.

## Explicit Non-Goals

This integration will not:

- merge all upstream UI changes
- replace TanTov branding
- remove TanTov's custom car templates
- merge unrelated upstream features such as general UI redesigns or source-module changes
- modify production branches directly
- publish a production release before real-car testing

## Safety and Branching

All work stays on `integration/bitChord-android-auto-2026-09` until verified.

Do not modify:

- `main`
- `feat/tantov-music-v1`
- `feat/tantov-auto-dashboard`
- `feat/android-auto-full`

No force updates to those branches.

## Testing

Automated validation should include:

1. Existing TanTov car template tests remain green.
2. Manifest tests confirm Android Auto media/template declarations.
3. Media3 library tests cover root, children, item lookup, and search.
4. Legacy MediaBrowser compatibility remains covered where applicable.
5. Playback tests verify Android Auto selections resolve to playable items.
6. Local Music remains accessible without breaking online browse behavior.
7. Build succeeds for the TanTov dev flavor.

## Real-Car Acceptance Test

After automated checks pass, produce a test APK for installation on the user's Samsung phone and validate on the user's Hyundai Kona through Android Auto.

Acceptance checks:

- TanTov appears in Android Auto
- TanTov icon and label are correct
- dashboard opens without errors
- Home opens
- Recents opens
- Library opens
- Local Music opens
- search works
- artwork appears where Android Auto allows it
- tapping a track starts playback
- play/pause works
- next/previous works
- reconnecting Android Auto does not break the browse tree
- phone-app playback and car playback remain synchronized

## Success Criteria

The integration is successful when TanTov keeps its custom Android Auto dashboard, gains the useful compatibility/reliability improvements from current BitChord, passes automated tests, builds successfully, and works on the real Hyundai Kona without regressions to the phone app.
