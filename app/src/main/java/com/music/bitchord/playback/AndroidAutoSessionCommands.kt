package com.music.bitchord.playback

import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommands

/**
 * Base session commands for BitChord's MediaLibrarySession.
 *
 * Using DEFAULT_SESSION_COMMANDS here removes the library commands Android Auto needs to call
 * getLibraryRoot/getChildren/search, which leaves the car UI connected but spinning forever.
 */
internal fun defaultBitChordLibraryCommands(): SessionCommands =
    MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
