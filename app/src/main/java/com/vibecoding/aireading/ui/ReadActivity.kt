package com.vibecoding.aireading.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.vibecoding.aireading.R
import com.vibecoding.aireading.data.BookRepository
import com.vibecoding.aireading.databinding.ActivityReaderBinding
import com.vibecoding.aireading.model.Book
import com.vibecoding.aireading.model.Chapter
import com.vibecoding.aireading.model.PlaybackState
import com.vibecoding.aireading.model.VoiceConfig
import com.vibecoding.aireading.model.VoiceType
import com.vibecoding.aireading.service.ReadAloudService
import com.vibecoding.aireading.ui.dialog.TocBottomSheetDialog
import com.vibecoding.aireading.ui.dialog.VoiceSelectDialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ReadActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReaderBinding
    private lateinit var repository: BookRepository

    private var currentBook: Book? = null
    private var chapters: List<Chapter> = emptyList()
    private var currentChapterIndex: Int = 0

    private var readService: ReadAloudService? = null
    private var isBound = false

    private var voiceConfig = VoiceConfig(VoiceType.NAILONG)
    private var isBarsVisible = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ReadAloudService.LocalBinder
            readService = binder.getService()
            isBound = true
            observePlaybackState()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            readService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = BookRepository(this)
        voiceConfig = VoiceConfig.loadFromPrefs(this)
        currentBook = intent.getSerializableExtra("EXTRA_BOOK") as? Book

        if (currentBook == null) {
            finish()
            return
        }

        chapters = repository.loadChapters(currentBook!!)
        currentChapterIndex = currentBook!!.currentChapterIndex.coerceIn(0, maxOf(0, chapters.size - 1))

        initViews()
        bindPlaybackService()
    }

    private fun initViews() {
        binding.tvHeaderTitle.text = currentBook?.title ?: "小说阅读"
        updateVoiceButtonBadge()

        updateShelfButton()

        // Setup Reader Canvas View
        if (chapters.isNotEmpty()) {
            loadAndDisplayChapter(currentChapterIndex, currentBook?.currentSentenceIndex ?: 0)
        }

        binding.readerCanvasView.onCenterClicked = {
            toggleBars()
        }

        binding.readerCanvasView.onSentenceClicked = { sentenceIdx ->
            val service = readService
            if (service != null && service.playbackState.value.bookId == currentBook?.id && service.playbackState.value.chapterIndex == currentChapterIndex) {
                service.jumpTo(currentChapterIndex, sentenceIdx)
            } else {
                startOrJumpTo(currentChapterIndex, sentenceIdx)
            }
        }

        binding.readerCanvasView.onNextChapterRequested = {
            if (currentChapterIndex + 1 < chapters.size) {
                Toast.makeText(this, "进入下一章: ${chapters[currentChapterIndex + 1].title}", Toast.LENGTH_SHORT).show()
                loadAndDisplayChapter(currentChapterIndex + 1, 0)
                if (readService?.playbackState?.value?.isPlaying == true) {
                    startOrJumpTo(currentChapterIndex, 0)
                }
            } else {
                Toast.makeText(this, "已经是全书最后一章了", Toast.LENGTH_SHORT).show()
            }
        }

        binding.readerCanvasView.onPrevChapterRequested = {
            if (currentChapterIndex > 0) {
                Toast.makeText(this, "进入上一章: ${chapters[currentChapterIndex - 1].title}", Toast.LENGTH_SHORT).show()
                loadAndDisplayChapter(currentChapterIndex - 1, 0)
                if (readService?.playbackState?.value?.isPlaying == true) {
                    startOrJumpTo(currentChapterIndex, 0)
                }
            } else {
                Toast.makeText(this, "已经是第一章了", Toast.LENGTH_SHORT).show()
            }
        }

        binding.readerCanvasView.onPageChanged = { current, total ->
            // Page changed
        }

        // Top Bar actions
        binding.btnBack.setOnClickListener { finish() }
        binding.btnToc.setOnClickListener { showTocDialog() }
        binding.btnVoiceSelect.setOnClickListener { showVoiceDialog() }
        binding.btnCloseBars.setOnClickListener { hideBars() }

        // Manual Page Buttons in Bottom Bar
        binding.btnPrevPage.setOnClickListener { binding.readerCanvasView.prevPage() }
        binding.btnNextPage.setOnClickListener { binding.readerCanvasView.nextPage() }

        // Bars start visible for initial user orientation, user can tap center or [X] to hide
        showBars()

        // Bottom Bar Playback actions
        binding.btnPlayPause.setOnClickListener {
            val service = readService ?: return@setOnClickListener
            if (service.playbackState.value.isPlaying) {
                service.pause()
            } else {
                if (service.playbackState.value.bookId == currentBook?.id &&
                    service.playbackState.value.chapterIndex == currentChapterIndex
                ) {
                    service.play()
                } else {
                    val curSentenceIdx = binding.readerCanvasView.getActiveSentenceIndex().coerceAtLeast(0)
                    startOrJumpTo(currentChapterIndex, curSentenceIdx)
                }
            }
        }

        binding.btnNextSentence.setOnClickListener { readService?.nextSentence() }
        binding.btnPrevSentence.setOnClickListener { readService?.prevSentence() }
        binding.btnStop.setOnClickListener { readService?.stopReading() }

        // Progress seekbar
        binding.seekProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && chapters.isNotEmpty()) {
                    val curChap = chapters[currentChapterIndex]
                    if (curChap.sentences.isNotEmpty()) {
                        val sentIdx = (progress * (curChap.sentences.size - 1) / 100).coerceIn(0, curChap.sentences.size - 1)
                        binding.readerCanvasView.setActiveSentence(sentIdx)
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (chapters.isNotEmpty()) {
                    val curChap = chapters[currentChapterIndex]
                    if (curChap.sentences.isNotEmpty()) {
                        val sentIdx = (seekBar!!.progress * (curChap.sentences.size - 1) / 100).coerceIn(0, curChap.sentences.size - 1)
                        startOrJumpTo(currentChapterIndex, sentIdx)
                    }
                }
            }
        })

        // Font size buttons
        binding.btnFontInc.setOnClickListener {
            val cur = binding.readerCanvasView.getFontSize()
            val next = (cur + 2f).coerceAtMost(32f)
            binding.readerCanvasView.setFontSize(next)
            binding.tvFontSize.text = next.toInt().toString()
        }

        binding.btnFontDec.setOnClickListener {
            val cur = binding.readerCanvasView.getFontSize()
            val next = (cur - 2f).coerceAtLeast(14f)
            binding.readerCanvasView.setFontSize(next)
            binding.tvFontSize.text = next.toInt().toString()
        }

        // Themes
        binding.themeParchment.setOnClickListener {
            applyTheme(0xFFF6F1E7.toInt(), 0xFF2B2520.toInt(), 0x35F59E0B.toInt())
        }
        binding.themeGreen.setOnClickListener {
            applyTheme(0xFFE9F2EA.toInt(), 0xFF1E3120.toInt(), 0x3510B981.toInt())
        }
        binding.themeNight.setOnClickListener {
            applyTheme(0xFF0F1012.toInt(), 0xFFD1D5DB.toInt(), 0x35FBBF24.toInt())
        }
        binding.themeWhite.setOnClickListener {
            applyTheme(0xFFFFFFFF.toInt(), 0xFF1F2937.toInt(), 0x35E5E7EB.toInt())
        }
    }

    private fun applyTheme(bgColor: Int, textColor: Int, highlightColor: Int) {
        binding.readerCanvasView.themeBgColor = bgColor
        binding.readerCanvasView.themeTextColor = textColor
        binding.readerCanvasView.themeHighlightColor = highlightColor
        binding.readerRoot.setBackgroundColor(bgColor)
    }

    private fun bindPlaybackService() {
        val intent = Intent(this, ReadAloudService::class.java)
        startService(intent) // Ensure service stays alive
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun observePlaybackState() {
        lifecycleScope.launch {
            readService?.playbackState?.collectLatest { state ->
                updateUiForPlaybackState(state)
            }
        }
    }

    private fun updateUiForPlaybackState(state: PlaybackState) {
        if (state.bookId != currentBook?.id) {
            binding.btnPlayPause.setImageResource(R.drawable.ic_play)
            binding.tvCurrentSentence.text = "轻触文字或点击播放开启 AI 朗读"
            binding.liveSoundWave.isPlaying = false
            return
        }

        // Live Equalizer Soundwave
        binding.liveSoundWave.isPlaying = state.isPlaying && !state.isLoading

        // Play / Pause FAB icon
        if (state.isPlaying) {
            binding.btnPlayPause.setImageResource(R.drawable.ic_pause)
        } else {
            binding.btnPlayPause.setImageResource(R.drawable.ic_play)
        }

        // Check if chapter changed
        if (state.chapterIndex != currentChapterIndex && state.chapterIndex in chapters.indices) {
            loadAndDisplayChapter(state.chapterIndex, state.sentenceIndex)
            repository.saveProgress(currentBook!!.id, state.chapterIndex, state.sentenceIndex)
        } else {
            // Update active sentence highlight & auto page-turn
            binding.readerCanvasView.setActiveSentence(state.sentenceIndex)
            repository.saveProgress(currentBook!!.id, currentChapterIndex, state.sentenceIndex)
        }

        // Update sentence preview text & seek progress
        val curChap = chapters.getOrNull(currentChapterIndex)
        val curSent = curChap?.sentences?.getOrNull(state.sentenceIndex)
        if (curSent != null) {
            val roleName = when (state.voiceType) {
                VoiceType.NAILONG -> "🐉 奶龙"
                VoiceType.DAGOU -> "🐕 大狗"
                VoiceType.CUSTOM_API -> "⚡ 模型"
                VoiceType.SYSTEM_TTS -> "📱 系统"
            }
            val channelTag = state.engineChannel?.let { " [$it]" } ?: ""
            val prefix = "$roleName$channelTag: "
            binding.tvCurrentSentence.text = if (state.isLoading) "${prefix}正在思考合成..." else "${prefix}${curSent.text}"

            val totalSents = curChap.sentences.size
            if (totalSents > 0) {
                binding.seekProgress.progress = (state.sentenceIndex * 100 / totalSents).coerceIn(0, 100)
            }
        }

        if (state.errorMessage != null) {
            binding.tvCurrentSentence.text = "⚠️ ${state.errorMessage}"
            Toast.makeText(this@ReadActivity, state.errorMessage, Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadAndDisplayChapter(chapIdx: Int, initialSentenceIdx: Int = 0) {
        if (chapIdx !in chapters.indices) return
        currentChapterIndex = chapIdx
        val chap = chapters[chapIdx]
        if (chap.content.isNotBlank()) {
            binding.readerCanvasView.setChapter(chap, initialSentenceIdx)
        } else {
            lifecycleScope.launch {
                binding.tvCurrentSentence.text = "正在从网络加载本章正文..."
                val loaded = repository.ensureChapterContent(currentBook!!, chap)
                if (loaded.content.isNotBlank()) {
                    binding.readerCanvasView.setChapter(loaded, initialSentenceIdx)
                    binding.tvCurrentSentence.text = "本章已加载完毕，点击播放开启朗读"
                } else {
                    val fallbackChapter = loaded.copy(
                        content = "【章节正文加载失败】\n\n未能从当前书源获取到有效正文内容。\n\n可能原因：\n1. 当前书源正文解析规则失效或网站改版\n2. 目标网站开启了防盗链或章节已被屏蔽\n\n建议操作：\n点击左上角返回，在全网搜书中重新搜索《${currentBook?.title}》，更换其它书源阅读。"
                    )
                    binding.readerCanvasView.setChapter(fallbackChapter, 0)
                    binding.tvCurrentSentence.text = "⚠️ 正文加载失败，建议更换其他书源"
                    Toast.makeText(this@ReadActivity, "章节正文加载失败，建议更换书源", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startOrJumpTo(chapterIdx: Int, sentenceIdx: Int) {
        val book = currentBook ?: return
        if (chapters.isEmpty()) return
        val chap = chapters.getOrNull(chapterIdx)
        if (chap != null && chap.content.isBlank()) {
            Toast.makeText(this, "正在加载本章内容，请稍候...", Toast.LENGTH_SHORT).show()
            loadAndDisplayChapter(chapterIdx, sentenceIdx)
            return
        }
        readService?.startReading(book, chapters, chapterIdx, sentenceIdx, voiceConfig)
    }

    private fun showVoiceDialog() {
        val dialog = VoiceSelectDialog(this, voiceConfig) { newConfig ->
            voiceConfig = newConfig
            voiceConfig.saveToPrefs(this)
            updateVoiceButtonBadge()
            readService?.setVoiceConfig(newConfig)
        }
        dialog.show()
    }

    private fun showTocDialog() {
        val dialog = TocBottomSheetDialog(this, chapters, currentChapterIndex) { selectedChapterIdx ->
            loadAndDisplayChapter(selectedChapterIdx, 0)
            if (readService?.playbackState?.value?.isPlaying == true) {

                startOrJumpTo(currentChapterIndex, 0)
            }
        }
        dialog.show()
    }

    private fun updateShelfButton() {
        val inShelf = currentBook?.let { repository.isBookInShelf(it.id) } ?: false
        binding.btnAddToShelf.visibility = View.VISIBLE
        if (inShelf) {
            binding.btnAddToShelf.text = "✓ 已在架"
            binding.btnAddToShelf.alpha = 0.75f
            binding.btnAddToShelf.setOnClickListener {
                Toast.makeText(this, "《${currentBook?.title}》已在书架中", Toast.LENGTH_SHORT).show()
            }
        } else {
            binding.btnAddToShelf.text = "+ 移入书架"
            binding.btnAddToShelf.alpha = 1.0f
            binding.btnAddToShelf.setOnClickListener {
                currentBook?.let { book ->
                    repository.saveBook(book)
                    repository.saveChaptersToc(book.id, chapters)
                    updateShelfButton()
                    Toast.makeText(this, "《${book.title}》已成功加入书架！", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateVoiceButtonBadge() {
        val roleName = when (voiceConfig.voiceType) {
            VoiceType.NAILONG -> "🐉 奶龙"
            VoiceType.DAGOU -> "🐕 大狗叫"
            VoiceType.CUSTOM_API -> "⚡ 自定义"
            VoiceType.SYSTEM_TTS -> "📱 系统"
        }
        val channel = when {
            voiceConfig.voiceType == VoiceType.SYSTEM_TTS -> "离线TTS"
            voiceConfig.fishAudioApiKey.isNotBlank() -> "Fish AI"
            voiceConfig.voiceType == VoiceType.CUSTOM_API -> "自建API"
            else -> "端侧离线"
        }
        binding.btnVoiceSelect.text = "$roleName [$channel]"
    }

    private fun showBars() {
        isBarsVisible = true
        binding.topBar.apply {
            visibility = View.VISIBLE
            alpha = 0f
            translationY = -height.toFloat().coerceAtLeast(140f)
            animate().alpha(1f).translationY(0f).setDuration(220).start()
        }
        binding.bottomBar.apply {
            visibility = View.VISIBLE
            alpha = 0f
            translationY = height.toFloat().coerceAtLeast(140f)
            animate().alpha(1f).translationY(0f).setDuration(220).start()
        }
    }

    private fun hideBars() {
        isBarsVisible = false
        binding.topBar.animate()
            .alpha(0f)
            .translationY(-binding.topBar.height.toFloat().coerceAtLeast(140f))
            .setDuration(200)
            .withEndAction { binding.topBar.visibility = View.GONE }
            .start()
        binding.bottomBar.animate()
            .alpha(0f)
            .translationY(binding.bottomBar.height.toFloat().coerceAtLeast(140f))
            .setDuration(200)
            .withEndAction { binding.bottomBar.visibility = View.GONE }
            .start()
    }

    private fun toggleBars() {
        if (isBarsVisible) hideBars() else showBars()
    }

    override fun onResume() {
        super.onResume()
        // Screen-On resume synchronization!
        val service = readService
        if (service != null && service.playbackState.value.bookId == currentBook?.id) {
            val state = service.playbackState.value
            if (state.chapterIndex != currentChapterIndex && state.chapterIndex in chapters.indices) {
                currentChapterIndex = state.chapterIndex
                binding.readerCanvasView.setChapter(chapters[currentChapterIndex], state.sentenceIndex)
            } else {
                binding.readerCanvasView.setActiveSentence(state.sentenceIndex)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}
