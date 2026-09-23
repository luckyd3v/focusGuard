package com.focusguard.data

object WindowMatcher {
    /**
     * Retorna a janela que vale no instante informado. Uma janela sob demanda ligada sobrepõe
     * as janelas por horário. Se houver sobreposição, vence a mais restritiva (menor limite).
     */
    fun match(windows: List<UsageWindow>, epochMillis: Long): UsageWindow? =
        onDemand(windows) ?: scheduled(windows, epochMillis)

    /** Janela sob demanda ligada no momento, se houver. */
    fun onDemand(windows: List<UsageWindow>): UsageWindow? =
        windows.filter { it.isOnDemandActive }.minByOrNull { it.limitMinutes }

    /** Janela por horário ativa no instante informado. */
    fun scheduled(windows: List<UsageWindow>, epochMillis: Long): UsageWindow? =
        windows.filter { it.isActiveAt(epochMillis) }.minByOrNull { it.limitMinutes }
}
