package com.vibecoding.aireading.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.vibecoding.aireading.R
import com.vibecoding.aireading.data.BookRepository
import com.vibecoding.aireading.databinding.ActivityMainBinding
import com.vibecoding.aireading.model.Book
import com.vibecoding.aireading.parser.TxtBookParser
import com.vibecoding.aireading.ui.adapter.BookAdapter
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: BookRepository
    private lateinit var bookAdapter: BookAdapter

    private var isGridView = false
    private var sortMode = 0 // 0: 最近阅读, 1: 书名, 2: 添加顺序

    private val openDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            importTxtFile(uri)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, "通知权限未开启，熄屏时将无法在通知栏展示朗读控制", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = BookRepository(this)
        val uiPrefs = getSharedPreferences("bookshelf_ui", Context.MODE_PRIVATE)
        isGridView = uiPrefs.getBoolean("is_grid", false)
        sortMode = uiPrefs.getInt("sort_mode", 0)

        initViews()
        checkNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        refreshBookshelf()
    }

    private fun initViews() {
        bookAdapter = BookAdapter(
            books = emptyList(),
            isGridView = isGridView,
            onBookClicked = { book -> openBook(book) },
            onBookLongClicked = { book -> showBookOptionsDialog(book) }
        )

        updateLayoutManager()
        binding.rvBooks.adapter = bookAdapter

        // Action Pills
        binding.btnSearch.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }

        binding.btnSourceManage.setOnClickListener {
            startActivity(Intent(this, SourceManageActivity::class.java))
        }

        binding.btnImport.setOnClickListener {
            openDocumentLauncher.launch(arrayOf("text/plain"))
        }

        binding.btnEmptySearch.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }

        // Layout Toggle
        binding.btnToggleLayout.setOnClickListener {
            isGridView = !isGridView
            getSharedPreferences("bookshelf_ui", Context.MODE_PRIVATE)
                .edit().putBoolean("is_grid", isGridView).apply()
            bookAdapter.isGridView = isGridView
            updateLayoutManager()
            refreshBookshelf()
            Toast.makeText(this, if (isGridView) "已切换为网格书架视图" else "已切换为详细列表视图", Toast.LENGTH_SHORT).show()
        }

        // Sort Options
        binding.btnSortBooks.setOnClickListener {
            val options = arrayOf("按最近阅读排序", "按书籍名称排序", "按加入时间排序")
            AlertDialog.Builder(this)
                .setTitle("书架排序方式")
                .setSingleChoiceItems(options, sortMode) { dialog, which ->
                    sortMode = which
                    getSharedPreferences("bookshelf_ui", Context.MODE_PRIVATE)
                        .edit().putInt("sort_mode", sortMode).apply()
                    refreshBookshelf()
                    dialog.dismiss()
                }
                .setNegativeButton("取消", null)
                .show()
        }

        refreshBookshelf()
    }

    private fun updateLayoutManager() {
        binding.rvBooks.layoutManager = if (isGridView) {
            GridLayoutManager(this, 3)
        } else {
            LinearLayoutManager(this)
        }
    }

    private fun refreshBookshelf() {
        var books = repository.getAllBooks()
        books = when (sortMode) {
            1 -> books.sortedBy { it.title }
            2 -> books.reversed()
            else -> books.sortedByDescending { it.lastReadTime }
        }

        bookAdapter.updateBooks(books)
        binding.tvBookCount.text = "共 ${books.size} 本"

        if (books.isEmpty()) {
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.rvBooks.visibility = View.GONE
        } else {
            binding.layoutEmpty.visibility = View.GONE
            binding.rvBooks.visibility = View.VISIBLE
        }
    }

    private fun showBookOptionsDialog(book: Book) {
        val options = arrayOf("📖 开启阅读 / 听书", "🧹 清理本地正文缓存", "🗑️ 从书架彻底移除")
        AlertDialog.Builder(this)
            .setTitle("《${book.title}》")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openBook(book)
                    1 -> {
                        repository.clearBookCache(book.id)
                        Toast.makeText(this, "已清除《${book.title}》的本地章节缓存", Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        AlertDialog.Builder(this)
                            .setTitle("确认移除")
                            .setMessage("确定要从书架删除《${book.title}》吗？缓存和进度将被清除。")
                            .setPositiveButton("删除") { _, _ ->
                                repository.deleteBook(book.id)
                                refreshBookshelf()
                                Toast.makeText(this, "已从书架移除《${book.title}》", Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun openBook(book: Book) {
        val intent = Intent(this, ReadActivity::class.java).apply {
            putExtra("EXTRA_BOOK", book)
        }
        startActivity(intent)
    }

    private fun importTxtFile(uri: Uri) {
        try {
            val fileName = getFileName(uri) ?: "导入小说_${System.currentTimeMillis()}.txt"
            val destDir = File(filesDir, "books").apply { if (!exists()) mkdirs() }
            val destFile = File(destDir, fileName)

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            val (book, _) = TxtBookParser.parse(destFile)
            repository.saveBook(book)
            refreshBookshelf()
            Toast.makeText(this, "成功导入《${book.title}》", Toast.LENGTH_SHORT).show()
            openBook(book)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "导入文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = cursor.getString(idx)
            }
        }
        return name
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
