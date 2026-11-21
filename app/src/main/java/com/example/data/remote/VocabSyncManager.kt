package com.example.data.remote

import android.util.Base64
import com.example.data.local.VocabDao
import com.example.data.local.VocabProgressEntity
import com.example.data.prefs.PenPreferencesRepository
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

@JsonClass(generateAdapter = true)
data class SyncPayload(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val records: List<SyncProgressRecord> = emptyList()
)

@JsonClass(generateAdapter = true)
data class SyncProgressRecord(
    val w: String, // wordId
    val s: String, // status
    val f: Int,    // familiarity
    val n: Long,   // nextReviewAt
    val e: Int,    // wrongCount
    val u: Long    // updatedAt
)

data class SyncResultSummary(
    val uploadedCount: Int,
    val mergedCount: Int,
    val message: String
)

/**
 * Dual-mode Android 5.0.2 (API 21) compatible Vocab Cloud Sync:
 * 1. WebDAV / Custom HTTP JSON Sync (Nutstore 坚果云 or any standard WebDAV server)
 * 2. Offline GZIP+Base64 Portable Sync Code & Raw JSON File Import/Export
 */
class VocabSyncManager(
    private val vocabDao: VocabDao,
    private val prefs: PenPreferencesRepository
) {
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val payloadAdapter = moshi.adapter(SyncPayload::class.java)

    private val client: OkHttpClient by lazy {
        Api21NetworkFactory.createClient(connectTimeoutSec = 10, readTimeoutSec = 12)
    }

    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val local = vocabDao.getAllProgressSnapshot()
        val payload = SyncPayload(
            version = 1,
            exportedAt = System.currentTimeMillis(),
            records = local.map {
                SyncProgressRecord(
                    w = it.wordId,
                    s = it.status,
                    f = it.familiarity,
                    n = it.nextReviewAt,
                    e = it.wrongCount,
                    u = it.updatedAt
                )
            }
        )
        payloadAdapter.toJson(payload)
    }

    suspend fun importJson(json: String): Result<SyncResultSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val remotePayload = payloadAdapter.fromJson(json.trim())
                ?: throw IllegalArgumentException("无效的进度 JSON 数据")
            val merged = mergeRemoteRecords(remotePayload.records)
            prefs.saveLastSyncTime(System.currentTimeMillis())
            SyncResultSummary(
                uploadedCount = 0,
                mergedCount = merged.size,
                message = "已成功合并 ${merged.size} 条单词学习进度"
            )
        }
    }

    suspend fun generateSyncCode(): String = withContext(Dispatchers.IO) {
        val jsonBytes = exportJson().toByteArray(Charsets.UTF_8)
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { it.write(jsonBytes) }
        "PENOS1:" + Base64.encodeToString(baos.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)
    }

    suspend fun importSyncCode(code: String): Result<SyncResultSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val cleaned = code.trim()
            if (!cleaned.startsWith("PENOS1:")) {
                throw IllegalArgumentException("同步码格式不正确（应以 PENOS1: 开头）")
            }
            val encoded = cleaned.removePrefix("PENOS1:")
            val compressed = Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP)
            val json = GZIPInputStream(ByteArrayInputStream(compressed)).bufferedReader(Charsets.UTF_8).use {
                it.readText()
            }
            importJson(json).getOrThrow()
        }
    }

    suspend fun syncWithWebDav(): Result<SyncResultSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val config = prefs.currentConfig()
            val baseUrl = config.webDavUrl.trim()
            if (baseUrl.isBlank() || (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://"))) {
                throw IllegalArgumentException("请先在设置中填写有效的 WebDAV 地址")
            }
            if (config.webDavUser.isBlank() || config.webDavPass.isBlank()) {
                throw IllegalArgumentException("请填写 WebDAV 账号与应用密码")
            }

            val fileUrl = if (baseUrl.endsWith(".json")) {
                baseUrl
            } else {
                baseUrl.trimEnd('/') + "/penos_vocab_sync.json"
            }
            val authHeader = Credentials.basic(config.webDavUser, config.webDavPass)

            // Step 1: Pull existing remote JSON if present
            var mergedCount = 0
            val getReq = Request.Builder()
                .url(fileUrl)
                .header("Authorization", authHeader)
                .get()
                .build()

            client.newCall(getReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string().orEmpty()
                    if (bodyStr.isNotBlank()) {
                        val remote = payloadAdapter.fromJson(bodyStr)
                        if (remote != null) {
                            mergedCount = mergeRemoteRecords(remote.records).size
                        }
                    }
                } else if (resp.code != 404) {
                    throw IllegalStateException("WebDAV 拉取失败 (HTTP ${resp.code})")
                }
            }

            // Step 2: Upload merged snapshot via HTTP PUT
            val mergedJson = exportJson()
            val putBody = mergedJson.toRequestBody("application/json; charset=utf-8".toMediaType())
            val putReq = Request.Builder()
                .url(fileUrl)
                .header("Authorization", authHeader)
                .put(putBody)
                .build()

            client.newCall(putReq).execute().use { resp ->
                if (!resp.isSuccessful && resp.code != 201 && resp.code != 204) {
                    throw IllegalStateException("WebDAV 上传失败 (HTTP ${resp.code})，请确认目录已创建")
                }
            }

            val totalNow = vocabDao.getAllProgressSnapshot().size
            prefs.saveLastSyncTime(System.currentTimeMillis())
            SyncResultSummary(
                uploadedCount = totalNow,
                mergedCount = mergedCount,
                message = "WebDAV 云同步成功（云端共 $totalNow 条记录）"
            )
        }
    }

    private suspend fun mergeRemoteRecords(remoteRecords: List<SyncProgressRecord>): List<VocabProgressEntity> {
        val localMap = vocabDao.getAllProgressSnapshot().associateBy { it.wordId }.toMutableMap()
        val toSave = mutableListOf<VocabProgressEntity>()

        for (rec in remoteRecords) {
            val existing = localMap[rec.w]
            if (existing == null || rec.u > existing.updatedAt) {
                val merged = VocabProgressEntity(
                    wordId = rec.w,
                    status = rec.s,
                    familiarity = rec.f,
                    nextReviewAt = rec.n,
                    wrongCount = rec.e,
                    updatedAt = rec.u
                )
                localMap[rec.w] = merged
                toSave.add(merged)
            }
        }
        if (toSave.isNotEmpty()) {
            vocabDao.upsertAllProgress(toSave)
        }
        return toSave
    }
}
