package com.example.relevantreasearchupdates.util

/**
 * arXiv's and Crossref's own keyword search is case-insensitive, so a watch for "Ising" would
 * otherwise also fire for "ising" or "ISING" showing up anywhere. This re-checks results
 * client-side so a keyword watch only fires when the keyword appears with its exact case.
 */
object KeywordMatcher {
    fun matches(keyword: String, vararg texts: String): Boolean {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return false
        return texts.any { it.contains(trimmed) } // String.contains is case-sensitive by default
    }
}
