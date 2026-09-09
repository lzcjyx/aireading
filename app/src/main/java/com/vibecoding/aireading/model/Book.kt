package com.vibecoding.aireading.model

import java.io.Serializable

data class Book(
    val id: String,
    val title: String,
    val author: String = "未知",
    val filePath: String? = null,
    val sourceUrl: String? = null,
    val bookUrl: String? = null,
    val intro: String = "",
    val coverUrl: String? = null,
    val coverColor: Int = 0xFFFF8F00.toInt(),
    var currentChapterIndex: Int = 0,
    var currentSentenceIndex: Int = 0,
    var totalChapters: Int = 0,
    var lastReadTime: Long = System.currentTimeMillis()
) : Serializable
