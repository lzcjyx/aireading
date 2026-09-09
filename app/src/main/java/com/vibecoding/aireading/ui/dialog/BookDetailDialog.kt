package com.vibecoding.aireading.ui.dialog

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.vibecoding.aireading.data.BookRepository
import com.vibecoding.aireading.databinding.DialogBookDetailBinding
import com.vibecoding.aireading.model.Book
import com.vibecoding.aireading.model.Chapter
import com.vibecoding.aireading.model.SearchResult
import com.vibecoding.aireading.source.BookSourceRepository
import com.vibecoding.aireading.source.NovelSearchEngine
import com.vibecoding.aireading.ui.ReadActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class BookDetailDialog(
    context: Context,
    private val searchResult: SearchResult,
    private val onBookAdded: ((Book) -> Unit)? = null
) : BottomSheetDialog(context) {

    private lateinit var binding: DialogBookDetailBinding
    private val repository = BookRepository(context)
    private val bookSourceRepo = BookSourceRepository(context)
    private val searchEngine = NovelSearchEngine()
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var loadedChapters: List<Chapter> = emptyList()
    private var isLoading = true
    private var readNowRequested = false
    private var addToShelfRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogBookDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()
        loadChapterList()
    }

    private fun initViews() {
        binding.tvBookTitle.text = searchResult.title
        binding.tvAuthor.text = "作者: ${searchResult.author}"
        binding.tvSourceName.text = "书源: ${searchResult.sourceName}"
        binding.tvCoverChar.text = searchResult.title.take(1)
        binding.tvIntro.text = if (searchResult.intro.isNotBlank()) searchResult.intro else "暂无小说简介"
        binding.tvChapterCount.text = "正在快速解析目录与章节..."

        binding.btnAddToShelf.setOnClickListener {
            if (loadedChapters.isNotEmpty()) {
                val book = saveToShelf()
                Toast.makeText(context, "已将《${book.title}》放入书架", Toast.LENGTH_SHORT).show()
                onBookAdded?.invoke(book)
                dismiss()
            } else if (isLoading) {
                addToShelfRequested = true
                binding.btnAddToShelf.text = "正在入架..."
                Toast.makeText(context, "正在同步目录，即将自动放入书架...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "未能从该书源解析出章节，建议更换书源", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnReadNow.setOnClickListener {
            if (loadedChapters.isNotEmpty()) {
                val book = createTempBookForReading()
                dismiss()
                val intent = Intent(context, ReadActivity::class.java).apply {
                    putExtra("EXTRA_BOOK", book)
                }
                context.startActivity(intent)
            } else if (isLoading) {
                readNowRequested = true
                binding.btnReadNow.text = "正在进入..."
                Toast.makeText(context, "正在连接书源，马上开启阅读...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "未能从该书源解析出章节，建议更换书源", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadChapterList() {
        isLoading = true
        scope.launch {
            val source = bookSourceRepo.getAllSources().find { it.bookSourceUrl == searchResult.sourceUrl }
                ?: bookSourceRepo.getEnabledSources().firstOrNull()

            if (source != null) {
                val chapters = withContext(Dispatchers.IO) {
                    searchEngine.fetchChapterList(source, searchResult.bookUrl)
                }
                loadedChapters = chapters
                isLoading = false
                binding.tvChapterCount.text = if (chapters.isNotEmpty()) "共 ${chapters.size} 章" else "目录解析失败，建议更换其他书源"

                if (chapters.isNotEmpty()) {
                    if (readNowRequested) {
                        val book = createTempBookForReading()
                        dismiss()
                        val intent = Intent(context, ReadActivity::class.java).apply {
                            putExtra("EXTRA_BOOK", book)
                        }
                        context.startActivity(intent)
                    } else if (addToShelfRequested) {
                        val book = saveToShelf()
                        Toast.makeText(context, "已将《${book.title}》放入书架", Toast.LENGTH_SHORT).show()
                        onBookAdded?.invoke(book)
                        dismiss()
                    }
                } else if (readNowRequested || addToShelfRequested) {
                    Toast.makeText(context, "该书源目录解析异常，请尝试更换其他书源", Toast.LENGTH_SHORT).show()
                    binding.btnReadNow.text = "立即阅读"
                    binding.btnAddToShelf.text = "加入书架"
                }
            } else {
                isLoading = false
                binding.tvChapterCount.text = "书源不可用"
            }
        }
    }

    private fun createTempBookForReading(): Book {
        val bookId = UUID.randomUUID().toString()
        val totalChaps = loadedChapters.size.coerceAtLeast(1)
        val book = Book(
            id = bookId,
            title = searchResult.title,
            author = searchResult.author,
            sourceUrl = searchResult.sourceUrl,
            bookUrl = searchResult.bookUrl,
            intro = searchResult.intro,
            coverUrl = searchResult.coverUrl,
            totalChapters = totalChaps
        )
        // Cache TOC chapters on disk for reading, but DO NOT save to bookshelf!
        repository.saveChaptersToc(bookId, loadedChapters)
        return book
    }

    private fun saveToShelf(): Book {
        val bookId = UUID.randomUUID().toString()
        val totalChaps = loadedChapters.size.coerceAtLeast(1)
        val book = Book(
            id = bookId,
            title = searchResult.title,
            author = searchResult.author,
            sourceUrl = searchResult.sourceUrl,
            bookUrl = searchResult.bookUrl,
            intro = searchResult.intro,
            coverUrl = searchResult.coverUrl,
            totalChapters = totalChaps
        )
        repository.saveBook(book)
        repository.saveChaptersToc(bookId, loadedChapters)
        return book
    }
}
