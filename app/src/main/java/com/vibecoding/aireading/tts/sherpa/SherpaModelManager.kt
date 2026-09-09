package com.vibecoding.aireading.tts.sherpa

import android.content.Context
import com.vibecoding.aireading.model.VoiceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class SherpaModelPaths(
    val model: String,
    val tokens: String,
    val lexicon: String,
    val ruleFsts: String
)

class SherpaModelManager(private val context: Context) {

    val modelsDir = File(context.filesDir, "sherpa_models").apply { if (!exists()) mkdirs() }
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun isModelReady(voiceType: VoiceType): Boolean {
        val paths = getModelPaths(voiceType) ?: return false
        val modelFile = File(paths.model)
        val tokensFile = File(paths.tokens)
        val lexiconFile = File(paths.lexicon)
        return modelFile.exists() && modelFile.length() > 5_000_000 &&
               tokensFile.exists() && tokensFile.length() > 100 &&
               lexiconFile.exists() && lexiconFile.length() > 100_000
    }

    fun getModelPaths(voiceType: VoiceType): SherpaModelPaths? {
        val folderName = when (voiceType) {
            VoiceType.NAILONG -> "vits-piper-zh_CN-xiao_ya-medium-int8"
            VoiceType.DAGOU -> "vits-piper-zh_CN-chaowen-medium-int8"
            else -> return null
        }
        val prefix = when (voiceType) {
            VoiceType.NAILONG -> "zh_CN-xiao_ya-medium"
            VoiceType.DAGOU -> "zh_CN-chaowen-medium"
            else -> return null
        }

        val folder = File(modelsDir, folderName)
        val modelFile = File(folder, "$prefix.onnx")
        val tokensFile = File(folder, "tokens.txt")
        val lexiconFile = File(folder, "lexicon.txt")

        val phoneFst = File(folder, "phone.fst").absolutePath
        val numberFst = File(folder, "number.fst").absolutePath
        val dateFst = File(folder, "date.fst").absolutePath
        val ruleFsts = listOf(phoneFst, numberFst, dateFst).filter { File(it).exists() }.joinToString(",")

        return SherpaModelPaths(
            model = modelFile.absolutePath,
            tokens = tokensFile.absolutePath,
            lexicon = lexiconFile.absolutePath,
            ruleFsts = ruleFsts
        )
    }

    suspend fun downloadModel(voiceType: VoiceType, onProgress: (Int) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        val (folderName, directUrl, mirrorUrl) = when (voiceType) {
            VoiceType.NAILONG -> Triple(
                "vits-piper-zh_CN-xiao_ya-medium-int8",
                "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-zh_CN-xiao_ya-medium-int8.tar.bz2",
                "https://ghproxy.net/https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-zh_CN-xiao_ya-medium-int8.tar.bz2"
            )
            VoiceType.DAGOU -> Triple(
                "vits-piper-zh_CN-chaowen-medium-int8",
                "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-zh_CN-chaowen-medium-int8.tar.bz2",
                "https://ghproxy.net/https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-zh_CN-chaowen-medium-int8.tar.bz2"
            )
            else -> return@withContext Result.failure(IllegalArgumentException("不支持的离线模型类型"))
        }

        val tempArchive = File(modelsDir, "$folderName.tar.bz2")
        val urlsToTry = listOf(mirrorUrl, directUrl)
        var downloadSuccess = false
        var lastException: Exception? = null

        for (url in urlsToTry) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Mozilla/5.0")
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful || response.body == null) {
                    continue
                }

                val body = response.body!!
                val totalLength = body.contentLength()
                var downloaded = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tempArchive).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read = input.read(buffer)
                        while (read != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (totalLength > 0) {
                                val percent = ((downloaded * 100) / totalLength).toInt().coerceIn(0, 95)
                                onProgress(percent)
                            }
                            read = input.read(buffer)
                        }
                    }
                }
                downloadSuccess = true
                break
            } catch (e: Exception) {
                lastException = e
            }
        }

        if (!downloadSuccess) {
            tempArchive.delete()
            return@withContext Result.failure(lastException ?: Exception("下载离线模型失败，请检查网络"))
        }

        try {
            onProgress(96)
            extractTarBz2(tempArchive, modelsDir)
            tempArchive.delete()
            onProgress(100)
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            tempArchive.delete()
            Result.failure(e)
        }
    }

    private fun extractTarBz2(archiveFile: File, outputDir: File) {
        FileInputStream(archiveFile).use { fis ->
            BZip2CompressorInputStream(fis).use { bzIn ->
                TarArchiveInputStream(bzIn).use { tarIn ->
                    var entry = tarIn.nextTarEntry
                    while (entry != null) {
                        val destPath = File(outputDir, entry.name)
                        if (entry.isDirectory) {
                            destPath.mkdirs()
                        } else {
                            destPath.parentFile?.mkdirs()
                            FileOutputStream(destPath).use { fos ->
                                tarIn.copyTo(fos)
                            }
                        }
                        entry = tarIn.nextTarEntry
                    }
                }
            }
        }
    }
}
