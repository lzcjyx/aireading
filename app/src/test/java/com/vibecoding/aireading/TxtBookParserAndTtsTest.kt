package com.vibecoding.aireading

import com.vibecoding.aireading.parser.TxtBookParser
import com.vibecoding.aireading.tts.CloudAiTtsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class TxtBookParserAndTtsTest {

    @Test
    fun testTxtBookParserMultiChapter() {
        val sampleText = """
            第一章 龙腾初现
            奶龙在草原上快活地奔跑。
            第二章 大狗相逢
            大狗在松林下静静打坐。
            第三章 携手天涯
            两人一同踏上了未知的旅程。
        """.trimIndent()

        val (book, chapters) = TxtBookParser.parse(
            ByteArrayInputStream(sampleText.toByteArray(Charsets.UTF_8)),
            "测试小说"
        )

        assertEquals("测试小说", book.title)
        assertEquals(3, chapters.size)
        assertEquals("第一章 龙腾初现", chapters[0].title)
        assertEquals("第二章 大狗相逢", chapters[1].title)
        assertEquals("第三章 携手天涯", chapters[2].title)
        assertTrue(chapters[0].sentences.isNotEmpty())
    }

    @Test
    fun testSecMsGecCalculation() {
        val token = CloudAiTtsEngine.generateSecMsGec()
        assertNotNull(token)
        assertEquals(64, token.length) // SHA256 hex string is 64 characters
        assertTrue(token.all { it.isDigit() || it in 'A'..'F' })
    }
}
