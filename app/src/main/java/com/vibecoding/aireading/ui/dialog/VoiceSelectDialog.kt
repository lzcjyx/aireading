package com.vibecoding.aireading.ui.dialog

import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.vibecoding.aireading.R
import com.vibecoding.aireading.databinding.DialogVoiceSettingsBinding
import com.vibecoding.aireading.model.Sentence
import com.vibecoding.aireading.model.VoiceConfig
import com.vibecoding.aireading.model.VoiceType
import com.vibecoding.aireading.tts.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VoiceSelectDialog(
    context: Context,
    private val currentConfig: VoiceConfig,
    private val onConfigApplied: (VoiceConfig) -> Unit
) : BottomSheetDialog(context) {

    private lateinit var binding: DialogVoiceSettingsBinding
    private val ttsManager = TtsManager(context)
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var previewPlayer: MediaPlayer? = null
    private var workingConfig = currentConfig.copy()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogVoiceSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setOnShowListener {
            val bottomSheet = findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            if (bottomSheet != null) {
                val behavior = BottomSheetBehavior.from(bottomSheet)
                behavior.skipCollapsed = true
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }

        initViews()
        checkOfflineModels()
        updateActiveChannelDisplay()
    }

    private fun checkOfflineModels() {
        val nailongReady = ttsManager.sherpaEngine.modelManager.isModelReady(VoiceType.NAILONG)
        binding.tvNailongBadge.visibility = if (nailongReady) View.VISIBLE else View.GONE
        binding.layoutNailongDownload.visibility = if (nailongReady) View.GONE else View.VISIBLE
        if (nailongReady) binding.layoutNailongProgress.visibility = View.GONE

        val dagouReady = ttsManager.sherpaEngine.modelManager.isModelReady(VoiceType.DAGOU)
        binding.tvDagouBadge.visibility = if (dagouReady) View.VISIBLE else View.GONE
        binding.layoutDagouDownload.visibility = if (dagouReady) View.GONE else View.VISIBLE
        if (dagouReady) binding.layoutDagouProgress.visibility = View.GONE

        updateActiveChannelDisplay()
    }

    private fun updateActiveChannelDisplay() {
        val apiKey = binding.etFishApiKey.text?.toString()?.trim() ?: ""
        val customUrl = binding.etCustomApi.text?.toString()?.trim() ?: ""
        val nailongReady = ttsManager.sherpaEngine.modelManager.isModelReady(VoiceType.NAILONG)
        val dagouReady = ttsManager.sherpaEngine.modelManager.isModelReady(VoiceType.DAGOU)

        val roleTitle = when (workingConfig.voiceType) {
            VoiceType.NAILONG -> "🐉 奶龙原声"
            VoiceType.DAGOU -> "🐕 大狗叫原声"
            VoiceType.CUSTOM_API -> "⚡ 自定义API"
            VoiceType.SYSTEM_TTS -> "📱 系统原生TTS"
        }
        binding.tvActiveVoiceTitle.text = roleTitle

        when (workingConfig.voiceType) {
            VoiceType.NAILONG -> {
                if (apiKey.isNotEmpty()) {
                    binding.tvActiveEngineTag.text = "🟢 Fish AI 在线大模型"
                    binding.tvActiveEngineTag.setBackgroundResource(R.drawable.bg_tag_offline)
                    binding.tvActiveEngineDetail.text = "🟢 正在调用 Fish Audio 官方神经克隆大模型 · 原版奶龙原声"
                    binding.tvActiveEngineHint.text = "💡 官方服务器位于海外，建议开启加速器使用；若连接超时（>4s）将自动降级为离线语音，绝不卡死。"
                } else if (nailongReady) {
                    binding.tvActiveEngineTag.text = "🟢 端侧离线轻量模型"
                    binding.tvActiveEngineTag.setBackgroundResource(R.drawable.bg_tag_offline)
                    binding.tvActiveEngineDetail.text = "🟢 正在使用端侧 Sherpa-ONNX 离线语音包 (14MB · 免流量)"
                    binding.tvActiveEngineHint.text = "💡 本地实时纯离线合成，无网也能流畅朗读，熄屏不掉线。"
                } else {
                    binding.tvActiveEngineTag.text = "📱 手机系统 TTS 兜底"
                    binding.tvActiveEngineTag.setBackgroundResource(R.drawable.bg_tag_offline)
                    binding.tvActiveEngineDetail.text = "📱 当前使用手机系统原生语音兜底朗读 (秒级出声)"
                    binding.tvActiveEngineHint.text = "💡 建议下载上方【端侧离线语音包】或填入【Fish Audio API Key】以获得奶龙特色原声。"
                }
            }
            VoiceType.DAGOU -> {
                if (apiKey.isNotEmpty()) {
                    binding.tvActiveEngineTag.text = "🟢 Fish AI 在线大模型"
                    binding.tvActiveEngineTag.setBackgroundResource(R.drawable.bg_tag_offline)
                    binding.tvActiveEngineDetail.text = "🟢 正在调用 Fish Audio 官方神经克隆大模型 · 100% 网络原版大狗叫"
                    binding.tvActiveEngineHint.text = "💡 官方服务器位于海外，建议开启加速器使用；若连接超时（>4s）将自动降级为离线语音，绝不卡死。"
                } else if (dagouReady) {
                    binding.tvActiveEngineTag.text = "🟢 端侧离线轻量模型"
                    binding.tvActiveEngineTag.setBackgroundResource(R.drawable.bg_tag_offline)
                    binding.tvActiveEngineDetail.text = "🟢 正在使用端侧 Sherpa-ONNX 离线语音包 (14MB · 免流量)"
                    binding.tvActiveEngineHint.text = "💡 本地实时纯离线合成，无网也能流畅朗读，熄屏不掉线。"
                } else {
                    binding.tvActiveEngineTag.text = "📱 手机系统 TTS 兜底"
                    binding.tvActiveEngineTag.setBackgroundResource(R.drawable.bg_tag_offline)
                    binding.tvActiveEngineDetail.text = "📱 当前使用手机系统原生语音兜底朗读 (秒级出声)"
                    binding.tvActiveEngineHint.text = "💡 建议下载上方【端侧离线语音包】或填入【Fish Audio API Key】以获得魔性大狗原声。"
                }
            }
            VoiceType.CUSTOM_API -> {
                binding.tvActiveEngineTag.text = "⚡ 自定义 GPT-SoVITS"
                binding.tvActiveEngineDetail.text = "⚡ 正在调用自建或公网克隆服务: ${if (customUrl.isNotEmpty()) customUrl else "未设置"}"
                binding.tvActiveEngineHint.text = "💡 适合有私有服务器的高级用户直连部署。"
            }
            VoiceType.SYSTEM_TTS -> {
                binding.tvActiveEngineTag.text = "📱 手机系统原生 TTS"
                binding.tvActiveEngineDetail.text = "📱 100% 离线极速朗读，系统级秒出声"
                binding.tvActiveEngineHint.text = "💡 极简无网络消耗，最省电、最稳定的读书保底选择。"
            }
        }
    }

    private fun initViews() {
        updateCardSelection(workingConfig.voiceType)

        binding.etCustomApi.setText(workingConfig.customApiUrl)
        binding.etFishApiKey.setText(workingConfig.fishAudioApiKey)
        binding.etFishModelId.setText(workingConfig.fishModelId)
        binding.etFishEndpoint.setText(workingConfig.fishCustomEndpoint)

        // Realtime status updates on user typing
        binding.etFishApiKey.doAfterTextChanged {
            workingConfig.fishAudioApiKey = it?.toString()?.trim() ?: ""
            updateActiveChannelDisplay()
        }
        binding.etCustomApi.doAfterTextChanged {
            workingConfig.customApiUrl = it?.toString()?.trim() ?: ""
            updateActiveChannelDisplay()
        }
        binding.etFishEndpoint.doAfterTextChanged {
            workingConfig.fishCustomEndpoint = it?.toString()?.trim() ?: ""
        }
        binding.etFishModelId.doAfterTextChanged {
            workingConfig.fishModelId = it?.toString()?.trim() ?: ""
        }

        binding.btnFillNailongModel.setOnClickListener {
            binding.etFishModelId.setText(VoiceConfig.FISH_MODEL_NAILONG)
            Toast.makeText(context, "已填入奶龙官方模型 ID", Toast.LENGTH_SHORT).show()
        }

        binding.btnFillDagouModel.setOnClickListener {
            binding.etFishModelId.setText(VoiceConfig.FISH_MODEL_DAGOU)
            Toast.makeText(context, "已填入大狗叫官方模型 ID", Toast.LENGTH_SHORT).show()
        }

        // Offline model download buttons
        binding.btnDownloadNailong.setOnClickListener {
            startDownloadModel(VoiceType.NAILONG)
        }

        binding.btnDownloadDagou.setOnClickListener {
            startDownloadModel(VoiceType.DAGOU)
        }

        // Card Clicks
        binding.cardNailong.setOnClickListener {
            workingConfig.voiceType = VoiceType.NAILONG
            updateCardSelection(VoiceType.NAILONG)
            updateActiveChannelDisplay()
        }

        binding.cardDagou.setOnClickListener {
            workingConfig.voiceType = VoiceType.DAGOU
            updateCardSelection(VoiceType.DAGOU)
            updateActiveChannelDisplay()
        }

        binding.cardCustom.setOnClickListener {
            workingConfig.voiceType = VoiceType.CUSTOM_API
            updateCardSelection(VoiceType.CUSTOM_API)
            updateActiveChannelDisplay()
        }

        binding.cardSystem.setOnClickListener {
            workingConfig.voiceType = VoiceType.SYSTEM_TTS
            updateCardSelection(VoiceType.SYSTEM_TTS)
            updateActiveChannelDisplay()
        }

        // Sliders
        val speedProgress = ((workingConfig.speed - 0.5f) / 1.5f * 100).toInt().coerceIn(0, 100)
        binding.seekSpeed.progress = speedProgress
        binding.tvSpeedLabel.text = "${String.format("%.1f", workingConfig.speed)}x"

        binding.seekSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = 0.5f + (progress / 100f) * 1.5f
                workingConfig.speed = speed
                binding.tvSpeedLabel.text = "${String.format("%.1f", speed)}x"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val pitchProgress = (workingConfig.pitchOffsetHz + 50).coerceIn(0, 100)
        binding.seekPitch.progress = pitchProgress
        binding.tvPitchLabel.text = "${workingConfig.pitchOffsetHz} Hz"

        binding.seekPitch.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val pitch = progress - 50
                workingConfig.pitchOffsetHz = pitch
                binding.tvPitchLabel.text = "$pitch Hz"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.btnCopyFishUrl.setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("fish_audio_url", "https://fish.audio")
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "已复制 Fish Audio 官网链接 (https://fish.audio)，请在浏览器中打开免费申请 API Key", Toast.LENGTH_LONG).show()
        }

        binding.btnPreview.setOnClickListener {
            workingConfig.customApiUrl = binding.etCustomApi.text?.toString()?.trim() ?: ""
            workingConfig.fishAudioApiKey = binding.etFishApiKey.text?.toString()?.trim() ?: ""
            workingConfig.fishModelId = binding.etFishModelId.text?.toString()?.trim() ?: ""
            workingConfig.fishCustomEndpoint = binding.etFishEndpoint.text?.toString()?.trim() ?: ""
            previewVoice(workingConfig)
        }

        binding.btnConfirm.setOnClickListener {
            workingConfig.customApiUrl = binding.etCustomApi.text?.toString()?.trim() ?: ""
            workingConfig.fishAudioApiKey = binding.etFishApiKey.text?.toString()?.trim() ?: ""
            workingConfig.fishModelId = binding.etFishModelId.text?.toString()?.trim() ?: ""
            workingConfig.fishCustomEndpoint = binding.etFishEndpoint.text?.toString()?.trim() ?: ""
            onConfigApplied(workingConfig)
            dismiss()
        }
    }

    private fun startDownloadModel(type: VoiceType) {
        val isNailong = type == VoiceType.NAILONG
        val downloadBtn = if (isNailong) binding.btnDownloadNailong else binding.btnDownloadDagou
        val progressLayout = if (isNailong) binding.layoutNailongProgress else binding.layoutDagouProgress
        val progressBar = if (isNailong) binding.progressNailong else binding.progressDagou
        val progressText = if (isNailong) binding.tvNailongProgress else binding.tvDagouProgress

        downloadBtn.isEnabled = false
        progressLayout.visibility = View.VISIBLE
        progressBar.progress = 0
        progressText.text = "准备连接下载..."

        scope.launch {
            val result = ttsManager.sherpaEngine.modelManager.downloadModel(type) { percent ->
                scope.launch(Dispatchers.Main) {
                    progressBar.progress = percent
                    progressText.text = if (percent >= 96) "正在解压部署模型..." else "正在下载模型 $percent%"
                }
            }

            if (result.isSuccess) {
                Toast.makeText(context, "🎉 端侧轻量离线语音包安装就绪！随时免流量朗读", Toast.LENGTH_LONG).show()
                checkOfflineModels()
            } else {
                val err = result.exceptionOrNull()?.message ?: "网络超时"
                Toast.makeText(context, "下载模型失败: $err", Toast.LENGTH_LONG).show()
                downloadBtn.isEnabled = true
                progressLayout.visibility = View.GONE
            }
        }
    }

    private fun updateCardSelection(type: VoiceType) {
        val amberColor = 0xFFF59E0B.toInt()
        val bronzeColor = 0xFF854D0E.toInt()
        val defaultBorder = 0xFFE5E7EB.toInt()

        // Card 1: 奶龙
        val isNailong = type == VoiceType.NAILONG
        binding.cardNailong.strokeColor = if (isNailong) amberColor else defaultBorder
        binding.cardNailong.strokeWidth = if (isNailong) 4 else 2
        binding.rbNailong.isChecked = isNailong

        // Card 2: 大狗
        val isDagou = type == VoiceType.DAGOU
        binding.cardDagou.strokeColor = if (isDagou) bronzeColor else defaultBorder
        binding.cardDagou.strokeWidth = if (isDagou) 4 else 2
        binding.rbDagou.isChecked = isDagou

        // Card 3: Custom
        val isCustom = type == VoiceType.CUSTOM_API
        binding.cardCustom.strokeColor = if (isCustom) 0xFF111827.toInt() else defaultBorder
        binding.cardCustom.strokeWidth = if (isCustom) 4 else 2
        binding.layoutCustomApi.visibility = if (isCustom) View.VISIBLE else View.GONE

        // Card 4: System
        val isSystem = type == VoiceType.SYSTEM_TTS
        binding.cardSystem.strokeColor = if (isSystem) 0xFF111827.toInt() else defaultBorder
        binding.cardSystem.strokeWidth = if (isSystem) 4 else 2
    }

    private fun previewVoice(config: VoiceConfig) {
        binding.btnPreview.isEnabled = false
        binding.btnPreview.text = "正在试听音色..."

        // Case 1: 奶龙原声
        if (config.voiceType == VoiceType.NAILONG) {
            binding.waveNailong.isPlaying = true
            if (config.fishAudioApiKey.isNotBlank()) {
                val previewText = "我是奶龙，今天我们一起来读一本好看的小说吧！"
                scope.launch {
                    val dummySentence = Sentence(0, 0, previewText, 0, previewText.length)
                    val file = withContext(Dispatchers.IO) {
                        ttsManager.getAudioFile(dummySentence, config)
                    }
                    resetPreviewBtn()
                    if (file != null && file.exists()) {
                        playFilePreview(file) { binding.waveNailong.isPlaying = false }
                    } else {
                        val errMsg = com.vibecoding.aireading.tts.FishAudioTtsEngine.lastError ?: "合成失败，请检查网络或 API Key"
                        Toast.makeText(context, "试听失败: $errMsg", Toast.LENGTH_LONG).show()
                        playRawPreview(R.raw.preview_nailong) {
                            binding.waveNailong.isPlaying = false
                        }
                    }
                }
            } else if (ttsManager.sherpaEngine.modelManager.isModelReady(VoiceType.NAILONG)) {
                val previewText = "我是奶龙，端侧离线语音包合成就绪，随时随地开启朗读！"
                scope.launch {
                    val dummySentence = Sentence(0, 0, previewText, 0, previewText.length)
                    val file = withContext(Dispatchers.IO) {
                        ttsManager.getAudioFile(dummySentence, config)
                    }
                    resetPreviewBtn()
                    if (file != null && file.exists()) {
                        playFilePreview(file) { binding.waveNailong.isPlaying = false }
                    } else {
                        playRawPreview(R.raw.preview_nailong) {
                            binding.waveNailong.isPlaying = false
                        }
                    }
                }
            } else {
                playRawPreview(R.raw.preview_nailong) {
                    binding.waveNailong.isPlaying = false
                    resetPreviewBtn()
                }
            }
            return
        }

        // Case 2: 大狗叫原声
        if (config.voiceType == VoiceType.DAGOU) {
            binding.waveDagou.isPlaying = true
            if (config.fishAudioApiKey.isNotBlank()) {
                val previewText = "大狗叫，汪汪汪，今天的小说太精彩了！"
                scope.launch {
                    val dummySentence = Sentence(0, 0, previewText, 0, previewText.length)
                    val file = withContext(Dispatchers.IO) {
                        ttsManager.getAudioFile(dummySentence, config)
                    }
                    resetPreviewBtn()
                    if (file != null && file.exists()) {
                        playFilePreview(file) { binding.waveDagou.isPlaying = false }
                    } else {
                        val errMsg = com.vibecoding.aireading.tts.FishAudioTtsEngine.lastError ?: "合成失败，请检查网络或 API Key"
                        Toast.makeText(context, "试听失败: $errMsg", Toast.LENGTH_LONG).show()
                        playRawPreview(R.raw.preview_dagou) {
                            binding.waveDagou.isPlaying = false
                        }
                    }
                }
            } else if (ttsManager.sherpaEngine.modelManager.isModelReady(VoiceType.DAGOU)) {
                val previewText = "大狗叫，汪汪汪，端侧离线语音包合成就绪！"
                scope.launch {
                    val dummySentence = Sentence(0, 0, previewText, 0, previewText.length)
                    val file = withContext(Dispatchers.IO) {
                        ttsManager.getAudioFile(dummySentence, config)
                    }
                    resetPreviewBtn()
                    if (file != null && file.exists()) {
                        playFilePreview(file) { binding.waveDagou.isPlaying = false }
                    } else {
                        playRawPreview(R.raw.preview_dagou) {
                            binding.waveDagou.isPlaying = false
                        }
                    }
                }
            } else {
                playRawPreview(R.raw.preview_dagou) {
                    binding.waveDagou.isPlaying = false
                    resetPreviewBtn()
                }
            }
            return
        }

        // Case 3: 系统原生 TTS 或 自定义 API
        val previewText = when (config.voiceType) {
            VoiceType.CUSTOM_API -> "你好，这是自定义语音模型的测试声音。"
            VoiceType.SYSTEM_TTS -> "正在试听系统原生离线语音朗读，稳定可靠随时畅听。"
            else -> "正在试听当前朗读声音。"
        }

        scope.launch {
            val dummySentence = Sentence(0, 0, previewText, 0, previewText.length)
            val file = withContext(Dispatchers.IO) {
                ttsManager.getAudioFile(dummySentence, config)
            }
            resetPreviewBtn()

            if (file != null && file.exists()) {
                playFilePreview(file) {}
            } else {
                Toast.makeText(context, "试听合成失败，请检查网络或系统TTS设置", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun playFilePreview(file: java.io.File, onComplete: () -> Unit) {
        try {
            previewPlayer?.release()
            previewPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { onComplete() }
                setOnErrorListener { _, _, _ ->
                    onComplete()
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onComplete()
        }
    }

    private fun playRawPreview(resId: Int, onComplete: () -> Unit) {
        try {
            previewPlayer?.release()
            previewPlayer = MediaPlayer.create(context, resId).apply {
                setOnCompletionListener {
                    onComplete()
                }
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onComplete()
            Toast.makeText(context, "播放试听音频失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resetPreviewBtn() {
        binding.btnPreview.isEnabled = true
        binding.btnPreview.text = "🎧 试听当前音色"
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        previewPlayer?.release()
        previewPlayer = null
        ttsManager.shutdown()
        scope.cancel()
    }
}
