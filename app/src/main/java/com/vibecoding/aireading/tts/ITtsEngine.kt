package com.vibecoding.aireading.tts

import com.vibecoding.aireading.model.VoiceConfig

interface ITtsEngine {
    /**
     * Synthesizes given text into raw audio bytes (e.g. MP3 or WAV).
     * Returns null if synthesis failed.
     */
    suspend fun synthesize(text: String, config: VoiceConfig): ByteArray?
}
