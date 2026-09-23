package com.focusguard.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusguard.app
import com.focusguard.service.FocusMonitorService

/** Reinicia o monitoramento após reiniciar o aparelho ou atualizar o app. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val relevant = intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (relevant && context.app.settings.monitoringEnabled) {
            FocusMonitorService.start(context)
        }
    }
}
