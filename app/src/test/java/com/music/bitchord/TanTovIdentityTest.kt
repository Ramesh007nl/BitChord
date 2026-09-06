package com.music.bitchord

import org.junit.Assert.assertEquals
import org.junit.Test

class TanTovIdentityTest {
    @Test
    fun carTestBuildUsesDedicatedTanTovApplicationId() {
        assertEquals("com.tantov.music.cartest", BuildConfig.APPLICATION_ID)
    }
}
