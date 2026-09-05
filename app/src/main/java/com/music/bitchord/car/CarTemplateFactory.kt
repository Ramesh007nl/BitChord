package com.music.bitchord.car

import androidx.annotation.DrawableRes
import androidx.car.app.CarContext
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridSection
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.RowSection
import androidx.car.app.model.Section
import androidx.car.app.model.SectionedItemTemplate
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.MediaItem
import com.music.bitchord.R

/** Destinations whose host-rendered tab icons are part of the Task D4 presentation layer. */
enum class CarDestination(
    val contentId: String,
    @DrawableRes val iconRes: Int,
) {
    HOME("home", R.drawable.ic_car_home),
    RECENTS("recents", R.drawable.ic_car_recents),
    BROWSE("browse", R.drawable.ic_car_browse),
    LIBRARY("library", R.drawable.ic_car_library),
}

/** Converts the existing Media3 catalog models into host-rendered car templates. */
class CarTemplateFactory(
    private val carContext: CarContext,
    val artworkLoader: CarArtworkLoader = CarArtworkLoader(carContext),
) {
    fun home(
        sections: List<DashboardSection>,
        errorMessage: String?,
        onItemClick: (MediaItem) -> Unit,
        onRetry: () -> Unit,
    ): SectionedItemTemplate = SectionedItemTemplate.Builder()
        .apply {
            sections.forEach { section ->
                addSection(section.toCarSection(onItemClick))
            }
            errorMessage?.takeIf(String::isNotBlank)?.let { message ->
                addSection(retrySection(message, onRetry))
            }
        }
        .build()

    fun browse(
        title: String,
        items: List<MediaItem>,
        onItemClick: (MediaItem) -> Unit,
    ): SectionedItemTemplate = SectionedItemTemplate.Builder()
        .addSection(
            RowSection.Builder()
                .setTitle(title)
                .setItems(items.map { item -> item.toRow(onItemClick) })
                .build(),
        )
        .build()

    @Suppress("DEPRECATION")
    fun message(title: String, message: String): MessageTemplate =
        MessageTemplate.Builder(message)
            .setTitle(title)
            .build()

    fun tabIcon(destination: CarDestination): CarIcon = resourceIcon(destination.iconRes)

    private fun DashboardSection.toCarSection(
        onItemClick: (MediaItem) -> Unit,
    ): Section<*> = when (layout) {
        DashboardLayout.SINGLE -> RowSection.Builder()
            .setTitle(title)
            .setItems(
                items.take(MAX_HOME_ITEMS).map { item ->
                    item.toRow(
                        onItemClick = onItemClick,
                        imageRes = R.drawable.ic_car_local_music.takeIf {
                            kind == DashboardSectionKind.LOCAL_MUSIC
                        },
                    )
                },
            )
            .build()

        DashboardLayout.ROW -> RowSection.Builder()
            .setTitle(title)
            .setItems(items.take(MAX_HOME_ITEMS).map { item -> item.toRow(onItemClick) })
            .build()

        DashboardLayout.GRID -> GridSection.Builder()
            .setTitle(title)
            .setItems(gridEntries().map { entry -> entry.toGridItem(onItemClick) })
            .build()
    }

    private fun DashboardSection.gridEntries(): List<GridEntry> {
        val shelfTarget = moreItem
        return if (items.size > MAX_HOME_ITEMS && shelfTarget != null) {
            items.take(MAX_HOME_ITEMS - 1).map { item -> GridEntry(item) } +
                GridEntry(item = shelfTarget, titleOverride = MORE_TITLE, isMore = true)
        } else {
            items.take(MAX_HOME_ITEMS).map { item -> GridEntry(item) }
        }
    }

    private fun MediaItem.toRow(
        onItemClick: (MediaItem) -> Unit,
        @DrawableRes imageRes: Int? = null,
    ): Row = Row.Builder()
        .setTitle(displayTitle())
        .apply {
            displaySubtitle()?.let { subtitle -> addText(subtitle) }
            imageRes?.let { setImage(resourceIcon(it)) }
            setBrowsable(mediaMetadata.isBrowsable == true)
            setOnClickListener { onItemClick(this@toRow) }
        }
        .build()

    private fun GridEntry.toGridItem(onItemClick: (MediaItem) -> Unit): GridItem =
        GridItem.Builder()
            .setTitle(titleOverride ?: item.displayTitle())
            .apply {
                item.displaySubtitle()?.let { subtitle -> setText(subtitle) }
            }
            .setImage(
                artworkLoader.cachedOrFallback(
                    artworkUri = item.mediaMetadata.artworkUri,
                    fallbackRes = if (isMore) R.drawable.ic_car_browse else R.drawable.ic_car_library,
                ),
            )
            .setOnClickListener { onItemClick(item) }
            .build()

    private fun retrySection(message: String, onRetry: () -> Unit): RowSection =
        RowSection.Builder()
            .setTitle(message)
            .addItem(
                Row.Builder()
                    .setTitle(RETRY_TITLE)
                    .setOnClickListener { onRetry() }
                    .build(),
            )
            .build()

    private fun MediaItem.displayTitle(): String =
        mediaMetadata.title?.toString()?.takeIf(String::isNotBlank) ?: UNTITLED

    private fun MediaItem.displaySubtitle(): String? =
        mediaMetadata.artist?.toString()?.takeIf(String::isNotBlank)
            ?: mediaMetadata.description?.toString()?.takeIf(String::isNotBlank)

    private fun resourceIcon(@DrawableRes resource: Int): CarIcon =
        CarIcon.Builder(IconCompat.createWithResource(carContext, resource)).build()

    private data class GridEntry(
        val item: MediaItem,
        val titleOverride: String? = null,
        val isMore: Boolean = false,
    )

    private companion object {
        const val MAX_HOME_ITEMS = 6
        const val MORE_TITLE = "More"
        const val RETRY_TITLE = "Retry"
        const val UNTITLED = "Untitled"
    }
}
