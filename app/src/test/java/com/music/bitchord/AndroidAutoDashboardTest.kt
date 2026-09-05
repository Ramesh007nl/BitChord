package com.music.bitchord

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.music.bitchord.car.AndroidAutoDashboard
import com.music.bitchord.car.DashboardSectionKind
import com.music.bitchord.car.DashboardShelf
import com.music.bitchord.playback.AndroidAutoMediaIds
import com.music.bitchord.playback.AndroidAutoRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class AndroidAutoDashboardTest {
    @Test
    fun localMusicAlwaysLeadsTheDashboard() {
        val localMusic = browseItem(
            AndroidAutoMediaIds.encode(AndroidAutoRoute.LocalMusic),
            "Local Music",
        )

        val sections = AndroidAutoDashboard.order(
            localMusic = localMusic,
            recents = listOf(playableItem("recent-1", "Recent Song")),
            homeShelves = listOf(
                shelf("Listen Again", "listen-1"),
                shelf("Quick Picks", "quick-1"),
                shelf("Made For You", "other-1"),
            ),
        )

        assertEquals(DashboardSectionKind.LOCAL_MUSIC, sections.first().kind)
        assertSame(localMusic, sections.first().items.single())
        assertEquals("tantov:auto:v1:local", sections.first().items.single().mediaId)
    }

    @Test
    fun recentsPreserveInputOrderImmediatelyAfterLocalMusic() {
        val sections = AndroidAutoDashboard.order(
            localMusic = localMusicItem(),
            recents = listOf(
                playableItem("recent-3", "Third played"),
                playableItem("recent-1", "First played"),
                playableItem("recent-2", "Second played"),
            ),
            homeShelves = listOf(shelf("Quick Picks", "quick-1")),
        )

        assertEquals(DashboardSectionKind.RECENTLY_PLAYED, sections[1].kind)
        assertEquals(
            listOf("recent-3", "recent-1", "recent-2"),
            sections[1].items.map { it.mediaId },
        )
    }

    @Test
    fun quickPicksAndListenAgainArePromotedByCaseAndPunctuationNormalizedTitles() {
        val sections = AndroidAutoDashboard.order(
            localMusic = localMusicItem(),
            recents = emptyList(),
            homeShelves = listOf(
                shelf("Made For You", "other-1"),
                shelf("lIsTeN...aGaIn!!", "listen-1"),
                shelf("qUiCk—PiCkS!!", "quick-1"),
            ),
        )

        assertEquals(
            listOf(
                DashboardSectionKind.LOCAL_MUSIC,
                DashboardSectionKind.QUICK_PICKS,
                DashboardSectionKind.LISTEN_AGAIN,
                DashboardSectionKind.OTHER,
            ),
            sections.map { it.kind },
        )
        assertEquals("quick-1", sections[1].items.single().mediaId)
        assertEquals("listen-1", sections[2].items.single().mediaId)
    }

    @Test
    fun firstPreferredShelfWinsWhenNormalizedTitlesAreDuplicated() {
        val sections = AndroidAutoDashboard.order(
            localMusic = localMusicItem(),
            recents = emptyList(),
            homeShelves = listOf(
                shelf("Quick Picks", "quick-first"),
                shelf("QUICK-PICKS", "quick-duplicate"),
                shelf("Listen Again", "listen-first"),
                shelf("listen_again", "listen-duplicate"),
            ),
        )

        val quickPicks = sections.filter { it.kind == DashboardSectionKind.QUICK_PICKS }
        val listenAgain = sections.filter { it.kind == DashboardSectionKind.LISTEN_AGAIN }

        assertEquals(1, quickPicks.size)
        assertEquals(listOf("quick-first"), quickPicks.single().items.map { it.mediaId })
        assertEquals(1, listenAgain.size)
        assertEquals(listOf("listen-first"), listenAgain.single().items.map { it.mediaId })
        assertFalse(sections.any { section ->
            section.items.any { it.mediaId == "quick-duplicate" || it.mediaId == "listen-duplicate" }
        })
    }

    @Test
    fun otherShelvesPreserveSourceOrderAfterNormalizedDuplicatesAreRemoved() {
        val sections = AndroidAutoDashboard.order(
            localMusic = localMusicItem(),
            recents = emptyList(),
            homeShelves = listOf(
                shelf("Morning Mix", "morning-first"),
                shelf("Quick Picks", "quick-1"),
                shelf("Focus Flow", "focus-1"),
                shelf("MORNING-MIX!!", "morning-duplicate"),
                shelf("Listen Again", "listen-1"),
                shelf("Evening Wind Down", "evening-1"),
            ),
        )

        val others = sections.filter { it.kind == DashboardSectionKind.OTHER }

        assertEquals(
            listOf("Morning Mix", "Focus Flow", "Evening Wind Down"),
            others.map { it.title },
        )
        assertEquals(
            listOf("morning-first", "focus-1", "evening-1"),
            others.map { it.items.single().mediaId },
        )
    }

    @Test
    fun emptyRecentsOmitOnlyRecentsAndNeverLocalMusic() {
        val localMusic = localMusicItem()

        val sections = AndroidAutoDashboard.order(
            localMusic = localMusic,
            recents = emptyList(),
            homeShelves = listOf(shelf("Fresh Finds", "fresh-1")),
        )

        assertEquals(DashboardSectionKind.LOCAL_MUSIC, sections.first().kind)
        assertSame(localMusic, sections.first().items.single())
        assertFalse(sections.any { it.kind == DashboardSectionKind.RECENTLY_PLAYED })
        assertEquals(
            listOf(DashboardSectionKind.LOCAL_MUSIC, DashboardSectionKind.OTHER),
            sections.map { it.kind },
        )
    }

    @Test
    fun displayedTitlesKeepTheirSourceCaseAndPunctuation() {
        val sections = AndroidAutoDashboard.order(
            localMusic = localMusicItem(),
            recents = emptyList(),
            homeShelves = listOf(
                shelf("qUiCk—PiCkS!!", "quick-1"),
                shelf("LISTEN...Again?!", "listen-1"),
                shelf("Fresh Finds!", "fresh-1"),
            ),
        )

        assertEquals(
            listOf("qUiCk—PiCkS!!", "LISTEN...Again?!", "Fresh Finds!"),
            sections.drop(1).map { it.title },
        )
    }

    @Test
    fun otherSectionKeysAreStableAcrossCaseAndPunctuationVariants() {
        val first = AndroidAutoDashboard.order(
            localMusic = localMusicItem(),
            recents = emptyList(),
            homeShelves = listOf(shelf("Made For You 2026", "first")),
        ).single { it.kind == DashboardSectionKind.OTHER }
        val variant = AndroidAutoDashboard.order(
            localMusic = localMusicItem(),
            recents = emptyList(),
            homeShelves = listOf(shelf("MADE-for_you 2026!!!", "variant")),
        ).single { it.kind == DashboardSectionKind.OTHER }

        assertEquals("other:madeforyou2026", first.key)
        assertEquals(first.key, variant.key)
    }

    @Test
    fun shelfMoreTargetSurvivesDashboardOrderingByIdentity() {
        val moreItem = browseItem(
            mediaId = AndroidAutoMediaIds.encode(
                AndroidAutoRoute.Shelf(
                    source = AndroidAutoRoute.Shelf.Source.HOME,
                    ordinal = 7,
                    title = "Focus & Flow / 夜",
                ),
            ),
            title = "Focus & Flow / 夜",
        )
        val shelf = DashboardShelf(
            title = "Focus & Flow / 夜",
            subtitle = "Shelf subtitle",
            items = (1..7).map { playableItem("track-$it", "Track $it") },
            moreItem = moreItem,
        )

        val orderedShelf = AndroidAutoDashboard.order(
            localMusic = localMusicItem(),
            recents = emptyList(),
            homeShelves = listOf(shelf),
        ).single { it.kind == DashboardSectionKind.OTHER }

        assertSame(moreItem, orderedShelf.moreItem)
        assertEquals(moreItem.mediaId, orderedShelf.moreItem?.mediaId)
    }

    private fun localMusicItem(): MediaItem = browseItem(
        AndroidAutoMediaIds.encode(AndroidAutoRoute.LocalMusic),
        "Local Music",
    )

    private fun shelf(title: String, mediaId: String): DashboardShelf = DashboardShelf(
        title = title,
        subtitle = "Shelf subtitle",
        items = listOf(playableItem(mediaId, "Track $mediaId")),
    )

    private fun browseItem(mediaId: String, title: String): MediaItem = mediaItem(
        mediaId = mediaId,
        title = title,
        browsable = true,
        playable = false,
    )

    private fun playableItem(mediaId: String, title: String): MediaItem = mediaItem(
        mediaId = mediaId,
        title = title,
        browsable = false,
        playable = true,
    )

    private fun mediaItem(
        mediaId: String,
        title: String,
        browsable: Boolean,
        playable: Boolean,
    ): MediaItem = MediaItem.Builder()
        .setMediaId(mediaId)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(browsable)
                .setIsPlayable(playable)
                .build(),
        )
        .build()
}
