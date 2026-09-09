package com.vibecoding.aireading.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.vibecoding.aireading.data.BookRepository
import com.vibecoding.aireading.model.Book

import com.vibecoding.aireading.model.Chapter
import com.vibecoding.aireading.model.PlaybackState
import com.vibecoding.aireading.model.Sentence
import com.vibecoding.aireading.model.VoiceConfig
import com.vibecoding.aireading.tts.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReadAloudService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var playJob: Job? = null

    private lateinit var ttsManager: TtsManager
    private lateinit var audioManager: AudioManager
    private lateinit var powerManager: PowerManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaPlayer: MediaPlayer? = null
    private var mediaSession: MediaSessionCompat? = null

    private var currentBook: Book? = null
    private var chapters: List<Chapter> = emptyList()
    private var currentChapterIndex: Int = 0
    private var currentSentenceIndex: Int = 0
    private var voiceConfig: VoiceConfig = VoiceConfig()
    private var consecutiveFailures: Int = 0

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    private var isAudioPaused = false

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                try { mediaPlayer?.setVolume(0.2f, 0.2f) } catch (e: Exception) {}
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasAudioFocus = false
                try { mediaPlayer?.setVolume(0.3f, 0.3f) } catch (e: Exception) {}
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                try {
                    mediaPlayer?.setVolume(1.0f, 1.0f)
                    if (mediaPlayer != null && !mediaPlayer!!.isPlaying && _playbackState.value.isPlaying) {
                        mediaPlayer?.start()
                    }
                } catch (e: Exception) {}
            }
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): ReadAloudService = this@ReadAloudService
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                pause()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ttsManager = TtsManager(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AiReading:ReadAloudServiceWakeLock").apply {
            setReferenceCounted(false)
        }

        NotificationHelper.createNotificationChannel(this)
        setupMediaSession()

        registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "AiReadingMediaSession").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { play() }
                override fun onPause() { pause() }
                override fun onSkipToNext() { nextSentence() }
                override fun onSkipToPrevious() { prevSentence() }
                override fun onStop() { stopReading() }
            })
            isActive = true
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            NotificationHelper.ACTION_PLAY -> play()
            NotificationHelper.ACTION_PAUSE -> pause()
            NotificationHelper.ACTION_PREV -> prevSentence()
            NotificationHelper.ACTION_NEXT -> nextSentence()
            NotificationHelper.ACTION_STOP -> stopReading()
        }
        return START_NOT_STICKY
    }

    fun startReading(book: Book, bookChapters: List<Chapter>, chapterIdx: Int, sentenceIdx: Int, config: VoiceConfig) {
        currentBook = book
        chapters = bookChapters
        currentChapterIndex = chapterIdx.coerceIn(0, maxOf(0, bookChapters.size - 1))
        currentSentenceIndex = sentenceIdx
        voiceConfig = config

        requestAudioFocus()
        wakeLock?.acquire(60 * 60 * 1000L) // 1 hour max, auto-renewed
        updateNotification(isPlaying = true)
        playSentence(currentChapterIndex, currentSentenceIndex)
    }

    fun setVoiceConfig(config: VoiceConfig) {
        voiceConfig = config
        _playbackState.value = _playbackState.value.copy(voiceType = config.voiceType)
        if (_playbackState.value.isPlaying) {
            // Re-play current sentence with new voice
            playSentence(currentChapterIndex, currentSentenceIndex)
        }
    }

    fun canResume(chapterIdx: Int, sentenceIdx: Int): Boolean {
        return currentBook != null &&
                currentChapterIndex == chapterIdx &&
                currentSentenceIndex == sentenceIdx &&
                mediaPlayer != null
    }

    fun play() {
        if (chapters.isEmpty()) return
        consecutiveFailures = 0
        requestAudioFocus()
        try {
            wakeLock?.acquire(60 * 60 * 1000L)
        } catch (e: Exception) {}
        val player = mediaPlayer
        if (isAudioPaused && player != null) {
            val isNearEnd = try {
                player.duration > 0 && player.currentPosition >= (player.duration - 300)
            } catch (e: Exception) {
                false
            }
            if (!isNearEnd) {
                try {
                    player.start()
                    isAudioPaused = false
                    updateState(isPlaying = true, isLoading = false)
                    updateNotification(isPlaying = true)
                    updateMediaSessionState(PlaybackStateCompat.STATE_PLAYING)
                    return
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        isAudioPaused = false
        playSentence(currentChapterIndex, currentSentenceIndex)
    }

    fun pause() {
        playJob?.cancel()
        val player = mediaPlayer
        if (player != null) {
            try {
                if (player.isPlaying) {
                    player.pause()
                    isAudioPaused = true
                }
            } catch (e: Exception) {
                isAudioPaused = false
            }
        } else {
            isAudioPaused = false
        }
        try {
            wakeLock?.release()
        } catch (e: Exception) {}
        updateState(isPlaying = false, isLoading = false)
        updateNotification(isPlaying = false)
        updateMediaSessionState(PlaybackStateCompat.STATE_PAUSED)
    }

    fun stopReading() {
        playJob?.cancel()
        isAudioPaused = false
        consecutiveFailures = 0
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {}
        mediaPlayer = null
        abandonAudioFocus()
        try {
            wakeLock?.release()
        } catch (e: Exception) {}
        updateState(isPlaying = false, isLoading = false)
        updateMediaSessionState(PlaybackStateCompat.STATE_STOPPED)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun nextSentence() {
        consecutiveFailures = 0
        val curChap = chapters.getOrNull(currentChapterIndex) ?: return
        if (currentSentenceIndex + 1 < curChap.sentences.size) {
            playSentence(currentChapterIndex, currentSentenceIndex + 1)
        } else if (currentChapterIndex + 1 < chapters.size) {
            playSentence(currentChapterIndex + 1, 0)
        } else {
            stopReading()
        }
    }

    fun prevSentence() {
        consecutiveFailures = 0
        if (currentSentenceIndex > 0) {
            playSentence(currentChapterIndex, currentSentenceIndex - 1)
        } else if (currentChapterIndex > 0) {
            val prevChap = chapters[currentChapterIndex - 1]
            playSentence(currentChapterIndex - 1, maxOf(0, prevChap.sentences.size - 1))
        }
    }

    fun jumpTo(chapterIdx: Int, sentenceIdx: Int) {
        consecutiveFailures = 0
        playSentence(chapterIdx, sentenceIdx)
    }

    private fun playSentence(chapterIdx: Int, sentenceIdx: Int) {
        playJob?.cancel()
        isAudioPaused = false
        try {
            mediaPlayer?.stop()
            mediaPlayer?.reset()
        } catch (e: Exception) {}

        val book = currentBook ?: return
        val rawChapter = chapters.getOrNull(chapterIdx) ?: return

        updateState(isPlaying = true, isLoading = true)
        updateNotification(isPlaying = true)
        updateMediaSessionState(PlaybackStateCompat.STATE_BUFFERING)

        playJob = serviceScope.launch {
            val chapter = if (rawChapter.content.isBlank()) {
                withContext(Dispatchers.IO) {
                    val loaded = BookRepository(this@ReadAloudService).ensureChapterContent(book, rawChapter)
                    loaded
                }
            } else {
                rawChapter
            }

            val sentences = chapter.sentences
            if (sentences.isEmpty()) {
                pause()
                updateState(isPlaying = false, isLoading = false, error = "当前章节正文为空，请更换书源")
                return@launch
            }

            val safeSentenceIdx = sentenceIdx.coerceIn(0, sentences.size - 1)
            currentChapterIndex = chapterIdx
            currentSentenceIndex = safeSentenceIdx

            val currentSentence = sentences[safeSentenceIdx]

            // Preload upcoming sentences in background
            ttsManager.preload(sentences, safeSentenceIdx + 1, 3, voiceConfig)

            val audioFile = withContext(Dispatchers.IO) {
                ttsManager.getAudioFile(currentSentence, voiceConfig)
            }

            if (audioFile == null || !audioFile.exists()) {
                consecutiveFailures++
                val errReason = com.vibecoding.aireading.tts.FishAudioTtsEngine.lastError ?: "音频合成失败，请检查网络或在音色设置中切换"
                if (consecutiveFailures >= 3) {
                    consecutiveFailures = 0
                    pause()
                    updateState(isPlaying = false, isLoading = false, error = errReason)
                    return@launch
                }
                updateState(isPlaying = true, isLoading = false, error = "$errReason，正在重试...")
                nextSentence()
                return@launch
            }

            consecutiveFailures = 0

            try {
                if (mediaPlayer == null) {
                    mediaPlayer = MediaPlayer()
                } else {
                    mediaPlayer?.reset()
                }

                mediaPlayer?.apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(audioFile.absolutePath)
                    prepare()
                    start()
                    isAudioPaused = false
                    setOnCompletionListener {
                        isAudioPaused = false
                        nextSentence()
                    }
                    setOnErrorListener { _, _, _ ->
                        isAudioPaused = false
                        nextSentence()
                        true
                    }
                }

                updateState(isPlaying = true, isLoading = false)
                updateNotification(isPlaying = true)
                updateMediaSessionState(PlaybackStateCompat.STATE_PLAYING)

            } catch (e: Exception) {
                e.printStackTrace()
                isAudioPaused = false
                nextSentence()
            }
        }
    }

    private fun updateState(isPlaying: Boolean, isLoading: Boolean, error: String? = null) {
        _playbackState.value = PlaybackState(
            bookId = currentBook?.id,
            chapterIndex = currentChapterIndex,
            sentenceIndex = currentSentenceIndex,
            isPlaying = isPlaying,
            isLoading = isLoading,
            voiceType = voiceConfig.voiceType,
            engineChannel = ttsManager.lastUsedChannel,
            errorMessage = error
        )
    }

    private fun updateNotification(isPlaying: Boolean) {
        val book = currentBook ?: return
        val chapter = chapters.getOrNull(currentChapterIndex) ?: return
        val sentence = chapter.sentences.getOrNull(currentSentenceIndex)?.text ?: ""

        val notification = NotificationHelper.buildNotification(
            this,
            book.title,
            chapter.title,
            sentence,
            isPlaying,
            mediaSession?.sessionToken
        )
        startForeground(NotificationHelper.NOTIFICATION_ID, notification)
    }

    private fun updateMediaSessionState(state: Int) {
        val playbackStateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_STOP
            )
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
        mediaSession?.setPlaybackState(playbackStateBuilder.build())
    }

    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        return hasAudioFocus
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus && audioFocusRequest == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(audioFocusChangeListener)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        hasAudioFocus = false
        audioFocusRequest = null
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        try {
            unregisterReceiver(noisyReceiver)
        } catch (e: Exception) {}
        try {
            mediaPlayer?.release()
        } catch (e: Exception) {}
        mediaSession?.release()
        abandonAudioFocus()
        try {
            wakeLock?.release()
        } catch (e: Exception) {}
        ttsManager.shutdown()
    }
}
