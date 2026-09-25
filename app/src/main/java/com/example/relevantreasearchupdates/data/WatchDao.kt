package com.example.relevantreasearchupdates.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchDao {
    @Query("SELECT * FROM watches ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Watch>>

    @Query("SELECT * FROM watches")
    suspend fun getAllOnce(): List<Watch>

    @Insert
    suspend fun insert(watch: Watch): Long

    @Delete
    suspend fun delete(watch: Watch)
}
