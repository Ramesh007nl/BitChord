package com.music.bitchord

import android.net.Uri
import androidx.car.app.OnDoneCallback
import androidx.car.app.model.GridSection
import androidx.car.app.model.Item
import androidx.car.app.model.OnClickDelegate
import androidx.car.app.model.RowSection
import androidx.car.app.model.Section
import androidx.car.app.testing.TestCarContext
import androidx.car.app.testing.TestDelegateInvoker
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.car.CarTemplateFactory
import com.music.bitchord.car.DashboardLayout
import com.music.bitchord.car.DashboardSection
import com.music.bitchord.car.DashboardSectionKind
import com.music.bitchord.playback.AndroidAutoLocalSection
import com.music.bitchord.playback.AndroidAutoMediaIds
import com.music.bitchord.playback.AndroidAutoRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TanTovCarTemplateTest {
    private lateinit var factory: CarTemplateFactory

    @Before
    fun setUp() {
        factory = CarTemplateFactory(
            TestCarContext.createCarContext(ApplicationProvider.getApplicationContext()),
        )
    }

    @Test
    fun homeTemplateContainsLocalSectionFirst() {
        val localMusic = browsableItem(
            mediaId = AndroidAutoMediaIds.encode(AndroidAutoRoute.LocalMusic),
            title = "Local Music",
        )
        val clicked = mutableListOf<MediaItem>()

        val template = factory.home(
            sections = listOf(
                section(
                    key = "local-music",
                    title = "Local Music",
                    kind = DashboardSectionKind.LOCAL_MUSIC,
                    layout = DashboardLayout.SINGLE,
                    items = listOf(localMusic),
                ),
                section(
                    key = "quick-picks",
                    title = "Quick Picks",
                    kind = DashboardSectionKind.QUICK_PICKS,
                    layout = DashboardLayout.GRID,
                    items = listOf(playableItem("quick-1", "Quick One")),
                ),
            ),
            errorMessage = null,
            onItemClick = clicked::add,
            onRetry = {},
        )

        val localSection = template.sections.first()
        assertTrue(localSection is RowSection)
        assertEquals("Local Music", localSection.title.toString())
        val localRow = items(localSection as RowSection).single()
        assertEquals("Local Music", localRow.title.toString())
        assertTrue(localRow.isBrowsable)

        click(requireNotNull(localRow.onClickDelegate))

        assertEquals(
            listOf(AndroidAutoMediaIds.encode(AndroidAutoRoute.LocalMusic)),
            clicked.map { it.mediaId },
        )
    }

    @Test
    fun gridSectionsKeepArtworkItemsBrowsableOrPlayable() {
        val playable = playableItem(
            mediaId = AndroidAutoMediaIds.encode(AndroidAutoRoute.Track("playable-1")),
            title = "Playable One",
            subtitle = "Artist One",
            artworkUri = "https://example.test/playable.jpg",
        )
        val browsable = browsableItem(
            mediaId = AndroidAutoMediaIds.encode(
                AndroidAutoRoute.Shelf(AndroidAutoRoute.Shelf.Source.HOME, 3, "Deep Focus"),
            ),
            title = "Deep Focus",
            subtitle = "Collection",
            artworkUri = "https://example.test/browsable.jpg",
        )
        val clicked = mutableListOf<MediaItem>()

        val template = factory.home(
            sections = listOf(
                section(
                    key = "other:deepfocus",
                    title = "Deep Focus",
                    kind = DashboardSectionKind.OTHER,
                    layout = DashboardLayout.GRID,
                    items = listOf(playable, browsable),
                ),
            ),
            errorMessage = null,
            onItemClick = clicked::add,
            onRetry = {},
        )

        val gridSection = template.sections.single()
        assertTrue(gridSection is GridSection)
        assertEquals("Deep Focus", gridSection.title.toString())
        val gridItems = items(gridSection as GridSection)
        assertEquals(listOf("Playable One", "Deep Focus"), gridItems.map { it.title.toString() })
        assertEquals(listOf("Artist One", "Collection"), gridItems.map { it.text.toString() })
        gridItems.forEach {
            assertNotNull(it.image)
            assertNotNull(it.onClickDelegate)
            click(requireNotNull(it.onClickDelegate))
        }

        assertEquals(listOf(playable.mediaId, browsable.mediaId), clicked.map { it.mediaId })
        assertTrue(clicked.first().mediaMetadata.isPlayable == true)
        assertFalse(clicked.first().mediaMetadata.isBrowsable == true)
        assertTrue(clicked.last().mediaMetadata.isBrowsable == true)
        assertFalse(clicked.last().mediaMetadata.isPlayable == true)
    }

    @Test
    fun oversizedShelfUsesFiveItemsAndExactMoreTarget() {
        val moreItem = browsableItem(
            mediaId = AndroidAutoMediaIds.encode(
                AndroidAutoRoute.Shelf(
                    source = AndroidAutoRoute.Shelf.Source.HOME,
                    ordinal = 7,
                    title = "Focus & Flow / 夜",
                ),
            ),
            title = "Focus & Flow / 夜",
        )
        val sourceItems = (1..7).map { index ->
            playableItem("track-$index", "Track $index")
        }
        val clicked = mutableListOf<MediaItem>()

        val template = factory.home(
            sections = listOf(
                section(
                    key = "other:focusflow夜",
                    title = "Focus & Flow / 夜",
                    kind = DashboardSectionKind.OTHER,
                    layout = DashboardLayout.GRID,
                    items = sourceItems,
                    moreItem = moreItem,
                ),
            ),
            errorMessage = null,
            onItemClick = clicked::add,
            onRetry = {},
        )

        val gridItems = items(template.sections.single() as GridSection)
        assertEquals(
            listOf("Track 1", "Track 2", "Track 3", "Track 4", "Track 5", "More"),
            gridItems.map { it.title.toString() },
        )

        click(requireNotNull(gridItems.last().onClickDelegate))

        assertSame(moreItem, clicked.single())
        assertEquals(
            AndroidAutoMediaIds.encode(
                AndroidAutoRoute.Shelf(
                    source = AndroidAutoRoute.Shelf.Source.HOME,
                    ordinal = 7,
                    title = "Focus & Flow / 夜",
                ),
            ),
            clicked.single().mediaId,
        )
    }

    @Test
    fun oversizedNonShelfSectionUsesSixItemsWithoutMore() {
        val sourceItems = (1..7).map { index ->
            playableItem("recent-$index", "Recent $index")
        }

        val template = factory.home(
            sections = listOf(
                section(
                    key = "recently-played",
                    title = "Recently Played",
                    kind = DashboardSectionKind.RECENTLY_PLAYED,
                    layout = DashboardLayout.GRID,
                    items = sourceItems,
                ),
            ),
            errorMessage = null,
            onItemClick = {},
            onRetry = {},
        )

        val gridItems = items(template.sections.single() as GridSection)
        assertEquals(6, gridItems.size)
        assertEquals(
            listOf("Recent 1", "Recent 2", "Recent 3", "Recent 4", "Recent 5", "Recent 6"),
            gridItems.map { it.title.toString() },
        )
        assertFalse(gridItems.any { it.title.toString() == "More" })
    }

    @Test
    fun errorTemplateKeepsLocalMusicAndAddsRetryRow() {
        var retries = 0
        val localMusic = browsableItem(
            mediaId = AndroidAutoMediaIds.encode(AndroidAutoRoute.LocalMusic),
            title = "Local Music",
        )

        val template = factory.home(
            sections = listOf(
                section(
                    key = "local-music",
                    title = "Local Music",
                    kind = DashboardSectionKind.LOCAL_MUSIC,
                    layout = DashboardLayout.SINGLE,
                    items = listOf(localMusic),
                ),
            ),
            errorMessage = "Some online content is unavailable.",
            onItemClick = {},
            onRetry = { retries++ },
        )

        assertEquals(2, template.sections.size)
        assertEquals("Local Music", template.sections.first().title.toString())
        val retrySection = template.sections.last()
        assertTrue(retrySection is RowSection)
        assertEquals("Some online content is unavailable.", retrySection.title.toString())
        val retryRow = items(retrySection as RowSection).single()
        assertEquals("Retry", retryRow.title.toString())

        click(requireNotNull(retryRow.onClickDelegate))

        assertEquals(1, retries)
    }

    @Test
    fun localTemplateTitlesAreAllSongsFoldersAlbumsArtists() {
        val localItems = AndroidAutoLocalSection.entries.map { localSection ->
            browsableItem(
                mediaId = AndroidAutoMediaIds.encode(AndroidAutoRoute.LocalSection(localSection)),
                title = when (localSection) {
                    AndroidAutoLocalSection.SONGS -> "All Songs"
                    AndroidAutoLocalSection.FOLDERS -> "Folders"
                    AndroidAutoLocalSection.ALBUMS -> "Albums"
                    AndroidAutoLocalSection.ARTISTS -> "Artists"
                },
            )
        }

        val template = factory.browse(
            title = "Local Music",
            items = localItems,
            onItemClick = {},
        )

        val localSection = template.sections.single()
        assertTrue(localSection is RowSection)
        val rows = items(localSection as RowSection)
        assertEquals(
            listOf("All Songs", "Folders", "Albums", "Artists"),
            rows.map { it.title.toString() },
        )
        assertTrue(rows.all { it.isBrowsable })
        assertTrue(rows.all { it.onClickDelegate != null })
    }

    private fun section(
        key: String,
        title: String,
        kind: DashboardSectionKind,
        layout: DashboardLayout,
        items: List<MediaItem>,
        moreItem: MediaItem? = null,
    ): DashboardSection = DashboardSection(
        key = key,
        title = title,
        kind = kind,
        layout = layout,
        items = items,
        moreItem = moreItem,
    )

    private fun browsableItem(
        mediaId: String,
        title: String,
        subtitle: String? = null,
        artworkUri: String? = null,
    ): MediaItem = mediaItem(
        mediaId = mediaId,
        title = title,
        subtitle = subtitle,
        artworkUri = artworkUri,
        browsable = true,
        playable = false,
    )

    private fun playableItem(
        mediaId: String,
        title: String,
        subtitle: String? = null,
        artworkUri: String? = null,
    ): MediaItem = mediaItem(
        mediaId = mediaId,
        title = title,
        subtitle = subtitle,
        artworkUri = artworkUri,
        browsable = false,
        playable = true,
    )

    private fun mediaItem(
        mediaId: String,
        title: String,
        subtitle: String?,
        artworkUri: String?,
        browsable: Boolean,
        playable: Boolean,
    ): MediaItem = MediaItem.Builder()
        .setMediaId(mediaId)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(subtitle)
                .setArtworkUri(artworkUri?.let(Uri::parse))
                .setIsBrowsable(browsable)
                .setIsPlayable(playable)
                .build(),
        )
        .build()

    private fun click(delegate: OnClickDelegate) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            delegate.sendClick(object : OnDoneCallback {})
        }
    }

    @Suppress("RestrictedApi")
    private fun <T : Item> items(section: Section<T>): List<T> =
        with(TestDelegateInvoker) {
            section.itemsDelegate.requestAllItemsForTest()
        }
}
