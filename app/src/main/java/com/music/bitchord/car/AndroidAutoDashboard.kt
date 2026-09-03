package com.music.bitchord.car

import androidx.media3.common.MediaItem
import java.util.Locale

enum class DashboardSectionKind {
    LOCAL_MUSIC,
    RECENTLY_PLAYED,
    QUICK_PICKS,
    LISTEN_AGAIN,
    OTHER,
}

enum class DashboardLayout {
    SINGLE,
    ROW,
    GRID,
}

data class DashboardShelf(
    val title: String,
    val subtitle: String,
    val items: List<MediaItem>,
)

data class DashboardSection(
    val key: String,
    val title: String,
    val kind: DashboardSectionKind,
    val layout: DashboardLayout,
    val items: List<MediaItem>,
)

object AndroidAutoDashboard {
    fun order(
        localMusic: MediaItem,
        recents: List<MediaItem>,
        homeShelves: List<DashboardShelf>,
    ): List<DashboardSection> {
        val uniqueShelves = homeShelves.distinctBy { it.title.dashboardKey() }
        val shelvesByKey = uniqueShelves.associateBy { it.title.dashboardKey() }

        return buildList {
            add(
                DashboardSection(
                    key = "local-music",
                    title = localMusic.mediaMetadata.title?.toString()
                        ?.takeIf(String::isNotBlank)
                        ?: "Local Music",
                    kind = DashboardSectionKind.LOCAL_MUSIC,
                    layout = DashboardLayout.SINGLE,
                    items = listOf(localMusic),
                ),
            )

            if (recents.isNotEmpty()) {
                add(
                    DashboardSection(
                        key = "recently-played",
                        title = "Recently Played",
                        kind = DashboardSectionKind.RECENTLY_PLAYED,
                        layout = DashboardLayout.GRID,
                        items = recents,
                    ),
                )
            }

            shelvesByKey[QUICK_PICKS_KEY]?.let { shelf ->
                add(shelf.toSection("quick-picks", DashboardSectionKind.QUICK_PICKS))
            }
            shelvesByKey[LISTEN_AGAIN_KEY]?.let { shelf ->
                add(shelf.toSection("listen-again", DashboardSectionKind.LISTEN_AGAIN))
            }

            uniqueShelves
                .filterNot { it.title.dashboardKey() in PREFERRED_KEYS }
                .forEach { shelf ->
                    add(
                        shelf.toSection(
                            key = "other:${shelf.title.dashboardKey()}",
                            kind = DashboardSectionKind.OTHER,
                        ),
                    )
                }
        }
    }

    private fun DashboardShelf.toSection(
        key: String,
        kind: DashboardSectionKind,
    ): DashboardSection = DashboardSection(
        key = key,
        title = title,
        kind = kind,
        layout = DashboardLayout.GRID,
        items = items,
    )

    private const val QUICK_PICKS_KEY = "quickpicks"
    private const val LISTEN_AGAIN_KEY = "listenagain"
    private val PREFERRED_KEYS = setOf(QUICK_PICKS_KEY, LISTEN_AGAIN_KEY)
}

private fun String.dashboardKey(): String =
    lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)
