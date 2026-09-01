package com.music.bitchord

import org.junit.Assert.assertEquals
import org.junit.Test

class TanTovIdentityTest {
    @Test
    fun devBuildUsesPermanentTanTovApplicationId() {
        assertEquals("com.tantov.music", BuildConfig.APPLICATION_ID)
    }
}
