package com.example.system

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Coordinates intentional app launches (e.g. "学王扫学", pinned CarPlay apps, hidden vault apps)
 * with PenLauncherGuardService so the anti-lock guard only intercepts unwanted stock launcher hijack.
 */
object AntiLockCoordinator {
    val guardServiceConnected = AtomicBoolean(false)

    private val allowedForegroundPackage = AtomicReference<String>("")
    private val allowedUntilElapsedMs = AtomicLong(0L)
    private val blockedLauncherPackage = AtomicReference<String>("")

    fun updateBlockedLauncher(packageName: String) {
        blockedLauncherPackage.set(packageName.trim())
    }

    fun getBlockedLauncher(): String = blockedLauncherPackage.get()

    /**
     * Marks a package as intentionally launched by the user from PenOS.
     * Even if it shares a package with a scan shell, it stays whitelisted while active.
     */
    fun allowIntentionalLaunch(packageName: String, graceDurationMs: Long = 10L * 60L * 1000L) {
        allowedForegroundPackage.set(packageName)
        allowedUntilElapsedMs.set(System.currentTimeMillis() + graceDurationMs)
    }

    fun clearIntentionalLaunch() {
        allowedForegroundPackage.set("")
        allowedUntilElapsedMs.set(0L)
    }

    fun shouldPullBackToPenOs(foregroundPackage: String, selfPackage: String): Boolean {
        if (foregroundPackage.isBlank() || foregroundPackage == selfPackage) return false
        val targetBlocked = blockedLauncherPackage.get()
        if (targetBlocked.isBlank()) return false
        if (foregroundPackage != targetBlocked) return false

        val allowedPkg = allowedForegroundPackage.get()
        if (allowedPkg == foregroundPackage && System.currentTimeMillis() < allowedUntilElapsedMs.get()) {
            return false
        }
        return true
    }
}
