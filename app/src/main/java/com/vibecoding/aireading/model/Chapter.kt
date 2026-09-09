package com.vibecoding.aireading.model

import java.io.Serializable

data class Chapter(
    val id: String,
    val index: Int,
    val title: String,
    var content: String = "",
    val url: String? = null,
    var isLoaded: Boolean = false,
    var sentences: List<Sentence> = emptyList()
) : Serializable

data class Sentence(
    val index: Int,
    val chapterIndex: Int,
    val text: String,
    val startChar: Int,
    val endChar: Int
) : Serializable
