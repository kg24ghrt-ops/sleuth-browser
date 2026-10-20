package com.sleuth.browser.browser

import java.net.URLEncoder

data class SearchEngine(val id: String, val name: String, val template: String) {
    fun urlFor(query: String): String =
        template.replace("%s", URLEncoder.encode(query, "UTF-8"))
}

object SearchEngines {

    val ALL: List<SearchEngine> = listOf(
        SearchEngine("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=%s"),
        SearchEngine("brave", "Brave", "https://search.brave.com/search?q=%s"),
        SearchEngine("google", "Google", "https://www.google.com/search?q=%s"),
        SearchEngine("bing", "Bing", "https://www.bing.com/search?q=%s"),
        SearchEngine("startpage", "Startpage", "https://www.startpage.com/sp/search?query=%s"),
        SearchEngine("wikipedia", "Wikipedia", "https://en.wikipedia.org/w/index.php?search=%s")
    )

    fun byId(id: String?): SearchEngine = ALL.firstOrNull { it.id == id } ?: ALL.first()

    fun searchUrl(id: String?, query: String): String = byId(id).urlFor(query)
}
