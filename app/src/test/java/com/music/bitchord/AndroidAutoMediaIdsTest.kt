package com.music.bitchord

import com.music.bitchord.playback.AndroidAutoCollectionKind
import com.music.bitchord.playback.AndroidAutoMediaIds
import com.music.bitchord.playback.AndroidAutoRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidAutoMediaIdsTest {
    @Test
    fun rootRoundTrips() {
        assertEquals(
            AndroidAutoRoute.Root,
            AndroidAutoMediaIds.parse(AndroidAutoMediaIds.encode(AndroidAutoRoute.Root)),
        )
    }

    @Test
    fun trackRoundTripsReservedCharacters() {
        val route = AndroidAutoRoute.Track("abc:def/ghi?x=1")
        assertEquals(route, AndroidAutoMediaIds.parse(AndroidAutoMediaIds.encode(route)))
    }

    @Test
    fun collectionRoundTripsBrowseId() {
        val route = AndroidAutoRoute.Collection(AndroidAutoCollectionKind.PLAYLIST, "VLPL-123:abc")
        assertEquals(route, AndroidAutoMediaIds.parse(AndroidAutoMediaIds.encode(route)))
    }

    @Test
    fun shelfRoundTripsTitleAndOrdinal() {
        val route = AndroidAutoRoute.Shelf(
            source = AndroidAutoRoute.Shelf.Source.HOME,
            ordinal = 2,
            title = "Listen again / தமிழ்",
        )
        assertEquals(route, AndroidAutoMediaIds.parse(AndroidAutoMediaIds.encode(route)))
    }

    @Test
    fun malformedOrForeignIdsAreRejected() {
        assertNull(AndroidAutoMediaIds.parse("spotify:track:123"))
        assertNull(AndroidAutoMediaIds.parse("bitchord:auto:v1:track:"))
        assertNull(AndroidAutoMediaIds.parse("tantov:auto:v1:track:"))
        assertNull(AndroidAutoMediaIds.parse("bitchord:auto:v1:shelf:home:not-a-number:YWJj"))
    }
}
