from pathlib import Path

path = Path("app/src/main/java/com/music/bitchord/playback/PlaybackService.kt")
text = path.read_text()

replacements = [
    (
        "import androidx.media3.session.MediaLibraryService\n"
        "import androidx.media3.session.MediaLibraryService.MediaLibrarySession\n"
        "import androidx.media3.session.MediaSession\n",
        "import androidx.media3.session.LibraryResult\n"
        "import androidx.media3.session.MediaLibraryService\n"
        "import androidx.media3.session.MediaLibraryService.LibraryParams\n"
        "import androidx.media3.session.MediaLibraryService.MediaLibrarySession\n"
        "import androidx.media3.session.MediaSession\n"
        "import com.google.common.collect.ImmutableList\n",
    ),
    (
        "import kotlinx.coroutines.flow.drop\n"
        "import kotlinx.coroutines.isActive\n",
        "import kotlinx.coroutines.flow.drop\n"
        "import kotlinx.coroutines.guava.future\n"
        "import kotlinx.coroutines.isActive\n",
    ),
    (
        "            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))\n"
        "        }\n"
        "    }\n\n"
        "    /**\n"
        "     * Everything the service books against the player it is currently on.\n",
        "            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))\n"
        "        }\n\n"
        "        override fun onGetLibraryRoot(\n"
        "            session: MediaLibrarySession,\n"
        "            browser: MediaSession.ControllerInfo,\n"
        "            params: LibraryParams?,\n"
        "        ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {\n"
        "            LibraryResult.ofItem(androidAutoCatalog.root(), params)\n"
        "        }\n\n"
        "        override fun onGetChildren(\n"
        "            session: MediaLibrarySession,\n"
        "            browser: MediaSession.ControllerInfo,\n"
        "            parentId: String,\n"
        "            page: Int,\n"
        "            pageSize: Int,\n"
        "            params: LibraryParams?,\n"
        "        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {\n"
        "            val route = AndroidAutoMediaIds.parse(parentId)\n"
        "                ?: return@future LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)\n"
        "            androidAutoCatalog.children(route, page, pageSize).fold(\n"
        "                onSuccess = { LibraryResult.ofItemList(it, params) },\n"
        "                onFailure = { LibraryResult.ofError(SessionError.ERROR_IO) },\n"
        "            )\n"
        "        }\n\n"
        "        override fun onGetItem(\n"
        "            session: MediaLibrarySession,\n"
        "            browser: MediaSession.ControllerInfo,\n"
        "            mediaId: String,\n"
        "        ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {\n"
        "            val route = AndroidAutoMediaIds.parse(mediaId)\n"
        "                ?: return@future LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)\n"
        "            androidAutoCatalog.item(route).fold(\n"
        "                onSuccess = { LibraryResult.ofItem(it, null) },\n"
        "                onFailure = { LibraryResult.ofError(SessionError.ERROR_IO) },\n"
        "            )\n"
        "        }\n\n"
        "        override fun onAddMediaItems(\n"
        "            mediaSession: MediaSession,\n"
        "            controller: MediaSession.ControllerInfo,\n"
        "            mediaItems: List<MediaItem>,\n"
        "        ): ListenableFuture<List<MediaItem>> = scope.future {\n"
        "            mediaItems.mapNotNull { incoming ->\n"
        "                when (AndroidAutoMediaIds.parse(incoming.mediaId)) {\n"
        "                    is AndroidAutoRoute.Track -> androidAutoCatalog.playableTrack(incoming).getOrNull()\n"
        "                    null -> incoming.takeIf { it.localConfiguration != null }\n"
        "                    else -> null\n"
        "                }\n"
        "            }\n"
        "        }\n"
        "    }\n\n"
        "    /**\n"
        "     * Everything the service books against the player it is currently on.\n",
    ),
]

for old, new in replacements:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one match, got {count}: {old[:120]!r}")
    text = text.replace(old, new, 1)

path.write_text(text)
print("Applied assertion-checked MediaLibrary browse callbacks.")
