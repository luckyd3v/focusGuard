package com.focusguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.focusguard.data.UsageWindow
import com.focusguard.service.Notifications
import com.focusguard.util.TimeFormat
import kotlinx.coroutines.delay

private val estimatePresets = listOf(15, 30, 60, 90, 120)
private const val DEFAULT_ESTIMATE_MINUTES = 60

/** Pergunta por quanto tempo o usuário pretende manter a janela sob demanda ligada. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EstimateDialog(
    window: UsageWindow,
    onConfirm: (minutes: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf((window.estimateMinutes ?: DEFAULT_ESTIMATE_MINUTES).toString()) }
    val minutes = text.toIntOrNull()
    val valid = minutes != null && minutes in 1..1440

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ligar ${window.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Quanto tempo você estima que essa atividade vai durar? Quando esse tempo passar, " +
                        "o FocusGuard pergunta se você quer desligar a janela.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    estimatePresets.forEach { preset ->
                        FilterChip(
                            selected = minutes == preset,
                            onClick = { text = preset.toString() },
                            label = { Text(TimeFormat.duration(preset * 60_000L)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter(Char::isDigit).take(4) },
                    label = { Text("Duração estimada (minutos)") },
                    singleLine = true,
                    isError = text.isNotEmpty() && !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { minutes?.let(onConfirm) }) { Text("Ligar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Atalhos da aba Uso para ligar/desligar janelas sob demanda e responder à estimativa esgotada. */
@Composable
fun OnDemandCard(
    windows: List<UsageWindow>,
    onActivate: (UsageWindow) -> Unit,
    onDeactivate: (UsageWindow) -> Unit,
    onExtend: (UsageWindow) -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(5_000)
        }
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 12.dp)) {
            Text("Janelas sob demanda", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            windows.forEachIndexed { index, window ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                OnDemandRow(window, now, onActivate, onDeactivate, onExtend)
            }
        }
    }
}

@Composable
private fun OnDemandRow(
    window: UsageWindow,
    now: Long,
    onActivate: (UsageWindow) -> Unit,
    onDeactivate: (UsageWindow) -> Unit,
    onExtend: (UsageWindow) -> Unit,
) {
    val active = window.isOnDemandActive
    val end = window.estimatedEndAt
    val exceeded = end != null && now >= end

    // O interruptor já mostra se a janela está ligada; o texto traz só o que é útil.
    Column(Modifier.padding(vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(window.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text(
                    when {
                        !active -> "${window.limitMinutes} min por desbloqueio" +
                            window.fixedEstimateMinutes?.let { " · estimativa de ${TimeFormat.duration(it * 60_000L)}" }.orEmpty()
                        end == null -> "Desde ${TimeFormat.timeOfDay(window.activatedAt!!)}"
                        exceeded -> "Tempo estimado esgotado às ${TimeFormat.timeOfDay(end)}"
                        else -> "Estimativa até ${TimeFormat.timeOfDay(end)} (faltam ${TimeFormat.duration(end - now)})"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (exceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = active,
                onCheckedChange = { if (it) onActivate(window) else onDeactivate(window) },
            )
        }
        if (exceeded) {
            Text(
                "Você estimou ${window.estimateMinutes} min para ${window.name}. Quer desligar a janela?",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, end = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = { onDeactivate(window) }) { Text("Desligar") }
                TextButton(onClick = { onExtend(window) }) { Text("Mais ${Notifications.EXTEND_MINUTES} min") }
            }
        }
    }
}
