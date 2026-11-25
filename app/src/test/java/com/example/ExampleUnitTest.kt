package com.example

import com.example.data.local.OfflineSeedData
import com.example.data.local.VocabCatalogExpanded
import com.example.data.prefs.AiApiProfile
import com.example.data.prefs.PenConfig
import com.example.data.prefs.PenPreferencesRepository
import com.example.system.AntiLockCoordinator
import com.example.ui.PenOsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun antiLockCoordinator_blocksSamePackageHomeWhileAllowingScanAndBypass60s() {
        AntiLockCoordinator.revokeStockHomeBypass()
        AntiLockCoordinator.updateRules(
            blockedPkg = "com.jiuxuewang.pen",
            blockedActivities = setOf("com.jiuxuewang.pen/com.jiuxuewang.pen.HomeActivity"),
            scanPkg = "com.jiuxuewang.pen",
            scanActivityClass = "com.jiuxuewang.pen.ScanDictActivity"
        )
        AntiLockCoordinator.allowIntentionalLaunch(
            packageName = "com.jiuxuewang.pen",
            activityClass = "com.jiuxuewang.pen.ScanDictActivity"
        )

        // Same-package ScanActivity is allowed
        assertFalse(
            AntiLockCoordinator.shouldPullBackToPenOs(
                "com.jiuxuewang.pen",
                "com.jiuxuewang.pen.ScanDictActivity",
                "com.aistudio.penoslauncher.xk9m2p"
            )
        )
        // Same-package HomeActivity is intercepted immediately
        assertTrue(
            AntiLockCoordinator.shouldPullBackToPenOs(
                "com.jiuxuewang.pen",
                "com.jiuxuewang.pen.HomeActivity",
                "com.aistudio.penoslauncher.xk9m2p"
            )
        )

        // 60s Vault bypass temporarily allows stock home
        AntiLockCoordinator.grantStockHomeBypass(60_000L)
        assertTrue(AntiLockCoordinator.isStockHomeBypassActive())
        assertFalse(
            AntiLockCoordinator.shouldPullBackToPenOs(
                "com.jiuxuewang.pen",
                "com.jiuxuewang.pen.HomeActivity",
                "com.aistudio.penoslauncher.xk9m2p"
            )
        )
        AntiLockCoordinator.revokeStockHomeBypass()
    }

    @Test
    fun vocabCatalog_hasRichOfflineWordsAndParsesCustomCsv() {
        assertTrue(OfflineSeedData.defaultWords.size >= 30)
        assertTrue(VocabCatalogExpanded.allExpandedWords.size >= 100)

        val raw = """
            # custom words
            word,phonetic,meaning,example
            serendipity,/ˌserənˈdɪpəti/,意外发现珍奇事物的运气,Finding this minimal launcher was pure serendipity.
            ephemeral	短暂的
        """.trimIndent()
        val parsed = VocabCatalogExpanded.parseCustomWordbook(raw)
        assertEquals(2, parsed.size)
        assertEquals("serendipity", parsed[0].word)
        assertEquals("ephemeral", parsed[1].word)
    }

    @Test
    fun clockSnapshot_formatsTwoDigitHourMinuteSecond() {
        val snap = PenOsViewModel.buildClockSnapshot(1_700_000_000_000L)
        assertEquals(2, snap.hour.length)
        assertEquals(2, snap.minute.length)
        assertEquals(2, snap.second.length)
        assertTrue(snap.dateLine.contains("月"))
        assertEquals(PenConfig.WALLPAPER_SOLID, PenConfig().staticWallpaperMode)
    }
}
