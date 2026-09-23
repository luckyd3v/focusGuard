package com.focusguard.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.service.LiveSession
import com.focusguard.util.TimeFormat
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ptBR: Locale = Locale.forLanguageTag("pt-BR")
private val dateFormatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", ptBR)

@Composable
fun DashboardScreen(vm: MainViewModel) {
    val stats by vm.dayStats.collectAsStateWithLifecycle()
    val date by vm.selectedDate.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val monitoring by vm.monitoringEnabled.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val currentLive = live

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle("Seu uso", "Tempo de tela entre cada desbloqueio e o bloqueio seguinte") }

        if (!monitoring) {
            item {
                NoticeCard(
                    "Monitoramento desligado",
                    "Ative o monitoramento na aba Configurar para registrar seus desbloqueios.",
                )
            }
        }

        item {
            DateSelector(
                date = date,
                today = today,
                onPrevious = vm::previousDay,
                onNext = vm::nextDay,
                onToday = vm::goToToday,
            )
        }

        if (date == today && currentLive != null) {
            item { LiveSessionCard(currentLive) }
        }

        item { DaySummary(stats) }

        if (stats.perWindow.isEmpty()) {
            item {
                Text(
                    "Nenhuma janela configurada para este dia e nenhum desbloqueio registrado.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp).fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            items(stats.perWindow, key = { it.key }) { WindowStatCard(it) }
        }
    }
}

@Composable
private fun DateSelector(
    date: LocalDate,
    today: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    val label = when (date) {
        today -> "Hoje"
        today.minusDays(1) -> "Ontem"
        else -> date.format(dateFormatter).replaceFirstChar { it.titlecase(ptBR) }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Dia anterior")
        }
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = date != today, onClick = onToday)
                .padding(vertical = 8.dp),
        )
        IconButton(onClick = onNext, enabled = date.isBefore(today)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Próximo dia")
        }
    }
}

@Composable
private fun LiveSessionCard(live: LiveSession) {
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
            Text("Desbloqueio atual", style = MaterialTheme.typography.labelLarge)
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

@Composable
private fun DaySummary(stats: DayStats) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(20.dp)) {
            Metric("Tempo total registrado", TimeFormat.duration(stats.totalMs), Modifier.weight(1f))
            Metric("Desbloqueios", stats.unlocks.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun WindowStatCard(stat: WindowStat) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(stat.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        stat.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (stat.exceeded > 0) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ) {
                        Text(
                            if (stat.exceeded == 1) "1 vez acima" else "${stat.exceeded} vezes acima",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row {
                Metric("Tempo total", TimeFormat.duration(stat.totalMs), Modifier.weight(1f))
                Metric(
                    if (stat.unlocks == 1) "Desbloqueio" else "Desbloqueios",
                    stat.unlocks.toString(),
                    Modifier.weight(1f),
                )
                Metric(
                    "Média",
                    if (stat.unlocks > 0) TimeFormat.duration(stat.totalMs / stat.unlocks) else "—",
                    Modifier.weight(1f),
                )
            }
        }
    }
}
