package com.vibecoding.aireading.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.vibecoding.aireading.databinding.ItemSearchResultBinding
import com.vibecoding.aireading.model.SearchResult

class SearchResultAdapter(
    private var results: List<SearchResult>,
    private val onItemClicked: (SearchResult) -> Unit,
    private val onAddClicked: (SearchResult) -> Unit
) : RecyclerView.Adapter<SearchResultAdapter.SearchResultViewHolder>() {

    fun updateResults(newResults: List<SearchResult>) {
        results = newResults
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultViewHolder {
        val binding = ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SearchResultViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SearchResultViewHolder, position: Int) {
        holder.bind(results[position])
    }

    override fun getItemCount(): Int = results.size

    inner class SearchResultViewHolder(private val binding: ItemSearchResultBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SearchResult) {
            binding.tvBookTitle.text = item.title
            binding.tvAuthor.text = "作者: ${item.author}"
            binding.tvSourceTag.text = item.sourceName
            binding.tvIntro.text = if (item.intro.isNotBlank()) item.intro else "暂无简介"
            binding.tvLatestChapter.text = if (item.latestChapter.isNotBlank()) "最新: ${item.latestChapter}" else ""

            binding.root.setOnClickListener { onItemClicked(item) }
            binding.btnAddBookshelf.setOnClickListener { onAddClicked(item) }
        }
    }
}
