package com.focusguard.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Snapshot da sessão em andamento, exposto para a UI. */
data class LiveSession(
    val startedAt: Long,
    val windowName: String?,
    val limitMinutes: Int?,
    /** Instante a partir do qual o limite é contado (normalmente = startedAt). */
    val limitAnchor: Long,
    val windowId: Long? = null,
    /** A sessão conta para uma janela sob demanda (que pode ser desligada pelo card). */
    val onDemand: Boolean = false,
)

object LiveSessionState {
    internal val mutableCurrent = MutableStateFlow<LiveSession?>(null)
    val current: StateFlow<LiveSession?> = mutableCurrent

    internal val mutableServiceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = mutableServiceRunning
}

object FocusConfig {
    /** Ao escolher "continuar", o alerta volta depois deste tempo. */
    const val SNOOZE_MINUTES = 5
    /** Ao escolher "Ignorar", se o usuário continuar usando, o alerta volta depois deste tempo. */
    const val REALERT_AFTER_IGNORE_MS = 60_000L
    /** Sessões menores que isso (ex.: tela acesa sem uso real) são descartadas. */
    const val MIN_SESSION_MS = 2_000L
}
