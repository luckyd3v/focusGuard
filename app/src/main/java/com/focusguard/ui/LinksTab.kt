package com.focusguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.data.LinkFilter
import com.focusguard.data.Tag
import com.focusguard.data.TagFilter
import com.focusguard.data.TaskOccurrence

private const val MAX_TAG_NAME = 20

/** Cores oferecidas para as tags (ARGB). */
internal val tagPalette = listOf(
    0xFF26A69A, 0xFF1E88E5, 0xFF5E35B1, 0xFFD81B60, 0xFFE53935,
    0xFFFB8C00, 0xFFF6B400, 0xFF43A047, 0xFF6D4C41, 0xFF757575,
).map { it.toInt() }

/** Aba Links de Afazeres: busca por título, filtro por tag e tags coloridas por link. */
@Composable
fun LinksTab(vm: MainViewModel) {
    val links by vm.links.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val tagsById = tags.associateBy { it.id }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by remember { mutableStateOf<TagFilter>(TagFilter.All) }
    var managing by remember { mutableStateOf(false) }
    var tagging by remember { mutableStateOf<TaskOccurrence?>(null) }

    // Tag excluída enquanto estava selecionada no filtro: volta para "Todas".
    val current = filter
    if (current is TagFilter.Only && current.tagId !in tagsById) filter = TagFilter.All
    val shown = LinkFilter.apply(links, query, filter)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(60) },
                    placeholder = { Text("Buscar pelo título") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = "Limpar busca") }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { managing = true }) { Text("Tags") }
            }
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                FilterChip(selected = filter == TagFilter.All, onClick = { filter = TagFilter.All }, label = { Text("Todas") })
                tags.forEach { tag ->
                    FilterChip(
                        selected = filter == TagFilter.Only(tag.id),
                        onClick = { filter = TagFilter.Only(tag.id) },
                        label = { Text(tag.name) },
                        leadingIcon = { ColorDot(tag.color) },
                    )
                }
                FilterChip(selected = filter == TagFilter.Untagged, onClick = { filter = TagFilter.Untagged }, label = { Text("Sem tag") })
            }
        }

        item {
            when {
                links.isEmpty() -> EmptyLinks("Nenhum link salvo. Em qualquer app, toque em Compartilhar → Salvar no FocusGuard.")
                shown.isEmpty() -> EmptyLinks("Nenhum link corresponde à busca ou ao filtro.")
                else -> Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        shown.forEachIndexed { index, link ->
                            if (index > 0) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                            }
                            LinkRow(
                                link = link,
                                tag = link.tagId?.let(tagsById::get),
                                onCheck = { vm.setDone(link, it) },
                                onTag = { tagging = link },
                                onRemove = { vm.deleteOccurrence(link) },
                            )
                        }
                    }
                }
            }
        }
    }

    tagging?.let { link ->
        TagPickerDialog(
            tags = tags,
            selected = link.tagId,
            onSelect = { tagId ->
                vm.setTag(link, tagId)
                tagging = null
            },
            onCreate = { name, color -> vm.createTag(name, color) },
            onDismiss = { tagging = null },
        )
    }
    if (managing) {
        ManageTagsDialog(
            tags = tags,
            onCreate = { name, color -> vm.createTag(name, color) },
            onUpdate = vm::updateTag,
            onDelete = vm::deleteTag,
            onDismiss = { managing = false },
        )
    }
}

@Composable
private fun LinkRow(
    link: TaskOccurrence,
    tag: Tag?,
    onCheck: (Boolean) -> Unit,
    onTag: () -> Unit,
    onRemove: () -> Unit,
) {
    Column {
        TaskRow(
            title = link.title,
            subtitle = link.linkSubtitle(),
            done = link.done,
            onCheck = onCheck,
        ) {
            OpenLinkButton(link.url)
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = "Tirar da lista") }
        }
        Box(Modifier.padding(start = 52.dp, bottom = 10.dp)) {
            if (tag != null) TagPill(tag, onClick = onTag) else AddTagPill(onClick = onTag)
        }
    }
}

// ---------------------------------------------------------------- peças de tag

/** Pílula colorida com o nome da tag. */
@Composable
internal fun TagPill(tag: Tag, onClick: (() -> Unit)? = null) {
    val color = Color(tag.color)
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.16f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = if (onClick != null) Modifier.clip(CircleShape).clickable(onClick = onClick) else Modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
            ColorDot(tag.color)
            Spacer(Modifier.width(6.dp))
            Text(tag.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun AddTagPill(onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Tag", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
internal fun ColorDot(color: Int, size: Int = 10) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Color(color)))
}

@Composable
private fun EmptyLinks(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

/** Escolha da tag de um link (ou nenhuma), com atalho para criar uma nova. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagSelector(
    tags: List<Tag>,
    selected: Long?,
    onSelect: (Long?) -> Unit,
    onNewTag: () -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("Nenhuma") })
        tags.forEach { tag ->
            FilterChip(
                selected = selected == tag.id,
                onClick = { onSelect(tag.id) },
                label = { Text(tag.name) },
                leadingIcon = { ColorDot(tag.color) },
            )
        }
        FilterChip(
            selected = false,
            onClick = onNewTag,
            label = { Text("Nova tag") },
            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
        )
    }
}

@Composable
private fun TagPickerDialog(
    tags: List<Tag>,
    selected: Long?,
    onSelect: (Long?) -> Unit,
    onCreate: (String, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    if (!creating) AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tag do link") },
        text = { TagSelector(tags, selected, onSelect = onSelect, onNewTag = { creating = true }) },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
    if (creating) {
        TagEditorDialog(
            initial = null,
            onSave = { name, color ->
                onCreate(name, color)
                creating = false
            },
            onDismiss = { creating = false },
        )
    }
}

/** Lista de tags com criar, editar e excluir. */
@Composable
private fun ManageTagsDialog(
    tags: List<Tag>,
    onCreate: (String, Int) -> Unit,
    onUpdate: (Tag) -> Unit,
    onDelete: (Tag) -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf<Tag?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Tag?>(null) }

    if (!creating && editing == null && deleting == null) AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tags") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (tags.isEmpty()) {
                    Text("Nenhuma tag ainda.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                tags.forEach { tag ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ColorDot(tag.color, size = 16)
                        Spacer(Modifier.width(12.dp))
                        Text(tag.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        IconButton(onClick = { editing = tag }) { Icon(Icons.Filled.Edit, contentDescription = "Editar ${tag.name}") }
                        IconButton(onClick = { deleting = tag }) { Icon(Icons.Filled.Delete, contentDescription = "Excluir ${tag.name}") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { creating = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Nova tag")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )

    if (creating) {
        TagEditorDialog(null, onSave = { n, c -> onCreate(n, c); creating = false }, onDismiss = { creating = false })
    }
    editing?.let { tag ->
        TagEditorDialog(tag, onSave = { n, c -> onUpdate(tag.copy(name = n, color = c)); editing = null }, onDismiss = { editing = null })
    }
    deleting?.let { tag ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Excluir a tag \"${tag.name}\"?") },
            text = { Text("Os links continuam salvos, só ficam sem tag.") },
            confirmButton = { TextButton(onClick = { onDelete(tag); deleting = null }) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }
}

/** Nome (até 20 caracteres) e cor da tag. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagEditorDialog(initial: Tag?, onSave: (name: String, color: Int) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var color by remember { mutableStateOf(initial?.color ?: tagPalette.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nova tag" else "Editar tag") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.limitChars(MAX_TAG_NAME) },
                    label = { Text("Nome") },
                    placeholder = { Text("Estudo") },
                    supportingText = { Text("${name.charCount()}/$MAX_TAG_NAME") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Cor", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    tagPalette.forEach { c ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .clickable { color = c },
                        ) {
                            if (c == color) Icon(Icons.Filled.Check, contentDescription = "Selecionada", tint = Color.White)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), color) }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
