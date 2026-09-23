package com.focusguard.data

object WindowMatcher {
    /**
     * Retorna a janela ativa no instante informado. Se houver sobreposição,
     * vence a mais restritiva (menor limite).
     */
    fun match(windows: List<UsageWindow>, epochMillis: Long): UsageWindow? =
        windows.filter { it.isActiveAt(epochMillis) }.minByOrNull { it.limitMinutes }
}
