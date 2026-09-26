package com.focusguard.data

/** Grupos de sugestão, em ordem de prioridade. */
enum class FreeTimeCategory(val label: String) {
    WINDOW("Da janela atual"),
    INDEPENDENT("Independente"),
    LINK("Link"),
}

data class FreeTimeSuggestion(val task: TaskOccurrence, val category: FreeTimeCategory)

/**
 * Escolhe o que fazer no tempo livre informado pelo usuário, na ordem de prioridade:
 *  1. afazeres cotidianos associados à janela ativa;
 *  2. independentes: cotidianos sem janela e pontuais comuns;
 *  3. links salvos.
 * Dentro de cada grupo, as tarefas maiores que ainda cabem vêm primeiro, aproveitando melhor o
 * tempo. Só entram tarefas pendentes com estimativa (sem ela não dá para saber se cabem).
 */
object FreeTimePlanner {

    fun categoryOf(task: TaskOccurrence, currentWindowId: Long?): FreeTimeCategory? = when {
        task.url != null -> FreeTimeCategory.LINK
        task.kind == TaskKind.DAILY && task.windowId != null ->
            FreeTimeCategory.WINDOW.takeIf { task.windowId == currentWindowId } // de outra janela: fica de fora
        else -> FreeTimeCategory.INDEPENDENT
    }

    fun suggest(pending: List<TaskOccurrence>, currentWindowId: Long?, freeMinutes: Int): List<FreeTimeSuggestion> {
        var remaining = freeMinutes
        val out = mutableListOf<FreeTimeSuggestion>()
        val candidates = pending.filter { !it.done && (it.estimateMinutes ?: 0) > 0 }
        for (category in FreeTimeCategory.entries) {
            candidates
                .filter { categoryOf(it, currentWindowId) == category }
                .sortedWith(compareByDescending<TaskOccurrence> { it.estimateMinutes }.thenBy { it.createdAt })
                .forEach { task ->
                    val minutes = task.estimateMinutes!!
                    if (minutes <= remaining) {
                        out += FreeTimeSuggestion(task, category)
                        remaining -= minutes
                    }
                }
        }
        return out
    }
}
