package com.vibecoding.aireading

import com.vibecoding.aireading.source.BookSourceParser
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookSourceTest {

    @Test
    fun testParseLegadoJsonArray() {
        val sampleJson = """
            [
              {
                "bookSourceName": "测试笔趣阁",
                "bookSourceUrl": "https://www.testbqg.com",
                "searchUrl": "https://www.testbqg.com/search?q={{key}}",
                "ruleSearch": {
                  "bookList": ".book-item",
                  "name": "a.title@text",
                  "author": ".author@text",
                  "bookUrl": "a.title@href"
                },
                "ruleToc": {
                  "chapterList": "#list dd a",
                  "chapterName": "text",
                  "chapterUrl": "href"
                },
                "ruleContent": {
                  "content": "#content",
                  "replaceRegex": "##请收藏.*"
                }
              }
            ]
        """.trimIndent()

        val sources = BookSourceParser.parseJson(sampleJson)
        assertEquals(1, sources.size)
        val source = sources[0]
        assertEquals("测试笔趣阁", source.bookSourceName)
        assertEquals("https://www.testbqg.com", source.bookSourceUrl)
        assertEquals(".book-item", source.ruleSearch.bookList)
        assertEquals("a.title@text", source.ruleSearch.name)
        assertEquals("#content", source.ruleContent.content)
    }

    @Test
    fun testExtractRule() {
        val html = """
            <div class="book-item">
                <a class="title" href="/book/1001.html">修仙奇谭</a>
                <span class="author">梦入神机</span>
                <p id="content">清晨，阳光明媚。请收藏本站以便下次阅读。</p>
            </div>
        """.trimIndent()

        val doc = Jsoup.parse(html, "https://www.testbqg.com")
        val item = doc.selectFirst(".book-item")
        assertNotNull(item)

        val title = BookSourceParser.extract(item!!, "a.title@text")
        assertEquals("修仙奇谭", title)

        val author = BookSourceParser.extract(item, ".author@text")
        assertEquals("梦入神机", author)

        val link = BookSourceParser.extract(item, "a.title@href", "https://www.testbqg.com")
        assertEquals("https://www.testbqg.com/book/1001.html", link)

        val content = BookSourceParser.extract(item, "#content##请收藏.*", "https://www.testbqg.com")
        assertEquals("清晨，阳光明媚。", content)
    }

    @Test
    fun testLegadoChainedSelectors() {
        val html = """
            <div class="bookbox">
              <div class="bookname">
                <h3>
                  <a href="/other">广告推荐</a>
                  <a href="/book/doupo.html">斗破苍穹</a>
                </h3>
              </div>
              <div class="author">作者：天蚕土豆</div>
              <div class="intro">这里是斗破苍穹小说简介...</div>
            </div>
        """.trimIndent()

        val doc = Jsoup.parse(html, "https://www.testbqg.com")
        val items = BookSourceParser.extractList(doc, "class.bookbox")
        assertEquals(1, items.size)

        val name = BookSourceParser.extract(items[0], "tag.h3.0@tag.a.1@text")
        assertEquals("斗破苍穹", name)

        val author = BookSourceParser.extract(items[0], "class.author@text##作者：")
        assertEquals("天蚕土豆", author)

        val link = BookSourceParser.extract(items[0], "class.bookname@tag.a.1@href", "https://www.testbqg.com")
        assertEquals("https://www.testbqg.com/book/doupo.html", link)
    }

    @Test
    fun testResolveRelativeUrls() {
        assertEquals("https://example.com/a/b", BookSourceParser.resolveUrl("https://example.com", "/a/b"))
        assertEquals("https://example.com/test/a/b", BookSourceParser.resolveUrl("https://example.com/test/", "a/b"))
        assertEquals("http://external.com/path", BookSourceParser.resolveUrl("https://example.com", "http://external.com/path"))
    }
}
