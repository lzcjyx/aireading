package com.vibecoding.aireading.tts

import android.content.Context
import com.vibecoding.aireading.model.Sentence
import com.vibecoding.aireading.model.VoiceConfig
import com.vibecoding.aireading.model.VoiceType
import com.vibecoding.aireading.tts.sherpa.SherpaOnnxTtsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class TtsManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    // Dedicated fast client for Fish Audio overseas connection - fails fast into offline fallback in 4s!
    private val fishHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    val sherpaEngine by lazy { SherpaOnnxTtsEngine(context) }
    private val fishAudioEngine = FishAudioTtsEngine(fishHttpClient)
    private val cloudEngine = CloudAiTtsEngine(okHttpClient)
    private val gptSovitsEngine = GptSovitsTtsEngine(okHttpClient)
    private val systemEngine by lazy { SystemTtsEngine(context) }

    @Volatile
    var lastUsedChannel: String = "系统TTS"
        private set

    private val cacheDir = File(context.cacheDir, "tts_cache").apply { if (!exists()) mkdirs() }
    private val inFlightPreloads = ConcurrentHashMap.newKeySet<String>()

    suspend fun getAudioFile(sentence: Sentence, config: VoiceConfig): File? = withContext(Dispatchers.IO) {
        val cacheKey = buildCacheKey(sentence.text, config)
        val wavCache = File(cacheDir, "$cacheKey.wav")
        if (wavCache.exists() && wavCache.length() > 200) {
            return@withContext wavCache
        }
        val mp3Cache = File(cacheDir, "$cacheKey.mp3")
        if (mp3Cache.exists() && mp3Cache.length() > 200) {
            return@withContext mp3Cache
        }

        // Synthesize audio
        val audioBytes = synthesizeWithFallback(sentence.text, config) ?: return@withContext null

        try {
            val isWav = audioBytes.size >= 4 && audioBytes[0] == 'R'.code.toByte() && audioBytes[1] == 'I'.code.toByte()
            val targetFile = if (isWav) wavCache else mp3Cache
            FileOutputStream(targetFile).use { it.write(audioBytes) }
            targetFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun preload(sentences: List<Sentence>, startIndex: Int, count: Int, config: VoiceConfig) {
        scope.launch {
            for (i in startIndex until minOf(startIndex + count, sentences.size)) {
                val sentence = sentences[i]
                val key = buildCacheKey(sentence.text, config)
                val wavCache = File(cacheDir, "$key.wav")
                val mp3Cache = File(cacheDir, "$key.mp3")
                if ((wavCache.exists() && wavCache.length() > 200) || (mp3Cache.exists() && mp3Cache.length() > 200)) continue

                if (inFlightPreloads.add(key)) {
                    try {
                        val bytes = synthesizeWithFallback(sentence.text, config)
                        if (bytes != null && bytes.isNotEmpty()) {
                            val isWav = bytes.size >= 4 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte()
                            val targetFile = if (isWav) wavCache else mp3Cache
                            FileOutputStream(targetFile).use { it.write(bytes) }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        inFlightPreloads.remove(key)
                    }
                }
            }
        }
    }

    private suspend fun synthesizeWithFallback(text: String, config: VoiceConfig): ByteArray? {
        var bytes: ByteArray? = null

        when (config.voiceType) {
            VoiceType.NAILONG, VoiceType.DAGOU -> {
                // 1. Genuine Fish Audio neural clone models (highest fidelity, 100% authentic)
                if (config.fishAudioApiKey.isNotBlank()) {
                    bytes = fishAudioEngine.synthesize(text, config)
                    if (bytes != null) lastUsedChannel = "Fish AI"
                }

                // 2. Custom GPT-SoVITS endpoint (if configured)
                if (bytes == null && config.customApiUrl.isNotBlank() && !config.customApiUrl.contains("127.0.0.1")) {
                    bytes = gptSovitsEngine.synthesize(text, config)
                    if (bytes != null) lastUsedChannel = "自定义API"
                }

                // 3. On-device offline Sherpa-ONNX neural model (free, 0 network, offline)
                if (bytes == null && sherpaEngine.modelManager.isModelReady(config.voiceType)) {
                    try {
                        bytes = sherpaEngine.synthesize(text, config)
                        if (bytes != null) lastUsedChannel = "端侧离线"
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }
                }

                // 4. Zero-Latency Instant Fallback: System Native TTS (< 50ms, zero lag, no network hang)
                if (bytes == null) {
                    bytes = systemEngine.synthesize(text, config)
                    if (bytes != null) lastUsedChannel = "系统TTS"
                }

                // 5. Cloud fallback if system TTS is uninitialized or unsupported
                if (bytes == null) {
                    bytes = cloudEngine.synthesize(text, config)
                    if (bytes != null) lastUsedChannel = "云端兜底"
                }
            }
            VoiceType.CUSTOM_API -> {
                bytes = gptSovitsEngine.synthesize(text, config)
                if (bytes != null) lastUsedChannel = "自定义API"
                if (bytes == null) {
                    bytes = systemEngine.synthesize(text, config)
                    if (bytes != null) lastUsedChannel = "系统TTS"
                }
                if (bytes == null) {
                    bytes = cloudEngine.synthesize(text, config)
                    if (bytes != null) lastUsedChannel = "云端兜底"
                }
            }
            VoiceType.SYSTEM_TTS -> {
                bytes = systemEngine.synthesize(text, config)
                if (bytes != null) lastUsedChannel = "系统TTS"
                // If system TTS is missing Chinese voice, fall back to Sherpa
                if (bytes == null && sherpaEngine.modelManager.isModelReady(VoiceType.NAILONG)) {
                    bytes = sherpaEngine.synthesize(text, config.copy(voiceType = VoiceType.NAILONG))
                    if (bytes != null) lastUsedChannel = "端侧离线"
                }
                if (bytes == null) {
                    bytes = cloudEngine.synthesize(text, config)
                    if (bytes != null) lastUsedChannel = "云端兜底"
                }
            }
        }

        return bytes
    }

    private fun buildCacheKey(text: String, config: VoiceConfig): String {
        val raw = "${config.voiceType.name}_${config.speed}_${config.pitchOffsetHz}_${config.fishAudioApiKey}_${config.customApiUrl}_$text"
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun clearCache() {
        scope.launch {
            cacheDir.listFiles()?.forEach { it.delete() }
        }
    }

    fun shutdown() {
        sherpaEngine.release()
        systemEngine.shutdown()
    }
}
