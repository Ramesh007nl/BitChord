package com.music.bitchord

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.music.bitchord.playback.AndroidAutoBrowseHints
import com.music.bitchord.playback.AndroidAutoRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AndroidAutoBrowseHintsTest {
    @Test
    fun rootAdvertisesContentStyleSupportAndGridHints() {
        val extras = AndroidAutoBrowseHints.rootParams().extras

        assertTrue(extras.getBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED"))
        assertEquals(2, extras.getInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"))
        assertEquals(2, extras.getInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"))
        assertEquals(2, extras.getInt("android.media.extras.CONTENT_STYLE_BROWSABLE_HINT"))
        assertEquals(2, extras.getInt("android.media.extras.CONTENT_STYLE_PLAYABLE_HINT"))
    }

    @Test
    fun homeAndLibraryPreferGridButTrackListsPreferList() {
        assertEquals(
            2,
            AndroidAutoBrowseHints.childParams(AndroidAutoRoute.Home)
                .extras
                .getInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"),
        )
        assertEquals(
            2,
            AndroidAutoBrowseHints.childParams(AndroidAutoRoute.Library)
                .extras
                .getInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"),
        )
        assertEquals(
            1,
            AndroidAutoBrowseHints.childParams(AndroidAutoRoute.Recent)
                .extras
                .getInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"),
        )
    }
}
