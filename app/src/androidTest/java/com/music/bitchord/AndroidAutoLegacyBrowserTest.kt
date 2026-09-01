package com.music.bitchord

import android.content.ComponentName
import android.media.browse.MediaBrowser
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.playback.PlaybackService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AndroidAutoLegacyBrowserTest {
    private var browser: MediaBrowser? = null

    @After
    fun tearDown() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            runCatching { browser?.disconnect() }
            browser = null
        }
    }

    @Test
    fun legacyBrowserCanLoadRootChildren() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val done = CountDownLatch(1)
        var failure: String? = null
        var rootId: String? = null
        var childTitles: List<String>? = null

        instrumentation.runOnMainSync {
            val connectionCallback = object : MediaBrowser.ConnectionCallback() {
                override fun onConnected() {
                    val connectedBrowser = browser ?: run {
                        failure = "Browser connected but instance was missing"
                        done.countDown()
                        return
                    }
                    rootId = connectedBrowser.root
                    connectedBrowser.subscribe(
                        connectedBrowser.root,
                        object : MediaBrowser.SubscriptionCallback() {
                            override fun onChildrenLoaded(
                                parentId: String,
                                children: MutableList<MediaBrowser.MediaItem>,
                            ) {
                                childTitles = children.map { it.description.title?.toString().orEmpty() }
                                done.countDown()
                            }

                            override fun onChildrenLoaded(
                                parentId: String,
                                children: MutableList<MediaBrowser.MediaItem>,
                                options: Bundle,
                            ) = onChildrenLoaded(parentId, children)

                            override fun onError(parentId: String) {
                                failure = "Legacy browser onError for parent=$parentId"
                                done.countDown()
                            }

                            override fun onError(parentId: String, options: Bundle) = onError(parentId)
                        },
                    )
                }

                override fun onConnectionFailed() {
                    failure = "Legacy browser connection failed"
                    done.countDown()
                }

                override fun onConnectionSuspended() {
                    failure = "Legacy browser connection suspended"
                    done.countDown()
                }
            }

            browser = MediaBrowser(
                context,
                ComponentName(context, PlaybackService::class.java),
                connectionCallback,
                null,
            ).also { it.connect() }
        }

        if (!done.await(20, TimeUnit.SECONDS)) {
            fail("Timed out waiting for legacy MediaBrowser root/children")
        }
        failure?.let(::fail)
        assertNotNull("Expected a non-null legacy root id", rootId)
        assertEquals("bitchord:auto:v1:root", rootId)
        assertEquals(
            listOf("Home", "Explore", "Recently Played", "Library"),
            childTitles,
        )
    }
}
