package com.vibecoding.aireading.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.vibecoding.aireading.databinding.ItemSourceBinding
import com.vibecoding.aireading.model.BookSource

class SourceAdapter(
    private var sources: List<BookSource>,
    private val onToggle: (BookSource, Boolean) -> Unit,
    private val onDelete: (BookSource) -> Unit
) : RecyclerView.Adapter<SourceAdapter.SourceViewHolder>() {

    fun updateSources(newSources: List<BookSource>) {
        sources = newSources
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SourceViewHolder {
        val binding = ItemSourceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SourceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SourceViewHolder, position: Int) {
        holder.bind(sources[position])
    }

    override fun getItemCount(): Int = sources.size

    inner class SourceViewHolder(private val binding: ItemSourceBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(source: BookSource) {
            binding.tvSourceName.text = source.bookSourceName
            binding.tvSourceUrl.text = source.bookSourceUrl
            binding.tvSourceGroup.text = if (source.bookSourceGroup.isNotBlank()) source.bookSourceGroup else "通用"

            binding.switchEnable.setOnCheckedChangeListener(null)
            binding.switchEnable.isChecked = source.enabled

            binding.switchEnable.setOnCheckedChangeListener { _, isChecked ->
                onToggle(source, isChecked)
            }

            binding.btnDelete.setOnClickListener {
                onDelete(source)
            }
        }
    }
}
