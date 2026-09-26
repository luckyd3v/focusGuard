package com.focusguard.data

import java.text.Normalizer

/** Filtro de tag da lista de links. */
sealed interface TagFilter {
    data object All : TagFilter
    data object Untagged : TagFilter
    data class Only(val tagId: Long) : TagFilter
}

object LinkFilter {
    /** Busca pelo título (sem diferenciar maiúsculas nem acentos) e pela tag escolhida. */
    fun apply(links: List<TaskOccurrence>, query: String, tag: TagFilter): List<TaskOccurrence> {
        val q = normalize(query.trim())
        return links.filter { link ->
            val tagOk = when (tag) {
                TagFilter.All -> true
                TagFilter.Untagged -> link.tagId == null
                is TagFilter.Only -> link.tagId == tag.tagId
            }
            tagOk && (q.isEmpty() || normalize(link.title).contains(q))
        }
    }

    private fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()
}
