package com.vibecoding.aireading.tts

import com.vibecoding.aireading.model.VoiceConfig
import com.vibecoding.aireading.model.VoiceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class FishAudioTtsEngine(private val okHttpClient: OkHttpClient) : ITtsEngine {

    companion object {
        @Volatile
        var lastError: String? = null
    }

    override suspend fun synthesize(text: String, config: VoiceConfig): ByteArray? = withContext(Dispatchers.IO) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return@withContext null

        val modelId = when {
            config.fishModelId.isNotBlank() -> config.fishModelId.trim()
            config.voiceType == VoiceType.DAGOU -> VoiceConfig.FISH_MODEL_DAGOU
            else -> VoiceConfig.FISH_MODEL_NAILONG
        }

        val rawKey = config.fishAudioApiKey.trim()
        val apiKey = rawKey.removePrefix("Bearer ").removePrefix("bearer ").trim()
        if (apiKey.isEmpty()) {
            lastError = "未配置 Fish Audio API Key"
            return@withContext null
        }

        val targetUrl = when {
            config.fishCustomEndpoint.isNotBlank() -> {
                val base = config.fishCustomEndpoint.trim().trimEnd('/')
                if (base.endsWith("/tts")) base else "$base/v1/tts"
            }
            else -> "https://api.fish.audio/v1/tts"
        }

        try {
            val json = JSONObject().apply {
                put("text", cleanText)
                put("reference_id", modelId)
                put("format", "mp3")
                put("mp3_bitrate", 64)
            }

            // Free developer tier of Fish Audio requires the 'model: s2.1-pro-free' header
            val requestBuilder = Request.Builder()
                .url(targetUrl)
                .header("Content-Type", "application/json")
                .header("Accept", "audio/*")
                .header("Authorization", "Bearer $apiKey")
                .header("model", "s2.1-pro-free")
                .post(json.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))

            var response = okHttpClient.newCall(requestBuilder.build()).execute()
            
            // If free model header returns 400 or 402, retry without header (for paid or legacy keys)
            if (response.code == 400 || response.code == 402) {
                response.close()
                val retryReq = Request.Builder()
                    .url(targetUrl)
                    .header("Content-Type", "application/json")
                    .header("Accept", "audio/*")
                    .header("Authorization", "Bearer $apiKey")
                    .post(json.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                response = okHttpClient.newCall(retryReq).execute()
            }

            if (response.isSuccessful && response.body != null) {
                val bytes = response.body!!.bytes()
                if (bytes.isNotEmpty()) {
                    lastError = null
                    return@withContext bytes
                }
            }

            val code = response.code
            val errBody = try { response.body?.string() ?: "" } catch (e: Exception) { "" }
            lastError = when (code) {
                401 -> "API Key 无效或未激活 (HTTP 401)"
                402 -> "Fish Audio 账户额度不足或未绑定模型 (HTTP 402)"
                404 -> "模型 ID 不存在或已被删除 (HTTP 404)"
                429 -> "请求频率超出限制 (HTTP 429)"
                else -> "Fish Audio 合成失败 (HTTP $code)"
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            lastError = "连接 Fish Audio 失败: ${e.message ?: "网络超时"}"
            null
        }
    }
}

