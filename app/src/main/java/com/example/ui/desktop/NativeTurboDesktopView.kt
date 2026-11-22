package com.example.ui.desktop

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.local.CustomWidgetEntity
import com.example.data.prefs.MultiClockConfig
import com.example.data.prefs.PenConfig
import com.example.system.NativeWidgetHostManager
import com.example.ui.ClockTelemetryState
import com.example.ui.DesktopOverlay
import com.example.ui.PenOsUiState
import com.example.widgets.LocalClockTickFlow
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure direction-lock classifier shared by NativeTurboDesktopView and unit tests.
 * Prevents vertical swipe-up/down gestures from ever blocking horizontal widget deck scrolling.
 */
enum class SwipeDirectionDecision {
    NONE,
    HORIZONTAL_SCROLL,
    VERTICAL_SWIPE_UP,
    VERTICAL_SWIPE_DOWN
}

object SwipeDirectionClassifier {
    fun classify(dx: Float, dy: Float, touchSlop: Float): SwipeDirectionDecision {
        val absX = abs(dx)
        val absY = abs(dy)
        if (absX < touchSlop && absY < touchSlop) return SwipeDirectionDecision.NONE
        return when {
            absY > touchSlop && absY > absX * 1.35f ->
                if (dy < 0f) SwipeDirectionDecision.VERTICAL_SWIPE_UP else SwipeDirectionDecision.VERTICAL_SWIPE_DOWN
            absX >= touchSlop && absX >= absY ->
                SwipeDirectionDecision.HORIZONTAL_SCROLL
            else -> SwipeDirectionDecision.NONE
        }
    }
}

/**
 * Callbacks bridge from pure native Android Views back into PenOsViewModel actions.
 */
class NativeTurboDesktopCallbacks(
    var onClockTap: () -> Unit = {},
    var onClockLongPress: () -> Unit = {},
    var onToggleQuickTheme: () -> Unit = {},
    var onLaunchScanApp: () -> Unit = {},
    var onLaunchApp: (String) -> Unit = {},
    var onOpenOverlay: (DesktopOverlay) -> Unit = {},
    var onOpenVocab: () -> Unit = {},
    var onEnterStandBy: () -> Unit = {},
    var onOpenAppleTvDrawer: () -> Unit = {},
    var onOpenMail: () -> Unit = {},
    var onCycleClockStyle: () -> Unit = {},
    var onOpenLab: () -> Unit = {},
    var onSetDeckMode: (String) -> Unit = {},
    var onRequestAddNativeWidget: () -> Unit = {},
    var onGestureHaptic: () -> Unit = {}
)

/**
 * MT6582 1GHz Native Android View Turbo Desktop Engine:
 * - Zero Compose recomposition during horizontal scrolling or 1-second clock ticks.
 * - Pure C++ HWUI native HorizontalScrollView + OverScroller inertial fling.
 * - Zero overdraw: 1 opaque solid background + 1 opaque GradientDrawable per card.
 * - Direction-locked vertical swipe interceptor that NEVER steals horizontal drags.
 */
@Composable
fun NativeTurboDesktopView(
    state: PenOsUiState,
    nativeWidgetHost: NativeWidgetHostManager,
    onClockTap: () -> Unit,
    onClockLongPress: () -> Unit,
    onToggleQuickTheme: () -> Unit,
    onLaunchScanApp: () -> Unit,
    onLaunchApp: (String) -> Unit,
    onOpenOverlay: (DesktopOverlay) -> Unit,
    onOpenVocab: () -> Unit,
    onEnterStandBy: () -> Unit,
    onOpenAppleTvDrawer: () -> Unit,
    onOpenMail: () -> Unit,
    onCycleClockStyle: () -> Unit,
    onOpenLab: () -> Unit,
    onSetDeckMode: (String) -> Unit,
    onRequestAddNativeWidget: () -> Unit,
    onGestureHaptic: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clockTickFlow = LocalClockTickFlow.current
    val callbacks = remember { NativeTurboDesktopCallbacks() }
    callbacks.onClockTap = onClockTap
    callbacks.onClockLongPress = onClockLongPress
    callbacks.onToggleQuickTheme = onToggleQuickTheme
    callbacks.onLaunchScanApp = onLaunchScanApp
    callbacks.onLaunchApp = onLaunchApp
    callbacks.onOpenOverlay = onOpenOverlay
    callbacks.onOpenVocab = onOpenVocab
    callbacks.onEnterStandBy = onEnterStandBy
    callbacks.onOpenAppleTvDrawer = onOpenAppleTvDrawer
    callbacks.onOpenMail = onOpenMail
    callbacks.onCycleClockStyle = onCycleClockStyle
    callbacks.onOpenLab = onOpenLab
    callbacks.onSetDeckMode = onSetDeckMode
    callbacks.onRequestAddNativeWidget = onRequestAddNativeWidget
    callbacks.onGestureHaptic = onGestureHaptic

    val viewHolder = remember { arrayOfNulls<NativeTurboDesktopContainer>(1) }

    // Decoupled 1-second tick directly mutates TextViews via setText() with zero Compose recomposition!
    LaunchedEffect(clockTickFlow) {
        clockTickFlow.collect { tick ->
            viewHolder[0]?.updateClockTick(tick)
        }
    }

    AndroidView(
        modifier = modifier
            .fillMaxSize()
            .testTag("native_turbo_desktop"),
        factory = { ctx ->
            NativeTurboDesktopContainer(ctx, nativeWidgetHost, callbacks).also { container ->
                viewHolder[0] = container
                container.bindState(state)
                container.updateClockTick(clockTickFlow.value)
            }
        },
        update = { container ->
            viewHolder[0] = container
            container.bindState(state)
        }
    )
}

/**
 * Custom FrameLayout that locks swipe direction cleanly after touchSlop:
 * horizontal moves pass 100% untouched to HorizontalScrollView;
 * decisive vertical swipes fire Apple TV Drawer (up) or Control Center (down).
 */
open class DirectionLockSwipeLayout(context: Context) : FrameLayout(context) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat().coerceAtLeast(10f)
    var swipeThresholdPx: Float = 34f * resources.displayMetrics.density
    var gesturesEnabled: Boolean = true
    var onVerticalSwipeUp: () -> Unit = {}
    var onVerticalSwipeDown: () -> Unit = {}

    private var downX = 0f
    private var downY = 0f
    private var horizontalLocked = false
    private var verticalLocked = false
    private var gestureTriggered = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!gesturesEnabled) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                horizontalLocked = false
                verticalLocked = false
                gestureTriggered = false
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (horizontalLocked) return false
                if (verticalLocked) return true
                val decision = SwipeDirectionClassifier.classify(ev.x - downX, ev.y - downY, touchSlop)
                when (decision) {
                    SwipeDirectionDecision.HORIZONTAL_SCROLL -> {
                        horizontalLocked = true
                        return false
                    }
                    SwipeDirectionDecision.VERTICAL_SWIPE_UP,
                    SwipeDirectionDecision.VERTICAL_SWIPE_DOWN -> {
                        verticalLocked = true
                        return true
                    }
                    SwipeDirectionDecision.NONE -> return false
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                horizontalLocked = false
                verticalLocked = false
                gestureTriggered = false
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!gesturesEnabled || !verticalLocked) return super.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                if (!gestureTriggered) {
                    val dy = ev.y - downY
                    when {
                        dy <= -swipeThresholdPx -> {
                            gestureTriggered = true
                            onVerticalSwipeUp()
                        }
                        dy >= swipeThresholdPx -> {
                            gestureTriggered = true
                            onVerticalSwipeDown()
                        }
                    }
                }
                if (ev.actionMasked == MotionEvent.ACTION_UP) {
                    verticalLocked = false
                    horizontalLocked = false
                    gestureTriggered = false
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                verticalLocked = false
                horizontalLocked = false
                gestureTriggered = false
                return true
            }
        }
        return true
    }
}

private class NativePalette(
    val isLight: Boolean,
    val canvasBg: Int,
    val cardBg: Int,
    val cardElevatedBg: Int,
    val chipBg: Int,
    val border: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val accentBlue: Int,
    val accentCoral: Int,
    val accentEmerald: Int,
    val accentAmber: Int,
    val dangerRed: Int
) {
    companion object {
        fun forTheme(isLight: Boolean): NativePalette = if (isLight) {
            NativePalette(
                isLight = true,
                canvasBg = Color.parseColor("#F2F4F7"),
                cardBg = Color.parseColor("#FFFFFF"),
                cardElevatedBg = Color.parseColor("#FFFFFF"),
                chipBg = Color.parseColor("#E9EEF4"),
                border = Color.parseColor("#E2E8F0"),
                textPrimary = Color.parseColor("#111827"),
                textSecondary = Color.parseColor("#64748B"),
                accentBlue = Color.parseColor("#0071E3"),
                accentCoral = Color.parseColor("#E85D04"),
                accentEmerald = Color.parseColor("#059669"),
                accentAmber = Color.parseColor("#D97706"),
                dangerRed = Color.parseColor("#EF4444")
            )
        } else {
            NativePalette(
                isLight = false,
                canvasBg = Color.parseColor("#06080D"),
                cardBg = Color.parseColor("#12161F"),
                cardElevatedBg = Color.parseColor("#161B26"),
                chipBg = Color.parseColor("#1E2636"),
                border = Color.parseColor("#232B3A"),
                textPrimary = Color.parseColor("#F8FAFC"),
                textSecondary = Color.parseColor("#94A3B8"),
                accentBlue = Color.parseColor("#38BDF8"),
                accentCoral = Color.parseColor("#FF6B4A"),
                accentEmerald = Color.parseColor("#10B981"),
                accentAmber = Color.parseColor("#F59E0B"),
                dangerRed = Color.parseColor("#EF4444")
            )
        }
    }
}

class NativeTurboDesktopContainer(
    context: Context,
    private val nativeWidgetHost: NativeWidgetHostManager,
    private val callbacks: NativeTurboDesktopCallbacks
) : DirectionLockSwipeLayout(context) {

    private val density = context.resources.displayMetrics.density
    private fun dp(value: Int): Int = (value * density).roundToInt()

    private val rootRow: LinearLayout
    private val dockContainer: LinearLayout
    private val dockClockBox: LinearLayout
    private val dockClockTv: TextView
    private val dockBatteryTv: TextView
    private val dockArmedDot: View
    private val dockButtons = mutableListOf<TextView>()
    private val deckScroll: HorizontalScrollView
    private val cardRow: LinearLayout

    // Leaf references updated on 1-second clock tick without rebuilding deck!
    private var clockCardTimeTv: TextView? = null
    private var clockCardSecTv: TextView? = null
    private var clockCardDateTv: TextView? = null
    private var lastClockTick: ClockTelemetryState = ClockTelemetryState()
    private var lastSignature: String = ""
    private var currentPalette: NativePalette = NativePalette.forTheme(false)

    init {
        onVerticalSwipeUp = {
            callbacks.onGestureHaptic()
            callbacks.onOpenAppleTvDrawer()
        }
        onVerticalSwipeDown = {
            callbacks.onGestureHaptic()
            callbacks.onOpenOverlay(DesktopOverlay.CONTROL_CENTER)
        }

        rootRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        addView(rootRow)

        // Left 70dp Native Dock (Swiss industrial zero-overdraw)
        dockContainer = LinearLayout(context).apply {
            tag = "native_left_dock"
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(5), dp(6), dp(5), dp(6))
            layoutParams = LinearLayout.LayoutParams(dp(70), LinearLayout.LayoutParams.MATCH_PARENT).apply {
                marginEnd = dp(8)
            }
        }
        rootRow.addView(dockContainer)

        // Clock & Battery trigger (5 taps + long press -> Secret Vault)
        dockClockBox = LinearLayout(context).apply {
            tag = "native_dock_clock_trigger"
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isClickable = true
            isLongClickable = true
            setPadding(dp(2), dp(2), dp(2), dp(2))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener { callbacks.onClockTap() }
            setOnLongClickListener {
                callbacks.onClockLongPress()
                true
            }
        }
        dockClockTv = TextView(context).apply {
            text = "08:30"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 1
        }
        dockBatteryTv = TextView(context).apply {
            text = "85%"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            gravity = Gravity.CENTER
            maxLines = 1
        }
        dockArmedDot = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(5), dp(5)).apply { topMargin = dp(2) }
            visibility = View.GONE
        }
        dockClockBox.addView(dockClockTv)
        dockClockBox.addView(dockBatteryTv)
        dockClockBox.addView(dockArmedDot)
        dockContainer.addView(dockClockBox)

        // Spacer pushing 6 action buttons cleanly to bottom of dock
        dockContainer.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(1, 0, 1f)
        })

        // 3x2 grid of zero-lag native circular buttons
        val gridSpecs = listOf(
            listOf(
                Triple("词", "native_dock_vocab_btn") { callbacks.onOpenVocab() },
                Triple("实", "native_dock_lab_btn") { callbacks.onOpenLab() }
            ),
            listOf(
                Triple("控", "native_dock_control_btn") { callbacks.onOpenOverlay(DesktopOverlay.CONTROL_CENTER) },
                Triple("原", "native_dock_deck_btn") {
                    val next = if (lastSignature.contains("|native_only|")) PenConfig.DECK_MODE_MIXED else PenConfig.DECK_MODE_NATIVE_ONLY
                    callbacks.onSetDeckMode(next)
                }
            ),
            listOf(
                Triple("息", "native_dock_standby_btn") { callbacks.onEnterStandBy() },
                Triple("昼", "native_dock_theme_btn") { callbacks.onToggleQuickTheme() }
            )
        )
        gridSpecs.forEachIndexed { rIdx, rowItems ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    if (rIdx > 0) topMargin = dp(4)
                }
            }
            rowItems.forEachIndexed { cIdx, (label, viewTag, action) ->
                val btn = TextView(context).apply {
                    tag = viewTag
                    contentDescription = label
                    text = label
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    isClickable = true
                    layoutParams = LinearLayout.LayoutParams(dp(25), dp(25)).apply {
                        if (cIdx > 0) marginStart = dp(4)
                    }
                    setOnClickListener { action() }
                }
                dockButtons.add(btn)
                row.addView(btn)
            }
            dockContainer.addView(row)
        }

        // Right Hardware-Accelerated Native HorizontalScrollView Deck
        deckScroll = HorizontalScrollView(context).apply {
            tag = "native_widget_deck_scroll"
            isHorizontalScrollBarEnabled = false
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_ALWAYS
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        }
        cardRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
        }
        deckScroll.addView(cardRow)
        rootRow.addView(deckScroll)
    }

    fun updateClockTick(tick: ClockTelemetryState) {
        lastClockTick = tick
        if (dockClockTv.text != tick.timeHHmm) {
            dockClockTv.text = tick.timeHHmm
        }
        val batText = if (tick.isCharging) "⚡${tick.batteryPercent}%" else "${tick.batteryPercent}%"
        if (dockBatteryTv.text != batText) {
            dockBatteryTv.text = batText
        }
        clockCardTimeTv?.let { tv ->
            if (tv.text != tick.timeHHmm) tv.text = tick.timeHHmm
        }
        clockCardSecTv?.let { tv ->
            val sec = ":${tick.secondsStr}"
            if (tv.text != sec) tv.text = sec
        }
        clockCardDateTv?.let { tv ->
            if (tv.text != tick.dateLabel) tv.text = tick.dateLabel
        }
    }

    fun bindState(state: PenOsUiState) {
        swipeThresholdPx = state.config.swipeSensitivityDp * density
        gesturesEnabled = state.activeOverlay == DesktopOverlay.NONE

        val isLight = state.config.isAppleLightDesktop()
        val isNativeOnly = state.config.desktopDeckMode == PenConfig.DECK_MODE_NATIVE_ONLY
        val isArmed = state.secretTrigger.isArmed
        val customDesktopWidgets = state.customWidgets.filter {
            it.placement == CustomWidgetEntity.PLACEMENT_DESKTOP || it.placement == CustomWidgetEntity.PLACEMENT_BOTH
        }

        val sig = buildString {
            append(isLight).append('|')
            append(state.config.desktopDeckMode).append('|')
            append(isArmed).append('|')
            append(state.config.enabledWidgetIds.joinToString(",")).append('|')
            append(state.config.multiClock.style).append(':').append(state.config.multiClock.showSeconds).append('|')
            append(state.config.scanAppPackage).append(':').append(state.config.scanActivityClass).append('|')
            append(state.vocabSession.activeBook).append(':').append(state.vocabSession.masteredCount).append(':')
                .append(state.vocabSession.mistakeCount).append(':').append(state.config.vocabDailyGoal).append('|')
            append(state.weather.cityName).append(':').append(state.weather.tempC).append(':').append(state.weather.weatherCode).append('|')
            append(state.mail.unreadCount).append(':').append(state.mail.messages.firstOrNull()?.subject.orEmpty()).append('|')
            append(state.todos.count { !it.isDone }).append(':').append(state.todos.firstOrNull { !it.isDone }?.title.orEmpty()).append('|')
            append(state.calendarEvents.size).append(':').append(state.calendarEvents.firstOrNull()?.title.orEmpty()).append('|')
            append(state.poem.content.take(18)).append('|')
            append(state.config.pinnedDockPackages.joinToString(",")).append('|')
            append(state.clock.availRamMb).append(':').append(state.clock.isWifiConnected).append('|')
            append(customDesktopWidgets.joinToString(",") { "${it.id}@${it.updatedAt}" }).append('|')
            append(state.config.nativeWidgetIds.joinToString(","))
        }

        if (sig == lastSignature) return
        lastSignature = sig

        val palette = NativePalette.forTheme(isLight)
        currentPalette = palette

        // 1. Opaque root background (zero overdraw)
        setBackgroundColor(palette.canvasBg)

        // 2. Style Dock
        dockContainer.background = flatRoundedBg(
            fill = palette.cardElevatedBg,
            stroke = if (isArmed) palette.accentCoral else palette.border,
            strokeWidthPx = if (isArmed) dp(2) else dp(1),
            radiusPx = dp(16)
        )
        dockClockTv.setTextColor(if (isArmed) palette.accentCoral else palette.textPrimary)
        dockBatteryTv.setTextColor(palette.textSecondary)
        dockArmedDot.visibility = if (isArmed) View.VISIBLE else View.GONE
        dockArmedDot.background = circleBg(palette.accentCoral)

        val buttonColors = listOf(
            palette.accentEmerald,
            palette.accentBlue,
            palette.accentCoral,
            if (isNativeOnly) palette.accentBlue else palette.accentAmber,
            palette.accentAmber,
            palette.accentBlue
        )
        dockButtons.forEachIndexed { idx, btn ->
            val accent = buttonColors.getOrElse(idx) { palette.accentBlue }
            val activeHighlight = (idx == 3 && isNativeOnly)
            btn.background = circleBg(if (activeHighlight) palette.accentBlue else palette.chipBg)
            btn.setTextColor(if (activeHighlight) Color.WHITE else accent)
            if (idx == 5) {
                btn.text = if (isLight) "夜" else "昼"
            }
        }

        // 3. Rebuild Card Row ONLY when deck data/config changed
        cardRow.removeAllViews()
        clockCardTimeTv = null
        clockCardSecTv = null
        clockCardDateTv = null

        if (!isNativeOnly) {
            state.config.enabledWidgetIds.forEach { widgetId ->
                val card = buildBuiltinNativeCard(widgetId, state, palette)
                if (card != null) {
                    cardRow.addView(card)
                }
            }
            customDesktopWidgets.forEach { cw ->
                cardRow.addView(buildCustomWidgetSummaryCard(cw, palette))
            }
        }

        // Native Android AppWidgetHostViews mounted directly into HorizontalScrollView!
        state.config.nativeWidgetIds.forEach { widgetId ->
            val widthDp = state.config.nativeWidgetWidths[widgetId] ?: 200
            val hostView = nativeWidgetHost.createHostView(context, widgetId, widthDp, 152)
            if (hostView != null) {
                (hostView.parent as? ViewGroup)?.removeView(hostView)
                val wrapper = FrameLayout(context).apply {
                    tag = "native_appwidget_$widgetId"
                    background = flatRoundedBg(palette.cardBg, palette.border, dp(1), dp(16))
                    setPadding(dp(4), dp(4), dp(4), dp(4))
                    layoutParams = cardLayoutParams(widthDp)
                    addView(hostView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                }
                cardRow.addView(wrapper)
            }
        }

        if (isNativeOnly) {
            cardRow.addView(buildAddNativeWidgetCard(palette))
        }

        updateClockTick(lastClockTick)
    }

    private fun cardLayoutParams(widthDp: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(dp(widthDp), LinearLayout.LayoutParams.MATCH_PARENT).apply {
            marginEnd = dp(8)
        }

    private fun createCardShell(
        widthDp: Int,
        viewTag: String,
        palette: NativePalette,
        borderColor: Int = palette.border,
        onClick: (() -> Unit)? = null
    ): LinearLayout = LinearLayout(context).apply {
        tag = viewTag
        orientation = LinearLayout.VERTICAL
        background = flatRoundedBg(palette.cardBg, borderColor, dp(1), dp(16))
        setPadding(dp(12), dp(10), dp(12), dp(10))
        layoutParams = cardLayoutParams(widthDp)
        if (onClick != null) {
            isClickable = true
            setOnClickListener { onClick() }
        }
    }

    private fun buildBuiltinNativeCard(
        widgetId: String,
        state: PenOsUiState,
        palette: NativePalette
    ): View? = when (widgetId) {
        "quick_scan" -> buildQuickScanCard(state, palette)
        "multi_clock" -> buildMultiClockCard(state, palette)
        "vocab_card" -> buildVocabCard(state, palette)
        "weather" -> buildWeatherCard(state, palette)
        "mail_inbox" -> buildMailCard(state, palette)
        "calendar" -> buildCalendarCard(state, palette)
        "todo" -> buildTodoCard(state, palette)
        "jinrishici" -> buildPoetryCard(state, palette)
        "pinned_apps" -> buildPinnedAppsCard(state, palette)
        "pen_status" -> buildStatusCard(state, palette)
        else -> null
    }

    private fun buildQuickScanCard(state: PenOsUiState, palette: NativePalette): View {
        val card = createCardShell(188, "native_card_quick_scan", palette, borderColor = palette.accentBlue) {
            callbacks.onLaunchScanApp()
        }
        val boundLabel = state.installedApps.firstOrNull { it.packageName == state.config.scanAppPackage }?.label
            ?: if (state.config.scanAppPackage.isBlank()) "密室绑定扫学组件" else "学王扫学"

        val header = horizontalHeaderRow(
            leftText = "极速查词",
            leftColor = palette.accentBlue,
            badgeText = "MT6582 极速",
            badgeBg = palette.chipBg,
            badgeTextColor = palette.textSecondary
        )
        card.addView(header)

        card.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(1, 0, 1f) })

        card.addView(TextView(context).apply {
            text = "学王扫学"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(palette.textPrimary)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        card.addView(TextView(context).apply {
            text = boundLabel
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTextColor(palette.textSecondary)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })

        card.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(1, 0, 1f) })

        val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        actionRow.addView(pillButton("一键扫学", palette.accentBlue, Color.WHITE) { callbacks.onLaunchScanApp() })
        actionRow.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(dp(6), 1) })
        actionRow.addView(pillButton("全部应用", palette.chipBg, palette.textPrimary) {
            callbacks.onOpenOverlay(DesktopOverlay.ALL_APPS)
        })
        card.addView(actionRow)
        return card
    }

    private fun buildMultiClockCard(state: PenOsUiState, palette: NativePalette): View {
        val card = createCardShell(160, "native_card_multi_clock", palette, borderColor = palette.accentCoral) {
            callbacks.onCycleClockStyle()
        }
        val styleName = MultiClockConfig.allStyles.firstOrNull { it.first == state.config.multiClock.style }?.second ?: "极简时钟"
        card.addView(horizontalHeaderRow(styleName, palette.accentCoral, "点切", palette.chipBg, palette.textSecondary))

        card.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(1, 0, 1f) })

        val centerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        centerRow.addView(View(context).apply {
            background = flatRoundedBg(palette.accentCoral, palette.accentCoral, 0, dp(2))
            layoutParams = LinearLayout.LayoutParams(dp(3), dp(42)).apply { marginEnd = dp(8) }
        })

        val timeCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val digitsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        val timeTv = TextView(context).apply {
            text = lastClockTick.timeHHmm
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            setTextColor(palette.textPrimary)
            maxLines = 1
        }
        val secTv = TextView(context).apply {
            text = ":${lastClockTick.secondsStr}"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(palette.accentCoral)
            visibility = if (state.config.multiClock.showSeconds) View.VISIBLE else View.GONE
            setPadding(dp(2), 0, 0, dp(4))
        }
        clockCardTimeTv = timeTv
        clockCardSecTv = secTv
        digitsRow.addView(timeTv)
        digitsRow.addView(secTv)
        timeCol.addView(digitsRow)

        val dateTv = TextView(context).apply {
            text = lastClockTick.dateLabel
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTextColor(palette.textSecondary)
            maxLines = 1
        }
        clockCardDateTv = dateTv
        timeCol.addView(dateTv)
        centerRow.addView(timeCol)
        card.addView(centerRow)

        card.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(1, 0, 1f) })
        return card
    }

    private fun buildVocabCard(state: PenOsUiState, palette: NativePalette): View {
        val card = createCardShell(184, "native_card_vocab_card", palette, borderColor = palette.accentEmerald) {
            callbacks.onOpenVocab()
        }
        val v = state.vocabSession
        val goal = state.config.vocabDailyGoal.coerceAtLeast(1)
        val frac = (v.dailyMasteredCount.toFloat() / goal.toFloat()).coerceIn(0f, 1f)
        val bookLabel = when (v.activeBook) {
            "middle" -> "中考"
            "cet4" -> "四级"
            "cet6" -> "六级"
            "daily" -> "日常"
            "mistakes" -> "错词"
            "custom" -> "自定义"
            else -> "高考"
        }

        card.addView(horizontalHeaderRow("百词斩背词", palette.accentEmerald, bookLabel, palette.chipBg, palette.accentEmerald))
        card.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(1, 0, 1f) })

        card.addView(TextView(context).apply {
            text = "今日 ${v.dailyMasteredCount} / $goal 词"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(palette.textPrimary)
            maxLines = 1
        })
        card.addView(TextView(context).apply {
            text = "已斩 ${v.masteredCount} · 错词 ${v.mistakeCount}"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTextColor(palette.textSecondary)
            maxLines = 1
        })

        card.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(1, dp(8)) })

        // Flat zero-overdraw progress bar
        val barTrack = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = flatRoundedBg(palette.chipBg, palette.chipBg, 0, dp(3))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(6))
        }
        val fillWeight = frac.coerceAtLeast(0.05f)
        barTrack.addView(View(context).apply {
            background = flatRoundedBg(palette.accentEmerald, palette.accentEmerald, 0, dp(3))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, fillWeight)
        })
        barTrack.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (1f - fillWeight).coerceAtLeast(0.001f))
        })
        card.addView(barTrack)

        card.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(1, 0, 1f) })

        card.addView(TextView(context).apply {
            text = "点按开始 4 选 1 斩词 ›"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTextColor(palette.accentEmerald)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        })
        return card
    }

    private fun buildWeatherCard(state: PenOsUiState, palette: NativePalette): View {
        val w = state.weather
        val card = createCardShell(166, "native_card_weather", palette) {
            callbacks.onOpenOverlay(DesktopOverlay.WEATHER)
        }
        card.addView(horizontalHeaderRow(w.cityName, palette.accentBlue, state.weatherSourceStatus, palette.chipBg, palette.textSecondary))
        card.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(1, 0, 1f) })
        card.addView(TextView(context).apply {
            text = "${w.tempC.toInt()}°"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(palette.textPrimary)
        })
        card.addView(TextView(context).apply {
            text = "${w.dailySummary.ifBlank { "多云" }} · ${w.tempMinC.toInt()}~${w.tempMaxC.toInt()}°"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTextColor(palette.textSecondary)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        return card
    }

    private fun buildMailCard(state: PenOsUiState, palette: NativePalette): View {
        val unread = state.mail.unreadCount
        val card = createCardShell(176, "native_card_mail_inbox", palette, borderColor = if (unread > 0) palette.dangerRed else palette.border) {
            callbacks.onOpenMail()
        }
        card.addView(
            horizontalHeaderRow(
                leftText = "笔端邮箱",
                leftColor = if (unread > 0) palette.dangerRed else palette.accentBlue,
                badgeText = if (unread > 0) "● $unread 未读" else "已同步",
                badgeBg = if (unread > 0) palette.dangerRed else palette.chipBg,
                badgeTextColor = if (unread > 0) Color.WHITE else palette.textSecondary
            )
        )
        card.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(1, 0, 1f) })
        val latest = state.mail.messages.firstOrNull()
        card.addView(TextView(context).apply {
            text = latest?.subject ?: "点击配置 IMAP/SMTP 邮箱"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(palette.textPrimary)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        })
        card.addView(TextView(context).apply {
            text = latest?.fromAddress ?: "支持 AI 总结与起草回复"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            setTextColor(palette.textSecondary)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        return card
    }

    private fun buildCalendarCard(state: PenOsUiState, palette: NativePalette): View {
        val card = createCardShell(168, "native_card_calendar", palette) {
            callbacks.onOpenOverlay(DesktopOverlay.CALENDAR)
        }
        card.addView(horizontalHeaderRow("日程安排", palette.accentAmber, "${state.calendarEvents.size} 项", palette.chipBg, palette.textSecondary))
        card.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(1, 0, 1f) })
        val next = state.calendarEvents.firstOrNull()
        card.addView(TextView(context).apply {
            text = next?.let { "${it.timeHHmm} ${it.title}" } ?: "今日暂无日程"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(palette.textPrimary)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        })
        return card
    }

    private fun buildTodoCard(state: PenOsUiState, palette: NativePalette): View {
        val pending = state.todos.filter { !it.isDone }
        val card = createCardShell(172, "native_card_todo", palette) {
            callbacks.onOpenOverlay(DesktopOverlay.TODO)
        }
        card.addView(horizontalHeaderRow("待办清单", palette.accentCoral, "余 ${pending.size}", palette.chipBg, palette.textSecondary))
        card.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(1, 0, 1f) })
        card.addView(TextView(context).apply {
            text = pending.firstOrNull()?.title ?: "全部待办已完成 ✓"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(palette.textPrimary)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        })
        return card
    }

    private fun buildPoetryCard(state: PenOsUiState, palette: NativePalette): View {
        val card = createCardShell(192, "native_card_jinrishici", palette) {
            callbacks.onOpenOverlay(DesktopOverlay.POEM)
        }
        card.addView(horizontalHeaderRow("每日诗词", palette.accentAmber, state.poem.dynasty, palette.chipBg, palette.textSecondary))
        card.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(1, 0, 1f) })
        card.addView(TextView(context).apply {
            text = state.poem.content
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.SERIF
            setTextColor(palette.textPrimary)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        })
        card.addView(TextView(context).apply {
            text = "— ${state.poem.author}《${state.poem.title}》"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            setTextColor(palette.textSecondary)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        return card
    }

    private fun buildPinnedAppsCard(state: PenOsUiState, palette: NativePalette): View {
        val card = createCardShell(164, "native_card_pinned_apps", palette) {
            callbacks.onOpenOverlay(DesktopOverlay.ALL_APPS)
        }
        card.addView(horizontalHeaderRow("常用应用", palette.accentBlue, "${state.config.pinnedDockPackages.size}", palette.chipBg, palette.textSecondary))
        card.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(1, 0, 1f) })
        val names = state.config.pinnedDockPackages.mapNotNull { pkg ->
            state.installedApps.firstOrNull { it.packageName == pkg }?.label
        }.take(3)
        card.addView(TextView(context).apply {
            text = if (names.isEmpty()) "点按打开全部应用" else names.joinToString(" · ")
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(palette.textPrimary)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        })
        return card
    }

    private fun buildStatusCard(state: PenOsUiState, palette: NativePalette): View {
        val card = createCardShell(158, "native_card_pen_status", palette) {
            callbacks.onOpenOverlay(DesktopOverlay.CONTROL_CENTER)
        }
        card.addView(horizontalHeaderRow("笔端状态", palette.accentEmerald, if (state.clock.isWifiConnected) "Wi-Fi" else "离线", palette.chipBg, palette.textSecondary))
        card.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(1, 0, 1f) })
        card.addView(TextView(context).apply {
            text = "RAM ${state.clock.availRamMb} MB"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(palette.textPrimary)
        })
        card.addView(TextView(context).apply {
            text = "原生 View 极速引擎运行中"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            setTextColor(palette.accentEmerald)
        })
        return card
    }

    private fun buildCustomWidgetSummaryCard(cw: CustomWidgetEntity, palette: NativePalette): View {
        val accent = cw.accentHex.toInt()
        val card = createCardShell(cw.widthDp.coerceIn(156, 220), "native_card_custom_${cw.id}", palette, borderColor = accent) {
            callbacks.onOpenAppleTvDrawer()
        }
        card.addView(horizontalHeaderRow(cw.title, accent, "MCP 组件", palette.chipBg, palette.textSecondary))
        card.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(1, 0, 1f) })
        card.addView(TextView(context).apply {
            text = "AI 声明式小组件 · 极速占位"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(palette.textPrimary)
            maxLines = 2
        })
        card.addView(TextView(context).apply {
            text = "点按进入完整交互抽屉 ›"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            setTextColor(accent)
        })
        return card
    }

    private fun buildAddNativeWidgetCard(palette: NativePalette): View {
        val card = createCardShell(172, "native_add_widget_card", palette, borderColor = palette.accentBlue) {
            callbacks.onRequestAddNativeWidget()
        }
        card.gravity = Gravity.CENTER
        card.addView(TextView(context).apply {
            text = "+ 添加系统原生小组件"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(palette.accentBlue)
            gravity = Gravity.CENTER
        })
        return card
    }

    private fun horizontalHeaderRow(
        leftText: String,
        leftColor: Int,
        badgeText: String,
        badgeBg: Int,
        badgeTextColor: Int
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        addView(TextView(context).apply {
            text = leftText
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(leftColor)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(TextView(context).apply {
            text = badgeText
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            setTextColor(badgeTextColor)
            background = flatRoundedBg(badgeBg, badgeBg, 0, dp(99))
            setPadding(dp(6), dp(2), dp(6), dp(2))
            maxLines = 1
        })
    }

    private fun pillButton(label: String, bg: Int, fg: Int, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(fg)
            background = flatRoundedBg(bg, bg, 0, dp(99))
            setPadding(dp(10), dp(4), dp(10), dp(4))
            isClickable = true
            setOnClickListener { onClick() }
        }

    private fun flatRoundedBg(fill: Int, stroke: Int, strokeWidthPx: Int, radiusPx: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx.toFloat()
            setColor(fill)
            if (strokeWidthPx > 0) setStroke(strokeWidthPx, stroke)
        }

    private fun circleBg(fill: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fill)
        }
}
