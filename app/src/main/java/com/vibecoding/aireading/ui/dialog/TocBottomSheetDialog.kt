package com.vibecoding.aireading.ui.dialog

import android.content.Context
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.vibecoding.aireading.databinding.DialogTocBinding
import com.vibecoding.aireading.model.Chapter
import com.vibecoding.aireading.ui.adapter.ChapterAdapter

class TocBottomSheetDialog(
    context: Context,
    private val chapters: List<Chapter>,
    private val currentChapterIndex: Int,
    private val onChapterSelected: (Int) -> Unit
) : BottomSheetDialog(context) {

    private lateinit var binding: DialogTocBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogTocBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvTocTitle.text = "章节目录 (共${chapters.size}章)"
        val adapter = ChapterAdapter(chapters, currentChapterIndex) { index ->
            onChapterSelected(index)
            dismiss()
        }

        binding.rvChapters.layoutManager = LinearLayoutManager(context)
        binding.rvChapters.adapter = adapter
        binding.rvChapters.scrollToPosition(currentChapterIndex)
    }
}
