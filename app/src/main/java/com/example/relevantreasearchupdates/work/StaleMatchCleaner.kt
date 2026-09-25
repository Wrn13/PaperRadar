package com.example.relevantreasearchupdates.work

import com.example.relevantreasearchupdates.data.AppDatabase
import com.example.relevantreasearchupdates.util.AuthorMatcher
import java.io.File

/**
 * Papers are inserted once and never re-checked afterwards. If a paper was stored back when
 * author matching was looser (matching on just a first or last name), it stays in the feed
 * forever even after the matcher is tightened. This re-validates every author-matched paper
 * against the current [AuthorMatcher] rules and removes anything that no longer qualifies.
 */
object StaleMatchCleaner {
    suspend fun purge(db: AppDatabase) {
        val papers = db.paperDao().getAuthorMatchedOnce()
        for (paper in papers) {
            val watchedName = paper.matchedQuery.removePrefix("Author:").trim()
            val candidateAuthors = paper.authors.split(",").map { it.trim() }
            if (watchedName.isBlank() || !AuthorMatcher.anyMatches(watchedName, candidateAuthors)) {
                paper.localPdfPath?.let { File(it).delete() }
                db.paperDao().deleteById(paper.id)
            }
        }
    }
}
