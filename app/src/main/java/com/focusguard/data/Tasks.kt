package com.focusguard.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Afazer cadastrado pelo usuário.
 *  - [TaskKind.DAILY] (cotidiano): ganha uma ocorrência nova a cada dia e pode ser associado a uma
 *    janela de uso ([windowId]); enquanto essa janela está em vigor, aparece na aba Uso.
 *  - [TaskKind.ONE_OFF] (pontual): é um modelo; cada "Adicionar" cria uma ocorrência para concluir.
 */
@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val kind: Int,
    val windowId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** Tempo estimado para fazer, em minutos (opcional); copiado para cada ocorrência. */
    val estimateMinutes: Int? = null,
)

object TaskKind {
    const val DAILY = 0
    const val ONE_OFF = 1
}

/**
 * Uma ocorrência de afazer a concluir: a de um dia para os cotidianos ([day] = dia em epochDay) ou
 * a de cada "Adicionar" dos pontuais. Guarda cópia do título e da janela para que o histórico
 * continue legível mesmo se o afazer for editado ou excluído.
 */
@Entity(
    tableName = "task_occurrences",
    indices = [Index("taskId", "day"), Index("day"), Index("completedAt")],
)
data class TaskOccurrence(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long?,
    val title: String,
    val kind: Int,
    val windowId: Long? = null,
    val day: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    /** Link compartilhado com o FocusGuard (vídeo, site...), se a tarefa veio de um. */
    val url: String? = null,
    /** Duração estimada da atividade, em minutos. */
    val estimateMinutes: Int? = null,
    /** Tag do link (uma por link), ou null. */
    val tagId: Long? = null,
) {
    val done: Boolean get() = completedAt != null
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE kind = :kind ORDER BY title COLLATE NOCASE")
    fun observeTasks(kind: Int): Flow<List<Task>>

    @Insert
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)

    @Query("UPDATE tasks SET windowId = NULL WHERE windowId = :windowId")
    suspend fun clearWindow(windowId: Long)

    // ------------------------------------------------------------ ocorrências

    @Insert
    suspend fun insertOccurrence(occurrence: TaskOccurrence): Long

    @Query("SELECT * FROM task_occurrences WHERE kind = 0 AND day = :day")
    fun observeDailyOccurrences(day: Long): Flow<List<TaskOccurrence>>

    /** Links salvos: em aberto e os concluídos a partir de [doneSince]. */
    @Query(
        "SELECT * FROM task_occurrences WHERE url IS NOT NULL AND (completedAt IS NULL OR completedAt >= :doneSince) " +
            "ORDER BY completedAt IS NOT NULL, createdAt DESC"
    )
    fun observeLinks(doneSince: Long): Flow<List<TaskOccurrence>>

    /** Pontuais em aberto (de qualquer dia) e os concluídos a partir de [doneSince]. */
    @Query(
        "SELECT * FROM task_occurrences WHERE kind = 1 AND (completedAt IS NULL OR completedAt >= :doneSince) " +
            "ORDER BY completedAt IS NOT NULL, createdAt"
    )
    fun observeOneOffOccurrences(doneSince: Long): Flow<List<TaskOccurrence>>

    /** Histórico de um dia: cotidianos do dia e pontuais concluídos no dia. */
    @Query(
        "SELECT * FROM task_occurrences WHERE (kind = 0 AND day = :day) " +
            "OR (kind = 1 AND completedAt >= :from AND completedAt < :to) ORDER BY kind, title COLLATE NOCASE"
    )
    fun observeHistory(day: Long, from: Long, to: Long): Flow<List<TaskOccurrence>>

    /** Tudo o que está pendente para o tempo livre: cotidianos de hoje e pontuais/links em aberto. */
    @Query("SELECT * FROM task_occurrences WHERE completedAt IS NULL AND ((kind = 0 AND day = :day) OR kind = 1)")
    suspend fun pendingForFreeTime(day: Long): List<TaskOccurrence>

    @Query("UPDATE task_occurrences SET completedAt = :completedAt WHERE id = :id")
    suspend fun setCompleted(id: Long, completedAt: Long?)

    @Query("DELETE FROM task_occurrences WHERE id = :id")
    suspend fun deleteOccurrence(id: Long)

    @Query(
        "UPDATE task_occurrences SET title = :title, windowId = :windowId, estimateMinutes = :estimateMinutes " +
            "WHERE taskId = :taskId AND day = :day AND completedAt IS NULL"
    )
    suspend fun updateOpenOccurrence(taskId: Long, day: Long, title: String, windowId: Long?, estimateMinutes: Int?)

    @Query("DELETE FROM task_occurrences WHERE taskId = :taskId AND day = :day AND completedAt IS NULL")
    suspend fun deleteOpenOccurrence(taskId: Long, day: Long)

    @Query("DELETE FROM task_occurrences WHERE day < :beforeDay")
    suspend fun deleteOccurrencesBefore(beforeDay: Long)

    @Query("SELECT * FROM tasks WHERE kind = 0")
    suspend fun dailyTasks(): List<Task>

    @Query("SELECT taskId FROM task_occurrences WHERE kind = 0 AND day = :day AND taskId IS NOT NULL")
    suspend fun dailyTaskIdsWithOccurrence(day: Long): List<Long>

    /** Cria a ocorrência do dia para cada cotidiano que ainda não tem. Idempotente. */
    @Transaction
    suspend fun ensureDailyOccurrences(day: Long) {
        val existing = dailyTaskIdsWithOccurrence(day).toSet()
        dailyTasks().filter { it.id !in existing }.forEach { task ->
            insertOccurrence(
                TaskOccurrence(
                    taskId = task.id,
                    title = task.title,
                    kind = TaskKind.DAILY,
                    windowId = task.windowId,
                    day = day,
                    estimateMinutes = task.estimateMinutes,
                )
            )
        }
    }
}
