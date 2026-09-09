package com.vibecoding.aireading.ui

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.vibecoding.aireading.databinding.ActivitySearchBinding
import com.vibecoding.aireading.model.SearchResult
import com.vibecoding.aireading.source.BookSourceRepository
import com.vibecoding.aireading.source.NovelSearchEngine
import com.vibecoding.aireading.ui.adapter.SearchResultAdapter
import com.vibecoding.aireading.ui.dialog.BookDetailDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private lateinit var bookSourceRepo: BookSourceRepository
    private val searchEngine = NovelSearchEngine()
    private lateinit var adapter: SearchResultAdapter
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bookSourceRepo = BookSourceRepository(this)
        initViews()
    }

    private fun initViews() {
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = SearchResultAdapter(
            results = emptyList(),
            onItemClicked = { item -> showBookDetail(item) },
            onAddClicked = { item -> showBookDetail(item) }
        )

        binding.rvResults.layoutManager = LinearLayoutManager(this)
        binding.rvResults.adapter = adapter

        binding.btnDoSearch.setOnClickListener {
            performSearch(binding.etSearchQuery.text?.toString() ?: "")
        }

        binding.etSearchQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch(binding.etSearchQuery.text?.toString() ?: "")
                true
            } else {
                false
            }
        }

        // Quick tags
        for (i in 0 until binding.chipGroupHot.childCount) {
            val chip = binding.chipGroupHot.getChildAt(i) as? Chip
            chip?.setOnClickListener {
                val kw = chip.text.toString()
                binding.etSearchQuery.setText(kw)
                performSearch(kw)
            }
        }
    }

    private fun performSearch(keyword: String) {
        val cleanKey = keyword.trim()
        if (cleanKey.isEmpty()) {
            Toast.makeText(this, "请输入搜索关键词", Toast.LENGTH_SHORT).show()
            return
        }

        val enabledSources = bookSourceRepo.getEnabledSources()
        if (enabledSources.isEmpty()) {
            Toast.makeText(this, "当前无启用的书源，请在书源管理中添加或启用", Toast.LENGTH_LONG).show()
            return
        }

        searchJob?.cancel()
        binding.progressBar.visibility = View.VISIBLE
        binding.tvSearchStatus.text = "正在检索 ${enabledSources.size} 个网络书源..."
        adapter.updateResults(emptyList())

        searchJob = lifecycleScope.launch {
            try {
                searchEngine.searchNovels(cleanKey, enabledSources).collect { results ->
                    binding.progressBar.visibility = View.GONE
                    adapter.updateResults(results)
                    binding.tvSearchStatus.text = "找到 ${results.size} 条关于“$cleanKey”的搜索结果"
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) return@launch
                binding.progressBar.visibility = View.GONE
                binding.tvSearchStatus.text = "检索已结束"
            }
        }
    }

    private fun showBookDetail(item: SearchResult) {
        BookDetailDialog(this, item) {
            // Book added
        }.show()
    }
}
