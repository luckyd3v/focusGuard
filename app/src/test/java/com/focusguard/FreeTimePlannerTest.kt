package com.focusguard

import com.focusguard.data.FreeTimeCategory
import com.focusguard.data.FreeTimePlanner
import com.focusguard.data.TaskKind
import com.focusguard.data.TaskOccurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeTimePlannerTest {

    private var nextId = 1L
    private fun task(
        title: String,
        minutes: Int?,
        kind: Int = TaskKind.ONE_OFF,
        windowId: Long? = null,
        url: String? = null,
        done: Boolean = false,
    ) = TaskOccurrence(
        id = nextId++, taskId = null, title = title, kind = kind, windowId = windowId, day = 0,
        createdAt = nextId, estimateMinutes = minutes, url = url, completedAt = if (done) 1L else null,
    )

    private val janelaAtual = 7L

    private fun titles(pending: List<TaskOccurrence>, minutes: Int) =
        FreeTimePlanner.suggest(pending, janelaAtual, minutes).map { it.task.title }

    @Test
    fun `respeita a ordem janela atual, independentes e links`() {
        val pending = listOf(
            task("Vídeo", 5, url = "https://youtu.be/x"),
            task("Pagar boleto", 5),
            task("Revisar e-mails", 5, kind = TaskKind.DAILY, windowId = janelaAtual),
            task("Beber água", 5, kind = TaskKind.DAILY),
        )
        assertEquals(listOf("Revisar e-mails", "Pagar boleto", "Beber água", "Vídeo"), titles(pending, 20))
        // Com pouco tempo, fica só o que tem mais prioridade.
        assertEquals(listOf("Revisar e-mails"), titles(pending, 7))
    }

    @Test
    fun `dentro do grupo usa as maiores que ainda cabem`() {
        val pending = listOf(task("A", 10), task("B", 25), task("C", 20))
        assertEquals(listOf("B"), titles(pending, 30)) // 25 cabe; depois só sobram 5
        assertEquals(listOf("B", "C"), titles(pending, 45))
    }

    @Test
    fun `ignora concluidas, sem estimativa e cotidianos de outras janelas`() {
        val pending = listOf(
            task("Feita", 5, done = true),
            task("Sem estimativa", null),
            task("Outra janela", 5, kind = TaskKind.DAILY, windowId = 99),
        )
        assertTrue(titles(pending, 60).isEmpty())
    }

    @Test
    fun `categorias`() {
        assertEquals(FreeTimeCategory.WINDOW, FreeTimePlanner.categoryOf(task("x", 5, TaskKind.DAILY, janelaAtual), janelaAtual))
        assertEquals(FreeTimeCategory.INDEPENDENT, FreeTimePlanner.categoryOf(task("x", 5, TaskKind.DAILY), janelaAtual))
        assertEquals(FreeTimeCategory.LINK, FreeTimePlanner.categoryOf(task("x", 5, url = "https://a.b"), janelaAtual))
    }
}
