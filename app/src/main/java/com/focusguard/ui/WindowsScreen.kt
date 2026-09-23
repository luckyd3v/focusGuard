package com.focusguard.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.data.UsageWindow
import com.focusguard.data.daysLabel
import com.focusguard.data.rangeLabel
import com.focusguard.util.TimeFormat
import java.time.DayOfWeek

@Composable
fun WindowsScreen(vm: MainViewModel) {
    val windows by vm.windows.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<UsageWindow?>(null) }
    var deleting by remember { mutableStateOf<UsageWindow?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ScreenTitle(
                    "Janelas de uso",
                    "Em cada janela, cada desbloqueio tem um tempo máximo. Se as janelas se sobrepõem, vale a de menor limite. " +
                        "Uma janela sob demanda, enquanto ligada, substitui as janelas por horário.",
                )
            }
            if (windows.isEmpty()) {
                item {
                    Text(
                        "Crie sua primeira janela, por exemplo: Expediente, das 09:00 às 19:00, 10 min por desbloqueio.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
            items(windows, key = { it.id }) { window ->
                WindowCard(
                    window = window,
                    onToggle = {
                        when {
                            !window.onDemand -> vm.setWindowEnabled(window, it)
                            it -> vm.requestActivation(window) // pede a estimativa antes de ligar
                            else -> vm.deactivateOnDemand(window)
                        }
                    },
                    onEdit = { editing = window },
                    onDelete = { deleting = window },
                )
            }
        }

        ExtendedFloatingActionButton(
            onClick = {
                editing = UsageWindow(name = "", startMinuteOfDay = 9 * 60, endMinuteOfDay = 19 * 60, limitMinutes = 10)
            },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Nova janela") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    editing?.let { window ->
        WindowEditorDialog(
            initial = window,
            onDismiss = { editing = null },
            onSave = {
                vm.saveWindow(it)
                editing = null
            },
        )
    }

    deleting?.let { window ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Excluir \"${window.name}\"?") },
            text = { Text("O histórico de uso já registrado continua disponível.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteWindow(window)
                    deleting = null
                }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun WindowCard(
    window: UsageWindow,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(window.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        windowDescription(window),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (window.isOnDemandActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Por horário: habilita/desabilita a janela. Sob demanda: liga/desliga agora.
                Switch(
                    checked = if (window.onDemand) window.isOnDemandActive else window.enabled,
                    onCheckedChange = onToggle,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Até ${window.limitMinutes} min por desbloqueio",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Editar") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Excluir") }
            }
        }
    }
}

private fun windowDescription(window: UsageWindow): String {
    if (!window.onDemand) return "${window.rangeLabel()}, ${window.daysLabel().lowercase()}"
    val since = window.activatedAt ?: return "Sob demanda, desligada"
    val until = window.estimatedEndAt?.let { ", estimativa até ${TimeFormat.timeOfDay(it)}" }.orEmpty()
    return "Sob demanda, ligada desde ${TimeFormat.timeOfDay(since)}$until"
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun WindowEditorDialog(
    initial: UsageWindow,
    onDismiss: () -> Unit,
    onSave: (UsageWindow) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var start by remember { mutableIntStateOf(initial.startMinuteOfDay) }
    var end by remember { mutableIntStateOf(initial.endMinuteOfDay) }
    var limitText by remember { mutableStateOf(initial.limitMinutes.toString()) }
    var days by remember { mutableIntStateOf(initial.daysMask) }
    var onDemand by remember { mutableStateOf(initial.onDemand) }

    val limit = limitText.toIntOrNull()
    val limitValid = limit != null && limit in 1..1440
    val valid = name.isNotBlank() && limitValid && (onDemand || days != 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Nova janela" else "Editar janela") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Nome") },
                    placeholder = { Text("Expediente") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Column {
                    Text("Quando vale", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !onDemand, onClick = { onDemand = false }, label = { Text("Por horário") })
                        FilterChip(selected = onDemand, onClick = { onDemand = true }, label = { Text("Sob demanda") })
                    }
                    if (onDemand) {
                        HintText("Você liga e desliga a janela quando quiser. Enquanto ligada, ela substitui as janelas por horário.")
                    }
                }

                if (!onDemand) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TimeButton("Início", start, Modifier.weight(1f)) { start = it }
                        TimeButton("Fim", end, Modifier.weight(1f)) { end = it }
                    }
                    when {
                        end == start -> HintText("Início igual ao fim: a janela vale o dia inteiro.")
                        end < start -> HintText("A janela atravessa a meia-noite e termina no dia seguinte.")
                    }
                }

                OutlinedTextField(
                    value = limitText,
                    onValueChange = { v -> limitText = v.filter(Char::isDigit).take(4) },
                    label = { Text("Limite por desbloqueio (minutos)") },
                    singleLine = true,
                    isError = limitText.isNotEmpty() && !limitValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (!onDemand) Column {
                    Text("Dias da semana", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DayOfWeek.values().forEach { day ->
                            val bit = UsageWindow.dayBit(day)
                            FilterChip(
                                selected = (days and bit) != 0,
                                onClick = { days = days xor bit },
                                label = { Text(TimeFormat.shortDay(day)) },
                            )
                        }
                    }
                    if (days == 0) HintText("Escolha pelo menos um dia.")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            startMinuteOfDay = start,
                            endMinuteOfDay = end,
                            limitMinutes = limit ?: initial.limitMinutes,
                            daysMask = days,
                            onDemand = onDemand,
                            // Em janelas sob demanda o interruptor controla a ativação, não o "enabled".
                            enabled = onDemand || initial.enabled,
                            // Virar janela por horário desliga a ativação sob demanda.
                            activatedAt = if (onDemand) initial.activatedAt else null,
                        )
                    )
                },
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun HintText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun TimeButton(label: String, minuteOfDay: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            TimePickerDialog(
                context,
                { _, hour, minute -> onChange(hour * 60 + minute) },
                minuteOfDay / 60,
                minuteOfDay % 60,
                true,
            ).show()
        },
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(TimeFormat.minuteOfDay(minuteOfDay), style = MaterialTheme.typography.titleMedium)
        }
    }
}
