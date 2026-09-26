package com.example.relevantreasearchupdates.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A paper the user removed from the feed. Keyed by [Paper.externalId] so later worker runs
 * that fetch the same paper again skip it instead of re-inserting it.
 */
@Entity(tableName = "dismissed_papers")
data class DismissedPaper(
    @PrimaryKey val externalId: String,
    val dismissedAt: Long = System.currentTimeMillis()
)
