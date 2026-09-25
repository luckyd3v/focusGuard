package com.focusguard

import android.app.Application
import android.content.Context
import com.focusguard.data.AppDatabase
import com.focusguard.data.FocusRepository
import com.focusguard.data.SettingsStore
import com.focusguard.service.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class FocusGuardApp : Application() {

    /** Escopo que sobrevive a Activities e ao serviço (usado para gravações no banco). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var repository: FocusRepository
        private set
    lateinit var settings: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.build(this)
        repository = FocusRepository(db.windowDao(), db.sessionDao(), db.taskDao())
        settings = SettingsStore(this)
        Notifications.createChannels(this)

        appScope.launch {
            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(RETENTION_DAYS)
            repository.purgeOlderThan(cutoff)
        }
    }

    companion object {
        const val RETENTION_DAYS = 90L
    }
}

val Context.app: FocusGuardApp
    get() = applicationContext as FocusGuardApp
