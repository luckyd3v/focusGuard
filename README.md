# FocusGuard

App Android (Kotlin + Jetpack Compose) que cronometra cada desbloqueio do celular até o bloqueio
seguinte, associa esse tempo a **janelas de uso** configuráveis e cobre a tela com um alerta quando
o limite por desbloqueio é ultrapassado.

Exemplo: janela **Expediente**, 09:00 às 19:00, **10 min por desbloqueio**. Ao final do dia a aba
"Uso" mostra algo como: *Expediente: tempo total 2h 30min, 3 desbloqueios, média 50 min, 3 vezes acima*.

## Como compilar

1. Abra a pasta `FocusGuard` no Android Studio (Ladybug ou mais recente) e aguarde o Gradle sincronizar.
   O projeto não inclui o `gradle-wrapper.jar`; o Android Studio baixa o Gradle 8.9 automaticamente.
   Pela linha de comando, rode uma vez `gradle wrapper` e depois `./gradlew assembleDebug`.
2. Rode em um aparelho físico (Android 8.0 / API 26 ou superior).
3. No app, aba **Configurar**: conceda "Sobrepor a outros apps", notificações e a liberação de
   bateria, e ligue o **Monitoramento**.
4. Na aba **Janelas**, ajuste a janela de exemplo "Expediente" ou crie novas.

Testes unitários da lógica de janelas e estatísticas: `./gradlew test`.

## Como funciona

| Peça | Arquivo | Papel |
|---|---|---|
| Serviço em primeiro plano | `service/FocusMonitorService.kt` | Registra um receiver para `USER_PRESENT`, `SCREEN_ON` e `SCREEN_OFF`, cronometra a sessão, decide a janela, dispara o alerta e grava a sessão ao bloquear |
| Overlay | `service/OverlayController.kt` + `res/layout/overlay_alert.xml` | Janela `TYPE_APPLICATION_OVERLAY` em tela cheia sobre o app em uso |
| Janelas | `data/UsageWindow.kt` | Horário (inclusive atravessando a meia-noite), dias da semana, limite |
| Sessões | `data/UsageSession.kt` | Início, fim, janela associada e se excedeu o limite (Room) |
| Estatísticas | `ui/MainViewModel.kt` (`buildDayStats`) | Tempo total, desbloqueios, média e excessos por janela e dia |
| Reinício | `receiver/BootReceiver.kt` | Religa o monitoramento após reiniciar o aparelho ou atualizar o app |

Regras de negócio:

- **Sessão** = do desbloqueio até a tela apagar. Sessões com menos de 2 s são descartadas.
- O tempo conta para a janela **em vigor a cada momento**. Se a janela muda no meio do desbloqueio
  (horário que começa ou termina, janela sob demanda ligada/desligada, janela editada ou excluída),
  o trecho anterior é gravado na janela antiga e o cronômetro e o limite recomeçam do zero na nova.
  Os trechos seguintes ao primeiro não contam como novos desbloqueios nas estatísticas.
- Janelas sobrepostas: vale a de **menor limite**.
- **Janelas sob demanda** não têm horário: valem enquanto o usuário as mantém ligadas e, nesse
  período, substituem as janelas por horário. Só uma fica ligada por vez. Ao ligar, o app pede a
  duração estimada (ou usa a estimativa fixa configurada) e, quando ela passa, pergunta se o
  usuário quer desligar a janela.
- Ao estourar o limite, o overlay oferece **"Parar e ir para a tela inicial"** (se o uso continuar,
  o alerta volta em 1 min) ou **"Continuar por mais 5 min"**. Valores em `FocusConfig`.
- Sem permissão de overlay, o alerta vira uma notificação de alta prioridade.
- Se o sistema matar o processo no meio de uma sessão, ela é recuperada pelo último "heartbeat"
  (gravado a cada 15 s).
- O histórico é mantido por 90 dias.

## Limitações conhecidas

- Os broadcasts de tela só chegam a receivers registrados em tempo de execução, por isso o
  serviço precisa ficar vivo (notificação fixa). Fabricantes como Xiaomi, Samsung e Huawei podem
  exigir, além da liberação de bateria, ativar "Início automático" nas configurações do sistema.
- O app não bloqueia o aparelho à força: isso exigiria torná-lo administrador do dispositivo.
  O overlay cobre a tela e leva o usuário à tela inicial.
- Publicar na Play Store com `FOREGROUND_SERVICE_SPECIAL_USE` e `SYSTEM_ALERT_WINDOW` exige
  justificar o uso na declaração de permissões do Play Console.
