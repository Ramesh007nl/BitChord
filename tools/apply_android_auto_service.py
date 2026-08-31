from pathlib import Path

path = Path("app/src/main/java/com/music/bitchord/playback/PlaybackService.kt")
text = path.read_text()

replacements = [
    (
        "import androidx.media3.session.MediaSession\n"
        "import androidx.media3.session.MediaSessionService\n",
        "import androidx.media3.session.MediaLibraryService\n"
        "import androidx.media3.session.MediaLibraryService.MediaLibrarySession\n"
        "import androidx.media3.session.MediaSession\n",
    ),
    (
        "class PlaybackService : MediaSessionService() {\n\n"
        "    private var mediaSession: MediaSession? = null\n",
        "class PlaybackService : MediaLibraryService() {\n\n"
        "    private var mediaSession: MediaLibrarySession? = null\n"
        "    private val androidAutoCatalog by lazy { AndroidAutoCatalog(YtMusicAndroidAutoDataSource) }\n",
    ),
    (
        "private val sessionCallback = object : MediaSession.Callback {",
        "private val sessionCallback = object : MediaLibrarySession.Callback {",
    ),
    (
        "mediaSession = MediaSession.Builder(this, SessionPlayer(exoPlayer, controller))\n"
        "            .setId(SESSION_ID)\n"
        "            .setSessionActivity(sessionActivity())\n"
        "            .setCallback(sessionCallback)\n"
        "            .build()",
        "mediaSession = MediaLibrarySession.Builder(this, SessionPlayer(exoPlayer, controller), sessionCallback)\n"
        "            .setId(SESSION_ID)\n"
        "            .setSessionActivity(sessionActivity())\n"
        "            .build()",
    ),
    (
        "override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =\n"
        "        mediaSession",
        "override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =\n"
        "        mediaSession",
    ),
]

for old, new in replacements:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one match, got {count}: {old[:90]!r}")
    text = text.replace(old, new, 1)

path.write_text(text)
print("Applied assertion-checked MediaLibrary service type conversion.")
