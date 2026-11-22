package com.example.system

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

data class WindowRadarEntry(
    val timestamp: String,
    val packageName: String,
    val className: String,
    val wasIntercepted: Boolean
) {
    val flatComponent: String get() = "$packageName/$className"
}

/**
 * Component-level Anti-Lock Coordinator built specifically for Jiuxuewang (久学网) dictionary pens
 * where the Stock Home Launcher AND Scan Activity ("学王扫学") share the EXACT SAME packageName
 * but use different Activity class names.
 */
object AntiLockCoordinator {
    val guardServiceConnected = AtomicBoolean(false)

    private val allowedForegroundPackage = AtomicReference("")
    private val allowedScanActivityClass = AtomicReference("")
    private val allowedUntilElapsedMs = AtomicLong(0L)

    private val blockedLauncherPackage = AtomicReference("")
    private val blockedHomeActivities = AtomicReference<Set<String>>(emptySet())
    private val stockHomeBypassUntilMs = AtomicLong(0L)

    private val _radarLog = MutableStateFlow<List<WindowRadarEntry>>(emptyList())
    val radarLog: StateFlow<List<WindowRadarEntry>> = _radarLog.asStateFlow()

    private val homeClassHints = listOf(
        "homeactivity", "launcheractivity", "desktopactivity",
        "mainlauncher", "penhome", "studylauncher", "home"
    )
    private val scanClassHints = listOf(
        "scan", "dict", "ocr", "translate", "word", "saoxue", "camera", "query", "lookup"
    )

    fun updateRules(
        blockedPkg: String,
        blockedActivities: Set<String>,
        scanPkg: String,
        scanActivityClass: String
    ) {
        blockedLauncherPackage.set(blockedPkg.trim())
        blockedHomeActivities.set(blockedActivities.map { it.trim() }.filter { it.isNotBlank() }.toSet())
        if (scanActivityClass.isNotBlank()) {
            allowedScanActivityClass.set(normalizeClassName(scanPkg.trim(), scanActivityClass.trim()))
        } else {
            allowedScanActivityClass.set("")
        }
    }

    fun getBlockedLauncher(): String = blockedLauncherPackage.get()

    fun allowIntentionalLaunch(
        packageName: String,
        activityClass: String = "",
        graceDurationMs: Long = 10L * 60L * 1000L
    ) {
        allowedForegroundPackage.set(packageName.trim())
        if (activityClass.isNotBlank()) {
            allowedScanActivityClass.set(normalizeClassName(packageName.trim(), activityClass.trim()))
        }
        allowedUntilElapsedMs.set(System.currentTimeMillis() + graceDurationMs)
    }

    fun clearIntentionalLaunch() {
        allowedForegroundPackage.set("")
        allowedUntilElapsedMs.set(0L)
    }

    /**
     * PenOS 5.0 Vault 60-second temporary pass so the user can enter stock Jiuxuewang Home
     * to adjust system settings or update dictionaries, automatically re-arming afterwards.
     */
    fun grantStockHomeBypass(durationMs: Long = 60_000L) {
        stockHomeBypassUntilMs.set(System.currentTimeMillis() + durationMs.coerceIn(5_000L, 300_000L))
    }

    fun revokeStockHomeBypass() {
        stockHomeBypassUntilMs.set(0L)
    }

    fun remainingStockHomeBypassSec(nowMs: Long = System.currentTimeMillis()): Int {
        val rem = stockHomeBypassUntilMs.get() - nowMs
        return if (rem <= 0L) 0 else ((rem + 999L) / 1000L).toInt()
    }

    fun isStockHomeBypassActive(nowMs: Long = System.currentTimeMillis()): Boolean =
        nowMs < stockHomeBypassUntilMs.get()

    fun recordWindowEvent(packageName: String, className: String, intercepted: Boolean) {
        if (packageName.isBlank()) return
        val normalized = normalizeClassName(packageName, className)
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val entry = WindowRadarEntry(time, packageName, normalized, intercepted)
        val current = _radarLog.value
        val deduplicated = listOf(entry) + current.filterNot {
            it.packageName == packageName && it.className == normalized && it.wasIntercepted == intercepted
        }
        _radarLog.value = deduplicated.take(15)
    }

    /**
     * Component-level decision:
     * 1. Explicit blocked Home Activity (`pkg/HomeActivity`) is ALWAYS intercepted immediately,
     *    even if `pkg` is currently whitelisted for same-package Scan Activity!
     * 2. If `pkg == blockedLauncherPackage`, check whether `className` looks like a Stock Home Activity
     *    (and is NOT the bound Scan Activity) -> intercept immediately!
     * 3. Otherwise allow intentional launches during grace window; intercept uninvited stock launcher pops.
     */
    fun shouldPullBackToPenOs(
        foregroundPackage: String,
        foregroundClass: String,
        selfPackage: String
    ): Boolean {
        if (foregroundPackage.isBlank() || foregroundPackage == selfPackage) return false
        if (isStockHomeBypassActive()) return false
        val normClass = normalizeClassName(foregroundPackage, foregroundClass)
        val flat = "$foregroundPackage/$normClass"

        val explicitBlockedSet = blockedHomeActivities.get()
        // Rule 1: Explicit blocked Home Activity ALWAYS gets pulled back (fixes Jiuxuewang Back -> HomeActivity!)
        if (explicitBlockedSet.any { blocked ->
                blocked.equals(flat, ignoreCase = true) ||
                    (blocked.contains("/") && blocked.substringBefore("/") == foregroundPackage &&
                        normClass.endsWith(blocked.substringAfter("/").removePrefix("."), ignoreCase = true))
            }
        ) {
            return true
        }

        val targetBlockedPkg = blockedLauncherPackage.get()
        if (targetBlockedPkg.isBlank() || foregroundPackage != targetBlockedPkg) {
            return false
        }

        // Rule 2: Same package as blocked launcher (Jiuxuewang architecture)
        val allowedScanCls = allowedScanActivityClass.get()
        val isExplicitAllowedScan = allowedScanCls.isNotBlank() && normClass.equals(allowedScanCls, ignoreCase = true)
        if (isExplicitAllowedScan) {
            return false
        }

        val lowerCls = normClass.lowercase()
        val looksLikeScan = scanClassHints.any { lowerCls.contains(it) }
        val looksLikeHome = !looksLikeScan && homeClassHints.any { lowerCls.contains(it) }

        val withinGrace = allowedForegroundPackage.get() == foregroundPackage &&
            System.currentTimeMillis() < allowedUntilElapsedMs.get()

        // If user pressed Back inside same-package Scan and landed on HomeActivity, pull back immediately!
        if (looksLikeHome) {
            return true
        }

        // Non-home activity inside same package (e.g., scan result / dictionary detail) is allowed during grace window
        return !withinGrace
    }

    private fun normalizeClassName(pkg: String, cls: String): String {
        val trimmed = cls.trim()
        return when {
            trimmed.isBlank() -> ""
            trimmed.startsWith(".") -> "$pkg$trimmed"
            else -> trimmed
        }
    }
}
