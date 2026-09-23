package com.focusguard.data

import kotlinx.coroutines.flow.Flow

class FocusRepository(
    private val windowDao: UsageWindowDao,
    private val sessionDao: UsageSessionDao,
) {
    fun observeWindows(): Flow<List<UsageWindow>> = windowDao.observeAll()
    suspend fun windowsOnce(): List<UsageWindow> = windowDao.getAll()
    suspend fun saveWindow(window: UsageWindow) = windowDao.upsert(window)
    suspend fun deleteWindow(window: UsageWindow) = windowDao.delete(window)

    suspend fun recordSession(session: UsageSession) = sessionDao.insert(session)
    fun observeSessions(from: Long, to: Long): Flow<List<UsageSession>> = sessionDao.observeBetween(from, to)
    suspend fun purgeOlderThan(before: Long) = sessionDao.deleteOlderThan(before)
}
