package com.example.relevantreasearchupdates.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class PaperSource {
    ARXIV,
    CROSSREF
}

/**
 * externalId uniquely identifies a paper across re-fetches (e.g. "arxiv:2409.12345" or
 * "doi:10.1000/xyz123"), so repeated worker runs can insert-or-ignore instead of duplicating rows.
 */
@Entity(
    tableName = "papers",
    indices = [Index(value = ["externalId"], unique = true)]
)
data class Paper(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val externalId: String,
    val title: String,
    val authors: String,
    val summary: String,
    val source: PaperSource,
    val sourceUrl: String,
    val pdfUrl: String?,
    val doi: String?,
    val journal: String?,
    val publishedDate: String,
    val matchedQuery: String,
    val isRead: Boolean = false,
    val localPdfPath: String? = null,
    val fetchedAt: Long = System.currentTimeMillis()
)
