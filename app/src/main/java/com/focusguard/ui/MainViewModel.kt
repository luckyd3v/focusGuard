package com.focusguard.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.FocusGuardApp
import com.focusguard.data.Tag
import com.focusguard.data.Task
import com.focusguard.data.TaskKind
import com.focusguard.data.TaskOccurrence
import com.focusguard.data.UsageSession
import com.focusguard.data.UsageWindow
import com.focusguard.data.WindowMatcher
import com.focusguard.data.durationMs
import com.focusguard.data.scheduleLabel
import com.focusguard.service.FocusMonitorService
import com.focusguard.service.LiveSessionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
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

    /** Com estimativa fixa liga direto; senão abre o diálogo pedindo a estimativa. */
    /** Janela sob demanda aguardando o código de confirmação (ligá-la afrouxaria o limite atual). */
    private val _pendingUnlock = MutableStateFlow<UsageWindow?>(null)
    val pendingUnlock: StateFlow<UsageWindow?> = _pendingUnlock

    /**
     * Ligar uma janela sob demanda: se ela afrouxa o limite em vigor, pede o código antes; depois,
     * com estimativa fixa liga direto, senão abre o diálogo pedindo a estimativa.
     */
    fun requestActivation(window: UsageWindow) = viewModelScope.launch {
        val loosens = WindowMatcher.activationLoosens(window, repository.windowsOnce(), System.currentTimeMillis())
        if (loosens) _pendingUnlock.value = window else proceedActivation(window)
    }

    fun confirmUnlock() {
        val window = _pendingUnlock.value ?: return
        _pendingUnlock.value = null
        proceedActivation(window)
    }

    fun cancelUnlock() {
        _pendingUnlock.value = null
    }

    private fun proceedActivation(window: UsageWindow) {
        val fixed = window.fixedEstimateMinutes
        if (fixed != null) activateOnDemand(window, fixed) else _pendingActivation.value = window
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

    fun deactivateOnDemand(window: UsageWindow) = deactivateOnDemand(window.id)

    fun deactivateOnDemand(windowId: Long) = viewModelScope.launch { repository.deactivateOnDemand(windowId) }

    fun extendEstimate(window: UsageWindow, minutes: Int) =
        viewModelScope.launch { repository.extendEstimate(window.id, minutes) }

    fun deleteWindow(window: UsageWindow) = viewModelScope.launch { repository.deleteWindow(window) }

    // ------------------------------------------------------------ afazeres

    /** Dia corrente; conferido a cada minuto para virar à meia-noite com o app aberto. */
    private val today: StateFlow<LocalDate> = flow {
        while (true) {
            emit(LocalDate.now())
            delay(60_000)
        }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, LocalDate.now())

    val dailyTasks: StateFlow<List<Task>> = repository.observeTasks(TaskKind.DAILY)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val oneOffTasks: StateFlow<List<Task>> = repository.observeTasks(TaskKind.ONE_OFF)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Ocorrências de hoje dos cotidianos (criadas na virada do dia, se ainda não existirem). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val todayDaily: StateFlow<List<TaskOccurrence>> = today
        .onEach { repository.ensureDailyOccurrences(it) }
        .flatMapLatest { repository.observeDailyOccurrences(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Pontuais em aberto e os concluídos hoje. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val oneOffOccurrences: StateFlow<List<TaskOccurrence>> = today
        .flatMapLatest { repository.observeOneOffOccurrences(startOfDay(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Histórico de afazeres do dia escolhido na tela Estatísticas. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val dayTasks: StateFlow<List<TaskOccurrence>> = _selectedDate
        .flatMapLatest { date -> repository.observeTaskHistory(date, startOfDay(date), startOfDay(date.plusDays(1))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createTask(title: String, kind: Int, windowId: Long?, addNow: Boolean) = viewModelScope.launch {
        val task = Task(title = title, kind = kind, windowId = windowId.takeIf { kind == TaskKind.DAILY })
        repository.createTask(task, today.value, addNow = kind == TaskKind.ONE_OFF && addNow)
    }

    fun updateTask(task: Task) = viewModelScope.launch { repository.updateTask(task, today.value) }

    fun deleteTask(task: Task) = viewModelScope.launch { repository.deleteTask(task, today.value) }

    fun addOneOff(task: Task) = viewModelScope.launch { repository.addOneOff(task, today.value) }

    fun setDone(occurrence: TaskOccurrence, done: Boolean) =
        viewModelScope.launch { repository.setOccurrenceDone(occurrence, done) }

    // ------------------------------------------------------------ links e tags

    val tags: StateFlow<List<Tag>> = repository.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Links salvos em aberto e os concluídos hoje. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val links: StateFlow<List<TaskOccurrence>> = today
        .flatMapLatest { repository.observeLinks(startOfDay(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createTag(name: String, color: Int) = viewModelScope.launch { repository.createTag(name, color) }
    fun updateTag(tag: Tag) = viewModelScope.launch { repository.updateTag(tag) }
    fun deleteTag(tag: Tag) = viewModelScope.launch { repository.deleteTag(tag) }
    fun setTag(occurrence: TaskOccurrence, tagId: Long?) = viewModelScope.launch { repository.setOccurrenceTag(occurrence, tagId) }

    fun deleteOccurrence(occurrence: TaskOccurrence) = viewModelScope.launch { repository.deleteOccurrence(occurrence) }

    private fun startOfDay(date: LocalDate): Long = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

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
        // Trechos de continuação são o mesmo desbloqueio contado em outra janela.
        unlocks = sessions.count { !it.continuation },
        perWindow = stats,
    )
}
