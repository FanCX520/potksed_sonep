package com.example

import android.content.Context
import android.view.MotionEvent
import android.view.View
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.VocabCatalogExpanded
import com.example.data.prefs.AiApiProfile
import com.example.data.prefs.MultiClockConfig
import com.example.data.prefs.PenConfig
import com.example.data.prefs.PenPrefsCodec
import com.example.data.remote.ImapSmtpMailClient
import com.example.data.remote.WmoWeatherMapper
import com.example.system.AntiLockCoordinator
import com.example.system.QrCodeMatrix
import com.example.system.SafeMediaImporter
import com.example.ui.ClockTelemetryState
import com.example.ui.PenOsUiState
import com.example.ui.desktop.DirectionLockSwipeLayout
import com.example.ui.desktop.NativeTurboDesktopCallbacks
import com.example.ui.desktop.NativeTurboDesktopContainer
import com.example.ui.desktop.SwipeDirectionClassifier
import com.example.ui.desktop.SwipeDirectionDecision
import com.example.ui.theme.FontSlotSpec
import com.example.ui.theme.GranularTypographyProfile
import com.example.ui.theme.PenFontCatalog
import com.example.widgets.CustomWidgetDslParser
import com.example.widgets.CustomWidgetTemplates
import com.example.widgets.DslWidgetBlock
import kotlinx.coroutines.runBlocking
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun penOs40FullUserJourney() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("PenOS 词典笔桌面", context.getString(R.string.app_name))

        composeRule.waitForIdle()
        // 0. First launch opens Apple-style Setup Assistant; verify and skip into Desktop
        composeRule.onNodeWithTag("setup_assistant_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("setup_skip_button").performClick()
        composeRule.waitForIdle()

        // 1a. PenOS 5.5 defaults to MT6582 Native Turbo Desktop Engine with 3 core cards
        composeRule.onNodeWithTag("native_turbo_desktop").assertIsDisplayed()
        composeRule.runOnUiThread {
            val root = composeRule.activity.window.decorView
            assertNotNull("Expected native left dock", root.findViewWithTag<View>("native_left_dock"))
            assertNotNull("Expected native quick_scan card", root.findViewWithTag<View>("native_card_quick_scan"))
            assertNotNull("Expected native multi_clock card", root.findViewWithTag<View>("native_card_multi_clock"))
            assertNotNull("Expected native vocab_card card", root.findViewWithTag<View>("native_card_vocab_card"))
            val ctrlBtn = root.findViewWithTag<View>("native_dock_control_btn")
            assertNotNull("Expected native control center button", ctrlBtn)
            ctrlBtn.performClick()
        }
        composeRule.waitForIdle()

        // 1b. Control Center opens over Native Turbo -> 1-tap hot-switch to Full Compose Desktop
        composeRule.onNodeWithTag("overlay_control_center").assertIsDisplayed()
        composeRule.onNodeWithTag("control_engine_toggle_chip").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("control_close_button").performClick()
        composeRule.waitForIdle()

        // 1c. Full Compose Desktop renders CarPlay dock, widget deck, quick scan & multi-style clock
        composeRule.onNodeWithTag("carplay_left_dock").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop_widget_deck").assertIsDisplayed()
        composeRule.onNodeWithTag("widget_quick_scan").assertIsDisplayed()
        composeRule.onNodeWithTag("widget_multi_clock").assertIsDisplayed()

        // 2. Quick theme toggle to Apple White Gallery
        composeRule.onNodeWithTag("dock_theme_toggle_button").performClick()
        composeRule.waitForIdle()

        // 3. Control Center -> Apple TV Drawer (1st stage) -> Full-Screen Pomodoro (2nd stage) -> Desktop
        composeRule.onNodeWithTag("dock_control_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("overlay_control_center").assertIsDisplayed()
        composeRule.onNodeWithText("TV抽屉").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("apple_tv_drawer_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("drawer_to_pomodoro_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("pomodoro_focus_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("pomodoro_exit_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("carplay_left_dock").assertIsDisplayed()

        // 4. Control Center -> Built-in IMAP/SMTP Mail Client -> Back to Desktop
        composeRule.onNodeWithTag("dock_control_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("笔端邮箱").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mail_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("mail_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("carplay_left_dock").assertIsDisplayed()

        // 5. Switch to StandBy mode, verify swipeable companion pane & wake back
        composeRule.onNodeWithTag("dock_standby_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("standby_root").assertIsDisplayed()
        composeRule.onNodeWithTag("standby_swipe_card", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("standby_root").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("carplay_left_dock").assertIsDisplayed()

        // 6. Open Baicizhan 2.0 Vocab Study mode & return
        composeRule.onNodeWithTag("dock_vocab_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("vocab_kill_button").assertIsDisplayed()
        composeRule.onNodeWithTag("vocab_import_button").assertIsDisplayed()
        composeRule.onNodeWithTag("vocab_back_button").performClick()
        composeRule.waitForIdle()

        // 7. Open PenOS 5.0 Lab (AI Harness MCP + Guardrail Toggle + LAN Console + Fonts) & return
        composeRule.onNodeWithTag("dock_lab_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("pen_lab_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("lab_ai_input").assertIsDisplayed()
        composeRule.onNodeWithTag("lab_mcp_preview_toggle").assertIsDisplayed()
        composeRule.onNodeWithTag("lab_back_button").performClick()
        composeRule.waitForIdle()

        // 8. Secret Vault gesture: tap clock 5 times, then long-press -> check Theme & 60s Stock Home Bypass
        repeat(5) {
            composeRule.onNodeWithTag("desktop_clock_trigger").performClick()
        }
        composeRule.onNodeWithTag("desktop_clock_trigger").performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("vault_back_button").assertIsDisplayed()
        composeRule.onNodeWithTag("vault_tab_widgets_theme").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("theme_option_apple_light").assertIsDisplayed()
        composeRule.onNodeWithTag("vault_tab_takeover_scan").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("vault_stock_home_bypass_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("vault_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("carplay_left_dock").assertIsDisplayed()
    }

    @Test
    fun qrCodeMatrixEncodesLanUrl() {
        val matrix = QrCodeMatrix.encode("http://192.168.1.42:8765")
        assertTrue("Expected QR dimension >= 25, got ${matrix.size}", matrix.size >= 25)
        assertEquals(matrix.size, matrix[0].size)
        // Top-left 7x7 finder pattern outer ring is dark, inner 5x5 border is light, center 3x3 is dark
        assertTrue(matrix[0][0])
        assertTrue(matrix[0][6])
        assertFalse(matrix[1][1])
        assertTrue(matrix[3][3])
    }

    @Test
    fun customWidgetDslParseAndChecklistToggle() {
        val sampleJson = """
            [
              {"type":"header","title":"冲刺看板","badge":"MCP"},
              {"type":"metric","label":"已斩单词","value":"42","unit":"词"},
              {"type":"checklist","items":[{"text":"听写20词","done":false},{"text":"复习错题","done":true}]},
              {"type":"action_button","label":"番茄25分","action":"pomodoro","target":"25"}
            ]
        """.trimIndent()

        val parsed = CustomWidgetDslParser.parse(sampleJson)
        assertEquals(4, parsed.size)
        assertTrue(parsed[0] is DslWidgetBlock.Header)
        val checklistBefore = parsed[2] as DslWidgetBlock.Checklist
        assertFalse(checklistBefore.items[0].second)
        assertTrue(checklistBefore.items[1].second)

        val toggledJson = CustomWidgetDslParser.toggleChecklistItem(sampleJson, blockIndex = 2, itemIndex = 0)
        val checklistAfter = CustomWidgetDslParser.parse(toggledJson)[2] as DslWidgetBlock.Checklist
        assertTrue(checklistAfter.items[0].second)
        assertTrue(checklistAfter.items[1].second)

        // Malformed JSON safely yields empty list
        assertTrue(CustomWidgetDslParser.parse("{bad json").isEmpty())
    }

    @Test
    fun lanAdminServerPinAndBearerSecurity() {
        val app = ApplicationProvider.getApplicationContext<PenOsApplication>()
        val server = app.lanAdminServer

        assertNull(server.verifyPinAndIssueToken("000000", "654321"))
        assertNull(server.verifyPinAndIssueToken("", "654321"))

        val token = server.verifyPinAndIssueToken("654321", "654321")
        assertNotNull(token)
        assertEquals(48, token!!.length)

        assertTrue(server.isAuthorized("Bearer $token"))
        assertFalse(server.isAuthorized("Bearer invalid_token"))
        assertFalse(server.isAuthorized(null))
    }

    @Test
    fun granularTypographyProfileCodecRoundTrip() {
        val original = GranularTypographyProfile(
            clock = FontSlotSpec(familyKey = "space_grotesk", scale = 1.15f, weight = 700, letterSpacingSp = -0.5f),
            title = FontSlotSpec(familyKey = "playfair_display", scale = 1.05f, weight = 600, letterSpacingSp = 0.2f),
            body = FontSlotSpec(familyKey = "system_sans", scale = 0.95f, weight = 400, letterSpacingSp = 0f),
            poetry = FontSlotSpec(familyKey = "noto_serif_sc", scale = 1.2f, weight = 500, letterSpacingSp = 0.8f),
            metric = FontSlotSpec(familyKey = "jetbrains_mono", scale = 1.0f, weight = 600, letterSpacingSp = 0.1f)
        )
        val encoded = PenPrefsCodec.encodeTypographyProfile(original)
        val decoded = PenPrefsCodec.decodeTypographyProfile(encoded)
        assertEquals("space_grotesk", decoded.clock.familyKey)
        assertEquals(1.15f, decoded.clock.scale, 0.001f)
        assertEquals("playfair_display", decoded.title.familyKey)
        assertEquals("noto_serif_sc", decoded.poetry.familyKey)
        assertEquals(1.2f, decoded.poetry.scale, 0.001f)
        assertEquals("jetbrains_mono", decoded.metric.familyKey)
    }

    @Test
    fun imapRfc2047SubjectDecoding() {
        val client = ImapSmtpMailClient()
        // "笔端邮件测试" in UTF-8 Base64 is 56yU56uv6YKu5Lu25rWL6K+V
        val decoded = client.decodeRfc2047("=?UTF-8?B?56yU56uv6YKu5Lu25rWL6K+V?=")
        assertEquals("笔端邮件测试", decoded)
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

    @Test
    fun penOs50CustomWidget17BlocksAndTemplates() {
        val advancedJson = """
            [
              {"type":"stopwatch","label":"单题计时"},
              {"type":"habit_counter","label":"今日刷题","value":2,"goal":10,"unit":"组"},
              {"type":"mood_picker","label":"状态","options":["心流","稳步","冲刺"],"selected":"心流"},
              {"type":"calc_pad","label":"速算"},
              {"type":"http_json","label":"温度","url":"http://127.0.0.1:8765/api/ping","json_path":"data.temp","unit":"°C","cached_value":"24","updated_at":"12:00"},
              {"type":"macro_button","label":"晚自习","steps":["night_eye_on","white_noise:rain","pomodoro:45"]},
              {"type":"timetable","title":"今日课表","slots":[{"time":"08:00-09:30","name":"英语"}]},
              {"type":"flashcard_mini","front":"F=ma","back":"牛顿第二定律","tag":"物理"}
            ]
        """.trimIndent()

        val parsed = CustomWidgetDslParser.parse(advancedJson)
        assertEquals(8, parsed.size)
        assertTrue(parsed[0] is DslWidgetBlock.Stopwatch)
        assertTrue(parsed[1] is DslWidgetBlock.HabitCounter)
        assertTrue(parsed[2] is DslWidgetBlock.MoodPicker)
        assertTrue(parsed[3] is DslWidgetBlock.CalcPad)
        assertTrue(parsed[4] is DslWidgetBlock.HttpJson)
        assertTrue(parsed[5] is DslWidgetBlock.MacroButton)
        assertTrue(parsed[6] is DslWidgetBlock.Timetable)
        assertTrue(parsed[7] is DslWidgetBlock.FlashcardMini)

        // Habit counter adjustment + clamping
        val incJson = CustomWidgetDslParser.adjustHabitCounter(advancedJson, blockIndex = 1, delta = 3)
        val afterHabit = CustomWidgetDslParser.parse(incJson)[1] as DslWidgetBlock.HabitCounter
        assertEquals(5, afterHabit.value)

        // Mood picker selection
        val moodJson = CustomWidgetDslParser.selectMoodOption(advancedJson, blockIndex = 2, option = "冲刺")
        val afterMood = CustomWidgetDslParser.parse(moodJson)[2] as DslWidgetBlock.MoodPicker
        assertEquals("冲刺", afterMood.selected)

        // HTTP JSON cache update & dot-path extraction
        val httpJson = CustomWidgetDslParser.updateHttpJsonCache(advancedJson, blockIndex = 4, value = "26.5", updatedAtLabel = "12:30")
        val afterHttp = CustomWidgetDslParser.parse(httpJson)[4] as DslWidgetBlock.HttpJson
        assertEquals("26.5", afterHttp.cachedValue)
        assertEquals("ok", CustomWidgetDslParser.extractJsonPath("""{"data":{"status":"ok"}}""", "data.status"))

        // All 6 built-in offline templates parse cleanly
        assertEquals(6, CustomWidgetTemplates.all.size)
        CustomWidgetTemplates.all.forEach { tpl ->
            val blocks = CustomWidgetDslParser.parse(tpl.dslJson)
            assertTrue("Template ${tpl.id} should have blocks", blocks.isNotEmpty())
        }
    }

    @Test
    fun penOs50SnapshotCaptureRollbackAndRedactedConfigExport() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<PenOsApplication>()
        val prefs = app.preferences
        val mgr = app.snapshotManager

        prefs.setDesignStyle(PenConfig.STYLE_DARK_STANDBY)
        prefs.upsertAiApiProfile(
            AiApiProfile(
                id = "test_secret_profile",
                name = "测试模型",
                baseUrl = "https://api.deepseek.com/v1",
                apiKey = "sk-super-secret-123456",
                model = "deepseek-chat"
            )
        )

        // 1. Capture snapshot before mutation -> mutate -> rollback
        val snap = mgr.captureSnapshot("测试回滚快照", "MCP")
        assertTrue(snap.id > 0L)
        prefs.setDesignStyle(PenConfig.STYLE_APPLE_LIGHT)
        assertEquals(PenConfig.STYLE_APPLE_LIGHT, prefs.currentConfig().designStyle)

        val restoreRes = mgr.restoreLatestSnapshot()
        assertTrue(restoreRes.isSuccess)
        assertEquals(PenConfig.STYLE_DARK_STANDBY, prefs.currentConfig().designStyle)

        // 2. Redacted full config export MUST strip API keys, and re-importing redacted JSON MUST preserve local key
        val exported = mgr.exportFullConfigJson(includeSecrets = false)
        assertFalse("Exported config must not leak API key", exported.contains("sk-super-secret-123456"))

        val importRes = mgr.importFullConfigJson(exported)
        assertTrue(importRes.isSuccess)
        val preserved = prefs.currentConfig().aiProfiles.firstOrNull { it.id == "test_secret_profile" }
        assertEquals("sk-super-secret-123456", preserved?.apiKey)
    }

    @Test
    fun penOs50StockHome60sBypassToggle() {
        val pkg = "com.jiuxue.pen"
        val homeAct = "com.jiuxue.pen.home.HomeActivity"
        val selfPkg = "com.aistudio.penoslauncher.xk9m2p"

        AntiLockCoordinator.revokeStockHomeBypass()
        AntiLockCoordinator.updateRules(
            blockedPkg = pkg,
            blockedActivities = setOf("$pkg/$homeAct"),
            scanPkg = pkg,
            scanActivityClass = "com.jiuxue.pen.scan.ScanActivity"
        )
        assertTrue(AntiLockCoordinator.shouldPullBackToPenOs(pkg, homeAct, selfPkg))

        // Grant 60s bypass -> HomeActivity is temporarily allowed
        AntiLockCoordinator.grantStockHomeBypass(60_000L)
        assertTrue(AntiLockCoordinator.isStockHomeBypassActive())
        assertTrue(AntiLockCoordinator.remainingStockHomeBypassSec() in 1..60)
        assertFalse(AntiLockCoordinator.shouldPullBackToPenOs(pkg, homeAct, selfPkg))

        // Revoke bypass -> HomeActivity is blocked again immediately
        AntiLockCoordinator.revokeStockHomeBypass()
        assertFalse(AntiLockCoordinator.isStockHomeBypassActive())
        assertTrue(AntiLockCoordinator.shouldPullBackToPenOs(pkg, homeAct, selfPkg))
    }

    @Test
    fun penOs50FontPreImportProbeRejectsInvalidFiles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fakeFont = File(context.cacheDir, "fake_corrupt.ttf")
        fakeFont.writeText("NOT_A_VALID_TTF_HEADER_AT_ALL")
        val fakeRes = SafeMediaImporter.validateAndProbeFontFile(fakeFont)
        assertTrue("Corrupt font header must be rejected", fakeRes.isFailure)
        fakeFont.delete()

        val emptyFont = File(context.cacheDir, "empty.ttf")
        emptyFont.writeBytes(ByteArray(0))
        val emptyRes = SafeMediaImporter.validateAndProbeFontFile(emptyFont)
        assertTrue("Empty font file must be rejected", emptyRes.isFailure)
        emptyFont.delete()
    }

    @Test
    fun penOs55Mt6582DefaultsAndThreeCardDeck() {
        val cfg = PenConfig()
        assertEquals(PenConfig.ENGINE_NATIVE_TURBO, cfg.desktopEngineMode)
        assertTrue("Zero-overdraw mode must be enabled by default for Mali-400", cfg.zeroOverdrawMode)
        assertEquals(listOf("quick_scan", "multi_clock", "vocab_card"), cfg.enabledWidgetIds)
        assertEquals(10, PenConfig.ALL_WIDGET_IDS.size)
        assertEquals(MultiClockConfig.STYLE_KDE_MINIMAL, cfg.multiClock.style)
        assertEquals(PenConfig.WALLPAPER_OBSIDIAN, cfg.darkWallpaperPresetId)
        assertEquals(PenConfig.WALLPAPER_PURE_MIST, cfg.lightWallpaperPresetId)
    }

    @Test
    fun penOs55SwipeDirectionClassifierProtectsHorizontalScroll() {
        val touchSlop = 16f

        // Below touchSlop -> NONE
        assertEquals(
            SwipeDirectionDecision.NONE,
            SwipeDirectionClassifier.classify(dx = 8f, dy = 10f, touchSlop = touchSlop)
        )
        // Pure horizontal drag -> HORIZONTAL_SCROLL
        assertEquals(
            SwipeDirectionDecision.HORIZONTAL_SCROLL,
            SwipeDirectionClassifier.classify(dx = -60f, dy = 12f, touchSlop = touchSlop)
        )
        // Shallow diagonal drag (|dy| <= |dx| * 1.35) -> never steals horizontal scroll
        assertEquals(
            SwipeDirectionDecision.NONE,
            SwipeDirectionClassifier.classify(dx = 40f, dy = -45f, touchSlop = touchSlop)
        )
        // Steep upward swipe -> VERTICAL_SWIPE_UP (opens Apple TV Drawer)
        assertEquals(
            SwipeDirectionDecision.VERTICAL_SWIPE_UP,
            SwipeDirectionClassifier.classify(dx = 10f, dy = -85f, touchSlop = touchSlop)
        )
        // Steep downward swipe -> VERTICAL_SWIPE_DOWN (opens Control Center)
        assertEquals(
            SwipeDirectionDecision.VERTICAL_SWIPE_DOWN,
            SwipeDirectionClassifier.classify(dx = -8f, dy = 88f, touchSlop = touchSlop)
        )
    }

    @Test
    fun penOs55SnapshotPreservesDualEngineAndZeroOverdraw() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<PenOsApplication>()
        val prefs = app.preferences
        val mgr = app.snapshotManager

        prefs.setDesktopEngineMode(PenConfig.ENGINE_COMPOSE_FULL)
        prefs.setZeroOverdrawMode(false)
        mgr.captureSnapshot("5.5引擎状态测试", "USER")

        prefs.setDesktopEngineMode(PenConfig.ENGINE_NATIVE_TURBO)
        prefs.setZeroOverdrawMode(true)
        assertEquals(PenConfig.ENGINE_NATIVE_TURBO, prefs.currentConfig().desktopEngineMode)
        assertTrue(prefs.currentConfig().zeroOverdrawMode)

        val restored = mgr.restoreLatestSnapshot()
        assertTrue(restored.isSuccess)
        assertEquals(PenConfig.ENGINE_COMPOSE_FULL, prefs.currentConfig().desktopEngineMode)
        assertFalse(prefs.currentConfig().zeroOverdrawMode)

        // Reset back to MT6582 defaults
        prefs.setDesktopEngineMode(PenConfig.ENGINE_NATIVE_TURBO)
        prefs.setZeroOverdrawMode(true)
    }

    @Test
    fun auditBug01RamJitterNeverRebuildsNativeDeck() {
        val app = ApplicationProvider.getApplicationContext<PenOsApplication>()
        val container = NativeTurboDesktopContainer(app, app.nativeWidgetHostManager, NativeTurboDesktopCallbacks())

        val baseState = PenOsUiState(
            clock = ClockTelemetryState(timeHHmm = "10:20", availRamMb = 340, isWifiConnected = true)
        )
        container.bindState(baseState)
        assertEquals(1, container.deckRebuildCount)
        assertNotNull("Discovery tail card must be mounted", container.findViewWithTag<View>("native_card_more_widgets"))

        // Simulate 15-second RAM jitter fluctuations -> MUST NOT destroy/rebuild deck!
        container.bindState(baseState.copy(clock = baseState.clock.copy(availRamMb = 318)))
        container.bindState(baseState.copy(clock = baseState.clock.copy(availRamMb = 295)))
        container.bindState(baseState.copy(clock = baseState.clock.copy(availRamMb = 362)))
        assertEquals("RAM fluctuations must NEVER trigger deck rebuild", 1, container.deckRebuildCount)
    }

    @Test
    fun auditBug04EmptySpaceVerticalSwipeTriggersCallbacks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val layout = DirectionLockSwipeLayout(context)
        layout.swipeThresholdPx = 30f
        var upCount = 0
        var downCount = 0
        layout.onVerticalSwipeUp = { upCount++ }
        layout.onVerticalSwipeDown = { downCount++ }

        // Empty-space swipe UP: ACTION_DOWN -> ACTION_MOVE -> ACTION_UP
        val downUp = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        assertTrue("ACTION_DOWN must return true to keep touch chain alive on empty space", layout.onTouchEvent(downUp))
        val moveUp = MotionEvent.obtain(0L, 20L, MotionEvent.ACTION_MOVE, 102f, 110f, 0)
        assertTrue(layout.onTouchEvent(moveUp))
        val endUp = MotionEvent.obtain(0L, 40L, MotionEvent.ACTION_UP, 102f, 100f, 0)
        layout.onTouchEvent(endUp)
        assertEquals(1, upCount)
        assertEquals(0, downCount)

        // Empty-space swipe DOWN
        val downDn = MotionEvent.obtain(100L, 100L, MotionEvent.ACTION_DOWN, 100f, 60f, 0)
        assertTrue(layout.onTouchEvent(downDn))
        val moveDn = MotionEvent.obtain(100L, 120L, MotionEvent.ACTION_MOVE, 101f, 160f, 0)
        assertTrue(layout.onTouchEvent(moveDn))
        val endDn = MotionEvent.obtain(100L, 140L, MotionEvent.ACTION_UP, 101f, 170f, 0)
        layout.onTouchEvent(endDn)
        assertEquals(1, downCount)

        listOf(downUp, moveUp, endUp, downDn, moveDn, endDn).forEach { it.recycle() }
    }

    @Test
    fun auditBug02And08DefaultsFullScreenAdaptiveAndNativeCjkTypography() {
        val cfg = PenConfig()
        assertFalse("Phones should default to full-screen adaptive mode (not 640x178 preview frame)", cfg.penFramePreviewEnabled)
        assertEquals(PenFontCatalog.FAMILY_SYSTEM_SANS, cfg.typographyProfile.body.familyKey)
        assertEquals(PenFontCatalog.FAMILY_SYSTEM_SANS, cfg.typographyProfile.title.familyKey)
        assertEquals(PenFontCatalog.FAMILY_OUTFIT, cfg.typographyProfile.clock.familyKey)
    }
}
