package com.example.relevantreasearchupdates.network

import android.util.Log
import android.util.Xml
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class ArxivEntry(
    val arxivId: String,
    val title: String,
    val authors: List<String>,
    val summary: String,
    val published: String,
    val absUrl: String,
    val pdfUrl: String?
)

/**
 * Thin client for the public arXiv API (https://info.arxiv.org/help/api/index.html).
 * No API key required. Callers should space out requests (arXiv asks for no more than
 * one request every 3 seconds).
 */
class ArxivClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        // arXiv's API regularly takes 30-45s to answer a perfectly good query. A shorter
        // timeout threw those responses away and then retried, making things slower still.
        .readTimeout(90, TimeUnit.SECONDS)
        .build()
) {
    /**
     * A quoted multi-word phrase against au: is unreliable on arXiv's search backend — author
     * names are documented to need per-word clauses (their own docs example is
     * "au:del_maestro"). ANDing one au: clause per word is the robust, documented-safe pattern,
     * and since [com.example.relevantreasearchupdates.util.AuthorMatcher] re-validates every
     * result client-side anyway, this query being a little looser than an exact phrase is fine.
     */
    fun searchByAuthor(name: String, maxResults: Int = 15): List<ArxivEntry> =
        search(buildFieldQuery("au", name), maxResults)

    fun searchByKeyword(keyword: String, maxResults: Int = 15): List<ArxivEntry> =
        search(buildFieldQuery("all", keyword), maxResults)

    fun authorClause(name: String): String = "(${buildFieldQuery("au", name)})"

    fun keywordClause(keyword: String): String = "(${buildFieldQuery("all", keyword)})"

    /**
     * One request matching any of [clauses] (from [authorClause]/[keywordClause]). arXiv
     * responses can take tens of seconds each, so combining several watches into one query is
     * far faster than a request per watch.
     */
    fun searchAny(clauses: List<String>, maxResults: Int): List<ArxivEntry> =
        search(clauses.joinToString(" OR "), maxResults)

    private fun buildFieldQuery(field: String, phrase: String): String {
        val words = phrase.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return "$field:$phrase"
        return words.joinToString(" AND ") { word -> "$field:$word" }
    }

    private fun search(searchQuery: String, maxResults: Int): List<ArxivEntry> {
        val encoded = URLEncoder.encode(searchQuery, "UTF-8")
        val url = "https://export.arxiv.org/api/query" +
            "?search_query=$encoded&sortBy=submittedDate&sortOrder=descending&max_results=$maxResults"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "PaperRadar/1.0 (Android literature alerts)")
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                // Rate limiting and server errors are transient, so surface them as failures
                // the worker can retry instead of quietly reporting "no new papers".
                if (response.code == 429 || response.code >= 500) {
                    throw IOException("arXiv returned HTTP ${response.code}")
                }
                if (!response.isSuccessful) {
                    Log.w(TAG, "arXiv search failed: HTTP ${response.code} for query \"$searchQuery\"")
                    return emptyList()
                }
                val body = response.body?.string() ?: return emptyList()
                return parseFeed(body)
            }
        } catch (e: Exception) {
            Log.w(TAG, "arXiv search errored for query \"$searchQuery\"", e)
            throw e
        }
    }

    private fun parseFeed(xml: String): List<ArxivEntry> {
        val entries = mutableListOf<ArxivEntry>()
        val parser: XmlPullParser = Xml.newPullParser()
        parser.setInput(StringReader(xml))

        var eventType = parser.eventType
        var inEntry = false

        var id = ""
        var title = ""
        var summary = ""
        var published = ""
        val authors = mutableListOf<String>()
        var pdfUrl: String? = null

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "entry" -> {
                            inEntry = true
                            id = ""; title = ""; summary = ""; published = ""; pdfUrl = null
                            authors.clear()
                        }
                        "id" -> if (inEntry) id = readText(parser)
                        "title" -> if (inEntry) title = readText(parser).trim().replace(Regex("\\s+"), " ")
                        "summary" -> if (inEntry) summary = readText(parser).trim()
                        "published" -> if (inEntry) published = readText(parser)
                        "name" -> if (inEntry) authors.add(readText(parser))
                        "link" -> if (inEntry) {
                            val title2 = parser.getAttributeValue(null, "title")
                            val href = parser.getAttributeValue(null, "href")
                            if (title2 == "pdf" && href != null) pdfUrl = href
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "entry" && inEntry) {
                        val arxivId = id.substringAfterLast("/abs/").ifBlank { id }
                        entries.add(
                            ArxivEntry(
                                arxivId = arxivId,
                                title = title,
                                authors = authors.toList(),
                                summary = summary,
                                published = published,
                                absUrl = id,
                                pdfUrl = pdfUrl
                            )
                        )
                        inEntry = false
                    }
                }
            }
            eventType = parser.next()
        }
        return entries
    }

    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text
            parser.nextTag()
        }
        return result
    }

    companion object {
        private const val TAG = "ArxivClient"
    }
}
