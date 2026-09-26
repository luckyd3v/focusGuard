package com.focusguard.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Etiqueta com cor para organizar os links salvos (uma por link). [color] é ARGB. */
@Entity(tableName = "tags")
data class Tag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
)

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Tag>>

    @Insert
    suspend fun insert(tag: Tag): Long

    @Update
    suspend fun update(tag: Tag)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE task_occurrences SET tagId = NULL WHERE tagId = :tagId")
    suspend fun clearTag(tagId: Long)

    @Query("UPDATE task_occurrences SET tagId = :tagId WHERE id = :occurrenceId")
    suspend fun setOccurrenceTag(occurrenceId: Long, tagId: Long?)

    /** Exclui a tag e a tira dos links que a usavam (os links continuam). */
    @Transaction
    suspend fun delete(tagId: Long) {
        clearTag(tagId)
        deleteById(tagId)
    }
}
