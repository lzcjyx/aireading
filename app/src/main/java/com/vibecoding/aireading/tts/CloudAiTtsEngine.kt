package com.vibecoding.aireading.tts

import com.vibecoding.aireading.model.VoiceConfig
import com.vibecoding.aireading.model.VoiceType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

class CloudAiTtsEngine(private val okHttpClient: OkHttpClient) : ITtsEngine {

    companion object {
        private const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
        private const val WIN_EPOCH = 11644473600L
        private const val S_TO_NS = 1000000000.0

        fun generateSecMsGec(): String {
            var ticks = (System.currentTimeMillis() / 1000.0) + WIN_EPOCH
            ticks -= (ticks % 300)
            ticks *= (S_TO_NS / 100.0)
            val strToHash = String.format(Locale.US, "%.0f%s", ticks, TRUSTED_CLIENT_TOKEN)
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(strToHash.toByteArray(Charsets.US_ASCII))
            return digest.joinToString("") { "%02X".format(it) }
        }
    }

    override suspend fun synthesize(text: String, config: VoiceConfig): ByteArray? {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return null

        return withTimeoutOrNull(3_000L) {
            val deferred = CompletableDeferred<ByteArray?>()
            val audioBuffer = ByteArrayOutputStream()

            val connectionId = UUID.randomUUID().toString().replace("-", "")
            val requestId = UUID.randomUUID().toString().replace("-", "")
            val secMsGec = generateSecMsGec()

            val wssUrl = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1" +
                    "?TrustedClientToken=$TRUSTED_CLIENT_TOKEN" +
                    "&ConnectionId=$connectionId" +
                    "&Sec-MS-GEC=$secMsGec" +
                    "&Sec-MS-GEC-Version=1-143.0.3650.75"

            val request = Request.Builder()
                .url(wssUrl)
                .addHeader("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0")
                .addHeader("Pragma", "no-cache")
                .addHeader("Cache-Control", "no-cache")
                .build()

            val (voiceName, pitch, rate) = getVoiceParameters(config)
            val ssml = buildSsml(cleanText, voiceName, pitch, rate)

            val wsListener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    val configMsg = "Content-Type:application/json; charset=utf-8\r\n" +
                            "Path:speech.config\r\n\r\n" +
                            "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"},\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}"
                    webSocket.send(configMsg)

                    val sdf = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                    val timestamp = sdf.format(Date())

                    val requestMsg = "X-RequestId:$requestId\r\n" +
                            "Content-Type:application/ssml+xml\r\n" +
                            "X-Timestamp:$timestamp\r\n" +
                            "Path:ssml\r\n\r\n$ssml"
                    webSocket.send(requestMsg)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (text.contains("Path:turn.end")) {
                        webSocket.close(1000, "Normal Closure")
                        val result = audioBuffer.toByteArray()
                        deferred.complete(if (result.isNotEmpty()) result else null)
                    }
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    val data = bytes.toByteArray()
                    if (data.size > 2) {
                        val headerLen = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                        if (data.size > 2 + headerLen) {
                            val headerText = String(data, 2, headerLen, Charsets.UTF_8)
                            if (headerText.contains("Path:audio")) {
                                val audioOffset = 2 + headerLen
                                audioBuffer.write(data, audioOffset, data.size - audioOffset)
                            }
                        }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    deferred.complete(null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (!deferred.isCompleted) {
                        val result = audioBuffer.toByteArray()
                        deferred.complete(if (result.isNotEmpty()) result else null)
                    }
                }
            }

            okHttpClient.newWebSocket(request, wsListener)
            deferred.await()
        }
    }

    private fun getVoiceParameters(config: VoiceConfig): Triple<String, String, String> {
        return when (config.voiceType) {
            VoiceType.NAILONG -> {
                // 奶龙：真正的少儿动漫男童声线 (Yunxia)，配合 +35Hz 萌化基频与活泼轻快语调
                val basePitchHz = 35 + config.pitchOffsetHz
                val pitchStr = if (basePitchHz >= 0) "+${basePitchHz}Hz" else "${basePitchHz}Hz"
                val ratePercent = ((config.speed * 1.08f - 1.0f) * 100).toInt()
                val rateStr = if (ratePercent >= 0) "+${ratePercent}%" else "${ratePercent}%"
                Triple("zh-CN-YunxiaNeural", pitchStr, rateStr)
            }
            VoiceType.DAGOU -> {
                // 大狗：浑厚粗犷江湖说书男声 (Yunjian)，配合 -22Hz 重低音与沉稳质感
                val basePitchHz = -22 + config.pitchOffsetHz
                val pitchStr = if (basePitchHz >= 0) "+${basePitchHz}Hz" else "${basePitchHz}Hz"
                val ratePercent = ((config.speed * 0.96f - 1.0f) * 100).toInt()
                val rateStr = if (ratePercent >= 0) "+${ratePercent}%" else "${ratePercent}%"
                Triple("zh-CN-YunjianNeural", pitchStr, rateStr)
            }
            else -> {
                val pitchHz = config.pitchOffsetHz
                val pitchStr = if (pitchHz >= 0) "+${pitchHz}Hz" else "${pitchHz}Hz"
                val ratePercent = ((config.speed - 1.0f) * 100).toInt()
                val rateStr = if (ratePercent >= 0) "+${ratePercent}%" else "${ratePercent}%"
                Triple("zh-CN-XiaoxiaoNeural", pitchStr, rateStr)
            }
        }
    }

    private fun buildSsml(text: String, voiceName: String, pitch: String, rate: String): String {
        val escaped = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

        return "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='zh-CN'>" +
                "<voice name='$voiceName'>" +
                "<prosody pitch='$pitch' rate='$rate'>" +
                escaped +
                "</prosody></voice></speak>"
    }
}
