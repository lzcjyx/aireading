package com.vibecoding.aireading.source

import com.vibecoding.aireading.model.BookSource
import com.vibecoding.aireading.model.RuleContent
import com.vibecoding.aireading.model.RuleSearch
import com.vibecoding.aireading.model.RuleToc
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Element
import java.net.URI

object BookSourceParser {

    fun parseJson(jsonString: String): List<BookSource> {
        val list = ArrayList<BookSource>()
        val trimmed = jsonString.trim()
        if (trimmed.isEmpty()) return list

        try {
            if (trimmed.startsWith("[")) {
                val array = JSONArray(trimmed)
                for (i in 0 until array.length()) {
                    parseSingleSource(array.getJSONObject(i))?.let { list.add(it) }
                }
            } else if (trimmed.startsWith("{")) {
                val obj = JSONObject(trimmed)
                parseSingleSource(obj)?.let { list.add(it) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun parseSingleSource(obj: JSONObject): BookSource? {
        val name = obj.optString("bookSourceName", "").trim()
        val url = obj.optString("bookSourceUrl", "").trim()
        if (name.isEmpty() || url.isEmpty()) return null

        val searchUrl = obj.optString("searchUrl", "")
        val searchEncoding = obj.optString("searchEncoding", "UTF-8")
        val searchMethod = if (searchUrl.contains("@post", ignoreCase = true) || obj.optString("searchMethod", "").equals("POST", ignoreCase = true)) "POST" else "GET"

        val ruleSearch = parseRuleSearch(obj.opt("ruleSearch"))
        val ruleToc = parseRuleToc(obj.opt("ruleToc"))
        val ruleContent = parseRuleContent(obj.opt("ruleContent"))

        return BookSource(
            bookSourceUrl = url,
            bookSourceName = name,
            bookSourceGroup = obj.optString("bookSourceGroup", ""),
            enabled = obj.optBoolean("enabled", true),
            searchUrl = searchUrl,
            searchEncoding = searchEncoding,
            searchMethod = searchMethod,
            ruleSearch = ruleSearch,
            ruleToc = ruleToc,
            ruleContent = ruleContent
        )
    }

    private fun parseRuleSearch(raw: Any?): RuleSearch {
        if (raw is JSONObject) {
            return RuleSearch(
                bookList = raw.optString("bookList", ""),
                name = raw.optString("name", ""),
                author = raw.optString("author", ""),
                bookUrl = raw.optString("bookUrl", ""),
                coverUrl = raw.optString("coverUrl", ""),
                intro = raw.optString("intro", ""),
                lastChapter = raw.optString("lastChapter", "")
            )
        }
        return RuleSearch()
    }

    private fun parseRuleToc(raw: Any?): RuleToc {
        if (raw is JSONObject) {
            return RuleToc(
                chapterList = raw.optString("chapterList", ""),
                chapterName = raw.optString("chapterName", ""),
                chapterUrl = raw.optString("chapterUrl", "")
            )
        }
        return RuleToc()
    }

    private fun parseRuleContent(raw: Any?): RuleContent {
        if (raw is JSONObject) {
            return RuleContent(
                content = raw.optString("content", ""),
                replaceRegex = raw.optString("replaceRegex", "")
            )
        }
        return RuleContent()
    }

    /**
     * Extracts text or attribute from a Jsoup Element based on a Legado-style rule (e.g. "a@href", "h4@text", "img@src")
     */
    /**
     * Extracts text or attribute from a Jsoup Element based on a Legado-style rule (e.g. "a@href", "tag.h3.0@tag.a.1@text", "class.author@text##作者：")
     */
    fun extract(element: Element, rule: String, baseUrl: String = ""): String {
        val trimmed = rule.trim()
        if (trimmed.isEmpty()) return ""

        // Support rule alternatives separated by || or \n
        val alternatives = if (trimmed.contains("||")) trimmed.split("||") else listOf(trimmed)
        for (alt in alternatives) {
            val res = evaluateSingleRule(element, alt.trim(), baseUrl)
            if (res.isNotBlank()) return res
        }
        return ""
    }

    private fun evaluateSingleRule(element: Element, rawRule: String, baseUrl: String): String {
        var ruleStr = rawRule
        var regexReplace: Pair<String, String>? = null

        // Handle regex replacement: "selector##regex##replace" or "##regex"
        if (ruleStr.contains("##")) {
            val parts = ruleStr.split("##")
            ruleStr = parts[0].trim()
            val regex = parts.getOrNull(1) ?: ""
            val replacement = parts.getOrNull(2) ?: ""
            regexReplace = Pair(regex, replacement)
        }

        val tokens = if (ruleStr.contains("@")) ruleStr.split("@") else listOf(ruleStr)
        var currentElements = listOf(element)
        var targetAttr = "text"

        for (token in tokens) {
            val cleanToken = token.trim()
            if (cleanToken.isEmpty()) continue

            // Check if terminal attribute
            val lowerToken = cleanToken.lowercase()
            if (lowerToken in listOf("text", "textnodes", "html", "href", "src", "data-src", "data-original", "alt", "title", "content", "value")) {
                targetAttr = lowerToken
                continue
            }

            val nextElements = ArrayList<Element>()
            for (curr in currentElements) {
                val matched = selectByLegadoToken(curr, cleanToken)
                nextElements.addAll(matched)
            }
            currentElements = nextElements
            if (currentElements.isEmpty()) break
        }

        val targetElement = currentElements.firstOrNull() ?: return ""

        var result = when (targetAttr) {
            "text", "textnodes" -> targetElement.text()
            "html" -> targetElement.html()
            "href" -> {
                val href = targetElement.attr("href").ifEmpty { targetElement.selectFirst("a[href]")?.attr("href") ?: "" }
                resolveUrl(baseUrl, href)
            }
            "src", "data-src", "data-original" -> {
                val src = targetElement.attr("src")
                    .ifEmpty { targetElement.attr("data-src") }
                    .ifEmpty { targetElement.attr("data-original") }
                    .ifEmpty { targetElement.selectFirst("img")?.attr("src") ?: "" }
                resolveUrl(baseUrl, src)
            }
            "alt" -> targetElement.attr("alt")
            "title" -> targetElement.attr("title")
            else -> targetElement.attr(targetAttr)
        }

        if (regexReplace != null && regexReplace.first.isNotEmpty()) {
            try {
                result = result.replace(Regex(regexReplace.first), regexReplace.second)
            } catch (e: Exception) {
                // Ignore regex syntax errors
            }
        }

        return result.trim()
    }

    fun extractList(element: Element, rule: String): List<Element> {
        val trimmed = rule.trim()
        if (trimmed.isNotEmpty()) {
            val alternatives = if (trimmed.contains("||")) trimmed.split("||") else listOf(trimmed)
            for (alt in alternatives) {
                val found = evaluateListRule(element, alt.trim())
                if (found.isNotEmpty()) return found
            }
        }

        // Heuristic fallback: if rule is empty or matched nothing, scan common novel list containers
        val heuristicSelectors = listOf(
            ".bookbox",
            ".so_list .list_item",
            ".novelslist li",
            ".item",
            ".novel-item",
            ".book-item",
            ".search-item",
            "table.grid tr:gt(0)",
            "table tr:has(td.odd)",
            "table tr:has(a[href])",
            "ul.list li:has(a[href])",
            ".result-item",
            ".search-list li",
            ".mybox li",
            ".book-card",
            "li:has(a[href*='book']), li:has(a[href*='chapter']), li:has(a[href*='html'])"
        )
        for (selector in heuristicSelectors) {
            try {
                val items = element.select(selector)
                if (items.isNotEmpty()) return items
            } catch (e: Exception) {}
        }

        return emptyList()
    }

    private fun evaluateListRule(element: Element, ruleStr: String): List<Element> {
        val cleanRule = if (ruleStr.contains("##")) ruleStr.substringBefore("##").trim() else ruleStr.trim()
        val tokens = if (cleanRule.contains("@")) cleanRule.split("@") else listOf(cleanRule)

        var currentElements = listOf(element)
        for (token in tokens) {
            val cleanToken = token.trim()
            if (cleanToken.isEmpty()) continue
            val lower = cleanToken.lowercase()
            if (lower in listOf("text", "textnodes", "html", "href", "src", "data-src", "data-original")) continue

            val nextElements = ArrayList<Element>()
            for (curr in currentElements) {
                nextElements.addAll(selectByLegadoToken(curr, cleanToken))
            }
            currentElements = nextElements
            if (currentElements.isEmpty()) break
        }
        return currentElements
    }

    /**
     * Resolves a single Legado token (e.g. "class.bookbox.0", "tag.li", "id.content", "tr!0", ".item")
     */
    private fun selectByLegadoToken(parent: Element, token: String): List<Element> {
        val parts = token.split(".")
        val prefix = parts[0].lowercase()

        val (cssSelector, targetIndex, excludeIndex) = when {
            prefix == "class" && parts.size >= 2 -> {
                val cls = parts[1]
                val idx = parts.getOrNull(2)?.toIntOrNull()
                Triple(".$cls", idx, null)
            }
            prefix == "tag" && parts.size >= 2 -> {
                val tag = parts[1]
                val idx = parts.getOrNull(2)?.toIntOrNull()
                Triple(tag, idx, null)
            }
            prefix == "id" && parts.size >= 2 -> {
                val id = parts[1]
                val idx = parts.getOrNull(2)?.toIntOrNull()
                Triple("#$id", idx, null)
            }
            token.contains("!") -> {
                val tag = token.substringBefore("!")
                val excl = token.substringAfter("!").toIntOrNull()
                Triple(tag, null, excl)
            }
            parts.size == 2 && parts[1].toIntOrNull() != null -> {
                Triple(parts[0], parts[1].toInt(), null)
            }
            else -> {
                Triple(token, null, null)
            }
        }

        val found = try {
            parent.select(cssSelector)
        } catch (e: Exception) {
            emptyList()
        }

        if (found.isEmpty()) return emptyList()

        if (targetIndex != null) {
            val normalizedIdx = if (targetIndex < 0) found.size + targetIndex else targetIndex
            return if (normalizedIdx in 0 until found.size) listOf(found[normalizedIdx]) else emptyList()
        }

        if (excludeIndex != null) {
            val normalizedIdx = if (excludeIndex < 0) found.size + excludeIndex else excludeIndex
            return found.filterIndexed { index, _ -> index != normalizedIdx }
        }

        return found
    }

    fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        val rel = relativeUrl.trim()
        if (rel.isEmpty()) return ""
        if (rel.startsWith("http://") || rel.startsWith("https://")) return rel
        if (baseUrl.isEmpty()) return rel

        return try {
            val baseUri = URI(baseUrl)
            baseUri.resolve(rel).toString()
        } catch (e: Exception) {
            if (rel.startsWith("/")) {
                val protoEnd = baseUrl.indexOf("://")
                if (protoEnd > 0) {
                    val domainEnd = baseUrl.indexOf('/', protoEnd + 3)
                    val domain = if (domainEnd > 0) baseUrl.substring(0, domainEnd) else baseUrl
                    "$domain$rel"
                } else {
                    rel
                }
            } else {
                "${baseUrl.trimEnd('/')}/$rel"
            }
        }
    }
}
