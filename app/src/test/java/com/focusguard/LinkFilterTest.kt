package com.focusguard

import com.focusguard.data.LinkFilter
import com.focusguard.data.TagFilter
import com.focusguard.data.TaskKind
import com.focusguard.data.TaskOccurrence
import org.junit.Assert.assertEquals
import org.junit.Test

class LinkFilterTest {

    private fun link(id: Long, title: String, tagId: Long?) = TaskOccurrence(
        id = id, taskId = null, title = title, kind = TaskKind.ONE_OFF, day = 0, url = "https://x.com/$id", tagId = tagId,
    )

    private val links = listOf(
        link(1, "Aula de Programação", tagId = 10),
        link(2, "Receita de pão", tagId = 20),
        link(3, "Documentário sobre o espaço", tagId = null),
    )

    private fun ids(query: String, tag: TagFilter) = LinkFilter.apply(links, query, tag).map { it.id }

    @Test
    fun `busca pelo titulo ignora maiusculas e acentos`() {
        assertEquals(listOf(1L), ids("programacao", TagFilter.All))
        assertEquals(listOf(2L), ids("PÃO", TagFilter.All))
        assertEquals(listOf(1L, 2L, 3L), ids("  ", TagFilter.All))
    }

    @Test
    fun `filtro por tag, sem tag e combinado com a busca`() {
        assertEquals(listOf(2L), ids("", TagFilter.Only(20)))
        assertEquals(listOf(3L), ids("", TagFilter.Untagged))
        assertEquals(emptyList<Long>(), ids("receita", TagFilter.Only(10)))
    }
}
