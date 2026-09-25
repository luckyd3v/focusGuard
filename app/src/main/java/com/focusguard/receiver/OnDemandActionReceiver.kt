package com.focusguard.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.focusguard.app
import com.focusguard.data.WindowMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/**
 * Botões das notificações para ligar (janelas com estimativa fixa) ou desligar a janela sob demanda
 * e para estender a estimativa, sem abrir o app. O serviço observa as janelas no banco e reage sozinho à mudança.
 */
class OnDemandActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val windowId = intent.getLongExtra(EXTRA_WINDOW_ID, -1L)
        if (windowId < 0) return
        val repository = context.app.repository
        val pending = goAsync()
        context.app.appScope.launch {
            try {
                when (intent.action) {
                    ACTION_ACTIVATE -> {
                        // O botão pode ter sido criado antes de a situação mudar: confere de novo.
                        val windows = repository.windowsOnce()
                        val target = windows.firstOrNull { it.id == windowId }
                        val now = System.currentTimeMillis()
                        if (target != null && WindowMatcher.activationLoosens(target, windows, now)) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Abra o FocusGuard para confirmar com o código.", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            repository.activateOnDemand(windowId, now, intent.getIntExtra(EXTRA_MINUTES, 60))
                        }
                    }
                    ACTION_DEACTIVATE -> repository.deactivateOnDemand(windowId)
                    ACTION_EXTEND -> repository.extendEstimate(windowId, intent.getIntExtra(EXTRA_MINUTES, 15))
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ACTIVATE = "com.focusguard.action.ACTIVATE_ON_DEMAND"
        const val ACTION_DEACTIVATE = "com.focusguard.action.DEACTIVATE_ON_DEMAND"
        const val ACTION_EXTEND = "com.focusguard.action.EXTEND_ON_DEMAND"
        const val EXTRA_WINDOW_ID = "window_id"
        const val EXTRA_MINUTES = "minutes"
    }
}
