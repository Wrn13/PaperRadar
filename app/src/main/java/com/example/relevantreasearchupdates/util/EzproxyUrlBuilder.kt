package com.example.relevantreasearchupdates.util

import java.net.URLEncoder

/**
 * Wraps [targetUrl] with the user's configured EZproxy login stem so an institutional
 * subscription can unlock a paywalled article. [template] may contain the literal token
 * "{url}", e.g. "https://libezproxy.example.edu/login?url={url}"; otherwise the target is
 * appended, so a bare stem like "https://libezproxy.example.edu/login?url=" works too. If
 * [template] is blank, [targetUrl] is returned unchanged.
 *
 * EZproxy's `url=` parameter takes the target URL raw — it treats everything after `url=` as
 * the destination, and a percent-encoded value there isn't recognized as a URL at all. Only
 * the `qurl=` variant expects it encoded, so encode only in that case.
 */
object EzproxyUrlBuilder {
    fun build(template: String, targetUrl: String): String {
        val trimmed = template.trim()
        if (trimmed.isEmpty() || isArxiv(targetUrl)) return targetUrl
        val value = if (trimmed.contains("qurl=")) URLEncoder.encode(targetUrl, "UTF-8") else targetUrl
        return if (trimmed.contains("{url}")) {
            trimmed.replace("{url}", value)
        } else {
            trimmed + value
        }
    }

    /** arXiv is open access, so routing it through the proxy only adds a pointless login. */
    private fun isArxiv(url: String): Boolean {
        val host = runCatching { java.net.URI(url).host }.getOrNull()?.lowercase() ?: return false
        return host == "arxiv.org" || host.endsWith(".arxiv.org")
    }
}
