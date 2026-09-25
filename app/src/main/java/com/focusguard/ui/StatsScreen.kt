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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.data.TaskKind
import com.focusguard.data.TaskOccurrence
import com.focusguard.util.TimeFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ptBR: Locale = Locale.forLanguageTag("pt-BR")
private val dateFormatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", ptBR)

/** Histórico de uso por dia: total, desbloqueios e resumo por janela. */
@Composable
fun StatsScreen(vm: MainViewModel) {
    val stats by vm.dayStats.collectAsStateWithLifecycle()
    val date by vm.selectedDate.collectAsStateWithLifecycle()
    val tasks by vm.dayTasks.collectAsStateWithLifecycle()
    val today = LocalDate.now()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle("Estatísticas", "Tempo de tela entre cada desbloqueio e o bloqueio seguinte") }

        item {
            DateSelector(
                date = date,
                today = today,
                onPrevious = vm::previousDay,
                onNext = vm::nextDay,
                onToday = vm::goToToday,
            )
        }

        item { DaySummary(stats) }

        if (tasks.isNotEmpty()) {
            item { TasksHistoryCard(tasks) }
        }

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

/** Afazeres do dia: cotidianos concluídos do total e pontuais concluídos no dia. */
@Composable
private fun TasksHistoryCard(tasks: List<TaskOccurrence>) {
    val daily = tasks.filter { it.kind == TaskKind.DAILY }
    val oneOff = tasks.filter { it.kind == TaskKind.ONE_OFF }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Afazeres", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Row {
                Metric("Cotidianos concluídos", "${daily.count { it.done }} de ${daily.size}", Modifier.weight(1f))
                Metric("Pontuais concluídos", oneOff.size.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            (daily.sortedBy { !it.done } + oneOff).forEach { occurrence ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        if (occurrence.done) "✓" else "○",
                        color = if (occurrence.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(24.dp),
                    )
                    Text(
                        occurrence.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (occurrence.done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (occurrence.kind == TaskKind.ONE_OFF) {
                        Text("pontual", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
