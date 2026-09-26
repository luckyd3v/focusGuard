package com.focusguard.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.ceil

/** O que foi possível descobrir sobre um link compartilhado. */
data class LinkInfo(
    val title: String?,
    val minutes: Int?,
    val source: Source?,
) {
    enum class Source { VIDEO, READING }
}

/**
 * Tenta avaliar quanto tempo leva a atividade de um link:
 *  - vídeo: duração publicada nos metadados (YouTube, Vimeo e sites com og:video/JSON-LD);
 *  - texto: tempo de leitura pelo número de palavras (~200 por minuto).
 * Sem rede, página fechada (app, login) ou sem essas informações, [LinkInfo.minutes] fica nulo e
 * o app pede a estimativa ao usuário.
 */
object LinkInspector {
    private const val WORDS_PER_MINUTE = 200
    private const val MIN_WORDS_FOR_READING = 150
    private const val MAX_BYTES = 2_000_000
    private const val TIMEOUT_MS = 8_000

    private val URL_REGEX = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

    /** Primeiro link http(s) de um texto compartilhado (apps costumam mandar "título + link"). */
    fun extractUrl(text: String?): String? =
        text?.let { URL_REGEX.find(it)?.value?.trimEnd('.', ',', ')', ']', '!', '?', ';', ':') }

    suspend fun inspect(url: String): LinkInfo = withContext(Dispatchers.IO) {
        val html = runCatching { download(url) }.getOrNull() ?: return@withContext LinkInfo(null, null, null)
        analyze(html)
    }

    /** Análise do HTML, separada da rede para poder ser testada. */
    fun analyze(html: String): LinkInfo {
        val title = extractTitle(html)
        videoSeconds(html)?.let { seconds ->
            return LinkInfo(title, ceil(seconds / 60.0).toInt().coerceAtLeast(1), LinkInfo.Source.VIDEO)
        }
        val words = countWords(html)
        if (words >= MIN_WORDS_FOR_READING) {
            return LinkInfo(title, ceil(words.toDouble() / WORDS_PER_MINUTE).toInt().coerceAtLeast(1), LinkInfo.Source.READING)
        }
        return LinkInfo(title, null, null)
    }

    // ------------------------------------------------------------ título

    fun extractTitle(html: String): String? {
        val og = metaContent(html, "og:title") ?: metaContent(html, "twitter:title")
        val tag = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.get(1)
        return (og ?: tag)?.let(::decodeEntities)?.replace(Regex("""\s+"""), " ")?.trim()?.takeIf { it.isNotEmpty() }
    }

    // ------------------------------------------------------------ vídeo

    fun videoSeconds(html: String): Long? {
        // YouTube: "lengthSeconds":"754"
        Regex(""""lengthSeconds"\s*:\s*"(\d+)"""").find(html)?.let { return it.groupValues[1].toLong().takeIf { s -> s > 0 } }
        // Open Graph / Vimeo: <meta property="video:duration" content="754">
        (metaContent(html, "video:duration") ?: metaContent(html, "og:video:duration"))
            ?.toLongOrNull()?.takeIf { it > 0 }?.let { return it }
        // schema.org: itemprop="duration" content="PT12M34S" ou "duration":"PT12M34S" no JSON-LD
        val iso = Regex("""itemprop=["']duration["'][^>]*content=["'](P[^"']+)["']""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
            ?: Regex(""""duration"\s*:\s*"(P[T0-9HMS.]+)"""").find(html)?.groupValues?.get(1)
        return iso?.let(::parseIsoDuration)?.takeIf { it > 0 }
    }

    /** "PT1H2M3S" → 3723. Aceita também dias ("P1DT2H"). */
    fun parseIsoDuration(value: String): Long? {
        val m = Regex("""P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?)?""").matchEntire(value.trim()) ?: return null
        val (d, h, min, s) = m.destructured
        if (d.isEmpty() && h.isEmpty() && min.isEmpty() && s.isEmpty()) return null
        return (d.toLongOrNull() ?: 0) * 86_400 + (h.toLongOrNull() ?: 0) * 3_600 +
            (min.toLongOrNull() ?: 0) * 60 + (s.toDoubleOrNull() ?: 0.0).toLong()
    }

    // ------------------------------------------------------------ leitura

    /** Palavras do texto visível: prefere <article>/<main> e ignora scripts, estilos e navegação. */
    fun countWords(html: String): Int {
        val flags = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        var body = Regex("""<(script|style|noscript|svg|nav|header|footer|aside|form)\b.*?</\1>""", flags).replace(html, " ")
        body = Regex("""<article\b.*?</article>""", flags).find(body)?.value
            ?: Regex("""<main\b.*?</main>""", flags).find(body)?.value
            ?: Regex("""<body\b.*?</body>""", flags).find(body)?.value
            ?: body
        val text = decodeEntities(Regex("""<[^>]+>""").replace(body, " "))
        return Regex("""[\p{L}\p{N}]{2,}""").findAll(text).count()
    }

    // ------------------------------------------------------------ utilidades

    private fun metaContent(html: String, name: String): String? {
        val n = Regex.escape(name)
        return Regex("""<meta[^>]+(?:property|name)=["']$n["'][^>]*content=["']([^"']*)["']""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
            ?: Regex("""<meta[^>]+content=["']([^"']*)["'][^>]*(?:property|name)=["']$n["']""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
    }

    private fun decodeEntities(s: String): String = s
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")

    private fun download(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.instanceFollowRedirects = true
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            // Navegador de desktop: sites entregam a página completa (com metadados) em vez da versão app.
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
            conn.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val bytes = conn.inputStream.use { input ->
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(16_384)
                while (buffer.size() < MAX_BYTES) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    buffer.write(chunk, 0, n)
                }
                buffer.toByteArray()
            }
            return String(bytes, Charsets.UTF_8)
        } finally {
            conn.disconnect()
        }
    }
}
