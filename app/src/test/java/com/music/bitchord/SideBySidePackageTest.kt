package com.music.bitchord

import org.junit.Assert.assertEquals
import org.junit.Test

class SideBySidePackageTest {
    @Test
    fun devBuildUsesSeparateCarTestApplicationId() {
        assertEquals("com.tantov.music.cartest", BuildConfig.APPLICATION_ID)
    }
}
