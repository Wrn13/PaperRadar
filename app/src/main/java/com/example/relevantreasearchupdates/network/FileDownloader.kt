package com.example.relevantreasearchupdates.network

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Downloads freely-accessible PDFs (arXiv, open-access Crossref links) into the app's cache
 * directory so they can be rendered locally by [PdfViewerScreen]. Paywalled journal articles are
 * never downloaded this way — those are opened through EZproxy in a browser instead, since they
 * require an authenticated institutional session.
 */
class FileDownloader(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    suspend fun downloadPdf(url: String, fileName: String): File = withContext(Dispatchers.IO) {
        val pdfDir = File(context.cacheDir, "pdfs").apply { mkdirs() }
        val destination = File(pdfDir, fileName)
        if (destination.exists() && destination.length() > 0) return@withContext destination

        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Download failed: HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("Empty response body")
            val contentType = body.contentType()?.toString().orEmpty()
            if (!contentType.contains("pdf") && !url.endsWith(".pdf", ignoreCase = true)) {
                throw IOException("Server did not return a PDF (content-type: $contentType)")
            }
            destination.outputStream().use { out ->
                body.byteStream().copyTo(out)
            }
        }
        destination
    }
}
