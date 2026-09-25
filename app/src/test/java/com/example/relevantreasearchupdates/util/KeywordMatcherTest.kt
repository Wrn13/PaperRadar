package com.example.relevantreasearchupdates.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordMatcherTest {

    @Test
    fun exactCaseMatches() {
        assertTrue(KeywordMatcher.matches("Ising", "The Ising model revisited", ""))
    }

    @Test
    fun differentCaseDoesNotMatch() {
        assertFalse(KeywordMatcher.matches("Ising", "the ising model revisited", ""))
        assertFalse(KeywordMatcher.matches("Ising", "THE ISING MODEL REVISITED", ""))
    }

    @Test
    fun matchesInEitherProvidedText() {
        assertTrue(KeywordMatcher.matches("Qubit", "Unrelated title", "New results on Qubit coherence"))
    }

    @Test
    fun blankKeywordNeverMatches() {
        assertFalse(KeywordMatcher.matches("  ", "anything at all"))
    }
}
