package com.focusguard.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.focusguard.R
import com.focusguard.data.UsageWindow
import com.focusguard.receiver.OnDemandActionReceiver
import com.focusguard.ui.MainActivity

object Notifications {
    const val CHANNEL_MONITOR = "focusguard_monitor"
    const val CHANNEL_ALERTS = "focusguard_alerts"
    const val MONITOR_ID = 1001
    const val ALERT_ID = 1002
    const val ESTIMATE_ID = 1003
    const val EXTEND_MINUTES = 15

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MONITOR, "Monitoramento", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Mostra o tempo do desbloqueio atual enquanto o FocusGuard está ativo"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "Alertas de limite", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Usado quando o overlay não pode ser exibido"
            }
        )
    }

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    // ------------------------------------------------------------ ações das janelas sob demanda

    /** Abre o app já pedindo a estimativa de duração para ligar a janela. */
    fun activateAction(context: Context, window: UsageWindow): NotificationCompat.Action {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_ACTIVATE_WINDOW_ID, window.id)
        val pending = PendingIntent.getActivity(
            context,
            requestCode(window, 1),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Action(0, "Ligar ${window.name}", pending)
    }

    fun deactivateAction(context: Context, window: UsageWindow, label: String = "Desligar ${window.name}") =
        NotificationCompat.Action(0, label, receiverIntent(context, window, OnDemandActionReceiver.ACTION_DEACTIVATE, 2))

    fun extendAction(context: Context, window: UsageWindow) =
        NotificationCompat.Action(
            0,
            "Mais $EXTEND_MINUTES min",
            receiverIntent(context, window, OnDemandActionReceiver.ACTION_EXTEND, 3),
        )

    private fun receiverIntent(context: Context, window: UsageWindow, action: String, code: Int): PendingIntent {
        val intent = Intent(context, OnDemandActionReceiver::class.java)
            .setAction(action)
            .putExtra(OnDemandActionReceiver.EXTRA_WINDOW_ID, window.id)
            .putExtra(OnDemandActionReceiver.EXTRA_MINUTES, EXTEND_MINUTES)
        return PendingIntent.getBroadcast(
            context,
            requestCode(window, code),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun requestCode(window: UsageWindow, action: Int): Int = (window.id * 10 + action).toInt()

    // ------------------------------------------------------------ notificações

    fun buildMonitor(
        context: Context,
        text: String,
        actions: List<NotificationCompat.Action> = emptyList(),
    ): Notification =
        NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("FocusGuard ativo")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent(context))
            .apply { actions.forEach(::addAction) }
            .build()

    @SuppressLint("MissingPermission")
    fun updateMonitor(context: Context, text: String, actions: List<NotificationCompat.Action>) {
        context.getSystemService(NotificationManager::class.java)
            .notify(MONITOR_ID, buildMonitor(context, text, actions))
    }

    /** Pergunta se o usuário quer desligar a janela sob demanda cuja estimativa se esgotou. */
    @SuppressLint("MissingPermission")
    fun showEstimateExceeded(context: Context, window: UsageWindow) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${window.name}: tempo estimado esgotado")
            .setContentText("Você estimou ${window.estimateMinutes} min. Quer desligar a janela sob demanda?")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openAppIntent(context))
            .addAction(deactivateAction(context, window, label = "Desligar"))
            .addAction(extendAction(context, window))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(ESTIMATE_ID, notification)
    }

    fun cancelEstimateExceeded(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ESTIMATE_ID)
    }

    @SuppressLint("MissingPermission")
    fun showLimitExceeded(context: Context, windowName: String, limitMinutes: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Tempo esgotado")
            .setContentText("Você passou de $limitMinutes min neste desbloqueio ($windowName).")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(ALERT_ID, notification)
    }

    fun cancelLimitAlert(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ALERT_ID)
    }
}
