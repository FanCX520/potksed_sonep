package com.example.widgets

import com.example.data.local.CustomWidgetEntity
import org.json.JSONArray
import org.json.JSONObject

data class CustomWidgetTemplate(
    val id: String,
    val title: String,
    val badge: String,
    val description: String,
    val placement: String,
    val widthDp: Int,
    val accentHex: Long,
    val dslJson: String
) {
    fun toEntity(sortOrder: Int = 0): CustomWidgetEntity = CustomWidgetEntity(
        id = id,
        title = title,
        placement = placement,
        widthDp = widthDp,
        accentHex = accentHex,
        dslJson = dslJson,
        sortOrder = sortOrder,
        updatedAt = System.currentTimeMillis()
    )
}

/**
 * PenOS 5.0 Built-in 1-Tap Declarative Custom Widget Gallery.
 * Usable completely offline without an AI API key, showcasing the expanded 17-block engine.
 */
object CustomWidgetTemplates {

    val all: List<CustomWidgetTemplate> = listOf(
        CustomWidgetTemplate(
            id = "tpl_evening_study_macro",
            title = "晚自习一键宏",
            badge = "工作流",
            description = "护眼遮罩 + 雨声白噪音 + 45分番茄钟",
            placement = CustomWidgetEntity.PLACEMENT_BOTH,
            widthDp = 212,
            accentHex = 0xFF38BDF8L,
            dslJson = JSONArray()
                .put(JSONObject().put("type", "header").put("title", "沉浸晚自习流").put("badge", "MACRO"))
                .put(JSONObject().put("type", "text").put("content", "一键护眼暗屏 · 轻雨白噪音 · 45m 专注"))
                .put(
                    JSONObject()
                        .put("type", "macro_button")
                        .put("label", "开启晚自习模式")
                        .put("steps", JSONArray(listOf("night_eye_on", "white_noise:rain", "pomodoro:45")))
                )
                .put(
                    JSONObject()
                        .put("type", "macro_button")
                        .put("label", "下课恢复日间")
                        .put("steps", JSONArray(listOf("night_eye_off", "white_noise:stop")))
                )
                .toString()
        ),
        CustomWidgetTemplate(
            id = "tpl_habit_mood_tracker",
            title = "每日打卡 & 心境",
            badge = "交互",
            description = "饮水/背词计数条 + 学习状态标签",
            placement = CustomWidgetEntity.PLACEMENT_BOTH,
            widthDp = 216,
            accentHex = 0xFF10B981L,
            dslJson = JSONArray()
                .put(
                    JSONObject()
                        .put("type", "habit_counter")
                        .put("label", "今日刷题组数")
                        .put("value", 2)
                        .put("goal", 8)
                        .put("unit", "组")
                )
                .put(
                    JSONObject()
                        .put("type", "mood_picker")
                        .put("label", "当前状态")
                        .put("options", JSONArray(listOf("心流", "稳步", "疲惫", "冲刺")))
                        .put("selected", "心流")
                )
                .toString()
        ),
        CustomWidgetTemplate(
            id = "tpl_physics_flashcard",
            title = "理科公式翻面卡",
            badge = "闪卡",
            description = "点击翻面默背高频物理/英语考点",
            placement = CustomWidgetEntity.PLACEMENT_BOTH,
            widthDp = 210,
            accentHex = 0xFFF59E0BL,
            dslJson = JSONArray()
                .put(JSONObject().put("type", "header").put("title", "轻点翻转默背").put("badge", "FLASH"))
                .put(
                    JSONObject()
                        .put("type", "flashcard_mini")
                        .put("front", "动能定理 & 动量定理")
                        .put("back", "W合 = ½mv₂² - ½mv₁²  |  F·t = Δp")
                        .put("tag", "高频必背")
                )
                .toString()
        ),
        CustomWidgetTemplate(
            id = "tpl_mini_timetable",
            title = "今日迷你课表",
            badge = "日程",
            description = "紧凑时间段列表，自动高亮当前节次",
            placement = CustomWidgetEntity.PLACEMENT_BOTH,
            widthDp = 220,
            accentHex = 0xFFA855F7L,
            dslJson = JSONArray()
                .put(
                    JSONObject()
                        .put("type", "timetable")
                        .put("title", "自习与课程表")
                        .put(
                            "slots",
                            JSONArray()
                                .put(JSONObject().put("time", "08:00-09:30").put("name", "英语精读 & 听写"))
                                .put(JSONObject().put("time", "10:00-11:40").put("name", "数学压轴专题"))
                                .put(JSONObject().put("time", "14:30-16:10").put("name", "理综限时训练"))
                                .put(JSONObject().put("time", "19:00-21:30").put("name", "错题复盘 + 单词"))
                        )
                )
                .toString()
        ),
        CustomWidgetTemplate(
            id = "tpl_stopwatch_calc",
            title = "刷题秒表 & 速算",
            badge = "工具",
            description = "内置精准秒表计时 + 快捷单位换算计算器",
            placement = CustomWidgetEntity.PLACEMENT_BOTH,
            widthDp = 224,
            accentHex = 0xFFFF6B4AL,
            dslJson = JSONArray()
                .put(JSONObject().put("type", "stopwatch").put("label", "单题计时"))
                .put(JSONObject().put("type", "calc_pad").put("label", "速算"))
                .toString()
        ),
        CustomWidgetTemplate(
            id = "tpl_lan_json_sensor",
            title = "局域网 JSON 看板",
            badge = "接口",
            description = "安全拉取校园/家用 JSON 接口指标卡片",
            placement = CustomWidgetEntity.PLACEMENT_BOTH,
            widthDp = 216,
            accentHex = 0xFF06B6D4L,
            dslJson = JSONArray()
                .put(
                    JSONObject()
                        .put("type", "http_json")
                        .put("label", "局域网服务状态")
                        .put("url", "http://127.0.0.1:8765/api/ping")
                        .put("json_path", "status")
                        .put("unit", "")
                        .put("cached_value", "点此刷新")
                        .put("updated_at", "待拉取")
                )
                .put(
                    JSONObject()
                        .put("type", "sparkline")
                        .put("label", "本周专注分钟趋势")
                        .put("values", JSONArray(listOf(35, 50, 45, 70, 60, 85, 90)))
                )
                .toString()
        )
    )

    fun findById(id: String): CustomWidgetTemplate? = all.firstOrNull { it.id == id }
}
