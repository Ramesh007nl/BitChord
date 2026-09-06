package com.music.bitchord

import android.content.ComponentName
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.playback.AndroidAutoMediaIds
import com.music.bitchord.playback.AndroidAutoRoute
import com.music.bitchord.playback.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class AndroidAutoMediaLibraryTest {
    private lateinit var mediaBrowser: MediaBrowser

    @Before
    fun setup() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val latch = CountDownLatch(1)
        var browserInstance: MediaBrowser? = null

        instrumentation.runOnMainSync {
            val future = MediaBrowser.Builder(context, token).buildAsync()
            future.addListener(
                {
                    browserInstance = future.get()
                    latch.countDown()
                },
                { command -> command.run() },
            )
        }

        assertTrue("MediaBrowser connection timed out", latch.await(10, TimeUnit.SECONDS))
        assertNotNull("MediaBrowser instance must not be null", browserInstance)
        mediaBrowser = browserInstance!!
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
    fun rootAndTopLevelChildrenUseStableTanTovIds() = runBlocking {
        val root = withContext(Dispatchers.Main) {
            mediaBrowser.getLibraryRoot(null).get(10, TimeUnit.SECONDS)
        }.value
        assertNotNull(root)
        assertEquals("tantov:auto:v1:root", root!!.mediaId)
        assertTrue(root.mediaMetadata.isBrowsable == true)

        val children = withContext(Dispatchers.Main) {
            mediaBrowser.getChildren(root.mediaId, 0, 20, null).get(10, TimeUnit.SECONDS)
        }.value
        assertNotNull(children)
        assertEquals(
            listOf("Home", "Explore", "Recently Played", "Library"),
            children!!.map { it.mediaMetadata.title.toString() },
        )
    }

    @Test
    fun getItemResolvesStableHomeRoute() = runBlocking {
        val homeId = AndroidAutoMediaIds.encode(AndroidAutoRoute.Home)
        val item = withContext(Dispatchers.Main) {
            mediaBrowser.getItem(homeId).get(10, TimeUnit.SECONDS)
        }.value

        assertNotNull(item)
        assertEquals(homeId, item!!.mediaId)
        assertTrue(item.mediaMetadata.isBrowsable == true)
    }

    @Test
    fun searchAndSearchResultReturnLibraryResultsWithoutCrashing() = runBlocking {
        val search = withContext(Dispatchers.Main) {
            mediaBrowser.search("Adele", null).get(10, TimeUnit.SECONDS)
        }
        assertNotNull(search)

        val results = withContext(Dispatchers.Main) {
            mediaBrowser.getSearchResult("Adele", 0, 10, null).get(10, TimeUnit.SECONDS)
        }
        assertNotNull(results)
    }
}
