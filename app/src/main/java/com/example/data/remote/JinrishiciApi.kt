package com.example.data.remote

import com.example.data.local.CachedPoemEntity
import com.example.data.local.OfflineSeedData
import com.example.data.local.PoemDao
import com.example.data.prefs.PenPreferencesRepository
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
import retrofit2.http.Header
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class JinrishiciTokenResponse(
    val status: String? = null,
    val data: String? = null
)

@JsonClass(generateAdapter = true)
data class JinrishiciSentenceResponse(
    val status: String? = null,
    val data: JinrishiciSentenceData? = null
)

@JsonClass(generateAdapter = true)
data class JinrishiciSentenceData(
    val id: String? = null,
    val content: String? = null,
    val origin: JinrishiciOrigin? = null
)

@JsonClass(generateAdapter = true)
data class JinrishiciOrigin(
    val title: String? = null,
    val dynasty: String? = null,
    val author: String? = null,
    val content: List<String>? = null
)

interface JinrishiciService {
    @GET("token")
    suspend fun fetchToken(): JinrishiciTokenResponse

    @GET("sentence")
    suspend fun fetchSentence(
        @Header("X-User-Token") token: String
    ): JinrishiciSentenceResponse
}

class PoetryRepository(
    private val poemDao: PoemDao,
    private val prefs: PenPreferencesRepository
) {
    private val api: JinrishiciService by lazy {
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        val client = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl("https://v2.jinrishici.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(JinrishiciService::class.java)
    }

    val latestPoemFlow: Flow<CachedPoemEntity?> = poemDao.observeLatestPoem()

    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        if (poemDao.getPoemCount() == 0) {
            poemDao.insertPoems(OfflineSeedData.defaultPoems)
        }
    }

    /**
     * Low-power smart fetch: only hits Jinrishici API when forced by user or > 60 min elapsed.
     * Falls back seamlessly to rotating offline classical poems if offline.
     */
    suspend fun refreshPoem(force: Boolean): Result<CachedPoemEntity> = withContext(Dispatchers.IO) {
        ensureSeeded()
        val config = prefs.currentConfig()
        val now = System.currentTimeMillis()
        val minIntervalMs = 60L * 60L * 1000L // 60 minutes for low battery consumption

        if (!force && (now - config.lastPoemFetchAt) < minIntervalMs) {
            val cached = poemDao.getCachedPoems().firstOrNull() ?: OfflineSeedData.defaultPoems.first()
            return@withContext Result.success(cached)
        }

        try {
            var token = config.jinrishiciToken
            if (token.isBlank()) {
                val tokenResp = api.fetchToken()
                if (tokenResp.status == "success" && !tokenResp.data.isNullOrBlank()) {
                    token = tokenResp.data
                    prefs.saveJinrishiciToken(token)
                }
            }
            if (token.isNotBlank()) {
                val resp = api.fetchSentence(token)
                val data = resp.data
                if (resp.status == "success" && data != null && !data.content.isNullOrBlank()) {
                    val entity = CachedPoemEntity(
                        id = data.id ?: "jrsc_$now",
                        content = data.content,
                        title = data.origin?.title ?: "佳句",
                        dynasty = data.origin?.dynasty ?: "古韵",
                        author = data.origin?.author ?: "佚名",
                        fullContent = data.origin?.content?.joinToString("\n") ?: data.content,
                        fetchedAt = now
                    )
                    poemDao.insertPoem(entity)
                    prefs.saveLastPoemFetchAt(now)
                    return@withContext Result.success(entity)
                }
            }
            rotateOfflinePoem(now)
        } catch (_: Exception) {
            rotateOfflinePoem(now)
        }
    }

    private suspend fun rotateOfflinePoem(now: Long): Result<CachedPoemEntity> {
        val pool = poemDao.getCachedPoems().ifEmpty { OfflineSeedData.defaultPoems }
        val chosen = if (pool.size > 1) pool[1] else pool.first()
        val bumped = chosen.copy(fetchedAt = now)
        poemDao.insertPoem(bumped)
        return Result.success(bumped)
    }
}
