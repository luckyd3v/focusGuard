package com.focusguard.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
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

    @Query("UPDATE usage_windows SET activatedAt = NULL WHERE onDemand = 1")
    suspend fun deactivateAllOnDemand()

    @Query("UPDATE usage_windows SET activatedAt = NULL WHERE id = :id")
    suspend fun deactivateOnDemand(id: Long)

    @Query("UPDATE usage_windows SET activatedAt = :at, estimateMinutes = :estimateMinutes WHERE id = :id AND onDemand = 1")
    suspend fun setActivated(id: Long, at: Long, estimateMinutes: Int)

    @Query("UPDATE usage_windows SET estimateMinutes = estimateMinutes + :minutes WHERE id = :id AND activatedAt IS NOT NULL")
    suspend fun extendEstimate(id: Long, minutes: Int)

    /** Liga uma janela sob demanda, desligando as demais: só uma vale por vez. */
    @Transaction
    suspend fun activateOnDemand(id: Long, at: Long, estimateMinutes: Int) {
        deactivateAllOnDemand()
        setActivated(id, at, estimateMinutes)
    }
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
