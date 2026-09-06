package com.music.bitchord

import android.content.ComponentName
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import com.music.bitchord.playback.AndroidAutoMediaIds
import com.music.bitchord.playback.AndroidAutoRoute
import com.music.bitchord.playback.PlaybackService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class AndroidAutoMediaLibraryTest {
    private lateinit var mediaBrowser: MediaBrowser

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))

        mediaBrowser = awaitOnMain {
            MediaBrowser.Builder(context, token).buildAsync()
        }
        assertNotNull("MediaBrowser instance must not be null", mediaBrowser)
    }

    @After
    fun tearDown() {
        if (::mediaBrowser.isInitialized) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                mediaBrowser.release()
            }
        }
    }

    @Test
    fun rootAndTopLevelChildrenUseStableTanTovIds() {
        val root = awaitOnMain {
            mediaBrowser.getLibraryRoot(null)
        }.value
        assertNotNull(root)
        assertEquals("tantov:auto:v1:root", root!!.mediaId)
        assertTrue(root.mediaMetadata.isBrowsable == true)

        val children = awaitOnMain {
            mediaBrowser.getChildren(root.mediaId, 0, 20, null)
        }.value
        assertNotNull(children)
        assertEquals(
            listOf("Home", "Recents", "Browse", "Library"),
            children!!.map { it.mediaMetadata.title.toString() },
        )
    }

    @Test
    fun getItemResolvesStableHomeRoute() {
        val homeId = AndroidAutoMediaIds.encode(AndroidAutoRoute.Home)
        val item = awaitOnMain {
            mediaBrowser.getItem(homeId)
        }.value

        assertNotNull(item)
        assertEquals(homeId, item!!.mediaId)
        assertTrue(item.mediaMetadata.isBrowsable == true)
    }

    @Test
    fun searchAndSearchResultReturnLibraryResultsWithoutCrashing() {
        val search = awaitOnMain {
            mediaBrowser.search("Adele", null)
        }
        assertNotNull(search)

        val results = awaitOnMain {
            mediaBrowser.getSearchResult("Adele", 0, 10, null)
        }
        assertNotNull(results)
    }

    /**
     * Media3 browser methods must be invoked from the browser's application looper (main),
     * but waiting on the returned future on that same looper deadlocks result delivery.
     */
    private fun <T> awaitOnMain(call: () -> ListenableFuture<T>): T {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var future: ListenableFuture<T>
        instrumentation.runOnMainSync {
            future = call()
        }
        return future.get(20, TimeUnit.SECONDS)
    }
}
