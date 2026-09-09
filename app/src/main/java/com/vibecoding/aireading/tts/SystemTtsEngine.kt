package com.vibecoding.aireading.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.vibecoding.aireading.model.VoiceConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.UUID

class SystemTtsEngine(private val context: Context) : ITtsEngine {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val initDeferred = CompletableDeferred<Boolean>()

    init {
        try {
            tts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val res1 = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)
                    if (res1 == TextToSpeech.LANG_MISSING_DATA || res1 == TextToSpeech.LANG_NOT_SUPPORTED) {
                        val res2 = tts?.setLanguage(Locale.CHINA)
                        if (res2 == TextToSpeech.LANG_MISSING_DATA || res2 == TextToSpeech.LANG_NOT_SUPPORTED) {
                            tts?.setLanguage(Locale.CHINESE)
                        }
                    }
                    isInitialized = true
                    initDeferred.complete(true)
                } else {
                    initDeferred.complete(false)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            initDeferred.complete(false)
        }
    }

    private val mutex = kotlinx.coroutines.sync.Mutex()

    override suspend fun synthesize(text: String, config: VoiceConfig): ByteArray? {
        val ready = withTimeoutOrNull(5000L) {
            if (isInitialized) true else initDeferred.await()
        } ?: false

        if (!ready || tts == null) return null

        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val tempFile = File(context.cacheDir, "systts_${UUID.randomUUID()}.wav")
                val utteranceId = UUID.randomUUID().toString()
                val finishDeferred = CompletableDeferred<Boolean>()

                tts?.setSpeechRate(config.speed)
                tts?.setPitch(1.0f + (config.pitchOffsetHz / 100f))

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) {
                        if (id == utteranceId) finishDeferred.complete(true)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) {
                        if (id == utteranceId) finishDeferred.complete(false)
                    }
                    override fun onError(id: String?, errorCode: Int) {
                        if (id == utteranceId) finishDeferred.complete(false)
                    }
                })

                val params = Bundle()
                val queueRes = tts?.synthesizeToFile(text, params, tempFile, utteranceId)
                if (queueRes != TextToSpeech.SUCCESS) {
                    tempFile.delete()
                    return@withLock null
                }

                val success = withTimeoutOrNull(4_000L) { finishDeferred.await() } ?: false
                if (success && tempFile.exists() && tempFile.length() > 0) {
                    val bytes = tempFile.readBytes()
                    tempFile.delete()
                    bytes
                } else {
                    tempFile.delete()
                    null
                }
            }
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
