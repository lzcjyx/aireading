package com.vibecoding.aireading.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.vibecoding.aireading.databinding.ActivitySourceManageBinding
import com.vibecoding.aireading.source.BookSourceRepository
import com.vibecoding.aireading.ui.adapter.SourceAdapter
import com.vibecoding.aireading.ui.dialog.ImportUrlDialog
import kotlinx.coroutines.launch

class SourceManageActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySourceManageBinding
    private lateinit var repository: BookSourceRepository
    private lateinit var adapter: SourceAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySourceManageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = BookSourceRepository(this)
        initViews()
    }

    private fun initViews() {
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = SourceAdapter(
            sources = repository.getAllSources(),
            onToggle = { source, enabled ->
                repository.toggleSource(source.bookSourceUrl, enabled)
                updateCount()
            },
            onDelete = { source ->
                repository.deleteSource(source.bookSourceUrl)
                adapter.updateSources(repository.getAllSources())
                updateCount()
                Toast.makeText(this, "已删除书源: ${source.bookSourceName}", Toast.LENGTH_SHORT).show()
            }
        )

        binding.rvSources.layoutManager = LinearLayoutManager(this)
        binding.rvSources.adapter = adapter
        updateCount()

        binding.btnImportUrl.setOnClickListener {
            ImportUrlDialog(this) { input ->
                doImport(input)
            }.show()
        }

        binding.btnResetDefaults.setOnClickListener {
            val defaults = repository.resetToDefaults()
            adapter.updateSources(defaults)
            updateCount()
            Toast.makeText(this, "已加载默认优质网络书源", Toast.LENGTH_SHORT).show()
        }
    }

    private fun doImport(input: String) {
        val cleanInput = input.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        val loadingDialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("正在导入书源")
            .setMessage("正在网络请求并解析书源规则，请稍候...")
            .setCancelable(false)
            .create()

        loadingDialog.show()

        lifecycleScope.launch {
            if (cleanInput.startsWith("http://") || cleanInput.startsWith("https://")) {
                val result = repository.importFromUrl(cleanInput)
                loadingDialog.dismiss()
                result.onSuccess { count ->
                    val all = repository.getAllSources()
                    adapter.updateSources(all)
                    updateCount()
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(this@SourceManageActivity)
                        .setTitle("✅ 书源导入成功")
                        .setMessage("成功解析并新增/更新了 $count 个网络书源！\n当前书源总数：${all.size} 个。")
                        .setPositiveButton("我知道了", null)
                        .show()
                }.onFailure { err ->
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(this@SourceManageActivity)
                        .setTitle("❌ 书源导入失败")
                        .setMessage("未能成功导入书源：\n${err.message ?: "网络连接超时或 JSON 格式无效"}\n\n请检查链接是否为可直接访问的 Legado/JSON 格式书源。")
                        .setPositiveButton("确定", null)
                        .show()
                }
            } else {
                loadingDialog.dismiss()
                val count = repository.importFromJson(cleanInput)
                if (count > 0) {
                    val all = repository.getAllSources()
                    adapter.updateSources(all)
                    updateCount()
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(this@SourceManageActivity)
                        .setTitle("✅ 书源解析成功")
                        .setMessage("成功从粘贴的文本中导入了 $count 个书源！\n当前书源总数：${all.size} 个。")
                        .setPositiveButton("完成", null)
                        .show()
                } else {
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(this@SourceManageActivity)
                        .setTitle("❌ 未识别到有效书源")
                        .setMessage("粘贴的文本未能解析出符合规范的书源 JSON 数据。\n请确保格式符合开源阅读 (Legado) 的书源标准。")
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
        }
    }

    private fun updateCount() {
        val total = repository.getAllSources().size
        val enabled = repository.getEnabledSources().size
        binding.tvSourceCount.text = "共 $total 个书源 (已启用 $enabled 个)"
    }
}
