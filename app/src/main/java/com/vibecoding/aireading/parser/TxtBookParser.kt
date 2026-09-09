package com.vibecoding.aireading.parser

import com.vibecoding.aireading.model.Book
import com.vibecoding.aireading.model.Chapter
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.UUID
import java.util.regex.Pattern

object TxtBookParser {

    private val CHAPTER_TITLE_REGEX = Pattern.compile("^[ \\t]*(第[0-9一二三四五六七八九十百千万]+[章回节卷集部篇].*)$")

    fun parse(file: File): Pair<Book, List<Chapter>> {
        val charset = detectCharset(file)
        return parse(FileInputStream(file), file.nameWithoutExtension, file.absolutePath, charset)
    }

    fun parse(
        inputStream: InputStream,
        title: String,
        filePath: String? = null,
        charset: Charset = Charsets.UTF_8
    ): Pair<Book, List<Chapter>> {
        val reader = BufferedReader(InputStreamReader(inputStream, charset))
        val rawChapters = ArrayList<Pair<String, StringBuilder>>()

        var currentTitle = "前言"
        var currentBuilder = StringBuilder()

        reader.forEachLine { line ->
            val trimmed = line.trim()
            val matcher = CHAPTER_TITLE_REGEX.matcher(trimmed)
            if (matcher.find()) {
                // If previous chapter had content, save it
                if (currentBuilder.isNotBlank()) {
                    rawChapters.add(Pair(currentTitle, currentBuilder))
                    currentBuilder = StringBuilder()
                }
                currentTitle = matcher.group(1)?.trim() ?: "章节"
            } else {
                currentBuilder.append(line).append("\n")
            }
        }
        if (currentBuilder.isNotBlank()) {
            rawChapters.add(Pair(currentTitle, currentBuilder))
        }

        // Fallback: If no chapter headers matched, split into logical chunks of ~2500 characters
        if (rawChapters.isEmpty()) {
            val allText = currentBuilder.toString()
            val chunkSize = 2500
            var chunkIndex = 1
            for (i in 0 until allText.length step chunkSize) {
                val end = minOf(i + chunkSize, allText.length)
                rawChapters.add(Pair("第${chunkIndex}部分", StringBuilder(allText.substring(i, end))))
                chunkIndex++
            }
        }

        val bookId = UUID.randomUUID().toString()
        val chapters = rawChapters.mapIndexed { index, (chapTitle, chapContent) ->
            val contentStr = chapContent.toString()
            Chapter(
                id = "${bookId}_chap_$index",
                index = index,
                title = chapTitle,
                content = contentStr,
                sentences = SentenceSplitter.split(index, contentStr)
            )
        }

        val book = Book(
            id = bookId,
            title = title,
            author = "精选小说",
            filePath = filePath,
            totalChapters = chapters.size
        )

        return Pair(book, chapters)
    }

    private fun detectCharset(file: File): Charset {
        val bytes = ByteArray(4096)
        val read = FileInputStream(file).use { it.read(bytes) }
        if (read <= 0) return Charsets.UTF_8

        // Check UTF-8 BOM
        if (read >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return Charsets.UTF_8
        }

        // Basic UTF-8 validity probe
        var isUtf8 = true
        var i = 0
        while (i < read) {
            val b = bytes[i].toInt() and 0xFF
            if (b <= 0x7F) {
                i++
            } else if ((b in 0xC2..0xDF) && i + 1 < read) {
                if ((bytes[i + 1].toInt() and 0xC0) != 0x80) { isUtf8 = false; break }
                i += 2
            } else if ((b in 0xE0..0xEF) && i + 2 < read) {
                if ((bytes[i + 1].toInt() and 0xC0) != 0x80 || (bytes[i + 2].toInt() and 0xC0) != 0x80) {
                    isUtf8 = false; break
                }
                i += 3
            } else {
                isUtf8 = false
                break
            }
        }

        return if (isUtf8) Charsets.UTF_8 else Charset.forName("GBK")
    }
}
