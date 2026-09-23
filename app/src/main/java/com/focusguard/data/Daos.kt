package com.focusguard.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageWindowDao {
    @Query("SELECT * FROM usage_windows ORDER BY startMinuteOfDay, name")
    fun observeAll(): Flow<List<UsageWindow>>

    @Query("SELECT * FROM usage_windows")
    suspend fun getAll(): List<UsageWindow>

    @Upsert
    suspend fun upsert(window: UsageWindow): Long

    @Delete
    suspend fun delete(window: UsageWindow)
}

@Dao
interface UsageSessionDao {
    @Insert
    suspend fun insert(session: UsageSession): Long

    @Query("SELECT * FROM usage_sessions WHERE startTime >= :from AND startTime < :to ORDER BY startTime")
    fun observeBetween(from: Long, to: Long): Flow<List<UsageSession>>

    @Query("DELETE FROM usage_sessions WHERE startTime < :before")
    suspend fun deleteOlderThan(before: Long)
}
