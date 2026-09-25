package com.focusguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.R
import com.focusguard.data.TaskOccurrence
import com.focusguard.service.LiveSession
import com.focusguard.service.Notifications
import com.focusguard.util.TimeFormat
import kotlinx.coroutines.delay

/** O que está acontecendo agora: desbloqueio atual e atalhos das janelas sob demanda. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(vm: MainViewModel) {
    val live by vm.live.collectAsStateWithLifecycle()
    val monitoring by vm.monitoringEnabled.collectAsStateWithLifecycle()
    val windows by vm.windows.collectAsStateWithLifecycle()
    val currentLive = live
    val onDemand = windows.filter { it.onDemand && it.enabled }
    val todayDaily by vm.todayDaily.collectAsStateWithLifecycle()
    val oneOff by vm.oneOffOccurrences.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    // Categorias da aba Tarefas, na ordem de exibição; categorias vazias não aparecem.
    val unassigned = todayDaily.filter { it.windowId == null }
    val currentWindowId = currentLive?.windowId
    val windowTasks = currentWindowId?.let { id -> todayDaily.filter { it.windowId == id } }.orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle("Seu uso", "Desbloqueio atual, tarefas do dia e janelas sob demanda") }

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

        item {
            PrimaryTabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Tarefas") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Janelas sob demanda") })
            }
        }

        val check = { occurrence: TaskOccurrence, done: Boolean -> vm.setDone(occurrence, done); Unit }
        if (tab == 0) {
            if (unassigned.isEmpty() && windowTasks.isEmpty() && oneOff.isEmpty()) {
                item { EmptyTabText("Não há tarefas planejadas para hoje") }
            }
            if (unassigned.isNotEmpty()) {
                item { TaskGroupCard("Tarefas independentes", unassigned, check) }
            }
            if (windowTasks.isNotEmpty()) {
                item { TaskGroupCard("Tarefas da janela atual · ${currentLive?.windowName.orEmpty()}", windowTasks, check) }
            }
            if (oneOff.isNotEmpty()) {
                item { TaskGroupCard("Tarefas pontuais", oneOff, check) }
            }
        } else {
            item {
                if (onDemand.isEmpty()) {
                    EmptyTabText("Nenhuma janela sob demanda. Crie uma na aba Janelas.")
                } else {
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
}

@Composable
private fun EmptyTabText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 24.dp).fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

/** Degradê do limite: verde-petróleo (calma) → âmbar → vermelho (alerta), em tons claros o bastante
 * para não "sujar" no meio e funcionar nos temas claro e escuro. */
private val limitGradient = listOf(Color(0xFF26A69A), Color(0xFFF6B400), Color(0xFFE53935))

/**
 * Card do desbloqueio atual: título e janela à esquerda, cronômetro à direita e, embaixo, a barra
 * do limite num degradê que vai da calma (verde) ao alerta (âmbar, vermelho).
 */
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
    val colors = MaterialTheme.colorScheme
    val progressColors = limitGradient

    val limit = live.limitMinutes
    val limitMs = limit?.let { it * 60_000L }
    val used = (now - live.limitAnchor).coerceAtLeast(0)
    val over = limitMs != null && used >= limitMs

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Tempo da Sessão",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                WindowChip(
                    text = live.windowName?.let { name -> limit?.let { "$name • ${it}m" } ?: name }
                        ?: "Fora das janelas",
                    alert = over,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    TimeFormat.clock(elapsed),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Light,
                    color = colors.onSurface.copy(alpha = 0.85f),
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                // Só janelas sob demanda podem ser desligadas daqui.
                val windowId = live.windowId
                if (live.onDemand && windowId != null) {
                    IconButton(onClick = { onDeactivateOnDemand(windowId) }, modifier = Modifier.size(44.dp)) {
                        Icon(
                            painterResource(R.drawable.ic_power),
                            contentDescription = "Desligar ${live.windowName}",
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }

            if (limitMs != null) {
                Spacer(Modifier.height(12.dp))
                GradientProgress(
                    fraction = (used.toFloat() / limitMs).coerceIn(0f, 1f),
                    colors = progressColors,
                )
            }
        }
    }
}

/** Pílula com a janela atual; fica vermelha quando o limite foi excedido. */
@Composable
private fun WindowChip(text: String, alert: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = if (alert) colors.errorContainer else colors.primaryContainer,
        contentColor = if (alert) colors.onErrorContainer else colors.onPrimaryContainer,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Icon(painterResource(R.drawable.ic_schedule), contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/**
 * Barra de progresso cujo degradê cobre a largura toda: o trecho preenchido mostra só a parte
 * do degradê até o ponto atual, então a cor indica o quanto do limite já foi usado.
 */
@Composable
private fun GradientProgress(fraction: Float, colors: List<Color>) {
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(
        Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(CircleShape)
            .background(track)
            .drawBehind {
                val width = size.width * fraction
                if (width > 0f) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(colors, startX = 0f, endX = size.width),
                        size = Size(width, size.height),
                        cornerRadius = CornerRadius(size.height / 2),
                    )
                }
            },
    )
}
