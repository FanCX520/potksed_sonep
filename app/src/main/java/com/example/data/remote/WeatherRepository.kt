package com.example.data.remote

import com.example.data.local.CachedWeatherEntity
import com.example.data.local.WeatherDao
import com.example.data.prefs.PenPreferencesRepository
import com.example.data.prefs.SavedCityEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.roundToInt

data class WeatherCityOption(
    val displayName: String,
    val regionLabel: String,
    val cmaCode: String,
    val latitude: Double,
    val longitude: Double,
    val pinyin: String = ""
) {
    fun toSavedEntry(): SavedCityEntry = SavedCityEntry(displayName, regionLabel, cmaCode, latitude, longitude)
}

data class WeatherSyncResult(
    val weather: CachedWeatherEntity,
    val isLive: Boolean,
    val sourceLabel: String,
    val errorHint: String? = null
)

object WmoWeatherMapper {
    fun describe(code: Int): String = when (code) {
        0 -> "晴朗"
        1 -> "大部晴朗"
        2 -> "多云"
        3 -> "阴天"
        45, 48 -> "有雾"
        51, 53, 55 -> "小雨"
        61, 63, 65 -> "降雨"
        66, 67 -> "冻雨"
        71, 73, 75, 77 -> "降雪"
        80, 81, 82 -> "阵雨"
        85, 86 -> "阵雪"
        95, 96, 99 -> "雷阵雨"
        else -> "晴间多云"
    }

    fun textToWmoCode(desc: String): Int = when {
        desc.contains("雷") -> 95
        desc.contains("雪") -> 71
        desc.contains("阵雨") -> 80
        desc.contains("雨") -> 61
        desc.contains("雾") || desc.contains("霾") -> 45
        desc.contains("阴") -> 3
        desc.contains("多云") -> 2
        desc.contains("晴") -> 0
        else -> 1
    }

    // 55+ Major Chinese cities with offline CMA 9-digit codes + lat/lon + pinyin (100% instant search even offline!)
    val presetCities: List<WeatherCityOption> = listOf(
        WeatherCityOption("北京", "北京", "101010100", 39.9042, 116.4074, "beijing bj"),
        WeatherCityOption("上海", "上海", "101020100", 31.2304, 121.4737, "shanghai sh"),
        WeatherCityOption("天津", "天津", "101030100", 39.0842, 117.2009, "tianjin tj"),
        WeatherCityOption("重庆", "重庆", "101040100", 29.5630, 106.5516, "chongqing cq"),
        WeatherCityOption("广州", "广东", "101280101", 23.1291, 113.2644, "guangzhou gz"),
        WeatherCityOption("深圳", "广东", "101280601", 22.5431, 114.0579, "shenzhen sz"),
        WeatherCityOption("杭州", "浙江", "101210101", 30.2741, 120.1551, "hangzhou hz"),
        WeatherCityOption("南京", "江苏", "101190101", 32.0603, 118.7969, "nanjing nj"),
        WeatherCityOption("苏州", "江苏", "101190401", 31.2989, 120.5853, "suzhou"),
        WeatherCityOption("成都", "四川", "101270101", 30.5728, 104.0668, "chengdu cd"),
        WeatherCityOption("武汉", "湖北", "101200101", 30.5928, 114.3055, "wuhan wh"),
        WeatherCityOption("西安", "陕西", "101110101", 34.3416, 108.9398, "xian xa"),
        WeatherCityOption("长沙", "湖南", "101250101", 28.2282, 112.9388, "changsha cs"),
        WeatherCityOption("郑州", "河南", "101180101", 34.7466, 113.6253, "zhengzhou zz"),
        WeatherCityOption("济南", "山东", "101120101", 36.6512, 117.1201, "jinan jn"),
        WeatherCityOption("青岛", "山东", "101120201", 36.0671, 120.3826, "qingdao qd"),
        WeatherCityOption("合肥", "安徽", "101220101", 31.8206, 117.2272, "hefei hf"),
        WeatherCityOption("福州", "福建", "101230101", 26.0745, 119.2965, "fuzhou fz"),
        WeatherCityOption("厦门", "福建", "101230201", 24.4798, 118.0894, "xiamen xm"),
        WeatherCityOption("宁波", "浙江", "101210401", 29.8683, 121.5440, "ningbo nb"),
        WeatherCityOption("温州", "浙江", "101210701", 27.9943, 120.6994, "wenzhou wz"),
        WeatherCityOption("无锡", "江苏", "101190201", 31.4912, 120.3119, "wuxi wx"),
        WeatherCityOption("常州", "江苏", "101191101", 31.8112, 119.9741, "changzhou"),
        WeatherCityOption("东莞", "广东", "101281601", 23.0207, 113.7518, "dongguan dg"),
        WeatherCityOption("佛山", "广东", "101280800", 23.0215, 113.1214, "foshan fs"),
        WeatherCityOption("珠海", "广东", "101280701", 22.2710, 113.5767, "zhuhai zh"),
        WeatherCityOption("沈阳", "辽宁", "101070101", 41.8057, 123.4315, "shenyang sy"),
        WeatherCityOption("大连", "辽宁", "101070201", 38.9140, 121.6147, "dalian dl"),
        WeatherCityOption("长春", "吉林", "101060101", 43.8171, 125.3235, "changchun cc"),
        WeatherCityOption("哈尔滨", "黑龙江", "101050101", 45.8038, 126.5350, "haerbin heb"),
        WeatherCityOption("石家庄", "河北", "101090101", 38.0428, 114.5149, "shijiazhuang sjz"),
        WeatherCityOption("太原", "山西", "101100101", 37.8706, 112.5489, "taiyuan ty"),
        WeatherCityOption("呼和浩特", "内蒙古", "101080101", 40.8426, 111.7492, "huhehaote hhht"),
        WeatherCityOption("南昌", "江西", "101240101", 28.6829, 115.8582, "nanchang nc"),
        WeatherCityOption("南宁", "广西", "101300101", 22.8170, 108.3665, "nanning nn"),
        WeatherCityOption("桂林", "广西", "101300501", 25.2736, 110.2900, "guilin gl"),
        WeatherCityOption("海口", "海南", "101310101", 20.0440, 110.1999, "haikou hk"),
        WeatherCityOption("三亚", "海南", "101310201", 18.2528, 109.5119, "sanya"),
        WeatherCityOption("贵阳", "贵州", "101260101", 26.6470, 106.6302, "guiyang gy"),
        WeatherCityOption("昆明", "云南", "101290101", 24.8801, 102.8329, "kunming km"),
        WeatherCityOption("拉萨", "西藏", "101140101", 29.6500, 91.1000, "lasa"),
        WeatherCityOption("兰州", "甘肃", "101160101", 36.0611, 103.8343, "lanzhou lz"),
        WeatherCityOption("西宁", "青海", "101150101", 36.6171, 101.7782, "xining xn"),
        WeatherCityOption("银川", "宁夏", "101170101", 38.4872, 106.2309, "yinchuan yc"),
        WeatherCityOption("乌鲁木齐", "新疆", "101130101", 43.8256, 87.6168, "wulumuqi wlmq"),
        WeatherCityOption("徐州", "江苏", "101190801", 34.2044, 117.2841, "xuzhou xz"),
        WeatherCityOption("南通", "江苏", "101190501", 31.9802, 120.8943, "nantong nt"),
        WeatherCityOption("烟台", "山东", "101120501", 37.4638, 121.4479, "yantai yt"),
        WeatherCityOption("潍坊", "山东", "101120601", 36.7067, 119.1619, "weifang wf"),
        WeatherCityOption("保定", "河北", "101090201", 38.8740, 115.4646, "baoding bd"),
        WeatherCityOption("洛阳", "河南", "101180901", 34.6197, 112.4540, "luoyang ly"),
        WeatherCityOption("嘉兴", "浙江", "101210301", 30.7468, 120.7555, "jiaxing jx"),
        WeatherCityOption("绍兴", "浙江", "101210501", 30.0298, 120.5802, "shaoxing sx"),
        WeatherCityOption("泉州", "福建", "101230501", 24.8741, 118.6757, "quanzhou qz"),
        WeatherCityOption("香港", "香港", "101320101", 22.3193, 114.1694, "xianggang hk hongkong"),
        WeatherCityOption("澳门", "澳门", "101330101", 22.1987, 113.5439, "aomen macau")
    )

    fun findBestCity(query: String): WeatherCityOption? {
        val q = query.trim().removeSuffix("市").removeSuffix("省")
        if (q.isBlank()) return null
        return presetCities.firstOrNull {
            it.displayName.equals(q, ignoreCase = true) ||
                it.displayName.contains(q, ignoreCase = true) ||
                it.pinyin.contains(q, ignoreCase = true)
        }
    }

    val fallbackWeather = CachedWeatherEntity(
        id = "current",
        cityName = "北京",
        latitude = 39.9042,
        longitude = 116.4074,
        tempC = 14.0,
        humidity = 42,
        windKmh = 9.0,
        weatherCode = 1,
        tempMaxC = 18.0,
        tempMinC = 6.0,
        dailySummary = "明天 晴 19°/7°|后天 多云 16°/5°",
        fetchedAt = 0L
    )
}

/**
 * China School-Network Friendly Weather Repository (API 21 TLS 1.2 + ICP-filed sources first):
 * 1. Optional user-configured AMap (restapi.amap.com, ICP-filed) or QWeather (devapi.qweather.com, ICP-filed)
 * 2. Free Keyless ICP-filed UApiPro (uapis.cn)
 * 3. Free Keyless China Meteorological mirror (t.weather.itboy.net)
 * 4. Overseas Open-Meteo fallback (only if non-filtered network)
 */
class WeatherRepository(
    private val weatherDao: WeatherDao,
    private val prefs: PenPreferencesRepository
) {
    private val httpClient by lazy {
        Api21NetworkFactory.createClient(connectTimeoutSec = 8, readTimeoutSec = 10)
    }

    val currentWeatherFlow: Flow<CachedWeatherEntity?> = weatherDao.observeCurrentWeather()

    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        if (weatherDao.getCurrentWeather() == null) {
            weatherDao.saveWeather(WmoWeatherMapper.fallbackWeather)
        }
    }

    suspend fun searchCities(query: String): Result<List<WeatherCityOption>> = withContext(Dispatchers.IO) {
        val trimmed = query.trim().removeSuffix("市")
        if (trimmed.isBlank()) return@withContext Result.success(WmoWeatherMapper.presetCities.take(16))

        val localMatches = WmoWeatherMapper.presetCities.filter {
            it.displayName.contains(trimmed, ignoreCase = true) ||
                it.regionLabel.contains(trimmed, ignoreCase = true) ||
                it.pinyin.contains(trimmed, ignoreCase = true)
        }
        if (localMatches.isNotEmpty()) {
            return@withContext Result.success(localMatches)
        }

        // Allow adding ANY arbitrary Chinese city name directly (uapis.cn & AMap resolve by Chinese city name!)
        Result.success(
            listOf(
                WeatherCityOption(
                    displayName = trimmed,
                    regionLabel = "自定义城市",
                    cmaCode = "",
                    latitude = 35.0,
                    longitude = 114.0
                )
            )
        )
    }

    suspend fun selectCityAndRefresh(city: WeatherCityOption): WeatherSyncResult {
        prefs.setWeatherCity(city.displayName, city.cmaCode, city.latitude, city.longitude)
        prefs.saveWeatherCityToFavorites(city.toSavedEntry())
        return refreshWeather(force = true)
    }

    suspend fun refreshWeather(force: Boolean): WeatherSyncResult = withContext(Dispatchers.IO) {
        ensureSeeded()
        val cfg = prefs.currentConfig()
        val now = System.currentTimeMillis()
        val minIntervalMs = 60L * 60L * 1000L

        val cached = weatherDao.getCurrentWeather() ?: WmoWeatherMapper.fallbackWeather
        if (!force && (now - cfg.lastWeatherFetchAt) < minIntervalMs && cached.cityName == cfg.weatherCityName && cached.fetchedAt > 0L) {
            return@withContext WeatherSyncResult(cached, isLive = false, sourceLabel = "本地缓存")
        }

        val errors = mutableListOf<String>()

        // Tier 1A: User AMap key (高德天气 - 国内 ICP 备案)
        if (cfg.weatherAmapKey.isNotBlank()) {
            fetchFromAmap(cfg.weatherCityName, cfg.weatherAmapKey, cfg.weatherLat, cfg.weatherLon, now)
                .onSuccess { updated ->
                    weatherDao.saveWeather(updated)
                    prefs.saveLastWeatherFetchAt(now)
                    return@withContext WeatherSyncResult(updated, true, "高德备案接口")
                }
                .onFailure { errors.add("高德:${it.message}") }
        }

        // Tier 1B: User QWeather key (和风天气 - 国内 ICP 备案)
        if (cfg.weatherQWeatherKey.isNotBlank() && cfg.weatherCityCode.isNotBlank()) {
            fetchFromQWeather(cfg.weatherCityName, cfg.weatherCityCode, cfg.weatherQWeatherKey, cfg.weatherLat, cfg.weatherLon, now)
                .onSuccess { updated ->
                    weatherDao.saveWeather(updated)
                    prefs.saveLastWeatherFetchAt(now)
                    return@withContext WeatherSyncResult(updated, true, "和风备案接口")
                }
                .onFailure { errors.add("和风:${it.message}") }
        }

        // Tier 2: Free Keyless ICP-filed UApiPro (uapis.cn)
        fetchFromUApiChina(cfg.weatherCityName, cfg.weatherLat, cfg.weatherLon, now)
            .onSuccess { updated ->
                weatherDao.saveWeather(updated)
                prefs.saveLastWeatherFetchAt(now)
                return@withContext WeatherSyncResult(updated, true, "国内备案天气源")
            }
            .onFailure { errors.add("UApi:${it.message}") }

        // Tier 3: Free Keyless China Meteorological mirror (t.weather.itboy.net)
        val cmaCode = cfg.weatherCityCode.ifBlank { WmoWeatherMapper.findBestCity(cfg.weatherCityName)?.cmaCode.orEmpty() }
        if (cmaCode.isNotBlank()) {
            fetchFromItboyCma(cfg.weatherCityName, cmaCode, cfg.weatherLat, cfg.weatherLon, now)
                .onSuccess { updated ->
                    weatherDao.saveWeather(updated)
                    prefs.saveLastWeatherFetchAt(now)
                    return@withContext WeatherSyncResult(updated, true, "中国气象节点")
                }
                .onFailure { errors.add("气象镜像:${it.message}") }
        }

        // Tier 4: Open-Meteo fallback (for non-campus networks)
        fetchFromOpenMeteo(cfg.weatherCityName, cfg.weatherLat, cfg.weatherLon, now)
            .onSuccess { updated ->
                weatherDao.saveWeather(updated)
                prefs.saveLastWeatherFetchAt(now)
                return@withContext WeatherSyncResult(updated, true, "Open-Meteo")
            }
            .onFailure { errors.add("OpenMeteo:${it.message}") }

        // Offline / blocked fallback — still updates cityName cleanly and surfaces explicit diagnostic hint
        val fallback = cached.copy(
            cityName = cfg.weatherCityName,
            latitude = cfg.weatherLat,
            longitude = cfg.weatherLon
        )
        weatherDao.saveWeather(fallback)
        WeatherSyncResult(
            weather = fallback,
            isLive = false,
            sourceLabel = "离线缓存",
            errorHint = "当前网络无法直连天气源（可检查 Wi-Fi 或在天气设置填入高德/和风免费备案 Key）"
        )
    }

    private fun fetchFromAmap(city: String, key: String, lat: Double, lon: Double, now: Long): Result<CachedWeatherEntity> = runCatching {
        val enc = URLEncoder.encode(city, "UTF-8")
        val url = "https://restapi.amap.com/v3/weather/weatherInfo?city=$enc&key=$key&extensions=base"
        val body = executeGet(url)
        val root = JSONObject(body)
        if (root.optString("status") != "1") throw IllegalStateException(root.optString("info", "Key无效"))
        val lives = root.optJSONArray("lives") ?: throw IllegalStateException("无实况")
        if (lives.length() == 0) throw IllegalStateException("未匹配城市")
        val item = lives.getJSONObject(0)
        val temp = item.optString("temperature", "16").toDoubleOrNull() ?: 16.0
        val hum = item.optString("humidity", "45").toIntOrNull() ?: 45
        val desc = item.optString("weather", "晴")
        CachedWeatherEntity(
            id = "current",
            cityName = item.optString("city", city).removeSuffix("市"),
            latitude = lat,
            longitude = lon,
            tempC = temp,
            humidity = hum,
            windKmh = 10.0,
            weatherCode = WmoWeatherMapper.textToWmoCode(desc),
            tempMaxC = temp + 4.0,
            tempMinC = temp - 5.0,
            dailySummary = "实况 $desc · ${item.optString("winddirection")}风${item.optString("windpower")}级",
            fetchedAt = now
        )
    }

    private fun fetchFromQWeather(city: String, cmaCode: String, key: String, lat: Double, lon: Double, now: Long): Result<CachedWeatherEntity> = runCatching {
        val url = "https://devapi.qweather.com/v7/weather/now?location=$cmaCode&key=$key"
        val body = executeGet(url)
        val root = JSONObject(body)
        if (root.optString("code") != "200") throw IllegalStateException("码${root.optString("code")}")
        val cur = root.getJSONObject("now")
        val temp = cur.optString("temp", "16").toDoubleOrNull() ?: 16.0
        val hum = cur.optString("humidity", "45").toIntOrNull() ?: 45
        val wind = cur.optString("windSpeed", "8").toDoubleOrNull() ?: 8.0
        val desc = cur.optString("text", "晴")
        CachedWeatherEntity(
            id = "current",
            cityName = city,
            latitude = lat,
            longitude = lon,
            tempC = temp,
            humidity = hum,
            windKmh = wind,
            weatherCode = WmoWeatherMapper.textToWmoCode(desc),
            tempMaxC = temp + 4.0,
            tempMinC = temp - 5.0,
            dailySummary = "和风实况 $desc · 体感 ${cur.optString("feelsLike", temp.roundToInt().toString())}°",
            fetchedAt = now
        )
    }

    private fun fetchFromUApiChina(city: String, lat: Double, lon: Double, now: Long): Result<CachedWeatherEntity> = runCatching {
        val enc = URLEncoder.encode(city, "UTF-8")
        val url = "https://uapis.cn/api/v1/misc/weather?city=$enc"
        val body = executeGet(url)
        val root = JSONObject(body)
        val data = root.optJSONObject("data") ?: root
        val temp = data.optDouble("temperature", Double.NaN)
            .takeIf { !it.isNaN() }
            ?: data.optString("temperature").replace("℃", "").trim().toDoubleOrNull()
            ?: data.optString("temp").replace("℃", "").trim().toDoubleOrNull()
            ?: throw IllegalStateException("无温度字段")
        val desc = data.optString("weather").ifBlank { data.optString("condition", "晴") }
        val hum = data.optInt("humidity", -1).takeIf { it >= 0 }
            ?: data.optString("humidity").replace("%", "").trim().toIntOrNull()
            ?: 48
        val wind = data.optDouble("wind_speed", 8.0)
        val high = data.optDouble("temp_max", temp + 4.0)
        val low = data.optDouble("temp_min", temp - 5.0)
        val windDir = data.optString("wind_direction", "")
        val windPow = data.optString("wind_power", "")
        CachedWeatherEntity(
            id = "current",
            cityName = city,
            latitude = lat,
            longitude = lon,
            tempC = temp,
            humidity = hum,
            windKmh = wind,
            weatherCode = WmoWeatherMapper.textToWmoCode(desc),
            tempMaxC = high,
            tempMinC = low,
            dailySummary = "今日 $desc ${high.roundToInt()}°/${low.roundToInt()}°|风向 $windDir $windPow".trim(),
            fetchedAt = now
        )
    }

    private fun fetchFromItboyCma(city: String, cmaCode: String, lat: Double, lon: Double, now: Long): Result<CachedWeatherEntity> = runCatching {
        val url = "http://t.weather.itboy.net/api/weather/city/$cmaCode"
        val body = executeGet(url)
        val root = JSONObject(body)
        if (root.optInt("status", 0) != 200) throw IllegalStateException("status=${root.optInt("status")}")
        val data = root.getJSONObject("data")
        val temp = data.optString("wendu", "15").toDoubleOrNull() ?: 15.0
        val hum = data.optString("shidu", "45%").replace("%", "").trim().toIntOrNull() ?: 45
        val forecast = data.optJSONArray("forecast")
        val today = forecast?.optJSONObject(0)
        val tomorrow = forecast?.optJSONObject(1)
        val dayAfter = forecast?.optJSONObject(2)

        val todayType = today?.optString("type", "晴") ?: "晴"
        val highToday = parseCmaTemp(today?.optString("high")) ?: (temp + 4.0)
        val lowToday = parseCmaTemp(today?.optString("low")) ?: (temp - 5.0)

        val summary = buildList {
            tomorrow?.let {
                val h = parseCmaTemp(it.optString("high"))?.roundToInt() ?: (temp + 3).roundToInt()
                val l = parseCmaTemp(it.optString("low"))?.roundToInt() ?: (temp - 4).roundToInt()
                add("明天 ${it.optString("type", "多云")} ${h}°/${l}°")
            }
            dayAfter?.let {
                val h = parseCmaTemp(it.optString("high"))?.roundToInt() ?: (temp + 2).roundToInt()
                val l = parseCmaTemp(it.optString("low"))?.roundToInt() ?: (temp - 5).roundToInt()
                add("后天 ${it.optString("type", "晴")} ${h}°/${l}°")
            }
        }.joinToString("|").ifBlank { "空气 ${data.optString("quality", "良")}" }

        CachedWeatherEntity(
            id = "current",
            cityName = city,
            latitude = lat,
            longitude = lon,
            tempC = temp,
            humidity = hum,
            windKmh = 9.0,
            weatherCode = WmoWeatherMapper.textToWmoCode(todayType),
            tempMaxC = highToday,
            tempMinC = lowToday,
            dailySummary = summary,
            fetchedAt = now
        )
    }

    private fun fetchFromOpenMeteo(city: String, lat: Double, lon: Double, now: Long): Result<CachedWeatherEntity> = runCatching {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=3"
        val body = executeGet(url)
        val root = JSONObject(body)
        val cur = root.getJSONObject("current")
        val daily = root.optJSONObject("daily")
        val temp = cur.optDouble("temperature_2m", 15.0)
        val hum = cur.optInt("relative_humidity_2m", 45)
        val code = cur.optInt("weather_code", 1)
        val wind = cur.optDouble("wind_speed_10m", 8.0)
        val maxArr = daily?.optJSONArray("temperature_2m_max")
        val minArr = daily?.optJSONArray("temperature_2m_min")
        val codeArr = daily?.optJSONArray("weather_code")
        val max0 = maxArr?.optDouble(0, temp + 4) ?: (temp + 4)
        val min0 = minArr?.optDouble(0, temp - 5) ?: (temp - 5)
        val parts = mutableListOf<String>()
        val labels = listOf("明天", "后天")
        for (i in 1..2) {
            if (maxArr != null && minArr != null && codeArr != null && maxArr.length() > i) {
                parts.add("${labels[i - 1]} ${WmoWeatherMapper.describe(codeArr.optInt(i, 1))} ${maxArr.optDouble(i).roundToInt()}°/${minArr.optDouble(i).roundToInt()}°")
            }
        }
        CachedWeatherEntity(
            id = "current",
            cityName = city,
            latitude = lat,
            longitude = lon,
            tempC = temp,
            humidity = hum,
            windKmh = wind,
            weatherCode = code,
            tempMaxC = max0,
            tempMinC = min0,
            dailySummary = parts.joinToString("|"),
            fetchedAt = now
        )
    }

    private fun parseCmaTemp(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val match = Regex("-?\\d+(\\.\\d+)?").find(raw)
        return match?.value?.toDoubleOrNull()
    }

    private fun executeGet(url: String): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "PenOS-Launcher/3.0 (Android)")
            .get()
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            return resp.body?.string().orEmpty().ifBlank { throw IllegalStateException("空响应") }
        }
    }
}
