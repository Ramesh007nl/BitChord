package com.music.bitchord

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.music.bitchord.ui.components.FrostedTopBar
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.Rule
import org.junit.Test

class TanTovTopBarBrandingTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun topBarShowsTanTovWordmarkWithoutDevBadge() {
        composeRule.setContent {
            BitChordTheme {
                FrostedTopBar(
                    title = "Listen Now",
                    scrolled = false,
                )
            }
        }

        composeRule.onNodeWithText("TanTov").assertIsDisplayed()
        composeRule.onNodeWithText("Dev").assertDoesNotExist()
    }
}
