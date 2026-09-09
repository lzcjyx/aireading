package com.vibecoding.aireading.source

import android.content.Context
import android.content.SharedPreferences
import com.vibecoding.aireading.model.BookSource
import com.vibecoding.aireading.model.RuleContent
import com.vibecoding.aireading.model.RuleSearch
import com.vibecoding.aireading.model.RuleToc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class BookSourceRepository(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("book_sources_prefs", Context.MODE_PRIVATE)
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun importFromUrl(url: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful || response.body == null) {
                return@withContext Result.failure(Exception("HTTP 请求失败: ${response.code}"))
            }

            val json = response.body!!.string()
            val sources = BookSourceParser.parseJson(json)
            if (sources.isEmpty()) {
                return@withContext Result.failure(Exception("未在链接中解析到有效的书源规则"))
            }

            val count = saveSources(sources)
            Result.success(count)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    fun importFromJson(jsonString: String): Int {
        val sources = BookSourceParser.parseJson(jsonString)
        return saveSources(sources)
    }

    private fun loadRawSources(): List<BookSource> {
        val savedJson = prefs.getString("custom_sources", null)
        if (savedJson.isNullOrEmpty()) return emptyList()
        return try {
            BookSourceParser.parseJson(savedJson)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun getAllSources(): List<BookSource> {
        val raw = loadRawSources()
        if (raw.isEmpty()) {
            val defaults = createDefaultSources()
            persistSources(defaults)
            return defaults
        }
        return raw
    }

    fun getEnabledSources(): List<BookSource> {
        return getAllSources().filter { it.enabled }
    }

    fun toggleSource(sourceUrl: String, enabled: Boolean) {
        val all = getAllSources().toMutableList()
        val index = all.indexOfFirst { it.bookSourceUrl == sourceUrl }
        if (index >= 0) {
            all[index].enabled = enabled
            persistSources(all)
        }
    }

    fun deleteSource(sourceUrl: String) {
        val all = getAllSources().filter { it.bookSourceUrl != sourceUrl }
        persistSources(all)
    }

    fun resetToDefaults(): List<BookSource> {
        val defaults = createDefaultSources()
        persistSources(defaults)
        return defaults
    }

    fun saveSources(newSources: List<BookSource>, overwrite: Boolean = false): Int {
        val current = if (overwrite) ArrayList() else loadRawSources().toMutableList()
        var addedCount = 0

        for (ns in newSources) {
            val existingIdx = current.indexOfFirst { it.bookSourceUrl == ns.bookSourceUrl }
            if (existingIdx >= 0) {
                current[existingIdx] = ns
            } else {
                current.add(ns)
                addedCount++
            }
        }

        persistSources(current)
        return if (overwrite) current.size else addedCount
    }

    private fun persistSources(sources: List<BookSource>) {
        val array = JSONArray()
        for (s in sources) {
            val obj = JSONObject().apply {
                put("bookSourceName", s.bookSourceName)
                put("bookSourceUrl", s.bookSourceUrl)
                put("bookSourceGroup", s.bookSourceGroup)
                put("bookSourceType", s.bookSourceType)
                put("enabled", s.enabled)
                put("searchUrl", s.searchUrl)
                put("searchEncoding", s.searchEncoding)
                put("searchMethod", s.searchMethod)

                put("ruleSearch", JSONObject().apply {
                    put("bookList", s.ruleSearch.bookList)
                    put("name", s.ruleSearch.name)
                    put("author", s.ruleSearch.author)
                    put("bookUrl", s.ruleSearch.bookUrl)
                    put("coverUrl", s.ruleSearch.coverUrl)
                    put("intro", s.ruleSearch.intro)
                    put("lastChapter", s.ruleSearch.lastChapter)
                })

                put("ruleToc", JSONObject().apply {
                    put("chapterList", s.ruleToc.chapterList)
                    put("chapterName", s.ruleToc.chapterName)
                    put("chapterUrl", s.ruleToc.chapterUrl)
                })

                put("ruleContent", JSONObject().apply {
                    put("content", s.ruleContent.content)
                    put("replaceRegex", s.ruleContent.replaceRegex)
                })
            }
            array.put(obj)
        }

        prefs.edit().putString("custom_sources", array.toString()).apply()
    }

    private fun createDefaultSources(): List<BookSource> {
        return listOf(
            BookSource(
                bookSourceName = "斗破苍穹原著专线",
                bookSourceUrl = "https://www.doupcq.cc",
                bookSourceGroup = "精选",
                enabled = true,
                searchUrl = "https://www.doupcq.cc/s?q={{key}}",
                ruleSearch = RuleSearch(
                    bookList = ".bookbox, .search-list li, tr:has(a)",
                    name = "h3 a@text || a.title@text || a@text",
                    author = ".author@text || span:contains(作)@text",
                    bookUrl = "h3 a@href || a.title@href || a@href",
                    coverUrl = "img@src",
                    intro = ".intro@text || p@text",
                    lastChapter = ".latest a@text || .cat a@text"
                ),
                ruleToc = RuleToc(
                    chapterList = "#list dd a, .chapters a, a[href*='html']",
                    chapterName = "text",
                    chapterUrl = "href"
                ),
                ruleContent = RuleContent(
                    content = "#content, .content",
                    replaceRegex = "##请收藏本站.*|本章未完.*"
                )
            ),
            BookSource(
                bookSourceName = "笔趣阁精品",
                bookSourceUrl = "https://www.bqgka.com",
                bookSourceGroup = "精选",
                enabled = true,
                searchUrl = "https://www.bqgka.com/s?q={{key}}",
                ruleSearch = RuleSearch(
                    bookList = ".bookbox",
                    name = ".bookname a@text",
                    author = ".author@text",
                    bookUrl = ".bookname a@href",
                    coverUrl = ".bookimg img@src",
                    intro = ".intro@text",
                    lastChapter = ".cat a@text"
                ),
                ruleToc = RuleToc(
                    chapterList = "#list dd a",
                    chapterName = "text",
                    chapterUrl = "href"
                ),
                ruleContent = RuleContent(
                    content = "#content",
                    replaceRegex = "##请收藏本站.*|本章未完.*"
                )
            ),
            BookSource(
                bookSourceName = "顶点中文网",
                bookSourceUrl = "https://www.dingdian.org",
                bookSourceGroup = "全网",
                enabled = true,
                searchUrl = "https://www.dingdian.org/search?keyword={{key}}",
                ruleSearch = RuleSearch(
                    bookList = ".so_list .list_item, .book-item, tr:has(td.odd)",
                    name = "a.name@text, .title a@text, td.odd a@text",
                    author = ".author@text, td:nth-child(3)@text",
                    bookUrl = "a.name@href, .title a@href, td.odd a@href",
                    coverUrl = "img@src",
                    intro = ".intro@text",
                    lastChapter = ".latest a@text, td:nth-child(2) a@text"
                ),
                ruleToc = RuleToc(
                    chapterList = "#list dd a, .chapters a, .chapter-list a",
                    chapterName = "text",
                    chapterUrl = "href"
                ),
                ruleContent = RuleContent(
                    content = "#content, #chaptercontent",
                    replaceRegex = "##顶点小说.*|请记住本书首发域名.*"
                )
            ),
            BookSource(
                bookSourceName = "69书吧轻小说",
                bookSourceUrl = "https://www.69shuba.cx",
                bookSourceGroup = "轻小说",
                enabled = true,
                searchUrl = "https://www.69shuba.cx/modules/article/search.php?searchkey={{key}}",
                searchEncoding = "GBK",
                ruleSearch = RuleSearch(
                    bookList = ".newbox ul li",
                    name = "h3 a@text",
                    author = ".labelbox span:nth-child(1)@text",
                    bookUrl = "h3 a@href",
                    coverUrl = "img@src",
                    intro = "ol@text",
                    lastChapter = ""
                ),
                ruleToc = RuleToc(
                    chapterList = "#catalog ul li a",
                    chapterName = "text",
                    chapterUrl = "href"
                ),
                ruleContent = RuleContent(
                    content = ".txtnav",
                    replaceRegex = "##69书吧.*|请退出转码页面.*"
                )
            )
        )
    }
}
