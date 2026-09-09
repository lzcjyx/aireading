package com.vibecoding.aireading.model

import java.io.Serializable

data class PlaybackState(
    val bookId: String? = null,
    val chapterIndex: Int = 0,
    val sentenceIndex: Int = 0,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val voiceType: VoiceType = VoiceType.NAILONG,
    val engineChannel: String? = null,
    val errorMessage: String? = null
) : Serializable
