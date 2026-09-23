package com.focusguard.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.FocusGuardApp
import com.focusguard.data.UsageSession
import com.focusguard.data.UsageWindow
import com.focusguard.data.durationMs
import com.focusguard.data.scheduleLabel
import com.focusguard.service.FocusMonitorService
import com.focusguard.service.LiveSessionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** Resumo de uso de uma janela em um dia. Ex.: Expediente — 150 min — 3 desbloqueios. */
data class WindowStat(
    val key: String,
    val name: String,
    val subtitle: String,
    val totalMs: Long,
    val unlocks: Int,
    val exceeded: Int,
    val limitMinutes: Int?,
)

data class DayStats(
    val date: LocalDate,
    val totalMs: Long,
    val unlocks: Int,
    val perWindow: List<WindowStat>,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as FocusGuardApp
    private val repository = app.repository

    val windows: StateFlow<List<UsageWindow>> = repository.observeWindows()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate

    val live = LiveSessionState.current
    val serviceRunning = LiveSessionState.serviceRunning
    val monitoringEnabled = app.settings.monitoringFlow

    @OptIn(ExperimentalCoroutinesApi::class)
    val dayStats: StateFlow<DayStats> = _selectedDate
        .flatMapLatest { date ->
            val zone = ZoneId.systemDefault()
            val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val to = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            combine(repository.observeSessions(from, to), repository.observeWindows()) { sessions, windows ->
                buildDayStats(date, sessions, windows)
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DayStats(LocalDate.now(), 0, 0, emptyList()),
        )

    init {
        // Garante que o serviço esteja rodando se o usuário deixou o monitoramento ligado.
        if (app.settings.monitoringEnabled) FocusMonitorService.start(app)
    }

    fun previousDay() {
        _selectedDate.value = _selectedDate.value.minusDays(1)
    }

    fun nextDay() {
        val next = _selectedDate.value.plusDays(1)
        if (!next.isAfter(LocalDate.now())) _selectedDate.value = next
    }

    fun goToToday() {
        _selectedDate.value = LocalDate.now()
    }

    fun saveWindow(window: UsageWindow) = viewModelScope.launch { repository.saveWindow(window) }

    fun setWindowEnabled(window: UsageWindow, enabled: Boolean) =
        viewModelScope.launch { repository.saveWindow(window.copy(enabled = enabled)) }

    // ------------------------------------------------------------ janelas sob demanda

    /** Janela sob demanda aguardando a estimativa de duração para ser ligada. */
    private val _pendingActivation = MutableStateFlow<UsageWindow?>(null)
    val pendingActivation: StateFlow<UsageWindow?> = _pendingActivation

    fun requestActivation(window: UsageWindow) {
        _pendingActivation.value = window
    }

    /** Usado pelo botão "Ligar" da notificação, que só conhece o id. */
    fun requestActivation(windowId: Long) = viewModelScope.launch {
        repository.windowsOnce().firstOrNull { it.id == windowId && it.onDemand }?.let(::requestActivation)
    }

    fun cancelActivation() {
        _pendingActivation.value = null
    }

    fun activateOnDemand(window: UsageWindow, estimateMinutes: Int) = viewModelScope.launch {
        _pendingActivation.value = null
        repository.activateOnDemand(window.id, System.currentTimeMillis(), estimateMinutes)
    }

    fun deactivateOnDemand(window: UsageWindow) = viewModelScope.launch { repository.deactivateOnDemand(window.id) }

    fun extendEstimate(window: UsageWindow, minutes: Int) =
        viewModelScope.launch { repository.extendEstimate(window.id, minutes) }

    fun deleteWindow(window: UsageWindow) = viewModelScope.launch { repository.deleteWindow(window) }

    fun setMonitoring(enabled: Boolean) {
        app.settings.monitoringEnabled = enabled
        if (enabled) FocusMonitorService.start(app) else FocusMonitorService.stop(app)
    }
}

/** Agrupa as sessões do dia por janela de uso. */
internal fun buildDayStats(
    date: LocalDate,
    sessions: List<UsageSession>,
    windows: List<UsageWindow>,
): DayStats {
    val byWindow = sessions.groupBy { it.windowId }
    val stats = mutableListOf<WindowStat>()

    fun stat(key: String, name: String, subtitle: String, list: List<UsageSession>, limit: Int?) =
        WindowStat(
            key = key,
            name = name,
            subtitle = subtitle,
            totalMs = list.sumOf { it.durationMs },
            unlocks = list.size,
            exceeded = list.count { it.exceeded },
            limitMinutes = limit,
        )

    windows
        .filter { w ->
            val scheduledToday = !w.onDemand && w.enabled && w.includesDay(date.dayOfWeek)
            scheduledToday || w.isOnDemandActive || byWindow.containsKey(w.id)
        }
        .forEach { w ->
            stats += stat(
                key = "w${w.id}",
                name = w.name,
                subtitle = "${w.scheduleLabel()} · ${w.limitMinutes} min por desbloqueio",
                list = byWindow[w.id].orEmpty(),
                limit = w.limitMinutes,
            )
        }

    // Sessões de janelas que foram excluídas depois
    byWindow
        .filterKeys { id -> id != null && windows.none { it.id == id } }
        .forEach { (id, list) ->
            stats += stat("d$id", list.first().windowName ?: "Janela excluída", "Janela excluída", list, list.first().limitMinutes)
        }

    byWindow[null]?.let { list ->
        stats += stat("none", "Fora das janelas", "Uso sem limite configurado", list, null)
    }

    return DayStats(
        date = date,
        totalMs = sessions.sumOf { it.durationMs },
        unlocks = sessions.size,
        perWindow = stats,
    )
}
