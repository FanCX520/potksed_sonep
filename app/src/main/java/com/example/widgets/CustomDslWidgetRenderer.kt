package com.example.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CustomWidgetEntity
import com.example.data.remote.Api21NetworkFactory
import com.example.ui.theme.PenTheme
import com.example.ui.theme.springPressScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

data class TimetableSlotItem(
    val timeRange: String,
    val name: String
)

/**
 * PenOS 5.0 17-Block Pure Declarative Custom Widget DSL.
 * Zero dynamic code execution / zero eval / 100% crash-proof schema validation.
 */
sealed class DslWidgetBlock {
    data class Header(val title: String, val badge: String) : DslWidgetBlock()
    data class Metric(val label: String, val value: String, val unit: String) : DslWidgetBlock()
    data class TextLine(val content: String) : DslWidgetBlock()
    data class Progress(val label: String, val percent: Float) : DslWidgetBlock()
    data class Countdown(val label: String, val targetIso: String) : DslWidgetBlock()
    data class Checklist(val blockIndex: Int, val items: List<Pair<String, Boolean>>) : DslWidgetBlock()
    data class Quote(val content: String, val author: String) : DslWidgetBlock()
    data class Sparkline(val label: String, val values: List<Float>) : DslWidgetBlock()
    data class ActionButton(val label: String, val action: String, val target: String) : DslWidgetBlock()

    // PenOS 5.0 Expanded 8 Interactive & Utility Declarative Blocks
    data class Stopwatch(val label: String) : DslWidgetBlock()
    data class HabitCounter(
        val blockIndex: Int,
        val label: String,
        val value: Int,
        val goal: Int,
        val unit: String
    ) : DslWidgetBlock()
    data class MoodPicker(
        val blockIndex: Int,
        val label: String,
        val options: List<String>,
        val selected: String
    ) : DslWidgetBlock()
    data class CalcPad(val label: String) : DslWidgetBlock()
    data class HttpJson(
        val blockIndex: Int,
        val label: String,
        val url: String,
        val jsonPath: String,
        val unit: String,
        val cachedValue: String,
        val updatedAt: String
    ) : DslWidgetBlock()
    data class MacroButton(val label: String, val steps: List<String>) : DslWidgetBlock()
    data class Timetable(val title: String, val slots: List<TimetableSlotItem>) : DslWidgetBlock()
    data class FlashcardMini(val front: String, val back: String, val tag: String) : DslWidgetBlock()
}

object CustomWidgetDslParser {

    private const val MAX_HTTP_BODY_BYTES = 64 * 1024

    private val httpClient by lazy {
        Api21NetworkFactory.createClient(connectTimeoutSec = 5, readTimeoutSec = 5)
    }

    fun parse(dslJson: String): List<DslWidgetBlock> {
        val arr = runCatching { JSONArray(dslJson) }.getOrNull() ?: return emptyList()
        val blocks = mutableListOf<DslWidgetBlock>()
        for (i in 0 until minOf(arr.length(), 8)) {
            val obj = arr.optJSONObject(i) ?: continue
            when (obj.optString("type").lowercase()) {
                "header" -> blocks.add(DslWidgetBlock.Header(obj.optString("title"), obj.optString("badge")))
                "metric" -> blocks.add(DslWidgetBlock.Metric(obj.optString("label"), obj.optString("value"), obj.optString("unit")))
                "text" -> blocks.add(DslWidgetBlock.TextLine(obj.optString("content")))
                "progress" -> blocks.add(
                    DslWidgetBlock.Progress(
                        obj.optString("label"),
                        obj.optDouble("percent", 50.0).toFloat().coerceIn(0f, 100f) / 100f
                    )
                )
                "countdown" -> blocks.add(DslWidgetBlock.Countdown(obj.optString("label", "倒计时"), obj.optString("target_iso")))
                "checklist" -> {
                    val itemsArr = obj.optJSONArray("items") ?: JSONArray()
                    val list = (0 until minOf(itemsArr.length(), 5)).mapNotNull { j ->
                        val itObj = itemsArr.optJSONObject(j)
                        if (itObj != null) {
                            itObj.optString("text") to itObj.optBoolean("done", false)
                        } else {
                            itemsArr.optString(j).takeIf { it.isNotBlank() }?.let { it to false }
                        }
                    }
                    blocks.add(DslWidgetBlock.Checklist(i, list))
                }
                "quote" -> blocks.add(DslWidgetBlock.Quote(obj.optString("content"), obj.optString("author")))
                "sparkline" -> {
                    val vArr = obj.optJSONArray("values") ?: JSONArray()
                    val vals = (0 until minOf(vArr.length(), 20)).map { vArr.optDouble(it, 0.0).toFloat() }
                    blocks.add(DslWidgetBlock.Sparkline(obj.optString("label"), vals))
                }
                "action_button" -> blocks.add(
                    DslWidgetBlock.ActionButton(
                        obj.optString("label", "执行"),
                        obj.optString("action", "pomodoro"),
                        obj.optString("target", "")
                    )
                )
                "stopwatch" -> blocks.add(
                    DslWidgetBlock.Stopwatch(obj.optString("label", "秒表计时"))
                )
                "habit_counter" -> blocks.add(
                    DslWidgetBlock.HabitCounter(
                        blockIndex = i,
                        label = obj.optString("label", "每日打卡"),
                        value = obj.optInt("value", 0).coerceIn(0, 9999),
                        goal = obj.optInt("goal", 8).coerceIn(1, 9999),
                        unit = obj.optString("unit", "次")
                    )
                )
                "mood_picker" -> {
                    val optArr = obj.optJSONArray("options")
                    val opts = if (optArr != null && optArr.length() > 0) {
                        (0 until minOf(optArr.length(), 5)).mapNotNull { j ->
                            optArr.optString(j).trim().takeIf { it.isNotBlank() }
                        }
                    } else {
                        listOf("心流", "稳步", "疲惫", "冲刺")
                    }
                    blocks.add(
                        DslWidgetBlock.MoodPicker(
                            blockIndex = i,
                            label = obj.optString("label", "状态"),
                            options = opts,
                            selected = obj.optString("selected", opts.firstOrNull().orEmpty())
                        )
                    )
                }
                "calc_pad" -> blocks.add(
                    DslWidgetBlock.CalcPad(obj.optString("label", "速算"))
                )
                "http_json" -> blocks.add(
                    DslWidgetBlock.HttpJson(
                        blockIndex = i,
                        label = obj.optString("label", "JSON 指标"),
                        url = obj.optString("url"),
                        jsonPath = obj.optString("json_path", "value"),
                        unit = obj.optString("unit"),
                        cachedValue = obj.optString("cached_value", "点此刷新"),
                        updatedAt = obj.optString("updated_at", "")
                    )
                )
                "macro_button" -> {
                    val stepsArr = obj.optJSONArray("steps") ?: JSONArray()
                    val steps = (0 until minOf(stepsArr.length(), 6)).mapNotNull { j ->
                        stepsArr.optString(j).trim().takeIf { it.isNotBlank() }
                    }
                    blocks.add(
                        DslWidgetBlock.MacroButton(
                            label = obj.optString("label", "运行宏工作流"),
                            steps = steps
                        )
                    )
                }
                "timetable" -> {
                    val sArr = obj.optJSONArray("slots") ?: JSONArray()
                    val slots = (0 until minOf(sArr.length(), 5)).mapNotNull { j ->
                        val so = sArr.optJSONObject(j) ?: return@mapNotNull null
                        val time = so.optString("time").trim()
                        val name = so.optString("name").trim()
                        if (name.isBlank()) null else TimetableSlotItem(time, name)
                    }
                    blocks.add(
                        DslWidgetBlock.Timetable(
                            title = obj.optString("title", "今日课表"),
                            slots = slots
                        )
                    )
                }
                "flashcard_mini" -> blocks.add(
                    DslWidgetBlock.FlashcardMini(
                        front = obj.optString("front", "点击查看背面"),
                        back = obj.optString("back", "暂无释义"),
                        tag = obj.optString("tag", "闪卡")
                    )
                )
            }
        }
        return blocks
    }

    fun toggleChecklistItem(dslJson: String, blockIndex: Int, itemIndex: Int): String {
        val arr = runCatching { JSONArray(dslJson) }.getOrNull() ?: return dslJson
        val block = arr.optJSONObject(blockIndex) ?: return dslJson
        val items = block.optJSONArray("items") ?: return dslJson
        val entry = items.optJSONObject(itemIndex) ?: return dslJson
        entry.put("done", !entry.optBoolean("done", false))
        return arr.toString()
    }

    fun adjustHabitCounter(dslJson: String, blockIndex: Int, delta: Int): String {
        val arr = runCatching { JSONArray(dslJson) }.getOrNull() ?: return dslJson
        val block = arr.optJSONObject(blockIndex) ?: return dslJson
        val next = (block.optInt("value", 0) + delta).coerceIn(0, 9999)
        block.put("value", next)
        return arr.toString()
    }

    fun selectMoodOption(dslJson: String, blockIndex: Int, option: String): String {
        val arr = runCatching { JSONArray(dslJson) }.getOrNull() ?: return dslJson
        val block = arr.optJSONObject(blockIndex) ?: return dslJson
        block.put("selected", option)
        return arr.toString()
    }

    fun updateHttpJsonCache(
        dslJson: String,
        blockIndex: Int,
        value: String,
        updatedAtLabel: String
    ): String {
        val arr = runCatching { JSONArray(dslJson) }.getOrNull() ?: return dslJson
        val block = arr.optJSONObject(blockIndex) ?: return dslJson
        block.put("cached_value", value.take(40))
        block.put("updated_at", updatedAtLabel)
        return arr.toString()
    }

    /**
     * Strictly safe HTTP JSON key-path fetcher:
     * - Allows only http:// or https:// URLs
     * - 5s connect/read timeout, <= 64KB response body cap
     * - Extracts pure scalar values via dot-path (e.g. "data.temp" or "items.0.name")
     */
    suspend fun fetchHttpJsonValue(url: String, jsonPath: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUrl = url.trim()
            if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                throw IllegalArgumentException("仅允许 http:// 或 https:// JSON 接口")
            }
            val req = Request.Builder().url(cleanUrl).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                val source = resp.body?.source() ?: throw IllegalStateException("响应为空")
                val peek = source.peek()
                peek.request(MAX_HTTP_BODY_BYTES.toLong() + 1L)
                val bytes = source.readByteArray().let {
                    if (it.size > MAX_HTTP_BODY_BYTES) it.copyOf(MAX_HTTP_BODY_BYTES) else it
                }
                val text = String(bytes, Charsets.UTF_8).trim()
                extractJsonPath(text, jsonPath)
                    ?: throw IllegalStateException("未在 JSON 中找到路径 $jsonPath")
            }
        }
    }

    fun extractJsonPath(rawJson: String, dotPath: String): String? {
        val trimmed = rawJson.trim()
        var current: Any? = when {
            trimmed.startsWith("{") -> runCatching { JSONObject(trimmed) }.getOrNull()
            trimmed.startsWith("[") -> runCatching { JSONArray(trimmed) }.getOrNull()
            else -> return null
        }
        val segments = dotPath.split(".").map { it.trim() }.filter { it.isNotBlank() }
        for (seg in segments) {
            current = when (current) {
                is JSONObject -> if (current.has(seg)) current.opt(seg) else null
                is JSONArray -> seg.toIntOrNull()?.let { idx ->
                    if (idx in 0 until current.length()) current.opt(idx) else null
                }
                else -> null
            }
            if (current == null || current == JSONObject.NULL) return null
        }
        return current?.toString()?.take(48)
    }

    fun isCurrentTimeInRange(range: String): Boolean {
        val parts = range.split("-")
        if (parts.size != 2) return false
        fun toMin(hhmm: String): Int? {
            val p = hhmm.trim().split(":")
            if (p.size != 2) return null
            val h = p[0].toIntOrNull() ?: return null
            val m = p[1].toIntOrNull() ?: return null
            return h * 60 + m
        }
        val start = toMin(parts[0]) ?: return false
        val end = toMin(parts[1]) ?: return false
        val cal = Calendar.getInstance()
        val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return nowMin in start..end
    }

    fun computeDaysRemaining(targetIso: String): Long {
        return runCatching {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val target = sdf.parse(targetIso.trim()) ?: return@runCatching 0L
            val diffMs = target.time - System.currentTimeMillis()
            TimeUnit.MILLISECONDS.toDays(diffMs).coerceAtLeast(0L)
        }.getOrDefault(0L)
    }

    fun parseHexColor(hex: String?, fallback: Long = 0xFF38BDF8): Long {
        val clean = hex?.trim()?.removePrefix("#").orEmpty()
        return when (clean.length) {
            6 -> ("FF$clean").toLongOrNull(16) ?: fallback
            8 -> clean.toLongOrNull(16) ?: fallback
            else -> fallback
        }
    }
}

@Composable
fun CustomDslWidgetCard(
    widget: CustomWidgetEntity,
    onToggleChecklist: (widgetId: String, blockIndex: Int, itemIndex: Int) -> Unit,
    onInvokeAction: (action: String, target: String) -> Unit,
    onMutateDsl: (widget: CustomWidgetEntity, updatedDslJson: String) -> Unit = { _, _ -> },
    onRunMacro: (steps: List<String>) -> Unit = { steps ->
        steps.firstOrNull()?.let { first ->
            val p = first.split(":")
            onInvokeAction(p[0], p.getOrElse(1) { "" })
        }
    },
    onRefreshHttpBlock: (widget: CustomWidgetEntity, blockIndex: Int, url: String, jsonPath: String) -> Unit = { _, _, _, _ -> },
    onDelete: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val accent = Color(widget.accentHex)
    val blocks = remember(widget.dslJson) { CustomWidgetDslParser.parse(widget.dslJson) }
    val colors = PenTheme.colors

    AdaptiveWidgetCard(
        modifier = modifier
            .width(widget.widthDp.coerceIn(150, 280).dp)
            .testTag("custom_widget_${widget.id}"),
        darkBorderColor = accent.copy(alpha = 0.40f)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = widget.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (onDelete != null) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "删除自定义小组件",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(14.dp)
                            .clickable { onDelete(widget.id) }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                blocks.take(4).forEach { block ->
                    when (block) {
                        is DslWidgetBlock.Header -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(block.title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                                if (block.badge.isNotBlank()) {
                                    Text(block.badge, style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        is DslWidgetBlock.Metric -> {
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = block.value,
                                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 22.sp),
                                    color = accent,
                                    fontWeight = FontWeight.Bold
                                )
                                if (block.unit.isNotBlank()) {
                                    Text(block.unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(block.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        is DslWidgetBlock.Countdown -> {
                            val days = remember(block.targetIso) { CustomWidgetDslParser.computeDaysRemaining(block.targetIso) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Column {
                                    Text(block.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    Text(block.targetIso, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = "$days",
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontFamily = PenTheme.clockFont,
                                            fontSize = 24.sp
                                        ),
                                        color = accent,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(" 天", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                        is DslWidgetBlock.Progress -> {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(block.label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    Text("${(block.percent * 100).toInt()}%", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = accent)
                                }
                                LinearProgressIndicator(
                                    progress = { block.percent },
                                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(99.dp)),
                                    color = accent,
                                    trackColor = colors.chipSurface
                                )
                            }
                        }
                        is DslWidgetBlock.Checklist -> {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                block.items.take(3).forEachIndexed { idx, (text, done) ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { onToggleChecklist(widget.id, block.blockIndex, idx) }
                                            .padding(vertical = 1.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (done) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                                            contentDescription = null,
                                            tint = if (done) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = text,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 10.sp,
                                                textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None
                                            ),
                                            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                        is DslWidgetBlock.TextLine -> {
                            Text(
                                text = block.content,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        is DslWidgetBlock.Quote -> {
                            Column {
                                Text(
                                    text = "「${block.content}」",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = PenTheme.poetryFont,
                                        fontSize = 11.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (block.author.isNotBlank()) {
                                    Text("— ${block.author}", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = accent)
                                }
                            }
                        }
                        is DslWidgetBlock.Sparkline -> {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                if (block.label.isNotBlank()) {
                                    Text(block.label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (block.values.size >= 2) {
                                    Canvas(modifier = Modifier.fillMaxWidth().height(24.dp)) {
                                        val minV = block.values.minOrNull() ?: 0f
                                        val maxV = (block.values.maxOrNull() ?: 1f).let { if (it <= minV) minV + 1f else it }
                                        val path = Path()
                                        block.values.forEachIndexed { i, v ->
                                            val x = (i.toFloat() / (block.values.size - 1)) * size.width
                                            val y = size.height - ((v - minV) / (maxV - minV)) * (size.height - 4f) - 2f
                                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                        }
                                        drawPath(path, color = accent, style = Stroke(width = 2.2f, cap = StrokeCap.Round))
                                    }
                                }
                            }
                        }
                        is DslWidgetBlock.ActionButton -> {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = accent.copy(alpha = 0.18f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .springPressScale { onInvokeAction(block.action, block.target) }
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 6.dp)
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = accent, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(block.label, style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        // --- PenOS 5.0 Expanded 8 Declarative Blocks ---
                        is DslWidgetBlock.Stopwatch -> {
                            StopwatchBlockView(label = block.label, accent = accent)
                        }
                        is DslWidgetBlock.HabitCounter -> {
                            val pct = (block.value.toFloat() / block.goal.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(block.label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                        Text(
                                            text = "${block.value}/${block.goal} ${block.unit}",
                                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = PenTheme.clockFont, fontSize = 13.sp),
                                            color = accent,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(colors.chipSurface)
                                                .clickable {
                                                    val next = CustomWidgetDslParser.adjustHabitCounter(widget.dslJson, block.blockIndex, -1)
                                                    onMutateDsl(widget, next)
                                                }
                                        ) {
                                            Icon(Icons.Rounded.Remove, contentDescription = "减1", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(13.dp))
                                        }
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(accent.copy(alpha = 0.22f))
                                                .clickable {
                                                    val next = CustomWidgetDslParser.adjustHabitCounter(widget.dslJson, block.blockIndex, +1)
                                                    onMutateDsl(widget, next)
                                                }
                                        ) {
                                            Icon(Icons.Rounded.Add, contentDescription = "加1", tint = accent, modifier = Modifier.size(13.dp))
                                        }
                                    }
                                }
                                LinearProgressIndicator(
                                    progress = { pct },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(99.dp)),
                                    color = accent,
                                    trackColor = colors.chipSurface
                                )
                            }
                        }
                        is DslWidgetBlock.MoodPicker -> {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                if (block.label.isNotBlank()) {
                                    Text(block.label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    block.options.take(4).forEach { opt ->
                                        val active = opt == block.selected
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (active) accent.copy(alpha = 0.24f) else colors.chipSurface,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable {
                                                    val next = CustomWidgetDslParser.selectMoodOption(widget.dslJson, block.blockIndex, opt)
                                                    onMutateDsl(widget, next)
                                                }
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(vertical = 3.dp)
                                            ) {
                                                Text(
                                                    text = opt,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                                                    color = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        is DslWidgetBlock.CalcPad -> {
                            CompactCalcPadBlock(label = block.label, accent = accent)
                        }
                        is DslWidgetBlock.HttpJson -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.chipSurface.copy(alpha = 0.7f))
                                    .clickable {
                                        onRefreshHttpBlock(widget, block.blockIndex, block.url, block.jsonPath)
                                    }
                                    .padding(horizontal = 7.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(block.label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    Text(
                                        text = "${block.cachedValue}${if (block.unit.isNotBlank()) " ${block.unit}" else ""}",
                                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                                        color = accent,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = "刷新 JSON", tint = accent, modifier = Modifier.size(13.dp))
                                    if (block.updatedAt.isNotBlank()) {
                                        Text(block.updatedAt, style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        is DslWidgetBlock.MacroButton -> {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = accent.copy(alpha = 0.20f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .springPressScale { onRunMacro(block.steps) }
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 6.dp)
                                ) {
                                    Icon(Icons.Rounded.Bolt, contentDescription = null, tint = accent, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "${block.label} (${block.steps.size}步)",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = accent,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        is DslWidgetBlock.Timetable -> {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                block.slots.take(4).forEach { slot ->
                                    val active = CustomWidgetDslParser.isCurrentTimeInRange(slot.timeRange)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(if (active) accent.copy(alpha = 0.18f) else Color.Transparent)
                                            .padding(horizontal = 4.dp, vertical = 1.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = slot.timeRange,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                                            color = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = slot.name,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                                            color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                        is DslWidgetBlock.FlashcardMini -> {
                            var flipped by remember(block.front, block.back) { mutableStateOf(false) }
                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = if (flipped) accent.copy(alpha = 0.16f) else colors.chipSurface.copy(alpha = 0.78f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(0.8.dp, accent.copy(alpha = if (flipped) 0.55f else 0.22f), RoundedCornerShape(9.dp))
                                    .clip(RoundedCornerShape(9.dp))
                                    .clickable { flipped = !flipped }
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(
                                            text = if (flipped) "背面解析" else block.tag,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                                            color = accent,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text("轻点翻面", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (flipped) block.back else block.front,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StopwatchBlockView(label: String, accent: Color) {
    var running by remember { mutableStateOf(false) }
    var elapsedSec by remember { mutableIntStateOf(0) }
    val colors = PenTheme.colors

    LaunchedEffect(running) {
        while (running) {
            delay(1000L)
            elapsedSec += 1
        }
    }

    val mm = elapsedSec / 60
    val ss = elapsedSec % 60
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.chipSurface.copy(alpha = 0.65f))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = "%02d:%02d".format(mm, ss),
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = PenTheme.clockFont, fontSize = 16.sp),
                color = accent,
                fontWeight = FontWeight.Bold
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(accent.copy(alpha = 0.22f))
                    .clickable { running = !running }
            ) {
                Icon(
                    imageVector = if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (running) "暂停秒表" else "开始秒表",
                    tint = accent,
                    modifier = Modifier.size(13.dp)
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.chipSurface)
                    .clickable {
                        running = false
                        elapsedSec = 0
                    }
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = "重置秒表", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
            }
        }
    }
}

@Composable
private fun CompactCalcPadBlock(label: String, accent: Color) {
    var value by remember { mutableIntStateOf(0) }
    val colors = PenTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.chipSurface.copy(alpha = 0.55f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label = $value",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = PenTheme.clockFont, fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf("+1" to 1, "+10" to 10, "×2" to -2, "C" to 0).forEach { (txt, op) ->
                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = if (txt == "C") colors.chipSurface else accent.copy(alpha = 0.18f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .clickable {
                            value = when (op) {
                                0 -> 0
                                -2 -> (value * 2).coerceAtMost(99999)
                                else -> (value + op).coerceAtMost(99999)
                            }
                        }
                ) {
                    Text(
                        text = txt,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = if (txt == "C") MaterialTheme.colorScheme.onSurfaceVariant else accent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
