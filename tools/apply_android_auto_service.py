from pathlib import Path

catalog_path = Path("app/src/main/java/com/music/bitchord/playback/AndroidAutoCatalog.kt")
catalog = catalog_path.read_text()

catalog_old = (
    "        all.page(page, pageSize)\n"
    "    }\n\n"
    "    fun clearAuthenticatedCache() {\n"
)
catalog_new = (
    "        all.page(page, pageSize)\n"
    "    }\n\n"
    "    /** Resolve a voice/Assistant search into BitChord's normal playable queue item. */\n"
    "    suspend fun playableSearchResult(query: String): Result<MediaItem> = runCatching {\n"
    "        val row = search(query, 0, 20).getOrThrow()\n"
    "            .firstOrNull { it.mediaMetadata.isPlayable == true }\n"
    "            ?: error(\"No playable Android Auto search result\")\n"
    "        playableTrack(row).getOrThrow()\n"
    "    }\n\n"
    "    fun clearAuthenticatedCache() {\n"
)
if catalog.count(catalog_old) != 1:
    raise SystemExit(f"Expected one catalog insertion point, got {catalog.count(catalog_old)}")
catalog = catalog.replace(catalog_old, catalog_new, 1)
catalog_path.write_text(catalog)

service_path = Path("app/src/main/java/com/music/bitchord/playback/PlaybackService.kt")
service = service_path.read_text()

service_old = (
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
)
service_new = (
    "        override fun onSearch(\n"
    "            session: MediaLibrarySession,\n"
    "            browser: MediaSession.ControllerInfo,\n"
    "            query: String,\n"
    "            params: LibraryParams?,\n"
    "        ): ListenableFuture<LibraryResult<Void>> = scope.future {\n"
    "            androidAutoCatalog.search(query, 0, Int.MAX_VALUE).fold(\n"
    "                onSuccess = { results ->\n"
    "                    session.notifySearchResultChanged(browser, query, results.size, params)\n"
    "                    LibraryResult.ofVoid(params)\n"
    "                },\n"
    "                onFailure = { LibraryResult.ofError(SessionError.ERROR_IO) },\n"
    "            )\n"
    "        }\n\n"
    "        override fun onGetSearchResult(\n"
    "            session: MediaLibrarySession,\n"
    "            browser: MediaSession.ControllerInfo,\n"
    "            query: String,\n"
    "            page: Int,\n"
    "            pageSize: Int,\n"
    "            params: LibraryParams?,\n"
    "        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {\n"
    "            androidAutoCatalog.search(query, page, pageSize).fold(\n"
    "                onSuccess = { LibraryResult.ofItemList(it, params) },\n"
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
    "                    null -> incoming.requestMetadata.searchQuery\n"
    "                        ?.takeIf { it.isNotBlank() }\n"
    "                        ?.let { androidAutoCatalog.playableSearchResult(it).getOrNull() }\n"
    "                        ?: incoming.takeIf { it.localConfiguration != null }\n"
    "                    else -> null\n"
    "                }\n"
    "            }\n"
    "        }\n"
)
if service.count(service_old) != 1:
    raise SystemExit(f"Expected one service search block, got {service.count(service_old)}")
service = service.replace(service_old, service_new, 1)
service_path.write_text(service)

print("Applied assertion-checked Android Auto search and voice playback wiring.")
