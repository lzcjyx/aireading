package com.vibecoding.aireading.tts.sherpa

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.vibecoding.aireading.model.VoiceConfig
import com.vibecoding.aireading.model.VoiceType
import com.vibecoding.aireading.tts.ITtsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SherpaOnnxTtsEngine(private val context: Context) : ITtsEngine {

    val modelManager = SherpaModelManager(context)
    private var currentTts: OfflineTts? = null
    private var currentLoadedType: VoiceType? = null

    @Synchronized
    private fun getOrCreateTts(voiceType: VoiceType): OfflineTts? {
        if (currentLoadedType == voiceType && currentTts != null) {
            return currentTts
        }

        val paths = modelManager.getModelPaths(voiceType) ?: return null
        if (!modelManager.isModelReady(voiceType)) return null

        try {
            currentTts?.release()
            currentTts = null

            // Important: dataDir and dictDir must be empty for Piper Chinese VITS models.
            // If dataDir is non-empty, sherpa-onnx checks for dataDir/phontab (espeak-ng) and fails validation!
            val vitsConfig = OfflineTtsVitsModelConfig(
                model = paths.model,
                lexicon = paths.lexicon,
                tokens = paths.tokens,
                dataDir = "",
                dictDir = "",
                noiseScale = 0.667f,
                noiseScaleW = 0.8f,
                lengthScale = 1.0f
            )
            val modelConfig = OfflineTtsModelConfig(
                vits = vitsConfig,
                numThreads = 2,
                debug = false
            )
            val ttsConfig = OfflineTtsConfig(
                model = modelConfig,
                ruleFsts = paths.ruleFsts
            )

            currentTts = OfflineTts(config = ttsConfig)
            currentLoadedType = voiceType
            return currentTts
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private val mutex = kotlinx.coroutines.sync.Mutex()

    override suspend fun synthesize(text: String, config: VoiceConfig): ByteArray? = withContext(Dispatchers.IO) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return@withContext null

        mutex.withLock {
            try {
                val tts = getOrCreateTts(config.voiceType) ?: return@withLock null
                val audio = tts.generate(
                    text = cleanText,
                    sid = 0,
                    speed = config.speed.coerceIn(0.6f, 2.0f)
                )

                val samples = audio.samples
                if (samples.isNotEmpty()) {
                    writeWav(samples, audio.sampleRate)
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Converts float samples in [-1.0, 1.0] to a standard 16-bit PCM WAV byte array in memory.
     */
    private fun writeWav(samples: FloatArray, sampleRate: Int): ByteArray {
        val numSamples = samples.size
        val numChannels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * numChannels * bitsPerSample / 8
        val blockAlign = numChannels * bitsPerSample / 8
        val dataSize = numSamples * 2
        val totalChunkSize = 36 + dataSize

        val byteBuffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        // RIFF chunk
        byteBuffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        byteBuffer.putInt(totalChunkSize)
        byteBuffer.put("WAVE".toByteArray(Charsets.US_ASCII))

        // fmt chunk
        byteBuffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        byteBuffer.putInt(16) // Subchunk1Size for PCM
        byteBuffer.putShort(1.toShort()) // AudioFormat 1 = PCM
        byteBuffer.putShort(numChannels.toShort())
        byteBuffer.putInt(sampleRate)
        byteBuffer.putInt(byteRate)
        byteBuffer.putShort(blockAlign.toShort())
        byteBuffer.putShort(bitsPerSample.toShort())

        // data chunk
        byteBuffer.put("data".toByteArray(Charsets.US_ASCII))
        byteBuffer.putInt(dataSize)

        // PCM 16-bit samples
        for (sample in samples) {
            val clamped = sample.coerceIn(-1.0f, 1.0f)
            val pcm = (clamped * 32767.0f).toInt().toShort()
            byteBuffer.putShort(pcm)
        }

        return byteBuffer.array()
    }

    fun release() {
        currentTts?.release()
        currentTts = null
        currentLoadedType = null
    }
}
