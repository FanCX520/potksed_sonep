package com.example.data.remote

import com.example.data.local.CachedWeatherEntity
import com.example.data.local.WeatherDao
import com.example.data.prefs.PenPreferencesRepository
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@JsonClass(generateAdapter = true)
data class OpenMeteoForecastResponse(
    val current: OpenMeteoCurrent? = null,
    val daily: OpenMeteoDaily? = null
)

@JsonClass(generateAdapter = true)
data class OpenMeteoCurrent(
    @Json(name = "temperature_2m") val temperature2m: Double? = null,
    @Json(name = "relative_humidity_2m") val relativeHumidity2m: Int? = null,
    @Json(name = "weather_code") val weatherCode: Int? = null,
    @Json(name = "wind_speed_10m") val windSpeed10m: Double? = null
)

@JsonClass(generateAdapter = true)
data class OpenMeteoDaily(
    val time: List<String>? = null,
    @Json(name = "weather_code") val weatherCode: List<Int>? = null,
    @Json(name = "temperature_2m_max") val tempMax: List<Double>? = null,
    @Json(name = "temperature_2m_min") val tempMin: List<Double>? = null
)

@JsonClass(generateAdapter = true)
data class OpenMeteoGeoResponse(
    val results: List<OpenMeteoGeoCity>? = null
)

@JsonClass(generateAdapter = true)
data class OpenMeteoGeoCity(
    val name: String? = null,
    val admin1: String? = null,
    val country: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class WeatherCityOption(
    val displayName: String,
    val regionLabel: String,
    val latitude: Double,
    val longitude: Double
)

interface OpenMeteoForecastApi {
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m",
        @Query("daily") daily: String = "weather_code,temperature_2m_max,temperature_2m_min",
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 3
    ): OpenMeteoForecastResponse
}

interface OpenMeteoGeoApi {
    @GET("v1/search")
    suspend fun searchCity(
        @Query("name") name: String,
        @Query("count") count: Int = 6,
        @Query("language") language: String = "zh",
        @Query("format") format: String = "json"
    ): OpenMeteoGeoResponse
}

object WmoWeatherMapper {
    fun describe(code: Int): String = when (code) {
        0 -> "晴朗"
        1 -> "大部晴朗"
        2 -> "多云"
        3 -> "阴天"
        45, 48 -> "有雾"
        51, 53, 55 -> "毛毛雨"
        61, 63, 65 -> "降雨"
        66, 67 -> "冻雨"
        71, 73, 75, 77 -> "降雪"
        80, 81, 82 -> "阵雨"
        85, 86 -> "阵雪"
        95, 96, 99 -> "雷阵雨"
        else -> "晴间多云"
    }

    val presetCities: List<WeatherCityOption> = listOf(
        WeatherCityOption("北京", "中国", 39.9042, 116.4074),
        WeatherCityOption("上海", "中国", 31.2304, 121.4737),
        WeatherCityOption("广州", "广东", 23.1291, 113.2644),
        WeatherCityOption("深圳", "广东", 22.5431, 114.0579),
        WeatherCityOption("杭州", "浙江", 30.2741, 120.1551),
        WeatherCityOption("成都", "四川", 30.5728, 104.0668),
        WeatherCityOption("武汉", "湖北", 30.5928, 114.3055),
        WeatherCityOption("西安", "陕西", 34.3416, 108.9398)
    )

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

class WeatherRepository(
    private val weatherDao: WeatherDao,
    private val prefs: PenPreferencesRepository
) {
    private val moshi by lazy {
        Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    }
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(7, TimeUnit.SECONDS)
            .readTimeout(7, TimeUnit.SECONDS)
            .build()
    }
    private val forecastApi: OpenMeteoForecastApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .client(httpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OpenMeteoForecastApi::class.java)
    }
    private val geoApi: OpenMeteoGeoApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://geocoding-api.open-meteo.com/")
            .client(httpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OpenMeteoGeoApi::class.java)
    }

    val currentWeatherFlow: Flow<CachedWeatherEntity?> = weatherDao.observeCurrentWeather()

    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        if (weatherDao.getCurrentWeather() == null) {
            weatherDao.saveWeather(WmoWeatherMapper.fallbackWeather)
        }
    }

    suspend fun searchCities(query: String): Result<List<WeatherCityOption>> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext Result.success(WmoWeatherMapper.presetCities)

        runCatching {
            val resp = geoApi.searchCity(name = trimmed)
            val remote = resp.results.orEmpty().mapNotNull { item ->
                val name = item.name ?: return@mapNotNull null
                val lat = item.latitude ?: return@mapNotNull null
                val lon = item.longitude ?: return@mapNotNull null
                val region = listOfNotNull(item.admin1, item.country).joinToString(" · ")
                WeatherCityOption(name, region, lat, lon)
            }
            if (remote.isNotEmpty()) remote else WmoWeatherMapper.presetCities.filter {
                it.displayName.contains(trimmed, ignoreCase = true) || it.regionLabel.contains(trimmed, ignoreCase = true)
            }
        }
    }

    suspend fun selectCityAndRefresh(city: WeatherCityOption): Result<CachedWeatherEntity> {
        prefs.setWeatherCity(city.displayName, city.latitude, city.longitude)
        return refreshWeather(force = true)
    }

    suspend fun refreshWeather(force: Boolean): Result<CachedWeatherEntity> = withContext(Dispatchers.IO) {
        ensureSeeded()
        val cfg = prefs.currentConfig()
        val now = System.currentTimeMillis()
        val minIntervalMs = 60L * 60L * 1000L // Low-power 60-min throttle

        val cached = weatherDao.getCurrentWeather() ?: WmoWeatherMapper.fallbackWeather
        if (!force && (now - cfg.lastWeatherFetchAt) < minIntervalMs && cached.cityName == cfg.weatherCityName) {
            return@withContext Result.success(cached)
        }

        return@withContext try {
            val resp = forecastApi.getForecast(latitude = cfg.weatherLat, longitude = cfg.weatherLon)
            val cur = resp.current
            val daily = resp.daily
            val maxToday = daily?.tempMax?.getOrNull(0) ?: (cur?.temperature2m ?: 15.0)
            val minToday = daily?.tempMin?.getOrNull(0) ?: ((cur?.temperature2m ?: 15.0) - 6.0)

            val dayLabels = listOf("明天", "后天")
            val summaryParts = mutableListOf<String>()
            for (i in 1..2) {
                val maxV = daily?.tempMax?.getOrNull(i)?.roundToInt()
                val minV = daily?.tempMin?.getOrNull(i)?.roundToInt()
                val codeV = daily?.weatherCode?.getOrNull(i)
                if (maxV != null && minV != null && codeV != null) {
                    summaryParts.add("${dayLabels[i - 1]} ${WmoWeatherMapper.describe(codeV)} ${maxV}°/${minV}°")
                }
            }

            val updated = CachedWeatherEntity(
                id = "current",
                cityName = cfg.weatherCityName,
                latitude = cfg.weatherLat,
                longitude = cfg.weatherLon,
                tempC = cur?.temperature2m ?: cached.tempC,
                humidity = cur?.relativeHumidity2m ?: cached.humidity,
                windKmh = cur?.windSpeed10m ?: cached.windKmh,
                weatherCode = cur?.weatherCode ?: cached.weatherCode,
                tempMaxC = maxToday,
                tempMinC = minToday,
                dailySummary = summaryParts.joinToString("|").ifBlank { cached.dailySummary },
                fetchedAt = now
            )
            weatherDao.saveWeather(updated)
            prefs.saveLastWeatherFetchAt(now)
            Result.success(updated)
        } catch (_: Exception) {
            // Update city label on cached fallback if offline after city switch
            val fallback = cached.copy(cityName = cfg.weatherCityName)
            weatherDao.saveWeather(fallback)
            Result.success(fallback)
        }
    }
}
