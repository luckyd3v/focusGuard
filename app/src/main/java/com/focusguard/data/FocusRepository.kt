package com.focusguard.data

import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class FocusRepository(
    private val windowDao: UsageWindowDao,
    private val sessionDao: UsageSessionDao,
    private val taskDao: TaskDao,
    private val tagDao: TagDao,
) {
    fun observeWindows(): Flow<List<UsageWindow>> = windowDao.observeAll()
    suspend fun windowsOnce(): List<UsageWindow> = windowDao.getAll()
    suspend fun saveWindow(window: UsageWindow) = windowDao.upsert(window)
    suspend fun deleteWindow(window: UsageWindow) {
        windowDao.delete(window)
        taskDao.clearWindow(window.id)
    }
    suspend fun activateOnDemand(windowId: Long, at: Long, estimateMinutes: Int) =
        windowDao.activateOnDemand(windowId, at, estimateMinutes)
    suspend fun deactivateOnDemand(windowId: Long) = windowDao.deactivateOnDemand(windowId)
    suspend fun extendEstimate(windowId: Long, minutes: Int) = windowDao.extendEstimate(windowId, minutes)

    suspend fun recordSession(session: UsageSession) = sessionDao.insert(session)
    fun observeSessions(from: Long, to: Long): Flow<List<UsageSession>> = sessionDao.observeBetween(from, to)
    suspend fun purgeOlderThan(before: Long) {
        sessionDao.deleteOlderThan(before)
        val beforeDay = Instant.ofEpochMilli(before).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        taskDao.deleteOccurrencesBefore(beforeDay)
    }

    // ------------------------------------------------------------ afazeres

    fun observeTasks(kind: Int): Flow<List<Task>> = taskDao.observeTasks(kind)
    fun observeDailyOccurrences(day: LocalDate): Flow<List<TaskOccurrence>> = taskDao.observeDailyOccurrences(day.toEpochDay())
    fun observeOneOffOccurrences(doneSince: Long): Flow<List<TaskOccurrence>> = taskDao.observeOneOffOccurrences(doneSince)
    fun observeTaskHistory(day: LocalDate, from: Long, to: Long): Flow<List<TaskOccurrence>> =
        taskDao.observeHistory(day.toEpochDay(), from, to)

    suspend fun ensureDailyOccurrences(day: LocalDate) = taskDao.ensureDailyOccurrences(day.toEpochDay())

    /** Cadastra o afazer. Cotidiano novo já vale para hoje; pontual pode ser adicionado na hora. */
    suspend fun createTask(task: Task, today: LocalDate, addNow: Boolean) {
        val id = taskDao.insert(task)
        when {
            task.kind == TaskKind.DAILY -> taskDao.ensureDailyOccurrences(today.toEpochDay())
            addNow -> addOneOff(task.copy(id = id), today)
        }
    }

    /** Edita o afazer e a ocorrência de hoje ainda em aberto (a história concluída fica como estava). */
    suspend fun updateTask(task: Task, today: LocalDate) {
        taskDao.update(task)
        taskDao.updateOpenOccurrence(task.id, today.toEpochDay(), task.title, task.windowId)
    }

    /** Exclui o afazer; ocorrências já concluídas continuam no histórico. */
    suspend fun deleteTask(task: Task, today: LocalDate) {
        taskDao.delete(task)
        taskDao.deleteOpenOccurrence(task.id, today.toEpochDay())
    }

    /** "Adicionar" de um afazer pontual: cria uma ocorrência nova para concluir. */
    suspend fun addOneOff(task: Task, today: LocalDate) {
        taskDao.insertOccurrence(TaskOccurrence(taskId = task.id, title = task.title, kind = TaskKind.ONE_OFF, day = today.toEpochDay()))
    }

    /** Link compartilhado com o app: vira uma tarefa pontual em "Para fazer". */
    suspend fun addLinkTask(title: String, url: String?, estimateMinutes: Int, today: LocalDate, tagId: Long? = null) {
        taskDao.insertOccurrence(
            TaskOccurrence(
                taskId = null,
                title = title,
                kind = TaskKind.ONE_OFF,
                day = today.toEpochDay(),
                url = url,
                estimateMinutes = estimateMinutes,
                tagId = tagId,
            )
        )
    }

    fun observeLinks(doneSince: Long): Flow<List<TaskOccurrence>> = taskDao.observeLinks(doneSince)

    // ------------------------------------------------------------ tags

    fun observeTags(): Flow<List<Tag>> = tagDao.observeAll()
    suspend fun createTag(name: String, color: Int): Long = tagDao.insert(Tag(name = name, color = color))
    suspend fun updateTag(tag: Tag) = tagDao.update(tag)
    suspend fun deleteTag(tag: Tag) = tagDao.delete(tag.id)
    suspend fun setOccurrenceTag(occurrence: TaskOccurrence, tagId: Long?) = tagDao.setOccurrenceTag(occurrence.id, tagId)

    suspend fun setOccurrenceDone(occurrence: TaskOccurrence, done: Boolean) =
        taskDao.setCompleted(occurrence.id, if (done) System.currentTimeMillis() else null)

    suspend fun deleteOccurrence(occurrence: TaskOccurrence) = taskDao.deleteOccurrence(occurrence.id)
}
