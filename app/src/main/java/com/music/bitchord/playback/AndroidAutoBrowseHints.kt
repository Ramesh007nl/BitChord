package com.music.bitchord.playback

import android.os.Bundle
import androidx.media3.session.MediaLibraryService

/** Host hints for Android Auto/Automotive media browsers; TanTov templates remain authoritative. */
internal object AndroidAutoBrowseHints {
    private const val LIST = 1
    private const val GRID = 2

    private const val SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
    private const val BROWSABLE = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
    private const val PLAYABLE = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
    private const val BROWSABLE_LEGACY = "android.media.extras.CONTENT_STYLE_BROWSABLE_HINT"
    private const val PLAYABLE_LEGACY = "android.media.extras.CONTENT_STYLE_PLAYABLE_HINT"

    fun rootParams(): MediaLibraryService.LibraryParams =
        MediaLibraryService.LibraryParams.Builder()
            .setExtras(styleExtras(GRID, GRID, supported = true))
            .build()

    fun childParams(parent: AndroidAutoRoute): MediaLibraryService.LibraryParams =
        MediaLibraryService.LibraryParams.Builder()
            .setExtras(
                when (parent) {
                    AndroidAutoRoute.Home,
                    AndroidAutoRoute.Explore,
                    AndroidAutoRoute.Library,
                    -> styleExtras(GRID, GRID)

                    else -> styleExtras(LIST, LIST)
                },
            )
            .build()

    private fun styleExtras(
        browsable: Int,
        playable: Int,
        supported: Boolean = false,
    ): Bundle = Bundle().apply {
        if (supported) putBoolean(SUPPORTED, true)
        putInt(BROWSABLE, browsable)
        putInt(PLAYABLE, playable)
        putInt(BROWSABLE_LEGACY, browsable)
        putInt(PLAYABLE_LEGACY, playable)
    }
}
