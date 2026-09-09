package com.vibecoding.aireading.data

import android.content.Context
import android.content.SharedPreferences
import com.vibecoding.aireading.model.Book
import com.vibecoding.aireading.model.Chapter
import com.vibecoding.aireading.parser.SentenceSplitter
import com.vibecoding.aireading.parser.TxtBookParser
import com.vibecoding.aireading.source.BookSourceRepository
import com.vibecoding.aireading.source.NovelSearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class BookRepository(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("bookshelf_prefs", Context.MODE_PRIVATE)
    private val bookSourceRepo = BookSourceRepository(context)
    private val searchEngine = NovelSearchEngine()
    private val chaptersCacheDir = File(context.cacheDir, "chapter_cache").apply { if (!exists()) mkdirs() }

    companion object {
        const val SAMPLE_BOOK_ID = "sample_dragon_dog_story"
    }

    fun getAllBooks(): List<Book> {
        val books = ArrayList<Book>()
        books.add(createSampleBook())

        val jsonStr = prefs.getString("user_books", null)
        if (!jsonStr.isNullOrEmpty()) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    books.add(
                        Book(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            author = obj.optString("author", "未知"),
                            filePath = obj.optString("filePath"),
                            sourceUrl = obj.optString("sourceUrl"),
                            bookUrl = obj.optString("bookUrl"),
                            intro = obj.optString("intro", ""),
                            coverUrl = obj.optString("coverUrl"),
                            coverColor = obj.optInt("coverColor", 0xFFE65100.toInt()),
                            currentChapterIndex = obj.optInt("currentChapterIndex", 0),
                            currentSentenceIndex = obj.optInt("currentSentenceIndex", 0),
                            totalChapters = obj.optInt("totalChapters", 1)
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return books
    }

    fun saveBook(book: Book) {
        val all = getAllBooks().filter { it.id != SAMPLE_BOOK_ID }.toMutableList()
        val existingIndex = all.indexOfFirst { it.id == book.id }
        if (existingIndex >= 0) {
            all[existingIndex] = book
        } else {
            all.add(0, book)
        }

        val array = JSONArray()
        for (b in all) {
            val obj = JSONObject().apply {
                put("id", b.id)
                put("title", b.title)
                put("author", b.author)
                put("filePath", b.filePath)
                put("sourceUrl", b.sourceUrl)
                put("bookUrl", b.bookUrl)
                put("intro", b.intro)
                put("coverUrl", b.coverUrl)
                put("coverColor", b.coverColor)
                put("currentChapterIndex", b.currentChapterIndex)
                put("currentSentenceIndex", b.currentSentenceIndex)
                put("totalChapters", b.totalChapters)
            }
            array.put(obj)
        }
        prefs.edit().putString("user_books", array.toString()).apply()
    }

    fun deleteBook(bookId: String): Boolean {
        if (bookId == SAMPLE_BOOK_ID) return false
        val all = getAllBooks().filter { it.id != SAMPLE_BOOK_ID && it.id != bookId }
        val array = JSONArray()
        for (b in all) {
            val obj = JSONObject().apply {
                put("id", b.id)
                put("title", b.title)
                put("author", b.author)
                put("filePath", b.filePath)
                put("sourceUrl", b.sourceUrl)
                put("bookUrl", b.bookUrl)
                put("intro", b.intro)
                put("coverUrl", b.coverUrl)
                put("coverColor", b.coverColor)
                put("currentChapterIndex", b.currentChapterIndex)
                put("currentSentenceIndex", b.currentSentenceIndex)
                put("totalChapters", b.totalChapters)
            }
            array.put(obj)
        }
        prefs.edit().putString("user_books", array.toString()).apply()

        clearBookCache(bookId)
        return true
    }

    fun isBookInShelf(bookId: String): Boolean {
        return getAllBooks().any { it.id == bookId }
    }

    fun clearBookCache(bookId: String) {
        val tocFile = File(chaptersCacheDir, "${bookId}_toc.json")
        if (tocFile.exists()) tocFile.delete()
        chaptersCacheDir.listFiles { _, name -> name.startsWith("${bookId}_content_") }?.forEach {
            it.delete()
        }
    }

    fun saveChaptersToc(bookId: String, chapters: List<Chapter>) {
        val file = File(chaptersCacheDir, "${bookId}_toc.json")
        val array = JSONArray()
        for (c in chapters) {
            val obj = JSONObject().apply {
                put("id", c.id)
                put("index", c.index)
                put("title", c.title)
                put("url", c.url)
            }
            array.put(obj)
        }
        file.writeText(array.toString(), Charsets.UTF_8)
    }

    fun loadChapters(book: Book): List<Chapter> {
        if (book.id == SAMPLE_BOOK_ID) {
            return createSampleChapters()
        }

        // Check TOC cache file for network novel
        val tocFile = File(chaptersCacheDir, "${book.id}_toc.json")
        if (tocFile.exists()) {
            try {
                val array = JSONArray(tocFile.readText(Charsets.UTF_8))
                val list = ArrayList<Chapter>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val index = obj.getInt("index")
                    val chapter = Chapter(
                        id = obj.getString("id"),
                        index = index,
                        title = obj.getString("title"),
                        url = obj.optString("url")
                    )
                    // Check if content already cached on disk
                    val contentFile = File(chaptersCacheDir, "${book.id}_content_$index.txt")
                    if (contentFile.exists()) {
                        val cachedText = contentFile.readText(Charsets.UTF_8)
                        val chineseScore = NovelSearchEngine.scoreChineseText(cachedText)
                        if (cachedText.length > 40 && chineseScore == 0) {
                            contentFile.delete()
                        } else {
                            chapter.content = cachedText
                            chapter.isLoaded = true
                            chapter.sentences = SentenceSplitter.split(index, cachedText)
                        }
                    }
                    list.add(chapter)
                }
                if (list.isNotEmpty()) return list
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Local TXT novel
        if (book.filePath != null) {
            val file = File(book.filePath)
            if (file.exists()) {
                val (_, chapters) = TxtBookParser.parse(file)
                return chapters
            }
        }

        return emptyList()
    }

    suspend fun ensureChapterContent(book: Book, chapter: Chapter): Chapter = withContext(Dispatchers.IO) {
        if (chapter.isLoaded && chapter.content.isNotBlank()) {
            val score = NovelSearchEngine.scoreChineseText(chapter.content)
            if (chapter.content.length <= 40 || score > 0) {
                return@withContext chapter
            }
        }

        val contentFile = File(chaptersCacheDir, "${book.id}_content_${chapter.index}.txt")
        if (contentFile.exists() && contentFile.length() > 0) {
            val text = contentFile.readText(Charsets.UTF_8)
            val score = NovelSearchEngine.scoreChineseText(text)
            if (text.length > 40 && score == 0) {
                contentFile.delete()
            } else {
                chapter.content = text
                chapter.isLoaded = true
                chapter.sentences = SentenceSplitter.split(chapter.index, text)
                return@withContext chapter
            }
        }

        // Fetch from network source
        if (book.sourceUrl != null && chapter.url != null) {
            val source = bookSourceRepo.getAllSources().find { it.bookSourceUrl == book.sourceUrl }
                ?: bookSourceRepo.getEnabledSources().firstOrNull()
            if (source != null) {
                val content = searchEngine.fetchChapterContent(source, chapter.url)
                if (content.isNotBlank()) {
                    contentFile.writeText(content, Charsets.UTF_8)
                    chapter.content = content
                    chapter.isLoaded = true
                    chapter.sentences = SentenceSplitter.split(chapter.index, content)
                }
            }
        }
        chapter
    }

    fun saveProgress(bookId: String, chapterIdx: Int, sentenceIdx: Int) {
        if (bookId == SAMPLE_BOOK_ID) {
            prefs.edit()
                .putInt("${SAMPLE_BOOK_ID}_chap", chapterIdx)
                .putInt("${SAMPLE_BOOK_ID}_sent", sentenceIdx)
                .apply()
        } else {
            val books = getAllBooks()
            books.find { it.id == bookId }?.let {
                it.currentChapterIndex = chapterIdx
                it.currentSentenceIndex = sentenceIdx
                saveBook(it)
            }
        }
    }

    private fun createSampleBook(): Book {
        val chap = prefs.getInt("${SAMPLE_BOOK_ID}_chap", 0)
        val sent = prefs.getInt("${SAMPLE_BOOK_ID}_sent", 0)
        return Book(
            id = SAMPLE_BOOK_ID,
            title = "灵龙犬侠传（内置试听小说）",
            author = "奶龙与大狗冒险记",
            coverColor = 0xFFFF9800.toInt(),
            currentChapterIndex = chap,
            currentSentenceIndex = sent,
            totalChapters = 3
        )
    }

    private fun createSampleChapters(): List<Chapter> {
        val c1 = """
            清晨的阳光洒在灵兽山谷的青石板路上，空气中弥漫着青草与野花的清香。
            一只浑身金黄、圆滚滚的可爱小龙迈着轻快的步伐，在山林间蹦蹦跳跳。
            “Duang duang！今天天气真好，我是世界上最无敌的奶龙！”
            奶龙挺着圆滚滚的大肚子，手里抓着一只刚摘下的野灵果，一边嚼一边满足地眯起了眼睛。
            就在这时，山谷后方传来了一阵沉稳而有力的脚步声。
            落叶沙沙作响，一位身披玄铁皮甲、眼神坚毅深邃的犬族游侠从密林中缓缓走了出来。
            正是闻名大江南北的豪杰——大狗。
            大狗停下脚步，双手抱胸，低沉粗犷的嗓音在山谷间回荡：“奶龙小兄弟，你这贪吃的毛病什么时候能改改？前方的玄冰迷宫可不是闹着玩的。”
            奶龙听到声音，转过头挥舞着胖乎乎的小手喊道：“大狗老铁！你别小看我，我的肚皮可是能反弹一切攻击的！”
            大狗嘴角微微上扬，露出一丝耐人寻味的豪爽笑意。
        """.trimIndent()

        val c2 = """
            两人穿过青翠的幽谷，眼前的气温骤然降低，两侧的岩壁上结满了幽蓝色的寒霜。
            “哇，好冷呀！大狗，你的毛皮大衣能不能借我挡挡风？”奶龙缩了缩脖子，紧紧靠在大狗身边。
            大狗从腰间解下一件厚实的黑熊皮斗篷，披在奶龙肉乎乎的身上，沉声道：“跟在我身后，不要踩碎脚下的符文冰晶。”
            突然，迷宫深处传来一声尖锐的嘶鸣！
            三只浑身散发着寒气的冰晶霜狼从冰窟中纵身跃出，泛着寒光的利爪直逼二人面门。
            “看招！Duang——！”
            奶龙急中生智，猛地吸进一大口气，整个身体如同气球般膨胀起来，狠狠向前方弹跳撞去！
            头领霜狼被结结实实地撞在肚皮上，瞬间被一股反震之力弹飞出去，重重砸在石壁上。
            大狗眼中闪过赞许之色，手中玄铁阔剑凌空一震：“好小子，有你的！剩下的交给我！”
            剑光如雷霆霹雳，带着霸道无匹的风声横扫全场，顷刻间将残存的霜狼逼退。
        """.trimIndent()

        val c3 = """
            穿过冰晶迷宫后，眼前豁然开朗，一片灿烂的金色云海在悬崖下翻涌。
            在云海中央的古老神台上，悬浮着传说中的《太古天书》。
            “我们终于到了！”奶龙欢呼雀跃，在石阶上连翻了两个跟头。
            大狗站在悬崖边缘，迎着呼啸的长风，长舒了一口气：“修仙之路漫漫，今日一试，方知天地之广阔。”
            天书缓缓展开，金色的符文如流光溢彩般照亮了整座山巅。
            奶龙拍着小手咯咯笑道：“大狗，等我们参透了天书，一定要去东海吃最好吃的大鱼！”
            大狗朗声大笑，粗犷的笑声震散了天边的流云：“好！到时候咱们痛痛快快吃个够！”
            夕阳西下，一龙一犬的身影被金色的晚霞拉得很长很长，新的传奇故事才刚刚开始。
        """.trimIndent()

        return listOf(
            Chapter("sample_1", 0, "第一章：山谷初遇", c1, null, true, SentenceSplitter.split(0, c1)),
            Chapter("sample_2", 1, "第二章：玄冰迷宫", c2, null, true, SentenceSplitter.split(1, c2)),
            Chapter("sample_3", 2, "第三章：云海天书", c3, null, true, SentenceSplitter.split(2, c3))
        )
    }
}
