package com.vibecoding.aireading.parser

import com.vibecoding.aireading.model.Sentence
import java.util.regex.Pattern

object SentenceSplitter {
    // Regex for punctuation that terminates a sentence, including closing quotes
    private val SENTENCE_PATTERN = Pattern.compile("([^。！？!?…\n]+[。！？!?…\n]*[”’\"']?)")

    fun split(chapterIndex: Int, content: String): List<Sentence> {
        val sentences = ArrayList<Sentence>()
        if (content.isBlank()) return sentences

        val matcher = SENTENCE_PATTERN.matcher(content)
        var index = 0
        while (matcher.find()) {
            val text = matcher.group().trim()
            if (text.isNotEmpty()) {
                sentences.add(
                    Sentence(
                        index = index++,
                        chapterIndex = chapterIndex,
                        text = text,
                        startChar = matcher.start(),
                        endChar = matcher.end()
                    )
                )
            }
        }

        // If no punctuation was found at all, treat non-empty content as one sentence
        if (sentences.isEmpty() && content.isNotBlank()) {
            sentences.add(
                Sentence(
                    index = 0,
                    chapterIndex = chapterIndex,
                    text = content.trim(),
                    startChar = 0,
                    endChar = content.length
                )
            )
        }

        return sentences
    }
}
