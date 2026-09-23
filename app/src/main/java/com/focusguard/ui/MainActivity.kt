package com.focusguard.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.focusguard.R
import com.focusguard.app
import com.focusguard.ui.theme.FocusGuardTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        // Primeira execução (monitoramento desligado) abre direto na configuração.
        val initialTab = if (app.settings.monitoringEnabled) TAB_USO else TAB_CONFIGURAR
        setContent {
            FocusGuardTheme {
                FocusGuardRoot(initialTab, vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Botão "Ligar ..." da notificação: abre o pedido de estimativa da janela sob demanda. */
    private fun handleIntent(intent: Intent?) {
        val windowId = intent?.getLongExtra(EXTRA_ACTIVATE_WINDOW_ID, -1L) ?: -1L
        if (windowId >= 0) {
            intent?.removeExtra(EXTRA_ACTIVATE_WINDOW_ID)
            vm.requestActivation(windowId)
        }
    }

    companion object {
        const val EXTRA_ACTIVATE_WINDOW_ID = "com.focusguard.extra.ACTIVATE_WINDOW_ID"
    }
}

private data class Tab(val label: String, val icon: @Composable () -> ImageVector)

private const val TAB_USO = 0
private const val TAB_JANELAS = 1
private const val TAB_ESTATISTICAS = 2
private const val TAB_CONFIGURAR = 3

private val tabs = listOf(
    Tab("Uso") { Icons.Filled.Home },
    Tab("Janelas") { Icons.Filled.DateRange },
    Tab("Estatísticas") { ImageVector.vectorResource(R.drawable.ic_bar_chart) },
    Tab("Configurar") { Icons.Filled.Settings },
)

@Composable
private fun FocusGuardRoot(initialTab: Int, vm: MainViewModel) {
    var selected by rememberSaveable { mutableIntStateOf(initialTab) }
    val pendingActivation by vm.pendingActivation.collectAsStateWithLifecycle()
    pendingActivation?.let { window ->
        EstimateDialog(
            window = window,
            onConfirm = { minutes -> vm.activateOnDemand(window, minutes) },
            onDismiss = vm::cancelActivation,
        )
    }
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(tab.icon(), contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selected) {
                TAB_USO -> DashboardScreen(vm)
                TAB_JANELAS -> WindowsScreen(vm)
                TAB_ESTATISTICAS -> StatsScreen(vm)
                else -> SetupScreen(vm)
            }
        }
    }
}
