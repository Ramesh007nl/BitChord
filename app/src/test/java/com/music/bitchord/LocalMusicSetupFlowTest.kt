package com.music.bitchord

import com.music.bitchord.data.local.LocalMusicSetupChoice
import com.music.bitchord.data.local.requestForSetupChoice
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMusicSetupFlowTest {
    @Test
    fun allMusicRequestsOnlyAudioAccess() {
        val request = requestForSetupChoice(LocalMusicSetupChoice.ALL_MUSIC)
        assertTrue(request.enableAllMusic)
        assertFalse(request.pickFolder)
    }

    @Test
    fun chooseFoldersRequestsOnlyFolderPicker() {
        val request = requestForSetupChoice(LocalMusicSetupChoice.CHOOSE_FOLDERS)
        assertFalse(request.enableAllMusic)
        assertTrue(request.pickFolder)
    }

    @Test
    fun useBothRequestsAudioAndFolderAccess() {
        val request = requestForSetupChoice(LocalMusicSetupChoice.BOTH)
        assertTrue(request.enableAllMusic)
        assertTrue(request.pickFolder)
    }

    @Test
    fun notNowRequestsNoAccess() {
        val request = requestForSetupChoice(LocalMusicSetupChoice.NOT_NOW)
        assertFalse(request.enableAllMusic)
        assertFalse(request.pickFolder)
    }
}
