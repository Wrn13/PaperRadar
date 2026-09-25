package com.example.relevantreasearchupdates.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PaperDao {
    @Query("SELECT * FROM papers ORDER BY publishedDate DESC, fetchedAt DESC")
    fun observeAll(): Flow<List<Paper>>

    @Query("SELECT * FROM papers WHERE id = :id")
    fun observeById(id: Long): Flow<Paper?>

    @Query("SELECT * FROM papers WHERE id = :id")
    suspend fun getById(id: Long): Paper?

    @Query("SELECT COUNT(*) FROM papers WHERE isRead = 0")
    fun observeUnreadCount(): Flow<Int>

    /** Returns -1 if a paper with the same externalId already exists (ignored), else the new row id. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoring(paper: Paper): Long

    @Query("UPDATE papers SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("UPDATE papers SET localPdfPath = :path WHERE id = :id")
    suspend fun updateLocalPdfPath(id: Long, path: String)

    @Query("DELETE FROM papers WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM papers WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** Papers that were matched via an author watch, so they can be re-validated against the current matcher. */
    @Query("SELECT * FROM papers WHERE matchedQuery LIKE 'Author:%'")
    suspend fun getAuthorMatchedOnce(): List<Paper>

    /**
     * Every paper past the [keep] most recently *published*. Ordering by fetch time instead
     * trimmed exactly the wrong papers: a run inserts results newest-first, so the newest
     * papers got the earliest fetch timestamps and were the first to be deleted.
     */
    @Query("SELECT * FROM papers ORDER BY publishedDate DESC, fetchedAt DESC LIMIT -1 OFFSET :keep")
    suspend fun getOverflow(keep: Int): List<Paper>

    @Query("SELECT COUNT(*) FROM papers")
    suspend fun count(): Int

    @Query("SELECT MIN(publishedDate) FROM papers WHERE publishedDate != ''")
    suspend fun oldestPublishedDate(): String?

    @Query("SELECT localPdfPath FROM papers WHERE localPdfPath IS NOT NULL")
    suspend fun getAllLocalPdfPaths(): List<String>
}
