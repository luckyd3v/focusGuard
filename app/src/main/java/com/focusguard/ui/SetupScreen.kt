package com.focusguard.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.util.Permissions

@Composable
fun SetupScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val monitoring by vm.monitoringEnabled.collectAsStateWithLifecycle()
    val running by vm.serviceRunning.collectAsStateWithLifecycle()

    // Reavalia as permissões sempre que o usuário volta das telas de configuração do sistema.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val overlayOk = remember(refresh) { Permissions.canDrawOverlays(context) }
    val notificationsOk = remember(refresh) { Permissions.notificationsGranted(context) }
    val batteryOk = remember(refresh) { Permissions.ignoringBatteryOptimizations(context) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refresh++ }

    fun open(intent: Intent) {
        runCatching { context.startActivity(intent) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle("Configurar", "Permissões necessárias para medir e limitar o uso")

        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (monitoring) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Monitoramento", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        when {
                            monitoring && running -> "Ativo. Cada desbloqueio está sendo cronometrado."
                            monitoring -> "Iniciando…"
                            else -> "Desligado. Nenhum desbloqueio é registrado."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(checked = monitoring, onCheckedChange = { vm.setMonitoring(it) })
            }
        }

        if (monitoring && !overlayOk) {
            NoticeCard(
                "Alerta limitado",
                "Sem a permissão de sobreposição, o aviso de excesso aparece só como notificação.",
            )
        }

        PermissionCard(
            title = "Sobrepor a outros apps",
            description = "Permite cobrir o app em uso com o alerta quando o tempo do desbloqueio acaba.",
            granted = overlayOk,
            actionLabel = "Permitir",
            onAction = { open(Permissions.overlaySettingsIntent(context)) },
        )

        PermissionCard(
            title = "Notificações",
            description = "Mostra o tempo restante do desbloqueio atual na barra de notificações.",
            granted = notificationsOk,
            actionLabel = "Permitir",
            onAction = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        )

        PermissionCard(
            title = "Rodar sem restrição de bateria",
            description = "Evita que o Android encerre o FocusGuard em segundo plano e perca sessões.",
            granted = batteryOk,
            actionLabel = "Liberar",
            onAction = { open(Permissions.batteryOptimizationIntent(context)) },
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Como funciona", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Uma sessão começa quando você desbloqueia o celular e termina quando a tela apaga. " +
                        "A sessão conta para a janela ativa no momento do desbloqueio. Se passar do limite, " +
                        "um alerta cobre a tela: você pode parar ou continuar por mais alguns minutos, " +
                        "e o excesso fica registrado no histórico.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            if (granted) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Concedida",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            } else {
                FilledTonalButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}
