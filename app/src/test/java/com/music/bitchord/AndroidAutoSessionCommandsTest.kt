package com.music.bitchord

import androidx.media3.session.SessionCommand
import com.music.bitchord.playback.defaultBitChordLibraryCommands
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAutoSessionCommandsTest {
    @Test
    fun mediaLibraryDefaultsKeepAndroidAutoBrowseCommands() {
        val commands = defaultBitChordLibraryCommands()

        assertTrue(commands.contains(SessionCommand.COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT))
        assertTrue(commands.contains(SessionCommand.COMMAND_CODE_LIBRARY_GET_CHILDREN))
        assertTrue(commands.contains(SessionCommand.COMMAND_CODE_LIBRARY_GET_ITEM))
        assertTrue(commands.contains(SessionCommand.COMMAND_CODE_LIBRARY_SEARCH))
        assertTrue(commands.contains(SessionCommand.COMMAND_CODE_LIBRARY_GET_SEARCH_RESULT))
    }
}
