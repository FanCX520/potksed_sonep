package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunchesWithDualThemesAndProductivityWidgets() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("PenOS 词典笔桌面", context.getString(R.string.app_name))

        composeRule.waitForIdle()
        // 1. Desktop renders CarPlay dock, widget deck, quick scan & weather
        composeRule.onNodeWithTag("carplay_left_dock").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop_widget_deck").assertIsDisplayed()
        composeRule.onNodeWithTag("widget_quick_scan").assertIsDisplayed()
        composeRule.onNodeWithTag("widget_weather").assertIsDisplayed()

        // 2. Toggle quick theme to Apple White Gallery Vitrine and open Weather overlay
        composeRule.onNodeWithTag("dock_theme_toggle_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("widget_weather").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("overlay_weather").assertIsDisplayed()

        // 3. Switch to StandBy mode & wake back
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dock_standby_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("standby_root").assertIsDisplayed()
        composeRule.onNodeWithTag("standby_root").performClick()
        composeRule.waitForIdle()

        // 4. Open Baicizhan Vocab Study mode & return
        composeRule.onNodeWithTag("dock_vocab_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("vocab_kill_button").assertIsDisplayed()
        composeRule.onNodeWithTag("vocab_back_button").performClick()
        composeRule.waitForIdle()

        // 5. Secret Vault gesture: tap clock 5 times, then long-press
        repeat(5) {
            composeRule.onNodeWithTag("desktop_clock_trigger").performClick()
        }
        composeRule.onNodeWithTag("desktop_clock_trigger").performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("vault_back_button").assertIsDisplayed()
        composeRule.onNodeWithTag("vault_tab_widgets_theme").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("theme_option_apple_light").assertIsDisplayed()
        composeRule.onNodeWithTag("vault_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("carplay_left_dock").assertIsDisplayed()
    }
}
