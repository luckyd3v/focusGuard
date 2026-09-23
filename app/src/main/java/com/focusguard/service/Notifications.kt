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
import com.focusguard.ui.MainActivity

object Notifications {
    const val CHANNEL_MONITOR = "focusguard_monitor"
    const val CHANNEL_ALERTS = "focusguard_alerts"
    const val MONITOR_ID = 1001
    const val ALERT_ID = 1002

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

    fun buildMonitor(context: Context, text: String): Notification =
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
            .build()

    @SuppressLint("MissingPermission")
    fun updateMonitor(context: Context, text: String) {
        context.getSystemService(NotificationManager::class.java)
            .notify(MONITOR_ID, buildMonitor(context, text))
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
