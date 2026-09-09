package com.vibecoding.aireading.ui.adapter

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.vibecoding.aireading.databinding.ItemChapterBinding
import com.vibecoding.aireading.model.Chapter

class ChapterAdapter(
    private val chapters: List<Chapter>,
    private var selectedIndex: Int,
    private val onChapterClicked: (Int) -> Unit
) : RecyclerView.Adapter<ChapterAdapter.ChapterViewHolder>() {

    fun setSelected(index: Int) {
        val old = selectedIndex
        selectedIndex = index
        notifyItemChanged(old)
        notifyItemChanged(selectedIndex)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChapterViewHolder {
        val binding = ItemChapterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChapterViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChapterViewHolder, position: Int) {
        holder.bind(chapters[position], position == selectedIndex)
    }

    override fun getItemCount(): Int = chapters.size

    inner class ChapterViewHolder(private val binding: ItemChapterBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(chapter: Chapter, isSelected: Boolean) {
            binding.tvChapterTitle.text = chapter.title
            if (isSelected) {
                binding.tvChapterTitle.setTextColor(0xFFFF6F00.toInt())
                binding.tvChapterTitle.setTypeface(null, Typeface.BOLD)
            } else {
                binding.tvChapterTitle.setTextColor(0xFF333333.toInt())
                binding.tvChapterTitle.setTypeface(null, Typeface.NORMAL)
            }
            binding.root.setOnClickListener { onChapterClicked(adapterPosition) }
        }
    }
}
