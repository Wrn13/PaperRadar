package com.example.relevantreasearchupdates.data

import android.content.Context
import java.io.File

/**
 * Keeps storage bounded: only the [MAX_STORED_PAPERS] most recently published papers are kept,
 * and any cached PDF file left behind by a deleted/trimmed paper is cleaned up too, since
 * downloaded PDFs otherwise sit in the app's cache directory indefinitely. Paper rows themselves
 * are tiny; the cap is generous so it only ever trims genuinely old papers.
 */
object PaperRetention {
    const val MAX_STORED_PAPERS = 500

    /**
     * Once the cap is reached, the publication date below which papers are trimmed anyway.
     * Searches keep returning old papers, and without this a trimmed paper would be re-inserted
     * — and re-notified as "new" — on every run, only to be trimmed again. Null while under cap.
     */
    suspend fun insertFloor(db: AppDatabase): String? {
        if (db.paperDao().count() < MAX_STORED_PAPERS) return null
        return db.paperDao().oldestPublishedDate()
    }

    suspend fun enforce(context: Context, db: AppDatabase) {
        val overflow = db.paperDao().getOverflow(MAX_STORED_PAPERS)
        if (overflow.isNotEmpty()) {
            for (paper in overflow) {
                paper.localPdfPath?.let { File(it).delete() }
            }
            db.paperDao().deleteByIds(overflow.map { it.id })
        }
        sweepOrphanPdfFiles(context, db)
    }

    private suspend fun sweepOrphanPdfFiles(context: Context, db: AppDatabase) {
        val pdfDir = File(context.cacheDir, "pdfs")
        val files = pdfDir.listFiles() ?: return
        if (files.isEmpty()) return
        val referenced = db.paperDao().getAllLocalPdfPaths().toSet()
        for (file in files) {
            if (file.absolutePath !in referenced) {
                file.delete()
            }
        }
    }
}
