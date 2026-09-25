package com.example.relevantreasearchupdates.network

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class CrossrefEntry(
    val doi: String,
    val title: String,
    val authors: List<String>,
    val journal: String?,
    val publishedDate: String,
    val url: String,
    val openAccessPdfUrl: String?
)

/**
 * Thin client for the free Crossref REST API (https://api.crossref.org), used to discover
 * journal-published literature that never appears on arXiv. Crossref indexes metadata (title,
 * authors, DOI, journal) for most publishers but rarely hosts full text itself, so the resulting
 * DOI link usually needs to go through the user's EZproxy to reach paywalled content.
 */
class CrossrefClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    fun searchByAuthor(name: String, rows: Int = 15): List<CrossrefEntry> =
        search("query.author", name, rows)

    fun searchByKeyword(keyword: String, rows: Int = 15): List<CrossrefEntry> =
        search("query.bibliographic", keyword, rows)

    private fun search(param: String, value: String, rows: Int): List<CrossrefEntry> {
        val encoded = URLEncoder.encode(value, "UTF-8")
        val url = "https://api.crossref.org/works?$param=$encoded&rows=$rows&sort=published&order=desc"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "RelevantResearchUpdates/1.0 (mailto:app-user@example.com)")
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                // Rate limiting and server errors are transient, so surface them as failures
                // the worker can retry instead of quietly reporting "no new papers".
                if (response.code == 429 || response.code >= 500) {
                    throw IOException("Crossref returned HTTP ${response.code}")
                }
                if (!response.isSuccessful) {
                    Log.w(TAG, "Crossref search failed: HTTP ${response.code} for $param=$value")
                    return emptyList()
                }
                val body = response.body?.string() ?: return emptyList()
                return parseWorks(body)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Crossref search errored for $param=$value", e)
            throw e
        }
    }

    private fun parseWorks(json: String): List<CrossrefEntry> {
        val root = JSONObject(json)
        val items = root.optJSONObject("message")?.optJSONArray("items") ?: return emptyList()
        val results = mutableListOf<CrossrefEntry>()
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val doi = item.optString("DOI").takeIf { it.isNotBlank() } ?: continue
            val title = item.optJSONArray("title")?.optString(0)?.takeIf { it.isNotBlank() }
                ?: continue
            val authors = mutableListOf<String>()
            item.optJSONArray("author")?.let { authorArray ->
                for (a in 0 until authorArray.length()) {
                    val author = authorArray.getJSONObject(a)
                    val given = author.optString("given", "")
                    val family = author.optString("family", "")
                    val full = "$given $family".trim()
                    if (full.isNotBlank()) authors.add(full)
                }
            }
            val journal = item.optJSONArray("container-title")?.optString(0)
            val published = extractDate(item)
            val pdfUrl = extractOpenAccessPdf(item)
            results.add(
                CrossrefEntry(
                    doi = doi,
                    title = title,
                    authors = authors,
                    journal = journal,
                    publishedDate = published,
                    url = item.optString("URL", "https://doi.org/$doi"),
                    openAccessPdfUrl = pdfUrl
                )
            )
        }
        return results
    }

    private fun extractDate(item: JSONObject): String {
        val dateParts = item.optJSONObject("published")?.optJSONArray("date-parts")
            ?: item.optJSONObject("published-print")?.optJSONArray("date-parts")
            ?: item.optJSONObject("published-online")?.optJSONArray("date-parts")
        val parts = dateParts?.optJSONArray(0) ?: return ""
        val year = parts.optInt(0, 0)
        val month = if (parts.length() > 1) parts.optInt(1, 1) else 1
        val day = if (parts.length() > 2) parts.optInt(2, 1) else 1
        if (year == 0) return ""
        return "%04d-%02d-%02d".format(year, month, day)
    }

    private fun extractOpenAccessPdf(item: JSONObject): String? {
        val links: JSONArray = item.optJSONArray("link") ?: return null
        for (i in 0 until links.length()) {
            val link = links.getJSONObject(i)
            val contentType = link.optString("content-type")
            if (contentType == "application/pdf") {
                return link.optString("URL").takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    companion object {
        private const val TAG = "CrossrefClient"
    }
}
