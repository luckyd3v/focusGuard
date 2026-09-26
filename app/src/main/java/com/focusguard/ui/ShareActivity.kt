package com.focusguard.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.focusguard.app
import com.focusguard.data.Tag
import com.focusguard.ui.theme.FocusGuardTheme
import com.focusguard.util.LinkInfo
import com.focusguard.util.LinkInspector
import com.focusguard.util.TimeFormat
import java.time.LocalDate
import kotlinx.coroutines.launch

private const val MAX_TITLE = 60

/**
 * Recebe o "Compartilhar" de outros apps (links de vídeos, sites, textos) e salva como tarefa
 * pontual. Tenta avaliar a duração sozinho; se não conseguir, pede a estimativa ao usuário.
 */
class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action != Intent.ACTION_SEND) {
            finish()
            return
        }
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: intent.getStringExtra(Intent.EXTRA_TITLE)
        val url = LinkInspector.extractUrl(text)
        // Sem link, o próprio texto compartilhado vira o título da tarefa.
        val initialTitle = subject?.takeIf { it.isNotBlank() }
            ?: text?.takeIf { url == null }?.trim()
            ?: ""

        setContent {
            FocusGuardTheme {
                val tags by app.repository.observeTags().collectAsState(initial = emptyList())
                ShareDialog(
                    url = url,
                    initialTitle = initialTitle.limitChars(MAX_TITLE),
                    tags = tags,
                    onCreateTag = { name, color -> app.repository.createTag(name, color) },
                    onSave = { title, minutes, tagId ->
                        app.appScope.launch {
                            app.repository.addLinkTask(title, url, minutes, LocalDate.now(), tagId)
                        }
                        Toast.makeText(this, "Salvo em Afazeres › Pontuais", Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onDismiss = ::finish,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShareDialog(
    url: String?,
    initialTitle: String,
    tags: List<Tag>,
    onCreateTag: suspend (name: String, color: Int) -> Long,
    onSave: (title: String, minutes: Int, tagId: Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var tagId by remember { mutableStateOf<Long?>(null) }
    var creatingTag by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(initialTitle) }
    var minutesText by remember { mutableStateOf("") }
    var info by remember { mutableStateOf<LinkInfo?>(null) }
    var loading by remember { mutableStateOf(url != null) }

    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        val result = LinkInspector.inspect(url)
        info = result
        if (title.isBlank()) result.title?.let { title = it.limitChars(MAX_TITLE) }
        if (minutesText.isEmpty()) result.minutes?.let { minutesText = it.toString() }
        loading = false
    }

    val minutes = minutesText.toIntOrNull()
    val minutesValid = minutes != null && minutes in 1..1440
    val evaluated = info?.minutes != null

    // Um diálogo por vez: enquanto cria a tag, o de salvar sai da frente (sem perder o que foi preenchido).
    if (!creatingTag) AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Salvar no FocusGuard") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (url != null) {
                    Text(
                        Uri.parse(url).host?.removePrefix("www.") ?: url,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.limitChars(MAX_TITLE) },
                    label = { Text("Tarefa") },
                    placeholder = { Text(if (loading) "Buscando o título…" else "Ex.: Assistir ao vídeo") },
                    supportingText = { Text("${title.charCount()}/$MAX_TITLE") },
                    modifier = Modifier.fillMaxWidth(),
                )

                when {
                    loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Avaliando a duração…", style = MaterialTheme.typography.bodyMedium)
                    }
                    evaluated -> Text(
                        when (info?.source) {
                            LinkInfo.Source.VIDEO -> "Vídeo de ${TimeFormat.duration(info!!.minutes!! * 60_000L)}. Ajuste se quiser."
                            else -> "Leitura de cerca de ${TimeFormat.duration(info!!.minutes!! * 60_000L)}. Ajuste se quiser."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    else -> {
                        Text(
                            if (url != null) "Não foi possível avaliar a duração. Quanto tempo você estima que essa atividade vai levar?"
                            else "Quanto tempo você estima que essa atividade vai levar?",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            estimatePresets.forEach { preset ->
                                FilterChip(
                                    selected = minutes == preset,
                                    onClick = { minutesText = preset.toString() },
                                    label = { Text(TimeFormat.duration(preset * 60_000L)) },
                                )
                            }
                        }
                    }
                }

                if (url != null) {
                    Text("Tag", style = MaterialTheme.typography.labelLarge)
                    TagSelector(tags, tagId, onSelect = { tagId = it }, onNewTag = { creatingTag = true })
                }

                if (!loading) {
                    OutlinedTextField(
                        value = minutesText,
                        onValueChange = { v -> minutesText = v.filter(Char::isDigit).take(4) },
                        label = { Text("Duração estimada (minutos)") },
                        singleLine = true,
                        isError = minutesText.isNotEmpty() && !minutesValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !loading && title.isNotBlank() && minutesValid,
                onClick = { minutes?.let { onSave(title.trim(), it, tagId) } },
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )

    if (creatingTag) {
        TagEditorDialog(
            initial = null,
            onSave = { name, color ->
                creatingTag = false
                scope.launch { tagId = onCreateTag(name, color) }
            },
            onDismiss = { creatingTag = false },
        )
    }
}
