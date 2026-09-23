package com.focusguard.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Preferências simples + estado da sessão em andamento (para recuperar após o processo morrer). */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("focusguard_prefs", Context.MODE_PRIVATE)

    private val monitoring = MutableStateFlow(prefs.getBoolean(KEY_MONITORING, false))
    val monitoringFlow: StateFlow<Boolean> = monitoring

    var monitoringEnabled: Boolean
        get() = monitoring.value
        set(value) {
            prefs.edit().putBoolean(KEY_MONITORING, value).apply()
            monitoring.value = value
        }

    /** Início da sessão em andamento (0 = nenhuma). */
    var activeSessionStart: Long
        get() = prefs.getLong(KEY_SESSION_START, 0L)
        set(value) {
            prefs.edit().putLong(KEY_SESSION_START, value).apply()
        }

    /** Último instante em que o serviço confirmou que a tela estava em uso. */
    var lastHeartbeat: Long
        get() = prefs.getLong(KEY_HEARTBEAT, 0L)
        set(value) {
            prefs.edit().putLong(KEY_HEARTBEAT, value).apply()
        }

    fun clearActiveSession() {
        prefs.edit().remove(KEY_SESSION_START).remove(KEY_HEARTBEAT).apply()
    }

    private companion object {
        const val KEY_MONITORING = "monitoring_enabled"
        const val KEY_SESSION_START = "active_session_start"
        const val KEY_HEARTBEAT = "active_session_heartbeat"
    }
}
