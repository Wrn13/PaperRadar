package com.example.relevantreasearchupdates.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class WatchType {
    AUTHOR,
    KEYWORD
}

@Entity(tableName = "watches")
data class Watch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: WatchType,
    val value: String,
    val createdAt: Long = System.currentTimeMillis()
)
