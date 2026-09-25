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

    /**
     * Ligar [target] afrouxa o limite em vigor? Sim se ele tiver limite maior que o da janela que
     * vale agora (sem janela em vigor, ligar só acrescenta um limite).
     */
    fun activationLoosens(target: UsageWindow, windows: List<UsageWindow>, epochMillis: Long): Boolean {
        val current = match(windows.filter { it.id != target.id }, epochMillis) ?: return false
        return target.limitMinutes > current.limitMinutes
    }

    /**
     * Salvar [edited] no lugar de [original] afrouxa uma janela por horário ativa? Mudar horário,
     * dias, virar sob demanda ou aumentar o limite afrouxa; renomear ou reduzir o limite, não.
     */
    fun editLoosens(original: UsageWindow, edited: UsageWindow): Boolean {
        if (original.id == 0L || original.onDemand || !original.enabled) return false
        return edited.onDemand ||
            edited.startMinuteOfDay != original.startMinuteOfDay ||
            edited.endMinuteOfDay != original.endMinuteOfDay ||
            edited.daysMask != original.daysMask ||
            edited.limitMinutes > original.limitMinutes
    }

    /** Janela por horário ativa no instante informado. */
    fun scheduled(windows: List<UsageWindow>, epochMillis: Long): UsageWindow? =
        windows.filter { it.isActiveAt(epochMillis) }.minByOrNull { it.limitMinutes }
}
