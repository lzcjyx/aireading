package com.vibecoding.aireading.tts

import com.vibecoding.aireading.model.VoiceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class GptSovitsTtsEngine(private val okHttpClient: OkHttpClient) : ITtsEngine {

    override suspend fun synthesize(text: String, config: VoiceConfig): ByteArray? {
        val urlStr = config.customApiUrl.trim()
        if (urlStr.isEmpty()) return null

        return withContext(Dispatchers.IO) {
            try {
                // Try GET first with query parameter
                val httpUrl = urlStr.toHttpUrlOrNull() ?: return@withContext null
                val getUrl = httpUrl.newBuilder()
                    .addQueryParameter("text", text)
                    .addQueryParameter("text_lang", "zh")
                    .addQueryParameter("speed", config.speed.toString())
                    .build()

                val getRequest = Request.Builder()
                    .url(getUrl)
                    .header("Accept", "audio/*")
                    .build()

                val getResponse = okHttpClient.newCall(getRequest).execute()
                if (getResponse.isSuccessful && getResponse.body != null) {
                    val bytes = getResponse.body!!.bytes()
                    if (bytes.isNotEmpty()) return@withContext bytes
                }

                // If GET failed or returned error, try POST JSON
                val json = JSONObject().apply {
                    put("text", text)
                    put("text_language", "zh")
                    put("speed", config.speed)
                }
                val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val postRequest = Request.Builder()
                    .url(urlStr)
                    .post(body)
                    .header("Accept", "audio/*")
                    .build()

                val postResponse = okHttpClient.newCall(postRequest).execute()
                if (postResponse.isSuccessful && postResponse.body != null) {
                    val bytes = postResponse.body!!.bytes()
                    if (bytes.isNotEmpty()) return@withContext bytes
                }

                null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
