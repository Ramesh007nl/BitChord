package com.music.bitchord

import com.music.bitchord.data.local.LocalMusicAccessConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMusicAccessTest {
    @Test
    fun aAndBMayBeEnabledTogether() {
        val config = LocalMusicAccessConfig(
            setupSeen = true,
            allMusicEnabled = true,
            treeUris = setOf("content://tree/music", "content://tree/tamil"),
        )
        assertTrue(config.allMusicEnabled)
        assertEquals(2, config.treeUris.size)
    }

    @Test
    fun removingFolderDoesNotDisableAllMusic() {
        val config = LocalMusicAccessConfig(true, true, setOf("one", "two")).removeTree("one")
        assertTrue(config.allMusicEnabled)
        assertEquals(setOf("two"), config.treeUris)
    }

    @Test
    fun skipMarksSetupSeenWithoutGrantingAnything() {
        val config = LocalMusicAccessConfig().markSetupSeen()
        assertTrue(config.setupSeen)
        assertFalse(config.allMusicEnabled)
        assertTrue(config.treeUris.isEmpty())
    }
}
