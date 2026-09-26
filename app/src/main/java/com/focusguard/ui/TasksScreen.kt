package com.focusguard.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.R
import com.focusguard.data.Tag
import com.focusguard.data.Task
import com.focusguard.data.TaskKind
import com.focusguard.data.TaskOccurrence
import com.focusguard.data.UsageWindow
import com.focusguard.util.TimeFormat

private const val MAX_TASK_TITLE = 60

/** Atalhos de estimativa para afazeres (mais curtos que os das janelas sob demanda). */
private val taskEstimatePresets = listOf(5, 10, 15, 30, 60)

/** Afazeres cotidianos (todo dia, opcionalmente ligados a uma janela) e pontuais (modelos reutilizáveis). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(vm: MainViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val kind = if (tab == 0) TaskKind.DAILY else TaskKind.ONE_OFF
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Task?>(null) }
    var deleting by remember { mutableStateOf<Task?>(null) }
    val windows by vm.windows.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
            ScreenTitle(
                "Afazeres",
                when (tab) {
                    0 -> "Repetem todo dia. Associe a uma janela para vê-los na aba Uso enquanto ela vale."
                    1 -> "Cadastre uma vez e adicione à lista sempre que precisar."
                    else -> "Links compartilhados com o FocusGuard. Organize com tags e filtre por tag ou título."
                },
            )
        }
        PrimaryTabRow(selectedTabIndex = tab, modifier = Modifier.padding(top = 8.dp)) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Cotidianos") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Pontuais") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Links") })
        }
        Box(Modifier.fillMaxSize()) {
            when (tab) {
                0 -> DailyTab(vm, windows, onEdit = { editing = it }, onDelete = { deleting = it })
                1 -> OneOffTab(vm, onEdit = { editing = it }, onDelete = { deleting = it })
                else -> LinksTab(vm)
            }
            // Links chegam pelo "Compartilhar" de outros apps, não por aqui.
            if (tab != 2) {
                ExtendedFloatingActionButton(
                    onClick = { creating = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Novo afazer") },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                )
            }
        }
    }

    if (creating) {
        TaskEditorDialog(
            kind = kind,
            initial = null,
            windows = windows,
            onDismiss = { creating = false },
            onSave = { title, windowId, addNow, estimate ->
                vm.createTask(title, kind, windowId, addNow, estimate)
                creating = false
            },
        )
    }
    editing?.let { task ->
        TaskEditorDialog(
            kind = task.kind,
            initial = task,
            windows = windows,
            onDismiss = { editing = null },
            onSave = { title, windowId, _, estimate ->
                vm.updateTask(task.copy(title = title, windowId = windowId, estimateMinutes = estimate))
                editing = null
            },
        )
    }
    deleting?.let { task ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Excluir \"${task.title}\"?") },
            text = { Text("O que você já concluiu continua no histórico.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteTask(task)
                    deleting = null
                }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }
}

// ---------------------------------------------------------------- cotidianos

@Composable
private fun DailyTab(
    vm: MainViewModel,
    windows: List<UsageWindow>,
    onEdit: (Task) -> Unit,
    onDelete: (Task) -> Unit,
) {
    val tasks by vm.dailyTasks.collectAsStateWithLifecycle()
    val today by vm.todayDaily.collectAsStateWithLifecycle()
    val byTask = today.associateBy { it.taskId }
    val done = tasks.count { byTask[it.id]?.done == true }

    TaskList {
        if (tasks.isEmpty()) {
            item { EmptyText("Nenhum afazer cotidiano. Ex.: \"Beber água\", \"Revisar a agenda\".") }
        } else {
            item {
                val remaining = tasks.filter { byTask[it.id]?.done != true }.sumOf { it.estimateMinutes ?: 0 }
                Text(
                    "Hoje: $done de ${tasks.size} concluídos" +
                        if (remaining > 0) " · faltam cerca de ${TimeFormat.duration(remaining * 60_000L)}" else "",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                ListCard {
                    tasks.forEachIndexed { index, task ->
                        if (index > 0) ItemDivider()
                        val occurrence = byTask[task.id]
                        TaskRow(
                            title = task.title,
                            subtitle = listOfNotNull(
                                task.estimateMinutes?.let { TimeFormat.duration(it * 60_000L) },
                                windows.firstOrNull { it.id == task.windowId }?.name ?: "Sem janela associada",
                            ).joinToString(" · "),
                            done = occurrence?.done == true,
                            onCheck = occurrence?.let { o -> { vm.setDone(o, it) } },
                        ) {
                            IconButton(onClick = { onEdit(task) }) { Icon(Icons.Filled.Edit, contentDescription = "Editar") }
                            IconButton(onClick = { onDelete(task) }) { Icon(Icons.Filled.Delete, contentDescription = "Excluir") }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- pontuais

@Composable
private fun OneOffTab(
    vm: MainViewModel,
    onEdit: (Task) -> Unit,
    onDelete: (Task) -> Unit,
) {
    val templates by vm.oneOffTasks.collectAsStateWithLifecycle()
    val allOccurrences by vm.oneOffOccurrences.collectAsStateWithLifecycle()
    val occurrences = allOccurrences.filter { it.url == null }

    TaskList {
        item { SectionTitle("Para fazer") }
        item {
            if (occurrences.isEmpty()) {
                EmptyText("Nada na lista. Toque em \"Adicionar\" num modelo abaixo ou compartilhe um link com o FocusGuard.")
            } else {
                ListCard {
                    occurrences.forEachIndexed { index, occurrence ->
                        if (index > 0) ItemDivider()
                        TaskRow(
                            title = occurrence.title,
                            subtitle = occurrence.linkSubtitle(),
                            done = occurrence.done,
                            onCheck = { vm.setDone(occurrence, it) },
                        ) {
                            OpenLinkButton(occurrence.url)
                            IconButton(onClick = { vm.deleteOccurrence(occurrence) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Tirar da lista")
                            }
                        }
                    }
                }
            }
        }

        item { SectionTitle("Modelos") }
        item {
            if (templates.isEmpty()) {
                EmptyText("Nenhum modelo. Ex.: \"Pagar boleto\", \"Levar o carro à revisão\".")
            } else {
                ListCard {
                    templates.forEachIndexed { index, task ->
                        if (index > 0) ItemDivider()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    task.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                task.estimateMinutes?.let {
                                    Text(
                                        TimeFormat.duration(it * 60_000L),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            TextButton(onClick = { vm.addOneOff(task) }) { Text("Adicionar") }
                            IconButton(onClick = { onEdit(task) }) { Icon(Icons.Filled.Edit, contentDescription = "Editar") }
                            IconButton(onClick = { onDelete(task) }) { Icon(Icons.Filled.Delete, contentDescription = "Excluir") }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- peças comuns

@Composable
private fun TaskList(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun ListCard(content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { content() }
    }
}

@Composable
private fun ItemDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

/** Linha com caixa de seleção; concluído fica riscado. [onCheck] nulo desabilita a caixa. */
@Composable
internal fun TaskRow(
    title: String,
    subtitle: String?,
    done: Boolean,
    onCheck: ((Boolean) -> Unit)?,
    actions: @Composable () -> Unit = {},
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)) {
        Checkbox(checked = done, onCheckedChange = onCheck, enabled = onCheck != null)
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        actions()
    }
}

// ---------------------------------------------------------------- editor

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskEditorDialog(
    kind: Int,
    initial: Task?,
    windows: List<UsageWindow>,
    onDismiss: () -> Unit,
    onSave: (title: String, windowId: Long?, addNow: Boolean, estimateMinutes: Int?) -> Unit,
) {
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var estimateText by remember { mutableStateOf(initial?.estimateMinutes?.toString().orEmpty()) }
    // Opcional: em branco = sem estimativa.
    val estimate = estimateText.toIntOrNull()
    val estimateValid = estimateText.isEmpty() || (estimate != null && estimate in 1..1440)
    var windowId by remember { mutableStateOf(initial?.windowId) }
    var addNow by remember { mutableStateOf(true) }
    val daily = kind == TaskKind.DAILY

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    initial != null -> "Editar afazer"
                    daily -> "Novo afazer cotidiano"
                    else -> "Novo afazer pontual"
                }
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.limitChars(MAX_TASK_TITLE) },
                    label = { Text("Afazer") },
                    supportingText = { Text("${title.charCount()}/$MAX_TASK_TITLE") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Column {
                    Text("Tempo estimado (opcional)", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        taskEstimatePresets.forEach { preset ->
                            FilterChip(
                                selected = estimate == preset,
                                onClick = { estimateText = if (estimate == preset) "" else preset.toString() },
                                label = { Text(TimeFormat.duration(preset * 60_000L)) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = estimateText,
                        onValueChange = { v -> estimateText = v.filter(Char::isDigit).take(4) },
                        label = { Text("Minutos") },
                        singleLine = true,
                        isError = !estimateValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (daily) {
                    Column {
                        Text("Janela associada", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = windowId == null, onClick = { windowId = null }, label = { Text("Nenhuma") })
                            windows.forEach { w ->
                                FilterChip(selected = windowId == w.id, onClick = { windowId = w.id }, label = { Text(w.name) })
                            }
                        }
                        Text(
                            "Enquanto a janela estiver em vigor, o afazer aparece na aba Uso.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (initial == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = addNow, onCheckedChange = { addNow = it })
                        Text("Adicionar à lista agora", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && estimateValid,
                onClick = { onSave(title.trim(), windowId, addNow, estimate) },
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Card da aba Uso com um grupo de tarefas do dia; pendentes primeiro, com separadores. */
@Composable
fun TaskGroupCard(
    title: String,
    tasks: List<TaskOccurrence>,
    onCheck: (TaskOccurrence, Boolean) -> Unit,
    tags: Map<Long, Tag> = emptyMap(),
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(4.dp))
            tasks.sortedBy { it.done }.forEachIndexed { index, occurrence ->
                if (index > 0) ItemDivider()
                Box(Modifier.padding(horizontal = 8.dp)) {
                    TaskRow(
                        occurrence.title,
                        subtitle = occurrence.linkSubtitle(),
                        done = occurrence.done,
                        onCheck = { onCheck(occurrence, it) },
                    ) { OpenLinkButton(occurrence.url) }
                }
                occurrence.tagId?.let(tags::get)?.let { tag ->
                    Box(Modifier.padding(start = 60.dp, bottom = 10.dp)) { TagPill(tag) }
                }
            }
        }
    }
}

/** "12 min · youtube.com" para tarefas que vieram de um link ou têm duração estimada. */
internal fun TaskOccurrence.linkSubtitle(): String? {
    val parts = listOfNotNull(
        estimateMinutes?.let { TimeFormat.duration(it * 60_000L) },
        url?.let { Uri.parse(it).host?.removePrefix("www.") },
    )
    return parts.joinToString(" · ").ifEmpty { null }
}

/** Abre o link da tarefa no app certo (YouTube, navegador...). Some se não houver link. */
@Composable
internal fun OpenLinkButton(url: String?) {
    if (url == null) return
    val context = LocalContext.current
    IconButton(onClick = {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }) {
        Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = "Abrir link")
    }
}
