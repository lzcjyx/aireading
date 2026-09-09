package com.vibecoding.aireading.ui.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.vibecoding.aireading.databinding.ItemBookBinding
import com.vibecoding.aireading.databinding.ItemBookGridBinding
import com.vibecoding.aireading.model.Book

class BookAdapter(
    private var books: List<Book>,
    var isGridView: Boolean = false,
    private val onBookClicked: (Book) -> Unit,
    private val onBookLongClicked: (Book) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_LIST = 0
        private const val TYPE_GRID = 1
    }

    fun updateBooks(newBooks: List<Book>) {
        books = newBooks
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (isGridView) TYPE_GRID else TYPE_LIST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_GRID) {
            val binding = ItemBookGridBinding.inflate(inflater, parent, false)
            GridViewHolder(binding)
        } else {
            val binding = ItemBookBinding.inflate(inflater, parent, false)
            ListViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val book = books[position]
        if (holder is ListViewHolder) {
            holder.bind(book)
        } else if (holder is GridViewHolder) {
            holder.bind(book)
        }
    }

    override fun getItemCount(): Int = books.size

    inner class ListViewHolder(private val binding: ItemBookBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(book: Book) {
            binding.tvBookTitle.text = book.title
            binding.tvBookAuthor.text = "作者: ${book.author}"
            binding.tvCoverChar.text = book.title.take(1)
            binding.coverLayout.backgroundTintList = ColorStateList.valueOf(book.coverColor)

            val isLocal = !book.filePath.isNullOrBlank()
            binding.tvCoverBadge.text = if (isLocal) "本地TXT" else "网络源"

            val pct = if (book.totalChapters > 0) {
                ((book.currentChapterIndex + 1) * 100 / book.totalChapters).coerceIn(0, 100)
            } else {
                0
            }

            binding.progressRead.progress = pct

            val progressText = if (book.totalChapters > 0) {
                "已读至 第${book.currentChapterIndex + 1}章 / 共${book.totalChapters}章 ($pct%)"
            } else {
                "尚未开始阅读"
            }
            binding.tvBookProgress.text = progressText

            binding.root.setOnClickListener { onBookClicked(book) }
            binding.btnRead.setOnClickListener { onBookClicked(book) }
            binding.root.setOnLongClickListener {
                onBookLongClicked(book)
                true
            }
        }
    }

    inner class GridViewHolder(private val binding: ItemBookGridBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(book: Book) {
            binding.tvBookTitle.text = book.title
            binding.tvCoverChar.text = book.title.take(1)
            binding.coverLayout.backgroundTintList = ColorStateList.valueOf(book.coverColor)

            val isLocal = !book.filePath.isNullOrBlank()
            binding.tvCoverBadge.text = if (isLocal) "本地TXT" else "网络源"

            val pct = if (book.totalChapters > 0) {
                ((book.currentChapterIndex + 1) * 100 / book.totalChapters).coerceIn(0, 100)
            } else {
                0
            }

            val progressText = if (book.totalChapters > 0) {
                "第${book.currentChapterIndex + 1}章 · $pct%"
            } else {
                "未读"
            }
            binding.tvBookProgress.text = progressText

            binding.root.setOnClickListener { onBookClicked(book) }
            binding.root.setOnLongClickListener {
                onBookLongClicked(book)
                true
            }
        }
    }
}
