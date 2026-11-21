package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.VocabCatalogExpanded
import com.example.data.remote.WmoWeatherMapper
import com.example.system.AntiLockCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
    fun penOs30FullUserJourney() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("PenOS 词典笔桌面", context.getString(R.string.app_name))

        composeRule.waitForIdle()
        // 1. Desktop renders CarPlay dock, widget deck, quick scan & weather
        composeRule.onNodeWithTag("carplay_left_dock").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop_widget_deck").assertIsDisplayed()
        composeRule.onNodeWithTag("widget_quick_scan").assertIsDisplayed()
        composeRule.onNodeWithTag("widget_weather").assertIsDisplayed()

        // 2. Quick theme toggle to Apple White Gallery & open ICP Weather overlay
        composeRule.onNodeWithTag("dock_theme_toggle_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("widget_weather").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("overlay_weather").assertIsDisplayed()
        composeRule.onNodeWithTag("weather_city_search_input").assertIsDisplayed()
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        // 3. Open Control Center (brightness/volume/night dimmer/white noise) & close
        composeRule.onNodeWithTag("dock_control_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("overlay_control_center").assertIsDisplayed()
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        // 4. Switch to StandBy mode, verify swipeable companion pane & wake back
        composeRule.onNodeWithTag("dock_standby_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("standby_root").assertIsDisplayed()
        composeRule.onNodeWithTag("standby_swipe_card", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("standby_root").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("carplay_left_dock").assertIsDisplayed()

        // 5. Open Baicizhan 2.0 Vocab Study mode & return
        composeRule.onNodeWithTag("dock_vocab_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("vocab_kill_button").assertIsDisplayed()
        composeRule.onNodeWithTag("vocab_import_button").assertIsDisplayed()
        composeRule.onNodeWithTag("vocab_back_button").performClick()
        composeRule.waitForIdle()

        // 6. Open PenOS 实验室 (AI+MCP + DSP White Noise + Jiuxuewang Radar) & return
        composeRule.onNodeWithTag("dock_lab_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("pen_lab_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("lab_ai_input").assertIsDisplayed()
        composeRule.onNodeWithTag("lab_back_button").performClick()
        composeRule.waitForIdle()

        // 7. Secret Vault gesture: tap clock 5 times, then long-press
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

    @Test
    fun jiuxuewangSamePackageMultiActivityAntiLock() {
        val pkg = "com.jiuxue.pen"
        val scanAct = "com.jiuxue.pen.scan.ScanActivity"
        val homeAct = "com.jiuxue.pen.home.HomeActivity"
        val selfPkg = "com.aistudio.penoslauncher.xk9m2p"

        AntiLockCoordinator.updateRules(
            blockedPkg = pkg,
            blockedActivities = setOf("$pkg/$homeAct"),
            scanPkg = pkg,
            scanActivityClass = scanAct
        )
        // User intentionally launches Scan Activity ("学王扫学")
        AntiLockCoordinator.allowIntentionalLaunch(pkg, scanAct, graceDurationMs = 60_000L)

        // Staying inside ScanActivity must NOT be intercepted
        assertFalse(AntiLockCoordinator.shouldPullBackToPenOs(pkg, scanAct, selfPkg))

        // Pressing Back inside ScanActivity drops into same-package HomeActivity -> MUST pull back immediately!
        assertTrue(AntiLockCoordinator.shouldPullBackToPenOs(pkg, homeAct, selfPkg))

        // PenOS itself is never intercepted
        assertFalse(AntiLockCoordinator.shouldPullBackToPenOs(selfPkg, "com.example.MainActivity", selfPkg))

        AntiLockCoordinator.clearIntentionalLaunch()
    }

    @Test
    fun baicizhanExpandedWordbookAndCustomParser() {
        val expanded = VocabCatalogExpanded.allExpandedWords
        assertTrue("Expected >= 160 expanded words, got ${expanded.size}", expanded.size >= 160)
        assertTrue(expanded.any { it.bookId == "middle" })
        assertTrue(expanded.any { it.bookId == "highschool" })
        assertTrue(expanded.any { it.bookId == "cet4" })
        assertTrue(expanded.any { it.bookId == "cet6" })
        assertTrue(expanded.any { it.bookId == "daily" })
        assertTrue(expanded.all { it.rootBreakdown.isNotBlank() && it.visualCue.isNotBlank() })

        val sampleTxt = """
            # Custom wordbook
            ephemeral,/ɪˈfemərəl/,短暂的,Fame is ephemeral.
            serendipity	意外发现珍奇事物的运气
            lucid|ˈluːsɪd|adj.|清晰的|luc(光)+id
            resilient - 有韧性的
        """.trimIndent()
        val parsed = VocabCatalogExpanded.parseCustomWordbook(sampleTxt)
        assertEquals(4, parsed.size)
        assertEquals("ephemeral", parsed[0].word)
        assertEquals("serendipity", parsed[1].word)
        assertEquals("lucid", parsed[2].word)
        assertEquals("resilient", parsed[3].word)
    }

    @Test
    fun offlineChineseCityCatalogLookup() {
        assertTrue(WmoWeatherMapper.presetCities.size >= 50)
        val hz = WmoWeatherMapper.findBestCity("杭州市")
        assertNotNull(hz)
        assertEquals("杭州", hz?.displayName)
        assertEquals("101210101", hz?.cmaCode)

        val cd = WmoWeatherMapper.findBestCity("chengdu")
        assertNotNull(cd)
        assertEquals("成都", cd?.displayName)

        val sz = WmoWeatherMapper.findBestCity("深圳")
        assertNotNull(sz)
        assertEquals("101280601", sz?.cmaCode)
    }
}
