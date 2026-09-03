package com.music.bitchord

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.music.bitchord.car.AndroidAutoDashboardSource
import com.music.bitchord.car.DashboardBrowseGateway
import com.music.bitchord.playback.AndroidAutoCollectionKind
import com.music.bitchord.playback.AndroidAutoMediaIds
import com.music.bitchord.playback.AndroidAutoRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class AndroidAutoDashboardSourceTest {
    @Test
    fun localSectionRequestsOnlyLocalMusicAndNotHomeOrRecent() = runTest {
        val localMusic = browseItem(
            AndroidAutoMediaIds.encode(AndroidAutoRoute.LocalMusic),
            "Local Music",
        )
        val gateway = FakeDashboardBrowseGateway().apply {
            items[AndroidAutoRoute.LocalMusic] = Result.success(localMusic)
        }
        val source = AndroidAutoDashboardSource(gateway, onlineTimeoutMs = 100)

        val result = source.localSection().getOrThrow()

        assertEquals(localMusic, result)
        assertEquals(listOf(AndroidAutoRoute.LocalMusic), gateway.itemRequests)
        assertTrue(gateway.childrenRequests.isEmpty())
        assertFalse(gateway.itemRequests.contains(AndroidAutoRoute.Home))
        assertFalse(gateway.itemRequests.contains(AndroidAutoRoute.Recent))
    }

    @Test
    fun homeShelfRowsExpandViaOnlyTheirEmittedShelfRoutes() = runTest {
        val emittedShelf = AndroidAutoRoute.Shelf(
            source = AndroidAutoRoute.Shelf.Source.HOME,
            ordinal = 7,
            title = "Deep Focus / தமிழ்",
        )
        val notEmittedShelf = AndroidAutoRoute.Shelf(
            source = AndroidAutoRoute.Shelf.Source.HOME,
            ordinal = 0,
            title = "Deep Focus / தமிழ்",
        )
        val nonShelf = AndroidAutoRoute.Collection(
            kind = AndroidAutoCollectionKind.PLAYLIST,
            browseId = "VLPL-not-a-home-shelf",
        )
        val shelfTrack = playableItem("shelf-track", "Focused Track")
        val gateway = FakeDashboardBrowseGateway().apply {
            children[AndroidAutoRoute.Recent] = { Result.success(emptyList()) }
            children[AndroidAutoRoute.Home] = {
                Result.success(
                    listOf(
                        browseItem(
                            mediaId = AndroidAutoMediaIds.encode(emittedShelf),
                            title = emittedShelf.title,
                            subtitle = "For quiet work",
                        ),
                        browseItem(
                            mediaId = AndroidAutoMediaIds.encode(nonShelf),
                            title = "Not a shelf",
                        ),
                        browseItem(mediaId = "foreign:malformed", title = "Unknown route"),
                    ),
                )
            }
            children[emittedShelf] = { Result.success(listOf(shelfTrack)) }
            children[notEmittedShelf] = {
                Result.success(listOf(playableItem("phantom-track", "Must not appear")))
            }
        }
        val source = AndroidAutoDashboardSource(gateway, onlineTimeoutMs = 100)

        val result = source.loadHomeOnline()

        assertEquals(1, result.homeShelves.size)
        assertEquals("Deep Focus / தமிழ்", result.homeShelves.single().title)
        assertEquals("For quiet work", result.homeShelves.single().subtitle)
        assertEquals(listOf(shelfTrack), result.homeShelves.single().items)
        assertEquals(
            setOf(AndroidAutoRoute.Recent, AndroidAutoRoute.Home, emittedShelf),
            gateway.childrenRequests.toSet(),
        )
        assertEquals(3, gateway.childrenRequests.size)
        assertFalse(gateway.childrenRequests.contains(notEmittedShelf))
        assertFalse(gateway.childrenRequests.contains(nonShelf))
    }

    @Test
    fun perRequestTimeoutIsBoundedWithoutAffectingIndependentlyBuiltLocal() = runTest {
        val localMusic = browseItem(
            AndroidAutoMediaIds.encode(AndroidAutoRoute.LocalMusic),
            "Local Music",
        )
        val gateway = FakeDashboardBrowseGateway().apply {
            items[AndroidAutoRoute.LocalMusic] = Result.success(localMusic)
            children[AndroidAutoRoute.Recent] = { awaitCancellation() }
            children[AndroidAutoRoute.Home] = { Result.success(emptyList()) }
        }
        val source = AndroidAutoDashboardSource(gateway, onlineTimeoutMs = 100)

        val local = source.localSection().getOrThrow()
        val online = source.loadHomeOnline()

        assertEquals(localMusic, local)
        assertEquals(100L, testScheduler.currentTime)
        assertTrue(online.recents.isEmpty())
        assertTrue(online.homeShelves.isEmpty())
        assertNotNull(online.errorMessage)
        assertTrue(online.errorMessage!!.isNotBlank())
        assertEquals(listOf(AndroidAutoRoute.LocalMusic), gateway.itemRequests)
    }

    @Test
    fun historyFailurePreservesSuccessfulHomeShelves() = runTest {
        val shelfRoute = AndroidAutoRoute.Shelf(
            source = AndroidAutoRoute.Shelf.Source.HOME,
            ordinal = 2,
            title = "Quick Picks",
        )
        val quickPick = playableItem("quick-pick", "Quick Pick")
        val gateway = FakeDashboardBrowseGateway().apply {
            children[AndroidAutoRoute.Recent] = {
                Result.failure(IOException("history unavailable"))
            }
            children[AndroidAutoRoute.Home] = {
                Result.success(
                    listOf(
                        browseItem(
                            mediaId = AndroidAutoMediaIds.encode(shelfRoute),
                            title = "Quick Picks",
                        ),
                    ),
                )
            }
            children[shelfRoute] = { Result.success(listOf(quickPick)) }
        }
        val source = AndroidAutoDashboardSource(gateway, onlineTimeoutMs = 100)

        val result = source.loadHomeOnline()

        assertTrue(result.recents.isEmpty())
        assertEquals(listOf(quickPick), result.homeShelves.single().items)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.isNotBlank())
    }

    @Test
    fun signedOutEmptyHistoryStillReturnsHomeShelves() = runTest {
        val shelfRoute = AndroidAutoRoute.Shelf(
            source = AndroidAutoRoute.Shelf.Source.HOME,
            ordinal = 0,
            title = "Fresh Finds",
        )
        val freshFind = playableItem("fresh-find", "Fresh Find")
        val gateway = FakeDashboardBrowseGateway().apply {
            children[AndroidAutoRoute.Recent] = { Result.success(emptyList()) }
            children[AndroidAutoRoute.Home] = {
                Result.success(
                    listOf(
                        browseItem(
                            mediaId = AndroidAutoMediaIds.encode(shelfRoute),
                            title = "Fresh Finds",
                        ),
                    ),
                )
            }
            children[shelfRoute] = { Result.success(listOf(freshFind)) }
        }
        val source = AndroidAutoDashboardSource(gateway, onlineTimeoutMs = 100)

        val result = source.loadHomeOnline()

        assertTrue(result.recents.isEmpty())
        assertEquals(listOf(freshFind), result.homeShelves.single().items)
        assertEquals(null, result.errorMessage)
    }

    @Test
    fun cancellationFailureResultIsRethrownRatherThanConvertedToError() = runTest {
        val cancellation = CancellationException("screen destroyed")
        val gateway = FakeDashboardBrowseGateway().apply {
            children[AndroidAutoRoute.Recent] = { Result.failure(cancellation) }
            children[AndroidAutoRoute.Home] = { Result.success(emptyList()) }
        }
        val source = AndroidAutoDashboardSource(gateway, onlineTimeoutMs = 100)

        try {
            source.loadHomeOnline()
            fail("Expected embedded cancellation to be rethrown")
        } catch (actual: CancellationException) {
            assertEquals(cancellation, actual)
        }
    }

    @Test
    fun thrownCancellationIsRethrownRatherThanConvertedToError() = runTest {
        val cancellation = CancellationException("screen destroyed")
        val gateway = FakeDashboardBrowseGateway().apply {
            children[AndroidAutoRoute.Recent] = { throw cancellation }
            children[AndroidAutoRoute.Home] = { Result.success(emptyList()) }
        }
        val source = AndroidAutoDashboardSource(gateway, onlineTimeoutMs = 100)

        try {
            source.loadHomeOnline()
            fail("Expected thrown cancellation to be rethrown")
        } catch (actual: CancellationException) {
            assertEquals(cancellation, actual)
        }
    }

    private class FakeDashboardBrowseGateway : DashboardBrowseGateway {
        val items = mutableMapOf<AndroidAutoRoute, Result<MediaItem>>()
        val children = mutableMapOf<AndroidAutoRoute, suspend () -> Result<List<MediaItem>>>()
        val itemRequests = mutableListOf<AndroidAutoRoute>()
        val childrenRequests = mutableListOf<AndroidAutoRoute>()

        override suspend fun item(route: AndroidAutoRoute): Result<MediaItem> {
            itemRequests += route
            return items[route] ?: Result.success(
                browseItem(AndroidAutoMediaIds.encode(route), route.toString()),
            )
        }

        override suspend fun children(route: AndroidAutoRoute): Result<List<MediaItem>> {
            childrenRequests += route
            return children[route]?.invoke() ?: Result.success(emptyList())
        }
    }

    private companion object {
        fun browseItem(
            mediaId: String,
            title: String,
            subtitle: String? = null,
        ): MediaItem = mediaItem(
            mediaId = mediaId,
            title = title,
            subtitle = subtitle,
            browsable = true,
            playable = false,
        )

        fun playableItem(mediaId: String, title: String): MediaItem = mediaItem(
            mediaId = mediaId,
            title = title,
            subtitle = null,
            browsable = false,
            playable = true,
        )

        fun mediaItem(
            mediaId: String,
            title: String,
            subtitle: String?,
            browsable: Boolean,
            playable: Boolean,
        ): MediaItem = MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setDescription(subtitle)
                    .setIsBrowsable(browsable)
                    .setIsPlayable(playable)
                    .build(),
            )
            .build()
    }
}
