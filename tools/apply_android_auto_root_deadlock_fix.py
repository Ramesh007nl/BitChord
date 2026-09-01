from pathlib import Path

catalog_path = Path("app/src/main/java/com/music/bitchord/playback/AndroidAutoCatalog.kt")
catalog = catalog_path.read_text()
old_root = '    suspend fun root(): MediaItem = browsable(AndroidAutoRoute.Root, "BitChord")'
new_root = '    fun root(): MediaItem = browsable(AndroidAutoRoute.Root, "BitChord")'
if catalog.count(old_root) != 1:
    raise SystemExit(f"Expected exactly one suspend root declaration, got {catalog.count(old_root)}")
catalog_path.write_text(catalog.replace(old_root, new_root, 1))

service_path = Path("app/src/main/java/com/music/bitchord/playback/PlaybackService.kt")
service = service_path.read_text()
old_callback = '''        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
            LibraryResult.ofItem(androidAutoCatalog.root(), params)
        }
'''
new_callback = '''        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(
            LibraryResult.ofItem(androidAutoCatalog.root(), params),
        )
'''
if service.count(old_callback) != 1:
    raise SystemExit(f"Expected exactly one async root callback, got {service.count(old_callback)}")
service_path.write_text(service.replace(old_callback, new_callback, 1))

print("Made legacy MediaBrowser root handshake synchronous/immediate.")
