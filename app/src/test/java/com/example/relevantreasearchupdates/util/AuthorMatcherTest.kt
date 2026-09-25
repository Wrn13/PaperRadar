package com.example.relevantreasearchupdates.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorMatcherTest {

    @Test
    fun exactFullNameMatches() {
        assertTrue(AuthorMatcher.matches("Michael I. Jordan", "Michael I. Jordan"))
    }

    @Test
    fun sameFirstNameDifferentLastNameDoesNotMatch() {
        assertFalse(AuthorMatcher.matches("Michael I. Jordan", "Michael Chen"))
    }

    @Test
    fun sameLastNameDifferentFirstNameDoesNotMatch() {
        assertFalse(AuthorMatcher.matches("Wei Li", "Xiang Li"))
        assertFalse(AuthorMatcher.matches("Wei Li", "Wei Zhang"))
    }

    @Test
    fun initialMatchesFullFirstName() {
        assertTrue(AuthorMatcher.matches("Wei Li", "W. Li"))
        assertTrue(AuthorMatcher.matches("W. Li", "Wei Li"))
    }

    @Test
    fun matchesOneAuthorAmongManyOnAPaper() {
        val authors = listOf("Xiang Li", "Wei Li", "Ada Lovelace")
        assertTrue(AuthorMatcher.anyMatches("Wei Li", authors))
    }

    @Test
    fun doesNotCrossMatchTokensAcrossDifferentAuthors() {
        // "Michael" from one author and "Li" from a different author must not combine into a match.
        val authors = listOf("Michael Chen", "Xiang Li")
        assertFalse(AuthorMatcher.anyMatches("Michael Li", authors))
    }
}
