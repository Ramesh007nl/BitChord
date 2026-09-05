package com.music.bitchord.car

import androidx.media3.common.MediaItem
import com.music.bitchord.playback.AndroidAutoCatalog
import com.music.bitchord.playback.AndroidAutoMediaIds
import com.music.bitchord.playback.AndroidAutoRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

interface DashboardBrowseGateway {
    suspend fun item(route: AndroidAutoRoute): Result<MediaItem>
    suspend fun children(route: AndroidAutoRoute): Result<List<MediaItem>>
}

class CatalogDashboardBrowseGateway(
    private val catalog: AndroidAutoCatalog,
) : DashboardBrowseGateway {
    override suspend fun item(route: AndroidAutoRoute): Result<MediaItem> = catalog.item(route)

    override suspend fun children(route: AndroidAutoRoute): Result<List<MediaItem>> =
        catalog.children(route, page = 0, pageSize = Int.MAX_VALUE)
}

data class DashboardOnlineResult(
    val recents: List<MediaItem>,
    val homeShelves: List<DashboardShelf>,
    val errorMessage: String?,
)

class AndroidAutoDashboardSource(
    private val gateway: DashboardBrowseGateway,
    private val onlineTimeoutMs: Long = DEFAULT_ONLINE_TIMEOUT_MS,
) {
    suspend fun localSection(): Result<MediaItem> = resultOf {
        gateway.item(AndroidAutoRoute.LocalMusic).valueOrThrowCancellation()
    }

    suspend fun loadHomeOnline(): DashboardOnlineResult = coroutineScope {
        val recents = async { loadRecents() }
        val home = async { loadHomeShelves() }

        val recentResult = recents.await()
        val homeResult = home.await()
        DashboardOnlineResult(
            recents = recentResult.getOrDefault(emptyList()),
            homeShelves = homeResult.shelves,
            errorMessage = ERROR_MESSAGE.takeIf {
                recentResult.isFailure || homeResult.hadFailure
            },
        )
    }

    suspend fun loadRecents(): Result<List<MediaItem>> =
        boundedChildren(AndroidAutoRoute.Recent)

    suspend fun loadBrowse(): Result<List<MediaItem>> =
        boundedChildren(AndroidAutoRoute.Explore)

    suspend fun loadLibrary(): Result<List<MediaItem>> =
        boundedChildren(AndroidAutoRoute.Library)

    private suspend fun boundedChildren(route: AndroidAutoRoute): Result<List<MediaItem>> = try {
        val children = withTimeoutOrNull(onlineTimeoutMs) {
            gateway.children(route).valueOrThrowCancellation()
        }
        if (children == null) {
            Result.failure(IllegalStateException(TIMEOUT_MESSAGE))
        } else {
            Result.success(children)
        }
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: Throwable) {
        Result.failure(failure)
    }

    private suspend fun loadHomeShelves(): HomeLoadResult {
        val shelves = mutableListOf<DashboardShelf>()
        var hadFailure = false

        val completed = try {
            withTimeoutOrNull(onlineTimeoutMs) {
                val homeRows = gateway.children(AndroidAutoRoute.Home)
                    .valueOrThrowCancellation()

                homeRows.forEach { row ->
                    val route = AndroidAutoMediaIds.parse(row.mediaId)
                        as? AndroidAutoRoute.Shelf
                    if (route?.source != AndroidAutoRoute.Shelf.Source.HOME) return@forEach

                    val items = try {
                        gateway.children(route).valueOrThrowCancellation()
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (_: Throwable) {
                        hadFailure = true
                        return@forEach
                    }

                    shelves += DashboardShelf(
                        title = row.mediaMetadata.title?.toString() ?: route.title,
                        subtitle = row.mediaMetadata.description?.toString().orEmpty(),
                        items = items,
                        moreItem = row,
                    )
                }
                true
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Throwable) {
            hadFailure = true
            true
        }
        if (completed == null) hadFailure = true

        return HomeLoadResult(shelves, hadFailure)
    }

    private fun <T> Result<T>.valueOrThrowCancellation(): T {
        val failure = exceptionOrNull()
        if (failure is CancellationException) throw failure
        return getOrThrow()
    }

    private suspend fun <T> resultOf(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: Throwable) {
        Result.failure(failure)
    }

    private data class HomeLoadResult(
        val shelves: List<DashboardShelf>,
        val hadFailure: Boolean,
    )

    private companion object {
        const val DEFAULT_ONLINE_TIMEOUT_MS = 8_000L
        const val ERROR_MESSAGE = "Some online content is unavailable."
        const val TIMEOUT_MESSAGE = "Online request timed out."
    }
}
