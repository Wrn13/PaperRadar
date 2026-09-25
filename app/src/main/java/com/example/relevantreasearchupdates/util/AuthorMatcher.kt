package com.example.relevantreasearchupdates.util

/**
 * arXiv's `au:` search and Crossref's `query.author` are both fuzzy/relevance searches, not exact
 * filters — searching for a full name like "Michael I. Jordan" or "Wei Li" returns any paper with
 * an author who merely shares the first or last name. This matcher re-checks results client-side
 * so a watch only fires when one specific author on the paper actually has both the first and
 * last name being watched.
 */
object AuthorMatcher {

    /** True if any single author string in [candidates] matches [watchedName]. */
    fun anyMatches(watchedName: String, candidates: List<String>): Boolean =
        candidates.any { matches(watchedName, it) }

    fun matches(watchedName: String, candidateName: String): Boolean {
        val watchTokens = normalize(watchedName)
        val candidateTokens = normalize(candidateName)
        if (watchTokens.isEmpty() || candidateTokens.isEmpty()) return false

        if (watchTokens.toSet() == candidateTokens.toSet()) return true

        val watchFirst = watchTokens.first()
        val watchLast = watchTokens.last()

        val lastNameMatches = candidateTokens.any { it == watchLast }
        val firstNameMatches = candidateTokens.any { token -> tokensMatch(watchFirst, token) }

        return lastNameMatches && firstNameMatches
    }

    /** Treats a single-letter token as an initial that only needs to match the other's first letter. */
    private fun tokensMatch(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.length == 1) return b.startsWith(a)
        if (b.length == 1) return a.startsWith(b)
        return false
    }

    private fun normalize(name: String): List<String> = name
        .lowercase()
        .replace(".", "")
        .replace(",", " ")
        .replace(Regex("[^a-z\\s'-]"), "")
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
}
