package com.focusguard.service

import android.app.KeyguardManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.focusguard.app
import com.focusguard.data.UsageSession
import com.focusguard.data.UsageWindow
import com.focusguard.data.WindowMatcher
import com.focusguard.data.limitMs
import com.focusguard.util.TimeFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Serviço em primeiro plano que:
 *  1. escuta ACTION_USER_PRESENT / SCREEN_ON (desbloqueio) e SCREEN_OFF (bloqueio);
 *  2. cronometra cada sessão desbloqueio → bloqueio;
 *  3. associa a sessão à janela de uso ativa e exibe o overlay quando o limite estoura;
 *  4. grava a sessão no banco ao bloquear.
 *
 * Esses broadcasts de tela só podem ser recebidos por receivers registrados em tempo de
 * execução, por isso o serviço precisa permanecer vivo.
 */
class FocusMonitorService : Service() {

    private class ActiveSession(
        val startedAt: Long,
        var window: UsageWindow? = null,
        var limitAnchor: Long = startedAt,
        var nextAlertAt: Long? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var overlay: OverlayController
    private lateinit var keyguard: KeyguardManager
    private lateinit var power: PowerManager

    private var windows: List<UsageWindow> = emptyList()
    private var session: ActiveSession? = null
    private var ticker: Job? = null
    private var lastHeartbeatWrite = 0L
    private var lastNotificationUpdate = 0L
    /** Fim de estimativa para o qual já perguntamos se o usuário quer desligar a janela. */
    private var promptedEstimateEnd: Long? = null

    /**
     * No Android 14+ esses broadcasts podem chegar com segundos de atraso, fora de ordem entre si
     * (SCREEN_ON/OFF são urgentes, USER_PRESENT não) ou ser descartados quando outro do mesmo
     * grupo os substitui. Por isso servem só de gatilho: o estado real é sempre lido do sistema.
     */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            syncWithDevice()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        overlay = OverlayController(this)
        keyguard = getSystemService(KeyguardManager::class.java)
        power = getSystemService(PowerManager::class.java)

        startInForeground()
        LiveSessionState.mutableServiceRunning.value = true

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        // Mantém um cache das janelas; edições na UI refletem na sessão em andamento.
        scope.launch {
            app.repository.observeWindows().collect { list ->
                windows = list
                val now = System.currentTimeMillis()
                session?.let { refreshWindow(it, now) }
                checkEstimate(now)
                // Os botões da notificação dependem das janelas sob demanda.
                updateNotification(force = true)
            }
        }

        restoreOrStart()
        startTicker()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        Notifications.cancelEstimateExceeded(this)
        ticker?.cancel()
        overlay.hide()
        // Se o usuário desligou o monitoramento, encerra a sessão agora.
        // Caso contrário (sistema matou o serviço), deixa a sessão salva para ser retomada.
        if (!app.settings.monitoringEnabled) {
            // Sem atualizar a notificação: o serviço já saiu do primeiro plano e um notify()
            // aqui recriaria uma notificação "Aguardando..." órfã, sem serviço por trás.
            session?.let { endSession(it, System.currentTimeMillis(), updateNotification = false) }
            session = null
        }
        LiveSessionState.mutableCurrent.value = null
        LiveSessionState.mutableServiceRunning.value = false
        scope.cancel()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- ciclo da sessão

    private fun isUnlockedNow(): Boolean = power.isInteractive && !keyguard.isKeyguardLocked

    /** Recupera uma sessão interrompida (processo morto) ou inicia uma se a tela já está desbloqueada. */
    private fun restoreOrStart() {
        val settings = app.settings
        val pendingStart = settings.activeSessionStart
        if (pendingStart > 0) {
            if (isUnlockedNow()) {
                beginSession(pendingStart)
                return
            }
            // A tela foi bloqueada enquanto o serviço estava morto: fecha no último heartbeat.
            val end = settings.lastHeartbeat.coerceAtLeast(pendingStart)
            settings.clearActiveSession()
            app.appScope.launch {
                val window = WindowMatcher.match(app.repository.windowsOnce(), pendingStart)
                buildRecord(pendingStart, end, window, pendingStart)?.let { app.repository.recordSession(it) }
            }
        }
        if (isUnlockedNow()) beginSession(System.currentTimeMillis())
    }

    /**
     * Abre ou fecha a sessão conforme o estado atual do aparelho. Chamado a cada broadcast e a
     * cada segundo pelo cronômetro, de modo que um evento atrasado, fora de ordem ou perdido
     * não deixa o serviço preso em "Aguardando" nem encerra uma sessão que está em uso.
     */
    private fun syncWithDevice() {
        when {
            !power.isInteractive -> onLocked()
            session == null && !keyguard.isKeyguardLocked -> beginSession(System.currentTimeMillis())
        }
    }

    private fun beginSession(startedAt: Long) {
        if (session != null) return
        val now = System.currentTimeMillis()
        val s = ActiveSession(startedAt)
        session = s
        app.settings.activeSessionStart = startedAt
        app.settings.lastHeartbeat = now
        lastHeartbeatWrite = now
        refreshWindow(s, now)
        publish()
        updateNotification(force = true)
    }

    private fun onLocked() {
        val s = session ?: return
        session = null
        overlay.hide()
        endSession(s, System.currentTimeMillis())
    }

    private fun endSession(s: ActiveSession, endedAt: Long, updateNotification: Boolean = true) {
        app.settings.clearActiveSession()
        LiveSessionState.mutableCurrent.value = null
        Notifications.cancelLimitAlert(this)
        buildRecord(s.startedAt, endedAt, s.window, s.limitAnchor)?.let { record ->
            app.appScope.launch { app.repository.recordSession(record) }
        }
        if (updateNotification) updateNotification(force = true)
    }

    private fun buildRecord(start: Long, end: Long, window: UsageWindow?, limitAnchor: Long): UsageSession? {
        if (end - start < FocusConfig.MIN_SESSION_MS) return null
        return UsageSession(
            windowId = window?.id,
            windowName = window?.name,
            limitMinutes = window?.limitMinutes,
            startTime = start,
            endTime = end,
            exceeded = window != null && end - limitAnchor > window.limitMs,
        )
    }

    /**
     * Define/atualiza a janela associada à sessão:
     *  - uma janela sob demanda ligada sobrepõe todas as outras, inclusive no meio da sessão;
     *  - senão mantém a janela por horário atual se ela ainda existir (edições de limite são aplicadas);
     *  - senão usa a janela ativa no momento do desbloqueio;
     *  - senão, se uma janela começar durante a sessão, passa a contar o limite a partir dali.
     */
    private fun refreshWindow(s: ActiveSession, now: Long) {
        val old = s.window
        val kept = old?.takeIf { !it.onDemand }
            ?.let { o -> windows.firstOrNull { it.id == o.id && it.enabled && !it.onDemand } }
        val matched = WindowMatcher.onDemand(windows)
            ?: kept
            ?: WindowMatcher.scheduled(windows, s.startedAt)
            ?: WindowMatcher.scheduled(windows, now)
        if (matched == old) return

        s.window = matched
        when {
            matched == null -> s.nextAlertAt = null
            old != null && old.id == matched.id && old.activatedAt == matched.activatedAt -> {
                if (old.limitMinutes != matched.limitMinutes) {
                    s.nextAlertAt = s.limitAnchor + matched.limitMs
                }
            }
            else -> {
                s.limitAnchor = limitAnchorFor(matched, switching = old != null, s.startedAt, now)
                s.nextAlertAt = s.limitAnchor + matched.limitMs
            }
        }
        // A nova janela pode ter limite maior (ou nenhum): tira o alerta que não vale mais.
        if (overlay.isShowing && s.nextAlertAt.let { it == null || now < it }) overlay.hide()
        publish()
        if (old != null) updateNotification(force = true)
    }

    /**
     * A partir de quando o limite de [window] é contado nesta sessão:
     *  - sob demanda: desde que foi ligada (ou desde o desbloqueio, se já estava ligada);
     *  - troca de janela no meio da sessão (ex.: janela sob demanda desligada): a partir de agora;
     *  - senão: desde o desbloqueio, se a janela já valia naquele momento.
     */
    private fun limitAnchorFor(window: UsageWindow, switching: Boolean, startedAt: Long, now: Long): Long = when {
        window.onDemand -> maxOf(startedAt, window.activatedAt ?: now)
        switching -> now
        window.isActiveAt(startedAt) -> startedAt
        else -> now
    }

    private fun publish() {
        LiveSessionState.mutableCurrent.value = session?.let {
            LiveSession(
                startedAt = it.startedAt,
                windowName = it.window?.name,
                limitMinutes = it.window?.limitMinutes,
                limitAnchor = it.limitAnchor,
                windowId = it.window?.id,
                onDemand = it.window?.onDemand == true,
            )
        }
    }

    // ---------------------------------------------------------------- cronômetro e alerta

    /**
     * Roda enquanto o serviço existir. Com a tela apagada o aparelho entra em suspensão e o
     * delay() não dispara, então o custo fora de uso é praticamente nulo.
     */
    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                tick()
                delay(1_000)
            }
        }
    }

    private fun tick() {
        syncWithDevice()
        val now = System.currentTimeMillis()
        checkEstimate(now)
        val s = session ?: return

        refreshWindow(s, now)

        val window = s.window
        val alertAt = s.nextAlertAt
        if (window != null && alertAt != null && now >= alertAt && !overlay.isShowing) {
            showLimitAlert(s, window, now)
        }
        if (overlay.isShowing) overlay.update(now - s.startedAt)

        if (now - lastHeartbeatWrite >= HEARTBEAT_INTERVAL_MS) {
            app.settings.lastHeartbeat = now
            lastHeartbeatWrite = now
        }
        updateNotification(force = false)
    }

    /**
     * Quando o tempo desde que a janela sob demanda foi ligada passa da estimativa do usuário,
     * pergunta (uma vez por estimativa) se ele quer desligá-la. Estender ou desligar remove a pergunta.
     */
    private fun checkEstimate(now: Long) {
        val window = WindowMatcher.onDemand(windows)
        val end = window?.estimatedEndAt
        if (window != null && end != null && now >= end) {
            if (promptedEstimateEnd != end) {
                promptedEstimateEnd = end
                Notifications.showEstimateExceeded(this, window)
                vibrate()
            }
        } else if (promptedEstimateEnd != null) {
            promptedEstimateEnd = null
            Notifications.cancelEstimateExceeded(this)
        }
    }

    private fun showLimitAlert(s: ActiveSession, window: UsageWindow, now: Long) {
        val shown = overlay.show(
            windowName = window.name,
            limitMinutes = window.limitMinutes,
            elapsedMs = now - s.startedAt,
            snoozeMinutes = FocusConfig.SNOOZE_MINUTES,
            onStop = {
                overlay.hide()
                s.nextAlertAt = System.currentTimeMillis() + FocusConfig.REALERT_AFTER_STOP_MS
                goHome()
            },
            onSnooze = {
                overlay.hide()
                s.nextAlertAt = System.currentTimeMillis() + FocusConfig.SNOOZE_MINUTES * 60_000L
            },
        )
        if (!shown) {
            // Sem permissão de overlay: usa notificação de alta prioridade como alternativa.
            Notifications.showLimitExceeded(this, window.name, window.limitMinutes)
            s.nextAlertAt = now + FocusConfig.SNOOZE_MINUTES * 60_000L
        }
        vibrateLimitAlert()
    }

    private fun goHome() {
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(home) }
    }

    private fun vibrator(): Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Vibrator::class.java)
    }

    private fun vibrate() {
        runCatching { vibrator()?.vibrate(VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE)) }
    }

    /**
     * Três pulsos fortes quando o limite estoura, para assustar mesmo. Usa a categoria de alarme
     * para não ser silenciada como vibração de toque/notificação comum.
     */
    private fun vibrateLimitAlert() {
        val vibrator = vibrator() ?: return
        val timings = longArrayOf(0, 500, 200, 500, 200, 500)
        val effect = if (vibrator.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(timings, intArrayOf(0, 255, 0, 255, 0, 255), -1)
        } else {
            VibrationEffect.createWaveform(timings, -1)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            } else {
                val audio = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, audio)
            }
        }
    }

    // ---------------------------------------------------------------- notificação

    private fun startInForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            Notifications.MONITOR_ID,
            Notifications.buildMonitor(this, notificationText(), monitorActions()),
            type,
        )
    }

    private fun updateNotification(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastNotificationUpdate < NOTIFICATION_INTERVAL_MS) return
        lastNotificationUpdate = now
        Notifications.updateMonitor(this, notificationText(), monitorActions())
    }

    /** Atalhos para ligar/desligar janelas sob demanda direto da notificação. */
    private fun monitorActions(): List<NotificationCompat.Action> {
        WindowMatcher.onDemand(windows)?.let { return listOf(Notifications.deactivateAction(this, it)) }
        return windows
            .filter { it.onDemand && it.enabled }
            .take(MAX_ACTIVATE_ACTIONS)
            .map { Notifications.activateAction(this, it) }
    }

    private fun notificationText(): String {
        val s = session ?: return WindowMatcher.onDemand(windows)
            ?.let { "Aguardando o próximo desbloqueio · ${it.name} ligada" }
            ?: "Aguardando o próximo desbloqueio"
        val now = System.currentTimeMillis()
        val window = s.window
            ?: return "Em uso há ${TimeFormat.duration(now - s.startedAt)} (fora das janelas)"
        val used = now - s.limitAnchor
        return if (used < window.limitMs) {
            "${window.name}: restam ${TimeFormat.duration(window.limitMs - used)} de ${window.limitMinutes} min"
        } else {
            "${window.name}: limite de ${window.limitMinutes} min excedido"
        }
    }

    companion object {
        private const val ACTION_STOP = "com.focusguard.action.STOP"
        private const val HEARTBEAT_INTERVAL_MS = 15_000L
        private const val NOTIFICATION_INTERVAL_MS = 15_000L
        private const val MAX_ACTIVATE_ACTIONS = 3

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, FocusMonitorService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FocusMonitorService::class.java))
        }
    }
}
