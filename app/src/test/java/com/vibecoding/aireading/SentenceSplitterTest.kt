package com.vibecoding.aireading

import com.vibecoding.aireading.parser.SentenceSplitter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceSplitterTest {

    @Test
    fun testDialogueWithQuotes() {
        val text = "“Duang duang！今天天气真好，我是奶龙！”奶龙蹦蹦跳跳地说道。大狗点点头说：“走吧。”"
        val sentences = SentenceSplitter.split(0, text)

        assertTrue(sentences.isNotEmpty())
        assertEquals(4, sentences.size)
        assertEquals("“Duang duang！", sentences[0].text)
        assertEquals("今天天气真好，我是奶龙！”", sentences[1].text)
        assertEquals("奶龙蹦蹦跳跳地说道。", sentences[2].text)
        assertEquals("大狗点点头说：“走吧。”", sentences[3].text)
    }


    @Test
    fun testPunctuationVarieties() {
        val text = "真的吗？太好了！你确定……好啊。"
        val sentences = SentenceSplitter.split(0, text)

        assertEquals(4, sentences.size)
        assertEquals("真的吗？", sentences[0].text)
        assertEquals("太好了！", sentences[1].text)
        assertEquals("你确定……", sentences[2].text)
        assertEquals("好啊。", sentences[3].text)
    }

    @Test
    fun testEmptyAndBlank() {
        val sentences = SentenceSplitter.split(0, "   \n\n   ")
        assertTrue(sentences.isEmpty())
    }
}
