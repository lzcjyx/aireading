package com.vibecoding.aireading.ui.dialog

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.vibecoding.aireading.databinding.DialogImportUrlBinding

class ImportUrlDialog(
    context: Context,
    private val onConfirm: (String) -> Unit
) : BottomSheetDialog(context) {

    private lateinit var binding: DialogImportUrlBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogImportUrlBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true

        binding.btnCancel.setOnClickListener { dismiss() }

        binding.btnClearInput.setOnClickListener {
            binding.etSourceInput.setText("")
        }

        binding.btnPasteClipboard.setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipData = clipboard?.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val pasted = clipData.getItemAt(0).coerceToText(context).toString().trim()
                if (pasted.isNotEmpty()) {
                    binding.etSourceInput.setText(pasted)
                    Toast.makeText(context, "已粘贴剪贴板内容", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "剪贴板为空", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "剪贴板为空", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnConfirmImport.setOnClickListener {
            var text = binding.etSourceInput.text?.toString()?.trim() ?: ""
            // Remove common wrapping characters e.g. "url" or 'url'
            text = text.removeSurrounding("\"").removeSurrounding("'").trim()
            if (text.isEmpty()) {
                Toast.makeText(context, "请输入书源链接或粘贴规则 JSON", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            onConfirm(text)
            dismiss()
        }
    }
}
