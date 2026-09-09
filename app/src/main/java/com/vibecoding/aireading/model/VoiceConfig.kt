package com.vibecoding.aireading.model

import android.content.Context
import java.io.Serializable

enum class VoiceType(val displayName: String, val desc: String) {
    NAILONG("奶龙", "Fish Audio / Sherpa 离线神经网络模型 · 奶龙原声"),
    DAGOU("大狗叫", "Fish Audio / Sherpa 离线神经网络模型 · 魔性大狗叫"),
    CUSTOM_API("自定义 GPT-SoVITS", "直连自建或公网克隆服务"),
    SYSTEM_TTS("系统原生离线 TTS", "无需网络保底")
}

data class VoiceConfig(
    var voiceType: VoiceType = VoiceType.NAILONG,
    var speed: Float = 1.0f,
    var pitchOffsetHz: Int = 0,
    var customApiUrl: String = "http://127.0.0.1:9880/tts",
    var fishAudioApiKey: String = "",
    var fishModelId: String = "",
    var fishCustomEndpoint: String = ""
) : Serializable {

    fun saveToPrefs(context: Context) {
        context.getSharedPreferences("voice_prefs", Context.MODE_PRIVATE).edit()
            .putString("voiceType", voiceType.name)
            .putFloat("speed", speed)
            .putInt("pitchOffsetHz", pitchOffsetHz)
            .putString("customApiUrl", customApiUrl)
            .putString("fishAudioApiKey", fishAudioApiKey)
            .putString("fishModelId", fishModelId)
            .putString("fishCustomEndpoint", fishCustomEndpoint)
            .apply()
    }

    companion object {
        const val FISH_MODEL_NAILONG = "3d1cb00d75184099992ddbaf0fdd7387"
        const val FISH_MODEL_DAGOU = "be33d4aa108d4f8c83c534b36ede1029"

        fun loadFromPrefs(context: Context): VoiceConfig {
            val prefs = context.getSharedPreferences("voice_prefs", Context.MODE_PRIVATE)
            val typeName = prefs.getString("voiceType", VoiceType.NAILONG.name) ?: VoiceType.NAILONG.name
            val type = try { VoiceType.valueOf(typeName) } catch (e: Exception) { VoiceType.NAILONG }
            return VoiceConfig(
                voiceType = type,
                speed = prefs.getFloat("speed", 1.0f),
                pitchOffsetHz = prefs.getInt("pitchOffsetHz", 0),
                customApiUrl = prefs.getString("customApiUrl", "http://127.0.0.1:9880/tts") ?: "http://127.0.0.1:9880/tts",
                fishAudioApiKey = prefs.getString("fishAudioApiKey", "") ?: "",
                fishModelId = prefs.getString("fishModelId", "") ?: "",
                fishCustomEndpoint = prefs.getString("fishCustomEndpoint", "") ?: ""
            )
        }
    }
}
