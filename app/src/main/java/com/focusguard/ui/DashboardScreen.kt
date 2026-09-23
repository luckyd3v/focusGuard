package com.focusguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.R
import com.focusguard.service.LiveSession
import com.focusguard.service.Notifications
import com.focusguard.util.TimeFormat
import kotlinx.coroutines.delay

/** O que está acontecendo agora: desbloqueio atual e atalhos das janelas sob demanda. */
@Composable
fun DashboardScreen(vm: MainViewModel) {
    val live by vm.live.collectAsStateWithLifecycle()
    val monitoring by vm.monitoringEnabled.collectAsStateWithLifecycle()
    val windows by vm.windows.collectAsStateWithLifecycle()
    val currentLive = live
    val onDemand = windows.filter { it.onDemand && it.enabled }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle("Seu uso", "Desbloqueio atual e janelas sob demanda") }

        if (!monitoring) {
            item {
                NoticeCard(
                    "Monitoramento desligado",
                    "Ative o monitoramento na aba Configurar para registrar seus desbloqueios.",
                )
            }
        }

        if (currentLive != null) {
            item { LiveSessionCard(currentLive, onDeactivateOnDemand = { vm.deactivateOnDemand(it) }) }
        } else if (monitoring) {
            item {
                Text(
                    "Nenhum desbloqueio em andamento.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp).fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (onDemand.isNotEmpty()) {
            item {
                OnDemandCard(
                    windows = onDemand,
                    onActivate = vm::requestActivation,
                    onDeactivate = { vm.deactivateOnDemand(it) },
                    onExtend = { vm.extendEstimate(it, Notifications.EXTEND_MINUTES) },
                )
            }
        }
    }
}

@Composable
private fun LiveSessionCard(live: LiveSession, onDeactivateOnDemand: (windowId: Long) -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(live.startedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val elapsed = (now - live.startedAt).coerceAtLeast(0)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Desbloqueio atual", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                val windowId = live.windowId
                if (live.onDemand && windowId != null) {
                    IconButton(onClick = { onDeactivateOnDemand(windowId) }) {
                        Icon(
                            painterResource(R.drawable.ic_power),
                            contentDescription = "Desligar ${live.windowName}",
                        )
                    }
                }
            }
            Text(
                TimeFormat.clock(elapsed),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                live.windowName?.let { "Contando para: $it" } ?: "Fora das janelas configuradas",
                style = MaterialTheme.typography.bodyMedium,
            )

            val limit = live.limitMinutes
            if (limit != null) {
                val limitMs = limit * 60_000L
                val used = (now - live.limitAnchor).coerceAtLeast(0)
                val over = used >= limitMs
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { (used.toFloat() / limitMs).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (over) "Limite de $limit min excedido"
                    else "Restam ${TimeFormat.duration(limitMs - used)} de $limit min",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
