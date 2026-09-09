package com.vibecoding.aireading.model

import java.io.Serializable

data class BookSource(
    val bookSourceUrl: String,
    val bookSourceName: String,
    val bookSourceGroup: String = "",
    val bookSourceType: Int = 0,
    var enabled: Boolean = true,
    val searchUrl: String = "",
    val searchEncoding: String = "UTF-8",
    val searchMethod: String = "GET",
    val ruleSearch: RuleSearch = RuleSearch(),
    val ruleToc: RuleToc = RuleToc(),
    val ruleContent: RuleContent = RuleContent()
) : Serializable

data class RuleSearch(
    val bookList: String = "",
    val name: String = "",
    val author: String = "",
    val bookUrl: String = "",
    val coverUrl: String = "",
    val intro: String = "",
    val lastChapter: String = ""
) : Serializable

data class RuleToc(
    val chapterList: String = "",
    val chapterName: String = "",
    val chapterUrl: String = ""
) : Serializable

data class RuleContent(
    val content: String = "",
    val replaceRegex: String = ""
) : Serializable

data class SearchResult(
    val title: String,
    val author: String,
    val bookUrl: String,
    val coverUrl: String = "",
    val intro: String = "",
    val latestChapter: String = "",
    val sourceName: String,
    val sourceUrl: String
) : Serializable
